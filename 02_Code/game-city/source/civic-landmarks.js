import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';

// Original, photo-informed game assets. Coordinates/footprint orientation were
// checked against OpenFreeMap/OSM; these are not surveyed architectural models.
export const CIVIC_PLACES=Object.freeze([
  {id:'akorda',title:'Ак Орда',coord:[71.44647,51.12580],height:86,radius:78,yaw:-1.823,footprint:[57,42,0],viewDirection:[-1.1,.72,-.35],description:'Белый дворец, колоннада, синий купол и золотой шпиль. Авторская игровая стилизация внешнего облика; не модель внутренних помещений.'},
  {id:'hazret',title:'Мечеть Хазрет Султан',coord:[71.47219,51.12542],height:77,radius:95,yaw:2.25,footprint:[60,96,20],viewDirection:[1,.75,-1],description:'Белые купола, четыре минарета, арочные окна и парадный портал. Детали упрощены для игровой карты.'},
  {id:'grandmosque',title:'Главная мечеть Астаны',coord:[71.40957,51.07242],height:130,radius:175,yaw:2.27,footprint:[83,135,62],focusLocalZ:65,viewDirection:[1,.8,-1],description:'Большой синий купол, четыре высоких минарета и открытый двор с галереями. Облегчённая авторская модель, не точная архитектурная копия.'},
]);

const TAU=Math.PI*2;
const project=(p,c)=>[(p[0]-c[0])*111320*Math.cos(c[1]*Math.PI/180),-(p[1]-c[1])*111320];
function snowMaterial(color,options={}){
  const material=new THREE.MeshStandardMaterial({color,roughness:.76,metalness:0,...options});
  const snow={value:0};
  material.onBeforeCompile=shader=>{
    shader.uniforms.uCivicSnow=snow;
    shader.vertexShader=shader.vertexShader.replace('#include <common>','#include <common>\nvarying vec3 vCivicNormal;').replace('#include <worldpos_vertex>','#include <worldpos_vertex>\nvCivicNormal = normalize(mat3(modelMatrix) * objectNormal);');
    shader.fragmentShader=shader.fragmentShader.replace('#include <common>','#include <common>\nvarying vec3 vCivicNormal; uniform float uCivicSnow;').replace('#include <color_fragment>','#include <color_fragment>\nfloat civicSnow = uCivicSnow * smoothstep(0.65,0.96,normalize(vCivicNormal).y);\ndiffuseColor.rgb = mix(diffuseColor.rgb,vec3(0.86,0.93,0.97),civicSnow);');
  };
  material.customProgramCacheKey=()=> 'eventmap-civic-snow-v1';material.userData.snow=snow;
  return material;
}

function materials(){return {
  ivory:snowMaterial(0xe7dfcc,{emissive:0xcbb78f,emissiveIntensity:0}),
  trim:snowMaterial(0xf2ead9,{roughness:.65,emissive:0xd8bf8c,emissiveIntensity:0}),
  stone:snowMaterial(0xb2b1a0), blue:snowMaterial(0x368ca7,{roughness:.4,metalness:.18}),
  gold:snowMaterial(0xc7a25c,{metalness:.45,roughness:.4}),
  glass:snowMaterial(0x3c626d,{roughness:.3,metalness:.2,emissive:0xffcf8a,emissiveIntensity:0}),
  dark:snowMaterial(0x48616a),
  glow:new THREE.MeshBasicMaterial({color:0xffdca5,toneMapped:false}),
};}

