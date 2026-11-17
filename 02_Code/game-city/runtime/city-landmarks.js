import * as THREE from 'three';
import data from '../source/baiterek.json';
import osm from '../source/city-landmarks-osm.json';
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
  ...notablePlaces(),
]);
// Malls and notable residential complexes from OSM outlines/parts (source/city-landmarks-osm.json).
// Heights: OSM height tag, else levels × storey (5 m in malls, 3.3 m in housing). Stylised, not surveyed.
function notablePlaces(){
  const sites=[
    {id:'keruen',site:'keruen',title:'ТРЦ «Керуен»',style:'mall'},
    {id:'asiapark',site:'asia',title:'ТРЦ Asia Park',style:'mall'},
    {id:'keruencity',site:'keruencity',title:'ТРЦ Keruen City',style:'mall'},
    {id:'astanamall',site:'astanamall',title:'ТРЦ Astana Mall',style:'mall'},
    {id:'eurasia',site:'eurasia',title:'ТРЦ «Евразия»',style:'mall'},
    {id:'severnoe',site:'severnoe',title:'ЖК «Северное сияние»',style:'tower',skin:'glass'},
    {id:'izumrud',site:'izumrud',title:'Изумрудный квартал',style:'tower',skin:'emerald'},
    {id:'triumph',site:'triumph',title:'ЖК «Триумф Астаны»',style:'tower',skin:'ivory',spire:34},
    {id:'highvill',site:'highvill',title:'ЖК Highvill Astana',style:'tower',skin:'ivory'},
    {id:'europecity',site:'europe',title:'ЖК Europe City',style:'tower',skin:'ivory'},
    {id:'lavie',site:'lavie',title:'ЖК La Vie',style:'tower',skin:'ivory'},
    {id:'dara',site:'dara',title:'ЖК Dara Residence',style:'tower',skin:'glass'},
  ];
  return sites.map(site=>{
    const all=osm.buildings.filter(b=>b.site===site.site),storey=site.style==='mall'?5:3.3;
    const fallback=Math.max(...all.map(b=>b.levels||0))||3;
    // A site outline without its own height is the ground plan for its parts, not a volume.
    const parts=all.filter(b=>b.part||b.height||b.levels||all.length===1||b.kind==='parking').map(b=>[String(b.id),Math.max(6,Math.round(b.height||(b.levels||(b.kind==='parking'?2:fallback))*storey))]);
    // Anchor at a mapped outline's centre (landmark exclusion only trusts footprints that contain
    // it), choosing the outline that keeps the whole complex closest (model radius ≤ 500 m).
    const pts=all.flatMap(b=>b.points),centre=r=>[r.reduce((a,p)=>a+p[0],0)/r.length,r.reduce((a,p)=>a+p[1],0)/r.length];
    const reach=([x0,y0])=>Math.max(...pts.map(([x,y])=>Math.hypot((x-x0)*LON_SCALE,(y-y0)*METERS)));
    const within=([x,y],r)=>{let c=false;for(let i=0,j=r.length-1;i<r.length;j=i++){const [xi,yi]=r[i],[xj,yj]=r[j];if((yi>y)!==(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)c=!c;}return c;};
    const box=[(Math.min(...pts.map(p=>p[0]))+Math.max(...pts.map(p=>p[0])))/2,(Math.min(...pts.map(p=>p[1]))+Math.max(...pts.map(p=>p[1])))/2];
    // Courtyard blocks (e.g. Dara) have no centre inside a ring: the site's mapped area holds the anchor instead.
    const [lng,lat]=all.filter(b=>parts.some(p=>p[0]===String(b.id))).map(b=>centre(b.points)).filter((c,i,list)=>within(c,all.filter(b=>parts.some(p=>p[0]===String(b.id)))[i].points)).sort((a,b)=>reach(a)-reach(b))[0]||box;
    const radius=Math.ceil(Math.max(...pts.map(([x,y])=>Math.hypot((x-lng)*LON_SCALE,(y-lat)*METERS)))+4);
    return {...site,lng:+lng.toFixed(6),lat:+lat.toFixed(6),height:Math.max(...parts.map(p=>p[1]))+(site.spire||0)+8,radius,parts};
  });
}
// Oriented bounding rectangle of a part outline in local metres (x east, y north).
// Roof embellishments are placed on this rectangle: they used to be axis-aligned
// at the model origin and sat across, or skewed on, the building below them.
export function orientedRect(points){
  let best=null;
  for(let i=0;i<points.length;i++){
    const a=points[i],b=points[(i+1)%points.length],len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<.5)continue;
    const c=(b[0]-a[0])/len,s=(b[1]-a[1])/len;let u0=Infinity,u1=-Infinity,v0=Infinity,v1=-Infinity;
    for(const [x,y]of points){const u=x*c+y*s,v=-x*s+y*c;u0=Math.min(u0,u);u1=Math.max(u1,u);v0=Math.min(v0,v);v1=Math.max(v1,v);}
    const w=u1-u0,d=v1-v0;if(best&&w*d>=best.area)continue;
    const u=(u0+u1)/2,v=(v0+v1)/2;
    best={area:w*d,angle:Math.atan2(s,c)+(w>=d?0:Math.PI/2),x:u*c-v*s,north:u*s+v*c,length:Math.max(w,d),width:Math.min(w,d)};
  }
  return best;
}
export function createCityLandmarks(){
  const group=new THREE.Group(),entries=[],materials={},geometries=new Set(),specs={ivory:CITY_PALETTE.ivory,glass:CITY_PALETTE.glass,roof:CITY_PALETTE.roof,gold:CITY_PALETTE.gold,green:CITY_PALETTE.leaf,dark:CITY_PALETTE.dark,emerald:CITY_PALETTE.emerald};
  group.name='Astana · authored landmarks';const byId=new Map([...data.buildings,...osm.buildings].map(b=>[String(b.id),b]));
  for(const [key,[day,night]]of Object.entries(specs)){
    const glassy=key==='glass'||key==='emerald';
    const m=new THREE.MeshStandardMaterial({color:day,roughness:glassy?.4:.8,metalness:glassy?.12:key==='gold'?.25:0});m.name=`city-${key}`;m.userData={day,night};
    if(['ivory','glass','emerald'].includes(key))m.userData.facade=decorateQuarterMaterial(m,{glass:glassy});
    materials[key]=m;
  }
  for(const spec of CITY_PLACES){
    const root=new THREE.Group(),detail=new THREE.Group(),p=worldPosition(spec.lng,spec.lat),batches=new Map();root.position.set(p.x,0,p.z);root.userData.place=spec;detail.name='facade-details';root.add(detail);group.add(root);
    const add=(key,g,near=false)=>{const geo=g.index?g.toNonIndexed():g;if(geo!==g)g.dispose();geo.deleteAttribute('uv');const bucket=`${key}:${near?'detail':'main'}`;if(!batches.has(bucket))batches.set(bucket,[]);batches.get(bucket).push(geo);};
    const box=(key,w,h,d,x=0,y=h/2,z=0)=>add(key,new THREE.BoxGeometry(w,h,d).translate(x,y,z));
    const cyl=(key,r,h,y=0)=>add(key,new THREE.CylinderGeometry(r,r,h,32).translate(0,y+h/2,0));
    const bar=(key,a,b,r=.4)=>{const v=new THREE.Vector3(...a),d=new THREE.Vector3(...b).sub(v);add(key,new THREE.CylinderGeometry(r,r,d.length(),5).applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),d.clone().normalize())).translate(...v.addScaledVector(d,.5).toArray()));};
    let partCount=0,crown=null; // tallest source part: what a roof embellishment stands on
    // Local +x is east and -z is north, so a positive Y rotation turns east toward north.
    const onCrown=(key,geometry,y)=>add(key,geometry.rotateY(crown?.angle||0).translate(crown?.x||0,y,crown?-crown.north:0));
    for(const [id,h]of spec.parts||[]){const b=byId.get(id);if(!b)continue;const s=new THREE.Shape();b.points.forEach(([lng,lat],i)=>{const x=(lng-spec.lng)*LON_SCALE,y=(lat-spec.lat)*METERS;i?s.lineTo(x,y):s.moveTo(x,y);});s.closePath();
      if(!crown||h>crown.h){const rect=orientedRect(b.points.map(([lng,lat])=>[(lng-spec.lng)*LON_SCALE,(lat-spec.lat)*METERS]));if(rect)crown={h,...rect};}
      const band=(key,y0,y1)=>{if(y1-y0>.2)add(key,new THREE.ExtrudeGeometry(s,{depth:y1-y0,steps:1,bevelEnabled:false,curveSegments:1}).rotateX(-Math.PI/2).translate(0,y0+.5,0));};
      if(spec.style==='mall'){
        // Shop-front glazing at street level, a sign band under the parapet.
        band('glass',0,Math.min(5,h));if(h>9){band('ivory',5,h-3.2);band('gold',h-3.2,h-1.7);band('ivory',h-1.7,h);}else band('ivory',5,h);
      }else if(spec.skin){band(h>30?spec.skin:'ivory',0,Math.min(h,6));band(h>30?spec.skin:'ivory',Math.min(h,6),h);}
      else add(h>30&&spec.id!=='beijing'?'glass':'ivory',new THREE.ExtrudeGeometry(s,{depth:h,steps:1,bevelEnabled:false,curveSegments:1}).rotateX(-Math.PI/2).translate(0,.5,0));
      add(spec.style==='tower'?'ivory':'roof',new THREE.ExtrudeGeometry(s,{depth:.65,steps:1,bevelEnabled:false,curveSegments:1}).rotateX(-Math.PI/2).translate(0,h+.6,0));partCount++;
      // Tower crowns: a recessed glass lantern and a slim cap on every high-rise part.
      if(spec.style==='tower'&&h>60){const rect=orientedRect(b.points.map(([lng,lat])=>[(lng-spec.lng)*LON_SCALE,(lat-spec.lat)*METERS]));
        if(rect){const w=rect.length*.62,d=rect.width*.62,crownKey=spec.skin;
          add(crownKey,new THREE.BoxGeometry(w,5,d).rotateY(rect.angle).translate(rect.x,h+1.2+2.5,-rect.north));
          add('ivory',new THREE.BoxGeometry(w*1.08,.8,d*1.08).rotateY(rect.angle).translate(rect.x,h+1.2+5.4,-rect.north));}}
    }
    if(spec.parts&&!partCount)box('ivory',35,spec.height-5,35); // recognisable scale, explicitly approximate fallback.
    if(spec.style==='mall'&&crown){for(const [dx,dz] of [[-.18,-.1],[.12,.15],[.22,-.2]])onCrown('dark',new THREE.BoxGeometry(6,3,4).translate(dx*crown.length,0,dz*crown.width),crown.h+2.1);}
    if(spec.spire&&crown){onCrown('ivory',new THREE.CylinderGeometry(3.2,4.4,6,12),crown.h+1+6+3);onCrown('gold',new THREE.ConeGeometry(2.2,spec.spire,12),crown.h+1+12+spec.spire/2);}
    if(spec.id==='beijing')for(const [y,w]of [[103,46],[111,36],[118,24]]){onCrown('green',new THREE.BoxGeometry(w,2,w),y);onCrown('green',new THREE.ConeGeometry(w*.65,6,4).rotateY(Math.PI/4),y+3);}
    // Long side along the tower: the cap is 58 x 26 m on a 97 x 28 m tower top.
    if(spec.id==='moscow'){onCrown('ivory',new THREE.BoxGeometry(58,4,26),98);onCrown('gold',new THREE.BoxGeometry(42,2,17),102);}
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
