// One geographic world, six presentation bands; never six copies of its meshes.
export const CITY_LOD_VERSION='astana-six-layers-1';
export const CITY_LOD_BANDS=Object.freeze([
  {id:'person',title:'Персонаж',max:60},
  {id:'building',title:'Здание',max:350},
  {id:'street',title:'Улица',max:1000},
  {id:'quarter',title:'Квартал',max:2600},
  {id:'district',title:'Район',max:7500},
  {id:'city',title:'Город',max:Infinity},
].map(Object.freeze));
export function cityLodBand(distance,previous){
  const d=Number.isFinite(distance)?Math.max(0,distance):Infinity;
  const at=CITY_LOD_BANDS.findIndex(b=>b.id===previous);
  if(at>=0&&d>=(at?CITY_LOD_BANDS[at-1].max*.9:0)&&d<CITY_LOD_BANDS[at].max*1.1)return CITY_LOD_BANDS[at];
  return CITY_LOD_BANDS.find(b=>d<b.max)||CITY_LOD_BANDS.at(-1);
}
export function distanceWeight(distance,full,off){
  if(!Number.isFinite(distance))return 0;
  const t=Math.max(0,Math.min(1,(distance-full)/(off-full)));
  return 1-t*t*(3-2*t);
}
export function cityLodWeights(distance,quality='balanced'){
  const economy=quality==='low'?.8:1;
  return {people:distanceWeight(distance,500*economy,950*economy),
    furniture:distanceWeight(distance,850*economy,1700*economy),
    vehicles:distanceWeight(distance,1000*economy,2100*economy),
    roofs:distanceWeight(distance,1700*economy,3100*economy),
    trees:distanceWeight(distance,3200*economy,5700*economy),
    // Keep this identical to backdrop coverage in both quality modes.
    tiles:distanceWeight(distance,4200,6100)};
}
// Screen-fixed ordered coverage. No time/random seed, alpha sorting, transparent
// city copies, depth-write changes, or shader recompilation during a zoom.
export const LOD_COVERAGE_GLSL=`
float cityCoverageSample(){
  vec2 p=mod(floor(gl_FragCoord.xy),4.0);
  vec2 a=mod(p,2.0),b=floor(p/2.0);
  return ((mod(a.x+a.y,2.0)*2.0+a.y)*4.0+mod(b.x+b.y,2.0)*2.0+b.y+.5)/16.0;
}
`;
export function decorateLodFade(material){
  const weight={value:1},compile=material.onBeforeCompile,key=material.customProgramCacheKey;
  material.onBeforeCompile=function(shader,renderer){
    compile.call(this,shader,renderer);shader.uniforms.uCityLodWeight=weight;
    shader.fragmentShader='uniform float uCityLodWeight;\n'+LOD_COVERAGE_GLSL+shader.fragmentShader;
    shader.fragmentShader=shader.fragmentShader.replace('#include <clipping_planes_fragment>',
      '#include <clipping_planes_fragment>\nif(cityCoverageSample()>=uCityLodWeight)discard;');
  };
  material.customProgramCacheKey=function(){return `${key.call(this)}:city-lod-1`;};
  return {set(value){weight.value=Math.max(0,Math.min(1,Number.isFinite(value)?value:0));},get value(){return weight.value;}};
}
