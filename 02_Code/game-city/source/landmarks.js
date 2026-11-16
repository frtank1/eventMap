import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';
import {createStadiumMatch} from './stadium-match.js';
import {createStadiumCrowd} from './stadium-crowd.js';

// Original procedural studies, deliberately simplified; not architectural scans.
const material=(color,opts={})=>new THREE.MeshStandardMaterial({color,roughness:.7,...opts});
const boxGeo=(x,y,z,px=0,py=0,pz=0)=>new THREE.BoxGeometry(x,y,z).translate(px,py,pz);
const cylinder=(top,bottom,height,x,y,z,segments=24)=>new THREE.CylinderGeometry(top,bottom,height,segments).translate(x,y,z);
function batch(parent,geos,mat,shadow=true){if(!geos.length)return;const plain=geos.map(g=>g.index?g.toNonIndexed():g),merged=mergeGeometries(plain);const m=new THREE.Mesh(merged,mat);m.castShadow=shadow;m.receiveShadow=true;parent.add(m);new Set([...geos,...plain]).forEach(g=>g.dispose());return m;}
function mesh(parent,geo,mat,shadow=true){const m=new THREE.Mesh(geo,mat);m.castShadow=shadow;m.receiveShadow=true;parent.add(m);return m;}
function tube(points,radius=.5,segments=24,sides=5){return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p))),segments,radius,sides,false);}
function bar(a,b,r=.25){const av=new THREE.Vector3(...a),bv=new THREE.Vector3(...b),delta=bv.clone().sub(av);const g=new THREE.CylinderGeometry(r,r,delta.length(),5);g.applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),delta.normalize()));g.translate(...av.add(bv).multiplyScalar(.5).toArray());return g;}
function ring(rx,rz,y,r=.3){const pts=[];for(let i=0;i<=80;i++){const a=i/80*Math.PI*2;pts.push([Math.cos(a)*rx,y,Math.sin(a)*rz])}return tube(pts,r,80,5)}
function halfRing(rx,rz,y,start,r=.3){const pts=[];for(let i=0;i<=40;i++){const a=start+i/40*Math.PI;pts.push([Math.cos(a)*rx,y,Math.sin(a)*rz])}return tube(pts,r,40,5)}
function ellipseShape(rx,rz,innerX=0,innerZ=0){const s=new THREE.Shape();s.absellipse(0,0,rx,rz,0,Math.PI*2,false,0);if(innerX){const h=new THREE.Path();h.absellipse(0,0,innerX,innerZ,0,Math.PI*2,true,0);s.holes.push(h)}return s;}
function slab(shape,height,y){return new THREE.ExtrudeGeometry(shape,{depth:height,bevelEnabled:false,curveSegments:64}).rotateX(-Math.PI/2).translate(0,y,0)}
function markerHalo(parent,r,color){const mat=new THREE.MeshBasicMaterial({color,transparent:true,opacity:.35,side:THREE.DoubleSide,depthWrite:false});const m=mesh(parent,new THREE.RingGeometry(r,r+1.3,96).rotateX(-Math.PI/2).translate(0,.7,0),mat,false);return m;}

// A closed, folded elliptical shell. Separate profile edges preserve the broad
// roof planes; only the circumference is smoothed. The aperture stays open.
function arenaRoof(){
  const profile=[[0,31.8],[0,34.2],[.22,39],[.58,40.2],[1,34.2],[1,31.8],[.58,37.8],[.22,36.6]];
  const positions=[],indices=[],segments=96;
  for(let j=0;j<profile.length;j++){
    const offset=positions.length/3;
    for(let i=0;i<=segments;i++){
      const a=i/segments*Math.PI*2;
      for(const [t,y]of [profile[j],profile[(j+1)%profile.length]])positions.push((126-43*t)*Math.cos(a),y,(102-45*t)*Math.sin(a));
      if(i<segments){const k=offset+i*2;indices.push(k,k+1,k+3,k,k+3,k+2);}
    }
  }
  const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setIndex(indices);geometry.computeVertexNormals();return geometry;
}

