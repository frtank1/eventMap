import * as THREE from 'three';
import data from '../source/baiterek.json';
import {mergeGeometries} from '../source/vendor/BufferGeometryUtils.js';
import {createCivicLandmarks} from '../source/civic-landmarks.js';
import {createCivicExtras} from '../source/civic-extras.js';
import {decorateQuarterMaterial} from './quarter-materials.js';
import {WORLD_CENTER,worldPosition,LON_SCALE,METERS} from './world-state.js';
import {CITY_PALETTE} from './art-direction.js';

// Original lightweight interpretations. Existing project coordinates/OSM snapshot,
// not licensed third-party meshes, interiors, official signs or surveyed facades.
export const CITY_PLACES=Object.freeze([
  {id:'adp',title:'Abu Dhabi Plaza',lng:71.42818,lat:51.12245,height:311,radius:105,parts:[['1373723368',311],['1373723370',190],['1373723369',89],['1373723363',116],['1373723364',103],['1373723365',94],['1373723366',112],['1373723367',106]]},
  {id:'ansar',title:'БЦ «Ансар»',lng:71.43157,lat:51.12346,height:76,radius:50,parts:[['230403254',9],['258752611',72]]},
  {id:'beijing',title:'Beijing Palace Soluxe',lng:71.43299,lat:51.12346,height:125,radius:47,parts:[['230403277',10],['460047238',104]]},
  {id:'moscow',title:'БЦ «Москва»',lng:71.43442,lat:51.12451,height:103,radius:72,parts:[['1530050672',9],['258345462',10],['230403257',96]]},
  {id:'petersburg',title:'БЦ «Санкт-Петербург»',lng:71.43597,lat:51.12458,height:83,radius:49,parts:[['1530050670',9],['230403283',11],['258351821',80]]},
  {id:'library',title:'Национальная академическая библиотека',lng:71.42715,lat:51.12721,height:22,radius:48,parts:[['230358855',19]]},
  {id:'talan',title:'Talan Towers',lng:71.43212,lat:51.12473,height:146,radius:68},
  {id:'khan',title:'Хан Шатыр',lng:71.40385,lat:51.1325,height:150,radius:160},
  {id:'expo',title:'EXPO · Нұр Әлем',lng:71.41629,lat:51.089227,height:100,radius:65},
  {id:'mega',title:'MEGA Silk Way',lng:71.40733,lat:51.08909,height:32,radius:265},
  {id:'museum',title:'Национальный музей',lng:71.467,lat:51.1184,height:45,radius:155},
]);
export function createCityLandmarks(){
  const group=new THREE.Group(),entries=[],materials={},geometries=new Set(),specs={ivory:CITY_PALETTE.ivory,glass:CITY_PALETTE.glass,roof:CITY_PALETTE.roof,gold:CITY_PALETTE.gold,green:CITY_PALETTE.leaf,dark:CITY_PALETTE.dark};
  group.name='Astana · authored landmarks';const byId=new Map(data.buildings.map(b=>[String(b.id),b]));
  for(const [key,[day,night]]of Object.entries(specs)){
    const m=new THREE.MeshStandardMaterial({color:day,roughness:key==='glass'?.4:.8,metalness:key==='glass'?.12:key==='gold'?.25:0});m.name=`city-${key}`;m.userData={day,night};
    if(['ivory','glass'].includes(key))m.userData.facade=decorateQuarterMaterial(m,{glass:key==='glass'});
    materials[key]=m;
  }
  for(const spec of CITY_PLACES){
    const root=new THREE.Group(),detail=new THREE.Group(),p=worldPosition(spec.lng,spec.lat),batches=new Map();root.position.set(p.x,0,p.z);root.userData.place=spec;detail.name='facade-details';root.add(detail);group.add(root);
    const add=(key,g,near=false)=>{const geo=g.index?g.toNonIndexed():g;if(geo!==g)g.dispose();geo.deleteAttribute('uv');const bucket=`${key}:${near?'detail':'main'}`;if(!batches.has(bucket))batches.set(bucket,[]);batches.get(bucket).push(geo);};
    const box=(key,w,h,d,x=0,y=h/2,z=0)=>add(key,new THREE.BoxGeometry(w,h,d).translate(x,y,z));
    const cyl=(key,r,h,y=0)=>add(key,new THREE.CylinderGeometry(r,r,h,32).translate(0,y+h/2,0));
    const bar=(key,a,b,r=.4)=>{const v=new THREE.Vector3(...a),d=new THREE.Vector3(...b).sub(v);add(key,new THREE.CylinderGeometry(r,r,d.length(),5).applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),d.clone().normalize())).translate(...v.addScaledVector(d,.5).toArray()));};
    let partCount=0;
    for(const [id,h]of spec.parts||[]){const b=byId.get(id);if(!b)continue;const s=new THREE.Shape();b.points.forEach(([lng,lat],i)=>{const x=(lng-spec.lng)*LON_SCALE,y=(lat-spec.lat)*METERS;i?s.lineTo(x,y):s.moveTo(x,y);});s.closePath();
      add(h>30&&spec.id!=='beijing'?'glass':'ivory',new THREE.ExtrudeGeometry(s,{depth:h,steps:1,bevelEnabled:false,curveSegments:1}).rotateX(-Math.PI/2).translate(0,.5,0));
      add('roof',new THREE.ExtrudeGeometry(s,{depth:.65,steps:1,bevelEnabled:false,curveSegments:1}).rotateX(-Math.PI/2).translate(0,h+.6,0));partCount++;
    }
    if(spec.parts&&!partCount)box('ivory',35,spec.height-5,35); // recognisable scale, explicitly approximate fallback.
    if(spec.id==='beijing')for(const [y,w]of [[103,46],[111,36],[118,24]]){box('green',w,2,w,0,y);add('green',new THREE.ConeGeometry(w*.65,6,4).rotateY(Math.PI/4).translate(0,y+3,0));}
    if(spec.id==='moscow'){box('ivory',26,4,58,0,98);box('gold',17,2,42,0,102);}
    if(spec.id==='petersburg')cyl('gold',20,1.4,81);
    if(spec.id==='library'){for(let i=0;i<5;i++)box('ivory',22,.4,8-i,0,.3+i*.4,30-i);}
    if(spec.id==='talan'){box('ivory',100,13,64);for(const [x,z,h]of [[-27,-8,146],[29,10,120]]){box('glass',29,h,33,x,h/2,z);box('roof',30,2,34,x,h,z);for(const dx of [-15,15])box('ivory',1.4,h,34,x+dx,h/2,z);}}
    if(spec.id==='khan'){
      cyl('ivory',123,4);cyl('glass',117,4,4);
      // Asymmetric tensioned membrane: one continuous silhouette, twelve
      // restrained cable seams at near LOD, not a dense radial wire cage.
      const positions=[],indices=[],segments=48,rings=12;
      for(let row=0;row<rings;row++){
        const u=row/rings,r=118*Math.pow(1-u,1.12);
        for(let j=0;j<=segments;j++){const a=j/segments*Math.PI*2;positions.push(Math.cos(a)*r+u*25,8+98*u,Math.sin(a)*r);}
      }
      const apex=positions.length/3;positions.push(25,106,0);
      for(let row=0;row<rings;row++)for(let j=0;j<segments;j++){
        const a=row*(segments+1)+j,b=a+1;
        if(row===rings-1)indices.push(a,apex,b);
        else{const c=a+segments+1,d=c+1;indices.push(a,c,d,a,d,b);}
      }
      const membrane=new THREE.BufferGeometry();membrane.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));membrane.setIndex(indices);membrane.computeVertexNormals();
      add('roof',membrane);bar('ivory',[25,90,0],[28,150,0],1.2);
      for(let i=0;i<12;i++){
        const a=i/12*Math.PI*2,points=[];
        for(let j=0;j<=12;j++){const u=j/12,r=118*Math.pow(1-u,1.12);points.push(new THREE.Vector3(Math.cos(a)*r+u*25,8+98*u+.12,Math.sin(a)*r));}
        add('ivory',new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points),24,.4,4,false),true);
      }
      box('glass',20,5,1,0,6.5,118);box('ivory',25,1.1,9,0,9.5,120);
      for(const x of [-11,11])box('ivory',1.5,5,8,x,6.5,120);
    }
    if(spec.id==='expo'){
      cyl('ivory',45,2);cyl('glass',40,7,2);cyl('ivory',42,1.1,9);
      add('glass',new THREE.SphereGeometry(40,40,24).translate(0,49,0));
      // Eight broad meridians plus three recessive latitude seams keep the
      // glass orb legible; close-up divisions no longer dominate its silhouette.
      for(let i=0;i<4;i++)add('ivory',new THREE.TorusGeometry(40.12,.46,4,64).rotateY(i*Math.PI/4).translate(0,49,0));
      for(const a of [-.6,0,.6])add('dark',new THREE.TorusGeometry(Math.cos(a)*40.12,.13,4,64).rotateX(Math.PI/2).translate(0,49+Math.sin(a)*40.12,0),true);
      box('glass',17,6,1,0,5,40.1);box('ivory',22,1,9,0,8.5,41);
      for(const x of [-10,10])box('ivory',1.4,6,8,x,5,41);
    }
    if(spec.id==='mega'){
      box('ivory',490,15,136);box('glass',488,9,1,0,8,69);
      const roofHeight=(x,z)=>20+5*Math.sin((x+250)/500*Math.PI*5)+4*Math.cos(z/146*Math.PI);
      const g=new THREE.PlaneGeometry(500,146,50,8).rotateX(-Math.PI/2),pos=g.attributes.position;
      for(let i=0;i<pos.count;i++)pos.setY(i,roofHeight(pos.getX(i),pos.getZ(i)));g.computeVertexNormals();add('ivory',g);root.rotation.y=.15;
      // Close the roof edge down to the existing walls. Skylights follow the
      // membrane surface instead of seven floating boxes at one fixed height.
      // Closed quad strips avoid cap triangulation through collinear wave
      // samples (which creates zero-area triangles and zero normals).
      const fascia=(width,left,right)=>{
        const geo=new THREE.BoxGeometry(width,1,.8),points=geo.attributes.position;
        for(let i=0;i<points.count;i++)points.setY(i,points.getY(i)>0?(points.getX(i)<0?left:right):14.8);
        geo.computeVertexNormals();return geo;
      };
      for(const z of [-73,73])for(let i=0;i<50;i++){
        const x=-250+i*10;add('roof',fascia(10,roofHeight(x,z),roofHeight(x+10,z)).translate(x+5,0,z));
      }
      for(const x of [-250,250])for(let i=0;i<8;i++){
        const z=-73+i*146/8;add('roof',fascia(146/8,roofHeight(x,z),roofHeight(x,z+146/8)).rotateY(-Math.PI/2).translate(x,0,z+146/16));
      }
      for(const x of [-210,-140,-70,0,70,140,210]){
        const skylight=new THREE.PlaneGeometry(24,14,4,2).rotateX(-Math.PI/2).translate(x,0,0),points=skylight.attributes.position;
        for(let i=0;i<points.count;i++)points.setY(i,roofHeight(points.getX(i),points.getZ(i))+.12);skylight.computeVertexNormals();add('glass',skylight,true);
      }
      for(const x of [-150,0,150]){box('ivory',36,1,11,x,11.8,72);for(const dx of [-17,17])box('ivory',1.3,11.3,10,x+dx,5.65,72);}
    }
    if(spec.id==='museum'){
      for(const [w,h,d,x,z]of [[85,34,72,-65,0],[80,28,105,15,5],[55,43,68,70,-18],[50,21,80,-110,12]]){box('ivory',w,h,d,x,h/2,z);box('glass',w*.5,h*.7,1,x+9,h*.55,z+d/2+.2);}root.rotation.y=-.1;
    }
    for(const [bucket,list]of batches){const [key,tier]=bucket.split(':'),geo=mergeGeometries(list);list.forEach(g=>g.dispose());geo.computeBoundingSphere();geometries.add(geo);const mesh=new THREE.Mesh(geo,materials[key]);mesh.name=`${spec.id}/${bucket}`;mesh.receiveShadow=true;(tier==='detail'?detail:root).add(mesh);}
    entries.push({entry:spec,group:root,detail});
  }
  const civic=createCivicLandmarks(WORLD_CENTER),extras=createCivicExtras(WORLD_CENTER);group.add(civic.group,extras.group);
  // Ak Orda is already supplied by the hash-checked content package.
  civic.pois.find(p=>p.id==='akorda').group.visible=false;
  for(const p of [...civic.pois.filter(p=>p.id!=='akorda'),...extras.pois]){const entry={id:p.id,title:p.title,lng:p.coord[0],lat:p.coord[1],height:p.height,radius:p.radius};p.group.userData.place=entry;entries.push({entry,group:p.group});}
  let disposed=false;
  function setAppearance(night,theme){for(const m of Object.values(materials)){m.color.setHex(night?m.userData.night:m.userData.day);m.userData.facade?.setNight(night);m.userData.facade?.setTheme(theme);}civic.setNight(night);civic.setTheme(theme);extras.setNight(night);extras.setTheme(theme);}
  function update(camera,distance){for(const p of entries){const localDistance=camera.distanceTo(p.group.position);p.group.visible=localDistance<Math.max(6500,distance*1.8);if(p.detail)p.detail.visible=localDistance<1800;}civic.updateDetail(camera);extras.updateDetail(camera);}
  function excludeBuilding(lng,lat){const p=worldPosition(lng,lat);return CITY_PLACES.some(s=>{const c=worldPosition(s.lng,s.lat);return Math.hypot(p.x-c.x,p.z-c.z)<s.radius;})||civic.excludeBuilding(lng,lat)||extras.excludeBuilding(lng,lat);}
  function destroy(){if(disposed)return;disposed=true;group.removeFromParent();geometries.forEach(g=>g.dispose());Object.values(materials).forEach(m=>{m.userData.facade?.dispose();m.dispose();});civic.dispose();extras.dispose();group.clear();}
  return {group,entries,setAppearance,update,excludeBuilding,destroy};
}