function builder(spec,center,palette){
  const group=new THREE.Group(),detail=new THREE.Group(),night=new THREE.Group();group.name=`civic/${spec.id}`;detail.name='facade-details';night.name='architectural-lighting';group.add(detail,night);
  const [x,z]=project(spec.coord,center);group.position.set(x,0,z);group.rotation.y=spec.yaw;
  const buckets=new Map(),geometries=new Set();
  function put(key,geometry,near=false){const bucket=`${key}:${near?'detail':'body'}`;if(!buckets.has(bucket))buckets.set(bucket,[]);buckets.get(bucket).push(geometry);}
  const box=(k,w,h,d,x=0,y=0,z=0,near=false)=>put(k,new THREE.BoxGeometry(w,h,d).translate(x,y,z),near);
  const cyl=(k,r1,r2,h,x,y,z,n=16,near=false)=>put(k,new THREE.CylinderGeometry(r1,r2,h,n).translate(x,y,z),near);
  function bar(k,a,b,r=.25,near=true){const p=new THREE.Vector3(...a),q=new THREE.Vector3(...b),v=q.clone().sub(p);if(v.length()<.001)return;put(k,new THREE.CylinderGeometry(r,r,v.length(),6).applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),v.normalize())).translate(...p.add(q).multiplyScalar(.5).toArray()),near)}
  function ring(k,r,y,x=0,z=0,tube=.22){put(k,new THREE.TorusGeometry(r,tube,4,r>10?32:16).rotateX(Math.PI/2).translate(x,y,z),true)}
  function dome(k,r,h,x,y,z,ribs=16){
    const profile=[new THREE.Vector2(0,y+h)];
    const segments=r>10?28:14,vertical=r>10?10:6,steps=6;
    for(let i=1;i<=vertical;i++){const a=i/vertical*Math.PI/2;profile.push(new THREE.Vector2(r*Math.sin(a),y+h*Math.cos(a)))}
    put(k,new THREE.LatheGeometry(profile.reverse(),segments).translate(x,0,z));ring('trim',r,y,x,z,.4);
    for(let j=0;j<ribs;j++){const a=j/ribs*TAU;for(let i=0;i<steps;i++){const t=i/steps*Math.PI/2,u=(i+1)/steps*Math.PI/2;bar(k==='blue'?'gold':'trim',[x+(r+.12)*Math.sin(t)*Math.cos(a),y+(h+.12)*Math.cos(t),z+(r+.12)*Math.sin(t)*Math.sin(a)],[x+(r+.12)*Math.sin(u)*Math.cos(a),y+(h+.12)*Math.cos(u),z+(r+.12)*Math.sin(u)*Math.sin(a)],k==='blue'?.10:.16,true)}}
  }
  function crescent(x,y,z,r=1){put('gold',new THREE.TorusGeometry(r,r*.14,6,24,Math.PI*1.62).rotateZ(Math.PI*.19).translate(x,y,z),true)}
  function arch(k,w,h,x,y,z,angle=0,near=true){
    const shape=new THREE.Shape();shape.moveTo(-w/2,0);shape.lineTo(w/2,0);shape.lineTo(w/2,h-w*.55);shape.quadraticCurveTo(w*.48,h-w*.2,0,h);shape.quadraticCurveTo(-w*.48,h-w*.2,-w/2,h-w*.55);shape.closePath();
    put(k,new THREE.ExtrudeGeometry(shape,{depth:.3,bevelEnabled:false,curveSegments:7}).rotateY(angle).translate(x,y,z),near);
  }
  function arches(width,h,y,z,step=7,angle=0){for(let x=-width/2+step/2;x<width/2;x+=step){const v=new THREE.Vector3(x,y,z).applyAxisAngle(new THREE.Vector3(0,1,0),angle);arch('trim',step*.66,h,v.x,v.y,v.z,angle);const p=new THREE.Vector3(x,y+.55,z+.35).applyAxisAngle(new THREE.Vector3(0,1,0),angle);arch('glass',step*.48,h-1,p.x,p.y,p.z,angle);}}
  function minaret(x,z,h,blue=false){
    const shaft=h*.64,base=7,capStart=h-11;
    box('ivory',8,base,8,x,base/2,z);cyl('trim',4.3,4.6,1,x,base,z,8);
    cyl('ivory',2.1,3.2,shaft,x,base+shaft/2,z,16);
    for(const y of [h*.32,h*.60,h*.79]){cyl('trim',4.0,3.2,1.6,x,y,z,16);ring('glow',3.7,y+.5,x,z,.12);for(let j=0;j<12;j++){const a=j/12*TAU;bar('ivory',[x+3.6*Math.cos(a),y+.5,z+3.6*Math.sin(a)],[x+3.6*Math.cos(a),y+2.2,z+3.6*Math.sin(a)],.12)}}
    cyl('ivory',2.1,2.5,capStart-(base+shaft),x,(capStart+base+shaft)/2,z,16);
    for(let j=0;j<8;j++){const a=j/8*TAU;arch('dark',1,5,x+2.15*Math.sin(a),capStart-7,z+2.15*Math.cos(a),a)}
    dome(blue?'blue':'ivory',2.8,5,x,capStart,z,8);bar('gold',[x,capStart+5,z],[x,h-1.4,z],.13);crescent(x,h-.8,z,.7);
  }
  function roof(w,d,y,x=0,z=0){box('trim',w+1.2,.9,d+1.2,x,y,z);for(const side of [-1,1]){box('glow',w,.16,.16,x,y+.6,z+side*d/2,true);box('glow',.16,.16,d,x+side*w/2,y+.6,z,true)}}
  function stairs(w,z,y=0){for(let i=0;i<6;i++)box('stone',w,.28,2.2,0,y+.14+i*.28,z-i*1.8)}
  function finish(){for(const [bucket,list] of buckets){const [key,kind]=bucket.split(':'),plain=list.map(g=>{const v=g.index?g.toNonIndexed():g;v.deleteAttribute('uv');v.clearGroups();if(v!==g)g.dispose();return v});const merged=mergeGeometries(plain);plain.forEach(g=>g.dispose());const m=new THREE.Mesh(merged,palette[key]);m.castShadow=key!=='glow'&&key!=='glass';m.receiveShadow=true;m.name=`${spec.id}/${bucket}`;(key==='glow'?night:kind==='detail'?detail:group).add(m);geometries.add(merged)}buckets.clear();return {group,detail,night,geometries,spec};}
  return {box,cyl,bar,ring,dome,crescent,arch,arches,minaret,roof,stairs,put,finish};
}