function arenaFrame(angle){
  const shape=new THREE.Shape();shape.moveTo(116,2);shape.lineTo(119,2);shape.lineTo(126,33);shape.lineTo(123,34);shape.closePath();
  return new THREE.ExtrudeGeometry(shape,{depth:2.2,bevelEnabled:false,curveSegments:1}).translate(0,0,-1.1).rotateY(-angle).scale(1,1,102/126);
}

export function createBaiterek(){
  const group=new THREE.Group(),detail=new THREE.Group(),activity=new THREE.Group();group.name='Baiterek-stylized';detail.name='facade-details';group.add(detail,activity);
  const ivory=material(0xf4e3c5,{roughness:.55}),stone=material(0xcdb394),gold=material(0xd5ad57,{metalness:.32,roughness:.34,emissive:0xc88922,emissiveIntensity:.08}),glass=material(0x397b79,{metalness:.1,roughness:.4}),bronze=material(0xb99854,{metalness:.3,roughness:.45});
  const entryLight=material(0xe5cfaa,{emissive:0xffcd8c,emissiveIntensity:0});
  batch(group,[cylinder(26,28,1.5,0,.75,0,64),cylinder(22,24,1.4,0,2,0,64),cylinder(17,20,2,0,3.5,0,64)],stone);
  // A low visitor pavilion anchors the stem; the entrance is part of the main
  // silhouette, not an optional facade detail that disappears at overview LOD.
  mesh(group,cylinder(9.3,10.5,3.6,0,6.3,0,32),glass);
  batch(group,[cylinder(10,10,.6,0,8.25,0,32),boxGeo(8,.65,4,0,8.3,10.2),boxGeo(.7,3.7,3.4,-3.65,6.15,10.2),boxGeo(.7,3.7,3.4,3.65,6.15,10.2)],ivory);
  batch(detail,[boxGeo(5.8,.16,2.8,0,7.9,10.5)],entryLight,false);
  mesh(group,cylinder(2.4,3.6,72,0,40,0,20),glass);
  // Twelve broad sculpted ribbons replace the noisy fine-lattice study. The
  // monument's geographic anchor, 105m height and 42m content radius stay fixed.
  const stems=[];
  for(let i=0;i<12;i++){
    const a=i/12*Math.PI*2,radial=new THREE.Vector3(Math.cos(a),0,Math.sin(a)),tangent=new THREE.Vector3(-Math.sin(a),0,Math.cos(a));
    const curve=new THREE.CatmullRomCurve3([[13,4],[10,22],[5.4,45],[6.2,63],[9,76],[12,86],[14.2,96]].map(([r,y])=>new THREE.Vector3(r,y,0)));
    const positions=[],indices=[];
    for(let j=0;j<=32;j++){
      const t=j/32,p=curve.getPoint(t),w=(1.4-.4*Math.sin(t*Math.PI))*Math.min(1,(1-t)*10+.08);
      for(const [rw,tw]of [[-.48,-w],[.48,-w],[.48,w],[-.48,w]])positions.push(radial.x*(p.x+rw)+tangent.x*tw,p.y,radial.z*(p.x+rw)+tangent.z*tw);
      if(j)for(let k=0;k<4;k++){const u=(j-1)*4+k,v=(j-1)*4+(k+1)%4;indices.push(u,v,v+4,u,v+4,u+4);}
    }
    indices.push(0,2,1,0,3,2,128,129,130,128,130,131);
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.setIndex(indices);g.computeVertexNormals();
    // Match outward winding consistently with the radial cross-section.
    for(let j=0;j<indices.length;j+=3){const b=g.index.array[j+1];g.index.array[j+1]=g.index.array[j+2];g.index.array[j+2]=b;}g.computeVertexNormals();stems.push(g);
  }
  batch(group,stems,ivory).name='baiterek-crown-ribbons';
  mesh(group,new THREE.SphereGeometry(13,40,28).translate(0,92,0),gold).name='baiterek-golden-sphere';
  const sphereLines=[];for(const lat of [-.5,0,.5]){const rad=Math.cos(lat)*13.06,y=92+Math.sin(lat)*13.06;sphereLines.push(ring(rad,rad,y,.045))}for(let i=0;i<6;i++){const a=i/6*Math.PI*2,pts=[];for(let j=0;j<=24;j++){const b=-1.4+j/24*2.8;pts.push([Math.cos(a)*Math.cos(b)*13.06,92+Math.sin(b)*13.06,Math.sin(a)*Math.cos(b)*13.06])}sphereLines.push(tube(pts,.045,24,4))}batch(detail,sphereLines,bronze,false);
  batch(group,[ring(10.7,10.7,84,.45)],ivory,false);
  // Four breaks in the reflecting basin and grounded stairs keep the plinth
  // visually connected to the public square, instead of a floating water ring.
  const stairs=[],basins=[];
  for(let side=0;side<4;side++){
    const a=side*Math.PI/2;
    for(let i=0;i<6;i++){const h=.45+i*.8;stairs.push(boxGeo(10,h,3,0,h/2,31-i*2.7).rotateY(a));}
    basins.push(new THREE.RingGeometry(28,33,16,1,a+.2,Math.PI/2-.4).rotateX(-Math.PI/2).translate(0,.24,0));
  }
  batch(group,stairs,stone).name='baiterek-plinth-approaches';
  const pool=batch(group,basins,material(0x60938e,{roughness:.3,metalness:.12}),false);
  const halo=markerHalo(activity,36,0xb799f4),spots=[];const lightMat=new THREE.MeshBasicMaterial({color:0xe0bdff});for(let i=0;i<10;i++){const a=i/10*Math.PI*2;const p=mesh(activity,new THREE.IcosahedronGeometry(.7,0),lightMat,false);p.position.set(Math.cos(a)*36,2,Math.sin(a)*36);spots.push(p)}
  const glow=new THREE.PointLight(0xf3bd6b,0,150,2);glow.position.set(0,65,0);group.add(glow);let event='calm',night=false;
  activity.visible=false;
  return {group,detail,activity,setEvent(mode){event=mode;activity.visible=mode==='live';glow.intensity=mode==='live'?950:night?400:0;},setNight(value){night=value;ivory.emissive.set(value?0xc3a477:0x000000);ivory.emissiveIntensity=value?.08:0;gold.emissiveIntensity=value?.45:.08;entryLight.emissiveIntensity=value?.7:0;glow.intensity=event==='live'?950:value?400:0;pool.material.emissive.set(value?0x17403d:0x000000);pool.material.emissiveIntensity=.12;},animate(t){if(!activity.visible)return;halo.material.opacity=.22+Math.sin(t*1.2)*.08;spots.forEach((s,i)=>s.position.y=2+Math.sin(t+i*.8)*.6);}};
}

