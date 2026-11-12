import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';
import {createStadiumMatch} from './stadium-match.js';
import {createStadiumCrowd} from './stadium-crowd.js';

// Original procedural studies, deliberately simplified; not architectural scans.
const material=(color,opts={})=>new THREE.MeshStandardMaterial({color,roughness:.7,...opts});
const boxGeo=(x,y,z,px=0,py=0,pz=0)=>new THREE.BoxGeometry(x,y,z).translate(px,py,pz);
const cylinder=(top,bottom,height,x,y,z,segments=24)=>new THREE.CylinderGeometry(top,bottom,height,segments).translate(x,y,z);
function batch(parent,geos,mat,shadow=true){if(!geos.length)return;const merged=mergeGeometries(geos.map(g=>g.index?g.toNonIndexed():g));const m=new THREE.Mesh(merged,mat);m.castShadow=shadow;m.receiveShadow=true;parent.add(m);geos.forEach(g=>g.dispose());return m;}
function mesh(parent,geo,mat,shadow=true){const m=new THREE.Mesh(geo,mat);m.castShadow=shadow;m.receiveShadow=true;parent.add(m);return m;}
function tube(points,radius=.5,segments=24,sides=5){return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p))),segments,radius,sides,false);}
function bar(a,b,r=.25){const av=new THREE.Vector3(...a),bv=new THREE.Vector3(...b),delta=bv.clone().sub(av);const g=new THREE.CylinderGeometry(r,r,delta.length(),5);g.applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),delta.normalize()));g.translate(...av.add(bv).multiplyScalar(.5).toArray());return g;}
function ring(rx,rz,y,r=.3){const pts=[];for(let i=0;i<=80;i++){const a=i/80*Math.PI*2;pts.push([Math.cos(a)*rx,y,Math.sin(a)*rz])}return tube(pts,r,80,5)}
function halfRing(rx,rz,y,start,r=.3){const pts=[];for(let i=0;i<=40;i++){const a=start+i/40*Math.PI;pts.push([Math.cos(a)*rx,y,Math.sin(a)*rz])}return tube(pts,r,40,5)}
function ellipseShape(rx,rz,innerX=0,innerZ=0){const s=new THREE.Shape();s.absellipse(0,0,rx,rz,0,Math.PI*2,false,0);if(innerX){const h=new THREE.Path();h.absellipse(0,0,innerX,innerZ,0,Math.PI*2,true,0);s.holes.push(h)}return s;}
function slab(shape,height,y){return new THREE.ExtrudeGeometry(shape,{depth:height,bevelEnabled:false,curveSegments:64}).rotateX(-Math.PI/2).translate(0,y,0)}
function markerHalo(parent,r,color){const mat=new THREE.MeshBasicMaterial({color,transparent:true,opacity:.35,side:THREE.DoubleSide,depthWrite:false});const m=mesh(parent,new THREE.RingGeometry(r,r+1.3,96).rotateX(-Math.PI/2).translate(0,.7,0),mat,false);return m;}