function akorda(b){
  const {box,cyl,bar,ring,dome,roof}=b;
  box('stone',112,1.8,78,0,.9,0);box('ivory',102,27,62,0,15.3,0);
  box('ivory',91,6.4,51,0,32,0);roof(104,64,29.2);roof(93,53,35.5);
  for(const y of [4.3,18.3,27.9])box('trim',103,.65,63,0,y,0);
  // Recessed glazing between tall pilasters; the rear and sides are finished too.
  for(const side of [-1,1])for(let x=-45;x<=45;x+=7.5){for(const y of [7,14.8,22.5,32])box('glass',4.4,y===32?2:5.2,.38,x,y,side*(y===32?25.65:31.2),true);box('trim',1.05,24,.9,x+3,16,side*31.5,true)}
  for(const side of [-1,1])for(let z=-25;z<=25;z+=6)for(const y of [7,15,23])box('glass',.35,5.1,3.3,side*51.2,y,z,true);
  // Rounded front portico, separate columns and capitals.
  b.put('glass',new THREE.CylinderGeometry(18.3,18.3,25,32,1,false,-Math.PI/2,Math.PI).translate(0,15,25));
  // The freestanding portico columns are defining architecture, so remain at
  // every detail level. Capitals are broad blocks, not a forest of tiny rails.
  for(let j=0;j<9;j++){const a=-Math.PI*.46+j/8*Math.PI*.92,x=Math.sin(a)*20,z=25+Math.cos(a)*20;cyl('ivory',1.05,1.3,25,x,15,z,12);cyl('trim',1.6,1.55,.9,x,3,z,12);box('trim',3,1.2,3,x,27.5,z)}
  b.put('trim',new THREE.CylinderGeometry(21,21,1.7,40,1,false,-Math.PI/2,Math.PI).translate(0,29,25));
  for(let i=0;i<6;i++){const h=(i+1)*.28;box('stone',36,h,2.2,0,h/2,54-i*1.8);}
  box('dark',12,10,.7,0,7,44);
  box('trim',14,1,2.4,0,12.5,43.9);box('glow',10,.18,1.4,0,11.92,44.3);
  cyl('ivory',17,17,3.8,0,37,0,40);dome('blue',17.2,20,0,39,0,10);
  cyl('gold',.45,1.6,23,0,70.5,0,16);cyl('gold',.18,.45,2,0,83,0,12);
  b.put('gold',new THREE.SphereGeometry(1.0,12,8).translate(0,84.8,0),true);
  for(let j=0;j<12;j++){const a=j/12*TAU;bar('gold',[Math.cos(a)*1.2,84.8+Math.sin(a)*1.2,0],[Math.cos(a)*1.6,84.8+Math.sin(a)*1.6,0],.08)}
  ring('gold',17.4,39.2);ring('glow',17.2,39.7,0,0,.13);
  // A continuous stone parapet reads as a cornice at mobile scale. No invented
  // inscriptions or ornamental pin lattice around the roofline.
  for(const z of [-31.4,31.4])box('ivory',102,1.3,.85,0,30.35,z);
  for(const x of [-50.6,50.6])box('ivory',.85,1.3,62,x,30.35,0);
}

