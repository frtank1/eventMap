import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';
import {createCivicLandmarks} from './civic-landmarks.js';
import {createCivicExtras} from './civic-extras.js';

// Original photo-informed silhouettes. Not surveyed architectural models.
const mat=(color,opts={})=>new THREE.MeshStandardMaterial({color,roughness:.66,...opts});
const box=(w,h,d,x=0,y=0,z=0)=>new THREE.BoxGeometry(w,h,d).translate(x,y,z);
const cylinder=(r,h,y=0,n=40)=>new THREE.CylinderGeometry(r,r,h,n).translate(0,y,0);
function add(g,geo,m){const mesh=new THREE.Mesh(geo,m);mesh.castShadow=true;mesh.receiveShadow=true;g.add(mesh);return mesh;}
function batch(g,geos,m){if(!geos.length)return;const geo=mergeGeometries(geos.map(x=>x.index?x.toNonIndexed():x));geos.forEach(x=>x.dispose());return add(g,geo,m);}
function solid(points,h,y=0,scale=1){const c=points.reduce((a,p)=>[a[0]+p[0]/points.length,a[1]+p[1]/points.length],[0,0]);const s=new THREE.Shape();points.forEach((p,i)=>{const x=c[0]+(p[0]-c[0])*scale,z=-(c[1]+(p[1]-c[1])*scale);i?s.lineTo(x,z):s.moveTo(x,z)});s.closePath();return new THREE.ExtrudeGeometry(s,{depth:h,bevelEnabled:false}).rotateX(-Math.PI/2).translate(0,y,0);}
function bar(a,b,r=.28){const v=new THREE.Vector3(...a),w=new THREE.Vector3(...b),d=w.clone().sub(v);return new THREE.CylinderGeometry(r,r,d.length(),5).applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),d.normalize())).translate(...v.add(w).multiplyScalar(.5).toArray());}
function ring(rx,rz,y,r=.35){const pts=[];for(let i=0;i<=64;i++){const a=i/64*Math.PI*2;pts.push(new THREE.Vector3(Math.cos(a)*rx,y,Math.sin(a)*rz))}return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts),64,r,5,false);}
function sign(g,text,w,y,z,color='#d3c299'){const c=document.createElement('canvas');c.width=1024;c.height=128;const x=c.getContext('2d');x.fillStyle=color;x.font='600 68px system-ui';x.textAlign='center';x.textBaseline='middle';x.fillText(text,512,64,1000);const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;const m=new THREE.MeshBasicMaterial({map:t,transparent:true,side:THREE.DoubleSide,depthWrite:false});add(g,new THREE.PlaneGeometry(w,w/8).translate(0,y,z),m);}