export function createBaiterek(){
  const group=new THREE.Group(),detail=new THREE.Group(),activity=new THREE.Group();group.name='Baiterek-stylized';group.add(detail,activity);
  const ivory=material(0xf7f2df,{roughness:.5}),stone=material(0xd9d2bc),gold=material(0xe1b64a,{metalness:.62,roughness:.27,emissive:0xa47616,emissiveIntensity:.12}),glass=material(0x8fbbb3,{metalness:.4,roughness:.3}),bronze=material(0xb2934b,{metalness:.5,roughness:.4});
  batch(group,[cylinder(26,28,1.5,0,.75,0,64),cylinder(22,24,1.4,0,2,0,64),cylinder(17,20,2,0,3.5,0,64)],stone);
  mesh(group,cylinder(3.8,4.8,82,0,45,0,24),glass);
  const stems=[];for(let i=0;i<28;i++){const a=i/28*Math.PI*2;const ys=[4,22,46,65,81,97],rs=[14,10,5.3,6.2,12,20.5];stems.push(tube(ys.map((y,j)=>[Math.cos(a)*rs[j],y,Math.sin(a)*rs[j]]),.6,24,5));}batch(group,stems,ivory);
  const lace=[];for(let i=0;i<14;i++){const a=i/14*Math.PI*2;for(let y=14;y<72;y+=12){const r=y<45?10-(y-14)*.15:5.4+(y-45)*.02;const b=a+Math.PI/7;lace.push(bar([Math.cos(a)*r,y,Math.sin(a)*r],[Math.cos(b)*(r*.96),y+12,Math.sin(b)*(r*.96)],.14));}}batch(detail,lace,ivory,false);
  mesh(group,new THREE.SphereGeometry(11,40,28).translate(0,94,0),gold);
  const sphereLines=[];for(const lat of [-.65,-.3,.12,.5]){const rad=Math.cos(lat)*11.03,y=94+Math.sin(lat)*11;sphereLines.push(ring(rad,rad,y,.055))}for(let i=0;i<12;i++){const a=i/12*Math.PI*2,pts=[];for(let j=0;j<=20;j++){const b=-Math.PI/2+j/20*Math.PI;pts.push([Math.cos(a)*Math.cos(b)*11.04,94+Math.sin(b)*11.04,Math.sin(a)*Math.cos(b)*11.04])}sphereLines.push(tube(pts,.055,20,4))}batch(detail,sphereLines,bronze,false);
  batch(detail,[ring(18.5,18.5,3.8,.35),ring(19.5,19.5,4.9,.18),ring(11.8,11.8,84,.35)],ivory,false);
  const stairs=[];for(let i=0;i<6;i++)stairs.push(boxGeo(11,.55,3.5,0,.28+i*.52,27-i*2.6));batch(detail,stairs,stone);
  const pool=mesh(group,new THREE.RingGeometry(28,33,64).rotateX(-Math.PI/2).translate(0,.24,0),material(0x7ebbbc,{roughness:.25,metalness:.15}),false);
  const halo=markerHalo(activity,36,0xb799f4),spots=[];const lightMat=new THREE.MeshBasicMaterial({color:0xe0bdff});for(let i=0;i<10;i++){const a=i/10*Math.PI*2;const p=mesh(activity,new THREE.IcosahedronGeometry(.7,0),lightMat,false);p.position.set(Math.cos(a)*36,2,Math.sin(a)*36);spots.push(p)}
  const glow=new THREE.PointLight(0xf3bd6b,0,150,2);glow.position.set(0,65,0);group.add(glow);let event='calm',night=false;
  activity.visible=false;
  return {group,detail,activity,setEvent(mode){event=mode;activity.visible=mode==='live';glow.intensity=mode==='live'?950:night?400:0;},setNight(value){night=value;ivory.emissive.set(value?0xe4ebff:0x000000);ivory.emissiveIntensity=value?.55:0;gold.emissiveIntensity=value?1:.12;glow.intensity=event==='live'?950:value?400:0;pool.material.emissive.set(value?0x174d58:0x000000);},animate(t){if(!activity.visible)return;halo.material.opacity=.22+Math.sin(t*1.2)*.08;spots.forEach((s,i)=>s.position.y=2+Math.sin(t+i*.8)*.6);}};
}