function hazret(b){
  const {box,cyl,dome,minaret,roof,arch,arches,bar}=b;
  box('stone',114,1.4,108,0,.7,0);box('ivory',98,21,88,0,12,0);roof(100,90,23);
  box('ivory',53,7,48,0,26.7,-10);roof(55,50,30.5,0,-10);
  cyl('ivory',15.6,15.6,4.5,0,32.5,-10,40);dome('ivory',14.05,16,0,35,-10,24);b.crescent(0,52.3,-10,1.1);
  for(const [x,z,r,y] of [[-32,-26,7,23],[32,-26,7,23],[-32,12,7,23],[32,12,7,23],[-16,-35,5.5,29],[16,-35,5.5,29],[-16,18,5.5,29],[16,18,5.5,29]]){cyl('ivory',r,r,2.2,x,y+1,z);dome('ivory',r,6,x,y+2,z,10);b.crescent(x,y+9,z,.65)}
  for(const x of [-48,48])for(const z of [-43,43])minaret(x,z,77);
  for(const angle of [0,Math.PI])arches(91,10,5,44.3,6.7,angle);
  for(const angle of [-Math.PI/2,Math.PI/2])arches(80,10,5,49.3,6.7,angle);
  box('ivory',30,28,9,0,15,47);roof(32,11,29.6,0,47);
  arch('dark',21,23,0,2,51.7);arch('trim',18,21,0,2.3,52.1);arch('glass',14.5,18.5,0,2.8,52.45);
  for(const x of [-13,13]){box('trim',.6,25,.6,x,15,52,true);bar('glow',[x,3,52.5],[x,27,52.5],.13)}
  for(let y=5;y<27;y+=3)for(const x of [-11.1,11.1])box('gold',.42,.42,.3,x,y,52.8,true);
  b.stairs(29,63);
}