export function createCityLandmarks(data,center,{extended=false}={}){
  const group=new THREE.Group(),detail=new THREE.Group();group.add(detail);const pois=[],excludeIds=new Set(),excluded=[],roofRecords=[],facadeDetails=[],nightTrims=[];let windowCount=0;
  const project=p=>[(p[0]-center[0])*111320*Math.cos(center[1]*Math.PI/180),-(p[1]-center[1])*111320];
  const ivory=mat(0xe0d3ba),blue=mat(0x416d7b,{roughness:.32,metalness:.24,emissive:0x12393f,emissiveIntensity:0}),teal=mat(0x56818a,{roughness:.35,metalness:.28}),gold=mat(0xc5a45c,{metalness:.4}),dark=mat(0x233c4c),roof=mat(0x6a7e83),green=mat(0x4b7168),red=mat(0x9f5144);
  const warmGlass=mat(0x9caaa7,{emissive:0xffba73,emissiveIntensity:0,roughness:.4}),coolGlass=mat(0x7da9b4,{emissive:0x9bdaef,emissiveIntensity:0,roughness:.3});
  const trimLight=new THREE.MeshBasicMaterial({color:0xffe7b5,toneMapped:false});
  const byId=new Map(data.buildings.map(b=>[b.id,b]));
  function at(id,title,coord,height,radius,description){const g=new THREE.Group(),p=project(coord);g.position.set(p[0],0,p[1]);group.add(g);g.userData.poi=id;pois.push({id,title,coord,p:[p[0],height*.48,p[1]],label:[p[0],height+12,p[1]],height,radius,description,group:g});excluded.push({coord,radius});return g;}
  function edgeLights(g,points,y){const geos=[];for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i];if(Math.hypot(b[0]-a[0],b[1]-a[1])>.1)geos.push(bar([a[0],y,a[1]],[b[0],y,b[1]],.22))}const m=batch(g,geos,trimLight);if(m){m.castShadow=false;m.visible=false;nightTrims.push(m)}}
  function roofAt(g,points,h){roofRecords.push({g,points,height:h});edgeLights(g,points,h+.45)}
  function shape(id,g,h,m,y=0,scale=1){const b=byId.get(id);if(!b)return;excludeIds.add(id);const points=b.points.map(p=>{const q=project(p);return[q[0]-g.position.x,q[1]-g.position.z]});add(g,solid(points,h,y,scale),m);roofAt(g,points,h+y);return points;}
  function windows(g,points,height,y,spacing){
    const sets=[[],[],[]],area=points.reduce((sum,p,i)=>{const q=points[(i+1)%points.length];return sum+p[0]*q[1]-q[0]*p[1]},0),sign=area>=0?1:-1;
    for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i],len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<4)continue;const nx=sign*(b[1]-a[1])/len,nz=-sign*(b[0]-a[0])/len;
      for(let d=2.5;d<len-2;d+=5.2)for(let level=spacing*1.5;level<height-2;level+=spacing){if(windowCount>=18000)break;const n=(Math.floor(d*5)+Math.floor(level*3)+i*13+windowCount++)%11,k=n<3?1:n<6?2:0;sets[k].push(new THREE.BoxGeometry(3.4,spacing*.66,.26).rotateY(Math.atan2(nx,nz)).translate(a[0]+(b[0]-a[0])*d/len+nx*.24,y+level,a[1]+(b[1]-a[1])*d/len+nz*.24))}
    }
    sets.forEach((geos,i)=>{const m=batch(g,geos,[dark,warmGlass,coolGlass][i]);if(m){m.castShadow=false;facadeDetails.push({mesh:m,g})}});
  }
  function grid(g,points,height,y=0,{spacing=4.1,vertical=true}={}){if(!points)return;const geos=[];for(let level=spacing;level<height;level+=spacing)geos.push(solid(points,.22,y+level,1.003));batch(g,geos,ivory);if(vertical){const ribs=[];for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i],l=Math.hypot(b[0]-a[0],b[1]-a[1]);for(let d=0;d<l;d+=10.4){const t=d/l;ribs.push(box(.35,height,.35,a[0]+(b[0]-a[0])*t,y+height/2,a[1]+(b[1]-a[1])*t))}}batch(g,ribs,ivory)}windows(g,points,height,y,spacing)}
  function rectangularTower(g,w,h,d,x,z){add(g,box(w,h,d,x,h/2,z),blue);const points=[[x-w/2,z-d/2],[x+w/2,z-d/2],[x+w/2,z+d/2],[x-w/2,z+d/2],[x-w/2,z-d/2]];grid(g,points,h,0,{spacing:4.4});add(g,box(w+1,2,d+1,x,h,z),roof);roofAt(g,points,h+1)}

  const adp=at('adp','Abu Dhabi Plaza',[71.42818,51.12245],311,105,'Стеклянные башни и ступенчатый силуэт. Форма стилизована; высота главной башни — около 311 м.');
  for(const [id,h] of [['1373723368',310.8],['1373723370',190],['1373723369',89],['1373723363',116],['1373723364',103],['1373723365',94],['1373723366',112],['1373723367',106]]){const p=shape(id,adp,h,blue);grid(adp,p,h)}
  // Shared podium and hotel remain their OSM geometry; only tower parts replaced.
  sign(adp,'QAZAQSTAN',36,301,15,'#eae9d9');
  const ansar=at('ansar','БЦ «Ансар»',[71.43157,51.12346],68,50,'БЦ «Ансар» на Сыганак. Светлые вертикальные рёбра, стеклянная башня и низкий стилобат.');
  shape('230403254',ansar,9,ivory);const ap=shape('258752611',ansar,64,blue,8);grid(ansar,ap,64,8);sign(ansar,'АНСАР',25,76,17,'#d3d7d2');
  const hotel=at('beijing','Beijing Palace Soluxe',[71.43299,51.12346],121,47,'Отель рядом с «Ансаром». Ступенчатые кровли передают характер здания; это игровая реконструкция.');
  shape('230403277',hotel,10,ivory);const hp=shape('460047238',hotel,98,ivory,6);grid(hotel,hp,98,6,{spacing:3.8});
  if(hp){const cx=hp.reduce((s,p)=>s+p[0],0)/hp.length,cz=hp.reduce((s,p)=>s+p[1],0)/hp.length;for(const [y,s] of [[96,1.12],[105,.91],[112,.68]]){add(hotel,solid(hp,1.8,y,s),green);add(hotel,solid(hp,6,y+1.8,s*.7),red);const scaled=hp.map(p=>[cx+(p[0]-cx)*s,cz+(p[1]-cz)*s]);roofAt(hotel,scaled,y+1.8)}add(hotel,new THREE.ConeGeometry(8,8,4).rotateY(Math.PI/4).translate(0,121,0),green)}
  const moscow=at('moscow','БЦ «Москва»',[71.43442,51.12451],103,72,'Здание-лайнер на Достык: округлый корпус, горизонтальные полосы и верхние террасы.');
  shape('1530050672',moscow,9,ivory);excludeIds.add('258345463');shape('258345462',moscow,10,ivory);const mp=shape('230403257',moscow,88,blue,8);grid(moscow,mp,88,8,{spacing:3.5,vertical:false});if(mp){add(moscow,solid(mp,5,96,.81),ivory);add(moscow,solid(mp,3,101,.62),gold)}
  const sp=at('petersburg','БЦ «Санкт-Петербург»',[71.43597,51.12458],83,49,'Цилиндрическая стеклянная башня и невысокая галерея. Силуэт восстановлен по контурам и фотографиям.');
  shape('1530050670',sp,9,ivory);shape('230403283',sp,11,blue);const spp=shape('258351821',sp,79,blue,1);grid(sp,spp,79,1,{spacing:3.6});if(spp)add(sp,solid(spp,2,80,1.02),gold);
  const library=at('library','Национальная библиотека',[71.42715,51.12721],22,48,'Национальная академическая библиотека: светлый фасад, стеклянные вставки и входная лестница.');
  const lp=shape('230358855',library,19,ivory);if(lp){const lines=[];for(let i=1;i<lp.length;i++){const a=lp[i-1],b=lp[i],l=Math.hypot(b[0]-a[0],b[1]-a[1]);for(let d=3;d<l-3;d+=5){const t=d/l;lines.push(box(1.7,13,1.7,a[0]+(b[0]-a[0])*t,10,a[1]+(b[1]-a[1])*t))}}batch(library,lines,blue);add(library,solid(lp,1.5,19,1.04),ivory)}
  for(let i=0;i<5;i++)add(library,box(21,1,9-i,0,.5+i*.4,33-i*.4),ivory);sign(library,'КІТАПХАНА',29,18,33,'#9c8658');
  const talan=at('talan','Talan Towers · Ritz-Carlton',[71.43212,51.12473],146,68,'Две светлые башни Talan Towers и общая торговая галерея. Размеры и фасады упрощены.');talan.rotation.y=.24;add(talan,box(100,13,64,0,6.5,0),ivory);rectangularTower(talan,29,146,33,-27,-8);rectangularTower(talan,29,120,33,29,10);

  if(extended){
    const khan=at('khan','Хан Шатыр',[71.40385,51.1325],150,160,'Характерный наклонный шатёр. Авторская облегчённая модель для игровой карты.');
    add(khan,cylinder(123,8,4,64),ivory);const cone=new THREE.ConeGeometry(118,98,64,12);const p=cone.attributes.position;for(let i=0;i<p.count;i++){const y=p.getY(i);p.setX(i,p.getX(i)+(y+49)/98*25)}cone.computeVertexNormals();add(khan,cone.translate(0,57,0),mat(0xc1d7cc,{metalness:.15,roughness:.4}));add(khan,bar([25,90,0],[28,150,0],1.2),ivory);const cables=[];for(let i=0;i<36;i++){const a=i/36*Math.PI*2;cables.push(bar([Math.cos(a)*118,8,Math.sin(a)*118],[25,106,0],.35))}batch(khan,cables,ivory);
    const expo=at('expo','EXPO · Нұр Әлем',[71.41629,51.089227],100,65,'Сфера павильона EXPO — отдельная игровая модель со стеклянными меридианами.');add(expo,cylinder(45,10,5,64),ivory);add(expo,new THREE.SphereGeometry(40,48,32).translate(0,49,0),blue);const rings=[];for(let a=-1.1;a<1.2;a+=.22)rings.push(ring(Math.cos(a)*40.2,Math.cos(a)*40.2,49+Math.sin(a)*40,.22));for(let i=0;i<24;i++){const a=i/24*Math.PI*2,points=[];for(let j=0;j<=32;j++){const b=-Math.PI/2+j/32*Math.PI;points.push(new THREE.Vector3(Math.cos(a)*Math.cos(b)*40.2,49+Math.sin(b)*40.2,Math.sin(a)*Math.cos(b)*40.2))}rings.push(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points),32,.24,4,false))}batch(expo,rings,ivory);
    const mega=at('mega','MEGA Silk Way',[71.40733,51.08909],30,260,'Длинный торговый комплекс с волнообразной кровлей и световыми фонарями. Стилизация по фотографиям.');mega.rotation.y=.15;
    add(mega,box(490,15,136,0,7.5,0),ivory);add(mega,box(488,9,1,0,8,69),blue);const vertices=[],indices=[],nx=80,nz=8;for(let i=0;i<=nx;i++)for(let j=0;j<=nz;j++){const x=-250+i/nx*500,z=-73+j/nz*146;vertices.push(x,19+5*Math.sin(i/nx*Math.PI*5)+4*Math.sin(j/nz*Math.PI),z)}for(let i=0;i<nx;i++)for(let j=0;j<nz;j++){const a=i*(nz+1)+j,b=a+nz+1;indices.push(a,a+1,b,a+1,b+1,b)}const geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));geo.setIndex(indices);geo.computeVertexNormals();add(mega,geo,ivory);for(let i=-210;i<=210;i+=70)add(mega,box(33,1.3,19,i,27,0),blue);sign(mega,'MEGA SILK WAY',100,21,75,'#b14c51');
    const museum=at('museum','Национальный музей',[71.467,51.1184],45,155,'Белые ступенчатые объёмы, синие стеклянные плоскости и открытая площадь. Приближённая модель по фото.');museum.rotation.y=-.1;
    for(const [w,h,d,x,z] of [[85,34,72,-65,0],[80,28,105,15,5],[55,43,68,70,-18],[50,21,80,-110,12]]){add(museum,box(w,h,d,x,h/2,z),ivory);add(museum,box(w*.5,h*.7,1,x+9,h*.55,z+d/2+.2),blue)}add(museum,box(220,1,66,-20,.5,95),mat(0xcac5b3));sign(museum,'ҰЛТТЫҚ МУЗЕЙ',100,25,60,'#af9560');
  }
  const civic=extended?[createCivicLandmarks(center),createCivicExtras(center)]:[];
  for(const layer of civic){group.add(layer.group);pois.push(...layer.pois)}
  group.updateMatrixWorld(true);
  const roofs=roofRecords.map(r=>({points:r.points.map(p=>{const v=new THREE.Vector3(p[0],0,p[1]).applyMatrix4(r.g.matrixWorld);return[v.x,v.z]}),height:r.height,base:0}));
  roofs.push(...civic.flatMap(layer=>layer.roofs));
  return {group,detail,pois,roofs,excludeIds,excluded,setNight(night){blue.emissiveIntensity=night?.025:0;teal.emissiveIntensity=0;warmGlass.emissiveIntensity=night?1.5:0;coolGlass.emissiveIntensity=night?1.3:0;nightTrims.forEach(m=>m.visible=night);civic.forEach(layer=>layer.setNight(night))},setTheme(theme){civic.forEach(layer=>layer.setTheme(theme))},updateDetail(camera){facadeDetails.forEach(({mesh,g})=>mesh.visible=camera.distanceTo(g.position)<2200);civic.forEach(layer=>layer.updateDetail(camera))},excludeBuilding(lon,lat){return civic.some(layer=>layer.excludeBuilding(lon,lat))||excluded.some(e=>Math.hypot((lon-e.coord[0])*70000,(lat-e.coord[1])*111320)<e.radius)}};
}