export function createArena(){
  const group=new THREE.Group(),detail=new THREE.Group(),activity=new THREE.Group();group.name='Astana-Arena-stylized';group.add(detail,activity);
  const shell=material(0xdfe8e1,{metalness:.25,roughness:.45}),white=material(0xf3f3e5),facade=material(0x729ca5,{metalness:.35,roughness:.35}),concrete=material(0xb8c1b8),seatBlue=material(0x568faa),seatGold=material(0xc5b270),field=material(0x6eab6a),dark=material(0x324e5b);
  mesh(group,slab(ellipseShape(130,105),1,0),concrete);
  mesh(group,slab(ellipseShape(118,94,100,75),25,2),facade);
  const ribs=[];for(let i=0;i<64;i++){const a=i/64*Math.PI*2;ribs.push(bar([Math.cos(a)*119,2,Math.sin(a)*95],[Math.cos(a)*124,34,Math.sin(a)*100],.6));}batch(group,ribs,white);
  // Open roof aperture reveals the venue's event state; not a precise roof mechanism.
  mesh(group,slab(ellipseShape(126,102,83,57),3,32),shell);
  batch(group,[ring(124,100,35.3,.8),ring(84,58,35.3,.65),ring(119,95,19,.7)],white);
  const roofRibs=[];for(let i=0;i<48;i++){const a=i/48*Math.PI*2;roofRibs.push(tube([[Math.cos(a)*125,34,Math.sin(a)*101],[Math.cos(a)*106,38,Math.sin(a)*80],[Math.cos(a)*84,34,Math.sin(a)*58]],.25,12,4))}batch(detail,roofRibs,white,false);
  for(let tier=0;tier<8;tier++){const rx=77+tier*3.1,rz=51+tier*2.5;mesh(group,slab(ellipseShape(rx,rz,rx-3.15,rz-2.55),1.5,3+tier*2.5),tier%3===0?seatGold:seatBlue,false)}
  mesh(group,boxGeo(112,.5,75,0,1.2,0),field,false);const stripes=[];for(let i=0;i<10;i+=2)stripes.push(boxGeo(10.5,.06,68,-47.25+i*10.5,1.51,0));batch(group,stripes,material(0x4f9a5a),false);
  const lines=[];const lineY=1.62;const line=(x1,z1,x2,z2,w=.22)=>{const len=Math.hypot(x2-x1,z2-z1);const g=new THREE.BoxGeometry(w,.08,len);g.rotateY(Math.atan2(x2-x1,z2-z1));g.translate((x1+x2)/2,lineY,(z1+z2)/2);lines.push(g)};
  line(-52.5,-34,52.5,-34);line(-52.5,34,52.5,34);line(-52.5,-34,-52.5,34);line(52.5,-34,52.5,34);line(0,-34,0,34);for(const sign of [-1,1]){line(sign*52.5,-20.16,sign*36,-20.16);line(sign*36,-20.16,sign*36,20.16);line(sign*36,20.16,sign*52.5,20.16);line(sign*52.5,-9.16,sign*47,-9.16);line(sign*47,-9.16,sign*47,9.16);line(sign*47,9.16,sign*52.5,9.16)}
  lines.push(new THREE.TorusGeometry(9.15,.13,4,48).rotateX(Math.PI/2).translate(0,lineY,0));batch(detail,lines,white,false);
  const goalPosts=[];for(const sign of [-1,1]){const x=sign*53;goalPosts.push(bar([x,1.6,-3.66],[x,4.04,-3.66],.12),bar([x,1.6,3.66],[x,4.04,3.66],.12),bar([x,4.04,-3.66],[x,4.04,3.66],.12));}batch(detail,goalPosts,white,false);
  const entrances=[];for(let i=0;i<16;i++){const a=i/16*Math.PI*2,g=boxGeo(8,6,1.5);g.rotateY(-a+Math.PI/2);g.translate(Math.cos(a)*120,5,Math.sin(a)*96);entrances.push(g)}batch(detail,entrances,dark,false);
  const roofPanels=new THREE.Group();group.add(roofPanels);const roofPanelMat=material(0xd9e3df,{metalness:.3,roughness:.4,transparent:true,opacity:.94});const left=mesh(roofPanels,boxGeo(84,2,106,-42,36,0),roofPanelMat),right=mesh(roofPanels,boxGeo(84,2,106,42,36,0),roofPanelMat);roofPanels.visible=false;
  const lamps=[];const lampMat=material(0xe9f2f0,{emissive:0xd5eeff,emissiveIntensity:.1});for(let i=0;i<20;i++){const a=i/20*Math.PI*2;lamps.push(boxGeo(3,1.2,2,Math.cos(a)*85,32,Math.sin(a)*59))}batch(group,lamps,lampMat,false);
  const stadiumLights=material(0xcdeeff,{emissive:0x6ed9ff,emissiveIntensity:0,roughness:.3,toneMapped:false});
  const awayLights=material(0xffefac,{emissive:0xf7d747,emissiveIntensity:0,roughness:.3,toneMapped:false});
  for(const [start,mat] of [[0,stadiumLights],[Math.PI,awayLights]]){
    batch(group,[halfRing(126.5,102.5,35.8,start,.65),halfRing(84,58,35.8,start,.5),halfRing(119.5,95.5,19.4,start,.34)],mat,false);
    const accentBars=[];for(let i=0;i<12;i++){const a=start+(i+.5)/12*Math.PI;accentBars.push(bar([Math.cos(a)*120,3,Math.sin(a)*96],[Math.cos(a)*125,32,Math.sin(a)*101],.28))}batch(group,accentBars,mat,false);
  }
  const halo=markerHalo(activity,134,0xb497ff);halo.scale.z=.82;
  const lights=[];for(const [x,z] of [[-60,-45],[60,-45],[-60,45],[60,45]]){const l=new THREE.PointLight(0xd5eaff,0,150,2);l.position.set(x,28,z);activity.add(l);lights.push(l)}
  const match=createStadiumMatch();activity.add(match.group);
  const crowd=createStadiumCrowd();activity.add(crowd.group);
  group.userData.supporterCount=crowd.supporterCount;
  group.userData.event='calm';
  // Four feathered geometry beams, not additional lights or bloom passes.
  // Each cone starts above the roof, widens upwards, and fades into the sky.
  const searchlights=new THREE.Group();searchlights.name='arena-roof-searchlights';activity.add(searchlights);
  const searchGeometry=new THREE.ConeGeometry(10,88,12,8,true).rotateX(Math.PI).translate(0,44,0);
  const searchPositions=searchGeometry.getAttribute('position'),searchColors=[];
  for(let i=0;i<searchPositions.count;i++){const u=THREE.MathUtils.clamp(searchPositions.getY(i)/88,0,1);searchColors.push(1,1,1,Math.pow(1-u,1.6))}
  searchGeometry.setAttribute('color',new THREE.Float32BufferAttribute(searchColors,4));
  const searchMaterials=[0x7bdcff,0xffe383].map(color=>new THREE.MeshBasicMaterial({color,vertexColors:true,transparent:true,opacity:.19,depthWrite:false,side:THREE.DoubleSide,forceSinglePass:true,blending:THREE.AdditiveBlending,toneMapped:false}));
  const roofBeams=[];for(const [i,[x,z]] of [[-96,-48],[96,-48],[-96,48],[96,48]].entries()){
    const beam=mesh(searchlights,searchGeometry,searchMaterials[z>=0?0:1],false);beam.name=`arena-searchlight-${i+1}`;beam.position.set(x,38,z);beam.renderOrder=2;roofBeams.push(beam);
  }
  const stage=new THREE.Group();activity.add(stage);batch(stage,[boxGeo(21,2,24,32,2.5,0),boxGeo(1,15,25,43,10,0),boxGeo(21,1,1,32,16,-12),boxGeo(21,1,1,32,16,12),boxGeo(1,13,1,22,9,-12),boxGeo(1,13,1,22,9,12)],dark);const screenMat=new THREE.MeshBasicMaterial({color:0xa58ce1});mesh(stage,boxGeo(.3,9,19,42.3,10,0),screenMat,false);
  const beams=[];for(let i=0;i<4;i++){const m=mesh(stage,new THREE.ConeGeometry(8,45,16,1,true),new THREE.MeshBasicMaterial({color:i%2?0xc4a0ff:0x80e3d0,transparent:true,opacity:.14,depthWrite:false,side:THREE.DoubleSide}),false);m.position.set(28,35,-10+i*7);m.rotation.z=Math.PI;beams.push(m)}
  let event='calm',night=false,lastAnimatedTime=null;activity.visible=false;stage.visible=false;
  function updateVenueLight(){const active=event!=='calm';facade.emissive.set(active?0x176789:night?0x123044:0x000000);facade.emissiveIntensity=active?.28:.15;stadiumLights.color.set(active?0x07141c:0xcdeeff);awayLights.color.set(active?0x1a1706:0xffefac);stadiumLights.emissive.set(event==='concert'?0xd8a2ff:0x6ed9ff);awayLights.emissive.set(event==='concert'?0x9caaff:0xf7d747);stadiumLights.emissiveIntensity=awayLights.emissiveIntensity=active?1.35:night?.7:0;lampMat.emissiveIntensity=active?5:night?1.2:.1;lights.forEach(l=>l.intensity=active?6000:0);field.emissive.set(active?0x28582f:0x000000);field.emissiveIntensity=.32;shell.emissive.set(active?0x234c63:0x000000);shell.emissiveIntensity=.35;halo.material.color.set(event==='match'?0x80d7f7:0xb497ff)}
  return {group,detail,activity,match,crowd,setEvent(mode){const next=['calm','match','concert'].includes(mode)?mode:'calm';if(next!==event)lastAnimatedTime=null;event=next;group.userData.event=event;activity.visible=event!=='calm';match.setActive(event==='match');stage.visible=event==='concert';crowd.group.visible=event!=='calm';searchlights.visible=event==='match';updateVenueLight()},setNight(value){night=value;updateVenueLight()},animate(t){if(!Number.isFinite(t)||t===lastAnimatedTime)return;lastAnimatedTime=t;left.position.x=-40;right.position.x=40;if(event==='calm')return;halo.material.opacity=.22+Math.sin(t)*.04;match.update(t);crowd.update(t);roofBeams.forEach((b,i)=>{b.rotation.z=Math.sin(t*.16+i*1.7)*.19;b.rotation.x=Math.cos(t*.13+i*1.2)*.14});beams.forEach((b,i)=>{b.rotation.z=Math.PI+Math.sin(t*.6+i)*.18;b.rotation.x=Math.cos(t*.5+i)*.2});screenMat.color.setHSL(.7+Math.sin(t*.25)*.08,.45,.65);}};
}