export function createArena(){
  const group=new THREE.Group(),detail=new THREE.Group(),activity=new THREE.Group();group.name='Astana-Arena-stylized';detail.name='facade-details';group.add(detail,activity);
  // Area-moment long axis of OSM way 166223262 in arena.json (2026-11-11),
  // using the city projection: +X east, -Z north. Keep the existing anchor.
  // Root yaw carries the pitch, crowd and local animations together; the static
  // exporter bakes it once, while the surrounding map/plaza stays unrotated.
  group.rotation.y=76.59927*Math.PI/180;
  const shell=material(0xe7d6ba,{metalness:.12,roughness:.65}),white=material(0xf3e5cb),facade=material(0x477e80,{metalness:.12,roughness:.48}),concrete=material(0xc2ae91),seatBlue=material(0x6d9d98),seatGold=material(0xc6ae76),field=material(0x719c60),dark=material(0x324e5b);
  mesh(group,slab(ellipseShape(130,105),1,0),concrete);
  mesh(group,slab(ellipseShape(118,94,100,75),25,2),facade);
  const ribs=[];for(let i=0;i<24;i++)ribs.push(arenaFrame(i/24*Math.PI*2));batch(group,ribs,white).name='arena-structural-frames';
  // Broad folded roof planes replace the floating slab and hairline rib cage.
  // Open aperture, pitch, seating and activity coordinates are unchanged.
  mesh(group,arenaRoof(),shell).name='arena-folded-roof';
  batch(group,[slab(ellipseShape(119.5,95.5,116.5,92.5),1.4,2),slab(ellipseShape(121,97,117,93),2.2,27)],white);
  for(let tier=0;tier<8;tier++){const rx=77+tier*3.1,rz=51+tier*2.5;mesh(group,slab(ellipseShape(rx,rz,rx-3.15,rz-2.55),1.5,3+tier*2.5),tier%3===0?seatGold:seatBlue,false)}
  mesh(group,boxGeo(112,.5,75,0,1.2,0),field,false);const stripes=[];for(let i=0;i<10;i+=2)stripes.push(boxGeo(10.5,.06,68,-47.25+i*10.5,1.51,0));batch(group,stripes,material(0x4f9a5a),false);
  const lines=[];const lineY=1.62;const line=(x1,z1,x2,z2,w=.22)=>{const len=Math.hypot(x2-x1,z2-z1);const g=new THREE.BoxGeometry(w,.08,len);g.rotateY(Math.atan2(x2-x1,z2-z1));g.translate((x1+x2)/2,lineY,(z1+z2)/2);lines.push(g)};
  line(-52.5,-34,52.5,-34);line(-52.5,34,52.5,34);line(-52.5,-34,-52.5,34);line(52.5,-34,52.5,34);line(0,-34,0,34);for(const sign of [-1,1]){line(sign*52.5,-20.16,sign*36,-20.16);line(sign*36,-20.16,sign*36,20.16);line(sign*36,20.16,sign*52.5,20.16);line(sign*52.5,-9.16,sign*47,-9.16);line(sign*47,-9.16,sign*47,9.16);line(sign*47,9.16,sign*52.5,9.16)}
  lines.push(new THREE.TorusGeometry(9.15,.13,4,48).rotateX(Math.PI/2).translate(0,lineY,0));batch(detail,lines,white,false);
  const goalPosts=[];for(const sign of [-1,1]){const x=sign*53;goalPosts.push(bar([x,1.6,-3.66],[x,4.04,-3.66],.12),bar([x,1.6,3.66],[x,4.04,3.66],.12),bar([x,4.04,-3.66],[x,4.04,3.66],.12));}batch(detail,goalPosts,white,false);
  // Four open entrance portals: two side piers, not a post blocking the centre.
  // Their broad frames and grounded stairs survive the optional detail LOD.
  const canopies=[],steps=[],entryGlazing=[];
  for(let i=0;i<4;i++){
    const a=i/4*Math.PI*2,x=Math.cos(a)*123,z=Math.sin(a)*99,yaw=-a+Math.PI/2;
    for(const [w,h,d,px,py,pz]of [[22,1.2,12,0,10,0],[1.5,8,11,-10.25,5.4,0],[1.5,8,11,10.25,5.4,0]]){const g=boxGeo(w,h,d,px,py,pz);g.rotateY(yaw);g.translate(x,0,z);canopies.push(g);}
    entryGlazing.push(boxGeo(17,7.6,.6,0,5.2,-4.6).rotateY(yaw).translate(x,0,z));
    for(let j=0;j<4;j++){const h=.35+j*.35,g=boxGeo(28-j*2,h,3,0,h/2,12-j*2.5);g.rotateY(yaw);g.translate(x,0,z);steps.push(g);}
  }
  batch(group,canopies,white).name='arena-entry-portals';batch(group,steps,concrete).name='arena-entry-steps';batch(group,entryGlazing,facade);
  const roofPanels=new THREE.Group();group.add(roofPanels);const roofPanelMat=material(0xd9e3df,{metalness:.3,roughness:.4,transparent:true,opacity:.94});const left=mesh(roofPanels,boxGeo(84,2,106,-42,36,0),roofPanelMat),right=mesh(roofPanels,boxGeo(84,2,106,42,36,0),roofPanelMat);roofPanels.visible=false;
  const lamps=[];const lampMat=material(0xe9deca,{emissive:0xffd59b,emissiveIntensity:.1});for(let i=0;i<20;i++){const a=i/20*Math.PI*2;lamps.push(boxGeo(3,1.2,2,Math.cos(a)*85,32,Math.sin(a)*59))}batch(group,lamps,lampMat,false);
  const stadiumLights=material(0xd8c9ac,{emissive:0xffd59b,emissiveIntensity:0,roughness:.45,toneMapped:false});
  const awayLights=material(0xd8c9ac,{emissive:0xffd59b,emissiveIntensity:0,roughness:.45,toneMapped:false});
  for(const [start,mat] of [[0,stadiumLights],[Math.PI,awayLights]]){
    // Concealed aperture lighting and entry soffits, not luminous facade ribs.
    const accents=[halfRing(84,58,32,start,.24)];
    for(let i=0;i<2;i++){const a=start+i*Math.PI/2;accents.push(boxGeo(18,.18,7,0,9.32,1).rotateY(-a+Math.PI/2).translate(Math.cos(a)*123,0,Math.sin(a)*99));}
    batch(group,accents,mat,false);
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
  function updateVenueLight(){const active=event!=='calm';facade.emissive.set(active?0x176789:night?0x826644:0x000000);facade.emissiveIntensity=active?.18:.06;stadiumLights.color.set(active?0x07141c:0xd8c9ac);awayLights.color.set(active?0x1a1706:0xd8c9ac);stadiumLights.emissive.set(!active?0xffd59b:event==='concert'?0xd8a2ff:0x6ed9ff);awayLights.emissive.set(!active?0xffd59b:event==='concert'?0x9caaff:0xf7d747);stadiumLights.emissiveIntensity=awayLights.emissiveIntensity=active?1.35:night?.65:0;lampMat.emissiveIntensity=active?5:night?1.2:.1;lights.forEach(l=>l.intensity=active?6000:0);field.emissive.set(active?0x28582f:0x000000);field.emissiveIntensity=.32;shell.emissive.set(active?0x234c63:0x000000);shell.emissiveIntensity=.12;halo.material.color.set(event==='match'?0x80d7f7:0xb497ff)}
  return {group,detail,activity,match,crowd,setEvent(mode){const next=['calm','match','concert'].includes(mode)?mode:'calm';if(next!==event)lastAnimatedTime=null;event=next;group.userData.event=event;activity.visible=event!=='calm';match.setActive(event==='match');stage.visible=event==='concert';crowd.group.visible=event!=='calm';searchlights.visible=event==='match';updateVenueLight()},setNight(value){night=value;updateVenueLight()},animate(t){if(!Number.isFinite(t)||t===lastAnimatedTime)return;lastAnimatedTime=t;left.position.x=-40;right.position.x=40;if(event==='calm')return;halo.material.opacity=.22+Math.sin(t)*.04;match.update(t);crowd.update(t);roofBeams.forEach((b,i)=>{b.rotation.z=Math.sin(t*.16+i*1.7)*.19;b.rotation.x=Math.cos(t*.13+i*1.2)*.14});beams.forEach((b,i)=>{b.rotation.z=Math.PI+Math.sin(t*.6+i)*.18;b.rotation.x=Math.cos(t*.5+i)*.2});screenMat.color.setHSL(.7+Math.sin(t*.25)*.08,.45,.65);}};
}
