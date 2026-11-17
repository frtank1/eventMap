// Shared miniature-city material: broad facade bays, quiet roofs and local
// night windows. No texture downloads or per-building dynamic lights.
export function decorateQuarterMaterial(material,{glass=false,paving=false,minorPath=false,amber=false}={}){
  const night={value:0},snow={value:0};
  const previous=material.onBeforeCompile,previousKey=material.customProgramCacheKey;
  material.onBeforeCompile=function(shader,renderer){
    previous.call(this,shader,renderer);
    shader.uniforms.uQuarterNight=night;shader.uniforms.uQuarterSnow=snow;
    shader.vertexShader=shader.vertexShader.replace('#include <common>',`#include <common>
      varying vec3 vQuarterWorld;varying vec3 vQuarterNormal;`)
      .replace('#include <worldpos_vertex>',`#include <worldpos_vertex>
      vec4 quarterPosition=vec4(transformed,1.0);
      #ifdef USE_INSTANCING
        quarterPosition=instanceMatrix*quarterPosition;
      #endif
      vQuarterWorld=(modelMatrix*quarterPosition).xyz;
      vQuarterNormal=inverseTransformDirection(transformedNormal,viewMatrix);`);
    shader.fragmentShader=shader.fragmentShader.replace('#include <common>',`#include <common>
      varying vec3 vQuarterWorld;varying vec3 vQuarterNormal;
      uniform float uQuarterNight;uniform float uQuarterSnow;
      float quarterHash(vec2 p){vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
      float quarterRect(vec2 p,vec2 halfSize,vec2 aa){vec2 a=1.0-smoothstep(halfSize-aa,halfSize+aa,abs(p-.5));return a.x*a.y;}`)
      .replace('#include <emissivemap_fragment>',`#include <emissivemap_fragment>
      ${paving?`
        vec2 q=vQuarterWorld.xz/4.8;vec2 aa=max(fwidth(q),vec2(.001));
        float tile=quarterRect(fract(q),vec2(.493),aa);
        float closeFade=1.0-smoothstep(100.0,310.0,distance(cameraPosition,vQuarterWorld));
        float shade=mix(.985,1.015,quarterHash(floor(q)));
        diffuseColor.rgb*=mix(1.0,shade*mix(.94,1.0,tile),closeFade);
        ${minorPath?'diffuseColor.a*=1.0-smoothstep(90.0,280.0,distance(cameraPosition,vQuarterWorld));if(diffuseColor.a<.015)discard;':''}
      `:`
        vec3 n=normalize(vQuarterNormal);float wall=1.0-smoothstep(.2,.55,abs(n.y));
        vec2 tangent=vec2(n.z,-n.x)/max(length(n.xz),.001);
        float u=dot(vQuarterWorld.xz,tangent),y=vQuarterWorld.y;
        vec2 q=vec2(u/${glass?'9.2':'8.6'},(y-5.0)/5.4),aa=max(fwidth(q),vec2(.001));
        float facade=wall*(1.0-smoothstep(.17,.48,max(aa.x,aa.y)));
        float pane=quarterRect(fract(q),vec2(${glass?'.475,.455':'.34,.33'}),aa)*smoothstep(5.0,5.6,y);
        vec2 shop=vec2(u/6.8,y/5.0);vec2 shopAA=max(fwidth(shop),vec2(.001));
        float shopPane=quarterRect(vec2(fract(shop.x),shop.y),vec2(.4,.34),shopAA)*(1.0-step(5.0,y));
        float glazing=clamp(pane+shopPane,0.0,1.0)*facade;
        vec3 glassTint=mix(${amber?'vec3(.62,.40,.10)':glass?'vec3(.09,.36,.42)':'vec3(.07,.25,.31)'},vec3(.025,.065,.12),uQuarterNight);
        // Large, stable sky-reflection bands. Derivative filtering below keeps
        // mullions from becoming a shimmering grid as the camera moves.
        float reflection=smoothstep(.18,.65,fract(u/93.0+y/110.0));
        glassTint=mix(glassTint,${amber?'vec3(1.0,.86,.52)':'vec3(.56,.84,.9)'},reflection*${glass?'.5':'.26'}*(1.0-uQuarterNight));
        ${glass?'glassTint=mix(diffuseColor.rgb*.7,glassTint,.75);':''}
        diffuseColor.rgb=mix(diffuseColor.rgb,glassTint,glazing);
        diffuseColor.rgb*=mix(.67,1.0,smoothstep(0.0,7.0,y));
        float pier=1.0-smoothstep(.12,.17,abs(fract(q.x/3.0)-.5));
        diffuseColor.rgb=mix(diffuseColor.rgb,diffuseColor.rgb*.86,pier*facade*.5);
        float sill=smoothstep(.30,.34,fract(q.y))*(1.0-smoothstep(.36,.40,fract(q.y)));
        diffuseColor.rgb=mix(diffuseColor.rgb,diffuseColor.rgb*1.08,sill*facade*.45);
        roughnessFactor=mix(roughnessFactor,.58,glazing);
        float face=floor(dot(vQuarterWorld.xz,n.xz)*.09);
        // Glass towers have individual rooms, not luminous 20m checkerboards.
        // Filter the finer night pattern independently so distant windows fade
        // instead of aliasing while the broad daytime silhouette stays legible.
        vec2 nightQ=${glass?'vec2(u/3.8,(y-5.0)/3.6)':'q'};
        vec2 nightAA=max(fwidth(nightQ),vec2(.001));
        float nightPane=quarterRect(fract(nightQ),vec2(.32,.29),nightAA)*smoothstep(5.0,5.6,y);
        // Windows smaller than a pixel fade out to avoid aliasing; replace them with their
        // average glow, or mid-distance districts went dark at night ("lights switched off").
        float nightFar=smoothstep(.2,.5,max(nightAA.x,nightAA.y));
        nightPane*=1.0-nightFar;
        float cluster=quarterHash(floor(nightQ)+vec2(face,face*.17));
        float occupied=step(.55,cluster);
        vec3 lamp=mix(vec3(1.0,.64,.3),vec3(.64,.84,1.0),step(.94,cluster));
        totalEmissiveRadiance+=uQuarterNight*(facade*lamp*(nightPane*occupied*.34+shopPane*.48)+wall*nightFar*vec3(1.0,.66,.34)*(smoothstep(5.0,5.6,y)*.075+smoothstep(.8,1.4,y)*(1.0-smoothstep(4.4,5.0,y))*.06));
      `}
      float roof=smoothstep(.65,.92,normalize(vQuarterNormal).y);
      diffuseColor.rgb=mix(diffuseColor.rgb,vec3(.86,.9,.91),uQuarterSnow*roof);
      `);
  };
  material.customProgramCacheKey=()=>`quarter-art-9:${glass}:${paving}:${minorPath}:${amber}`;
  return {setNight:value=>{night.value=value?1:0;},setTheme:value=>{snow.value=value==='default'?0:1;},dispose(){material.onBeforeCompile=previous;material.customProgramCacheKey=previousKey;material.needsUpdate=true;}};
}