function grandMosque(b){
  const {box,cyl,dome,minaret,roof,arches,arch}=b;
  box('stone',164,1.5,260,0,.75,62);
  box('ivory',108,33,108,0,18,0);roof(110,110,35.3);
  box('ivory',74,8,74,0,39,0);roof(75,75,43.5);
  cyl('ivory',32,32,7,0,47.5,0,48);dome('blue',31,32,0,51,0,32);b.crescent(0,85,0,1.4);
  // Four larger auxiliary blue domes, each on its own square drum.
  for(const x of [-34,34])for(const z of [-34,34]){box('ivory',28,6,28,x,38,z);cyl('ivory',13.5,13.5,3,x,42,z,24);dome('blue',13,12,x,43.5,z,16);b.crescent(x,56.5,z,.8)}
  // Open courtyard; never fill the space between the galleries with a solid box.
  for(const x of [-71,71]){box('ivory',18,20,145,x,11,114);roof(20,147,21.5,x,114)}
  box('ivory',142,20,18,0,11,178);roof(144,20,21.5,0,178);
  for(const x of [-72,72])for(const z of [55,181])minaret(x,z,130,true);
  for(let z=55;z<176;z+=12)for(const x of [-71,71]){dome('ivory',5.6,4.5,x,22,z,0);for(const side of [-1,1])arch('glass',5,13,x+side*9.25,3,z,side*Math.PI/2)}
  for(let x=-58;x<=58;x+=11.6){dome('ivory',5.1,4.3,x,22,178,0);arch('glass',5,13,x,3,187.25);arch('glass',5,13,x,3,168.75,Math.PI)}
  for(const angle of [0,Math.PI])arches(103,19,7,54.3,9,angle);
  for(const angle of [-Math.PI/2,Math.PI/2])arches(103,19,7,54.3,9,angle);
  box('ivory',33,28,10,0,15.3,184);roof(35,12,30,0,184);
  arch('dark',23,24,0,2,189.3);arch('trim',20,22,0,2.5,189.7);arch('glass',16,19,0,3,190);
  b.stairs(34,202);
  // Subtle paved courtyard pattern, not fictional shops or advertising.
  for(let x=-48;x<=48;x+=16)for(let z=70;z<161;z+=16)box('trim',6,.08,6,x,1.58,z,true);
}

export function createCivicLandmarks(center){
  if(!Array.isArray(center)||center.length!==2||!center.every(Number.isFinite))throw new TypeError('center must be [lon,lat]');
  const group=new THREE.Group();group.name='civic-landmarks';const palette=materials(),records=[],pois=[];
  const build={akorda,hazret,grandmosque:grandMosque};
  for(const spec of CIVIC_PLACES){const b=builder(spec,center,palette);build[spec.id](b);const record=b.finish();records.push(record);group.add(record.group);const p=record.group.position,focusZ=spec.focusLocalZ||0;pois.push({...spec,p:[p.x+Math.sin(spec.yaw)*focusZ,spec.height*.45,p.z+Math.cos(spec.yaw)*focusZ],label:[p.x,spec.height+12,p.z],group:record.group})}
  let disposed=false;
  function setNight(night){if(disposed)return;records.forEach(r=>r.night.visible=Boolean(night));palette.ivory.emissiveIntensity=night?.13:0;palette.trim.emissiveIntensity=night?.22:0;palette.glass.emissiveIntensity=night?.5:0;palette.blue.emissive.setHex(0x1f5062);palette.blue.emissiveIntensity=night?.16:0;}
  function setTheme(theme){if(disposed)return;for(const m of Object.values(palette))if(m.userData.snow)m.userData.snow.value=theme==='winter'||theme==='newyear'?1:0;}
  function updateDetail(camera){if(disposed||!camera)return;records.forEach(r=>r.detail.visible=camera.distanceTo(r.group.position)<2400)}
  function excludeBuilding(lon,lat){const [x,z]=project([lon,lat],center);return records.some(({spec,group:g})=>{const dx=x-g.position.x,dz=z-g.position.z,c=Math.cos(spec.yaw),s=Math.sin(spec.yaw),lx=c*dx-s*dz,lz=s*dx+c*dz;const [hx,hz,cz]=spec.footprint;return Math.abs(lx)<hx&&Math.abs(lz-cz)<hz})}
  function dispose(){if(disposed)return;disposed=true;group.removeFromParent();for(const r of records){for(const g of r.geometries)g.dispose();r.group.clear()}Object.values(palette).forEach(m=>m.dispose());group.clear()}
  setNight(false);setTheme('default');
  return {group,pois,roofs:[],setNight,setTheme,updateDetail,excludeBuilding,dispose};
}
