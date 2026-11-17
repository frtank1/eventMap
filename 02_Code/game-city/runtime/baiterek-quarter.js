import * as THREE from 'three';
import baiterekData from '../source/baiterek.json';
import arenaData from '../source/arena.json';
import {mergeGeometries} from '../source/vendor/BufferGeometryUtils.js';
import {decorateQuarterMaterial} from './quarter-materials.js';
import {createPedestrians} from '../source/people.js';
import {createKitCrowd,KIT_CROWD_NEAR} from './kit-crowd.js';
import {QUARTER_CENTER,QUALITY,GROUND,detailLevel} from './quarter-state.js';
import {CITY_PALETTE} from './art-direction.js';
import {createLandmarkExclusion} from './landmark-footprints.js';
import {onOrInPolygon,createSpatialIndex,ringBounds} from '../source/city-overview.js';
import {createQuaterniusProps} from './quaternius-props.js';
import {createTraffic} from './traffic.js';
import {cityLodWeights,decorateLodFade} from './city-lod.js';

// Bundled OSM snapshot, not a live map/survey. Architectural details are original styling.
// This authored reference quarter is deliberately an alternative to network tiles, never an overlay
// of duplicate streets/buildings. No location, API credentials or attendance data here.
const lonScale=111320*Math.cos(QUARTER_CENTER[1]*Math.PI/180);
const seed=id=>[...String(id)].reduce((n,c)=>(n*31+c.charCodeAt(0))>>>0,7);
const centerOf=p=>p.reduce((a,b)=>[a[0]+b[0]/p.length,a[1]+b[1]/p.length],[0,0]);
const number=(v,fallback)=>Number.isFinite(Number(v))&&Number(v)>0?Number(v):fallback;
export function createBaiterekQuarter({district='baiterek',entries,catalog,replacement=null}={}){
  const arena=district==='arena',data=arena?arenaData:baiterekData,origin=arena?arenaData.center:QUARTER_CENTER;
  const project=p=>[(p[0]-origin[0])*lonScale,-(p[1]-origin[1])*111320];
  const originX=(origin[0]-QUARTER_CENTER[0])*lonScale,originZ=-(origin[1]-QUARTER_CENTER[1])*111320;
  const plazaRadius=arena?157:64,contextRadius=arena?750:1200;
  const excludeBuilding=createLandmarkExclusion(entries||[{id:district,lng:origin[0],lat:origin[1]}],catalog);
  const group=new THREE.Group(),details=new THREE.Group(),street=new THREE.Group();
  group.name=`${district} · bundled styled OSM quarter`;group.position.set(originX,0,originZ);group.add(details,street);
  const specs=Object.fromEntries(['ivory','sand','sage','glass','amber','rooftop','trim','grass','water','pavement','road','kerb','trunk','leaf','gold','dark','wood'].map(k=>[k,CITY_PALETTE[k]]));
  const materials={},partMaterials={},batches=new Map(),resources=new Set();let buildingPart=false;
  for(const [key,[day,night]]of Object.entries(specs)){
    const mat=new THREE.MeshStandardMaterial({color:day,roughness:.88,metalness:0});
    mat.userData={day,night};
    if(['ivory','sand','sage','glass','amber','pavement'].includes(key))mat.userData.facade=decorateQuarterMaterial(mat,{glass:key==='glass'||key==='amber',amber:key==='amber',paving:key==='pavement'});
    if(key==='water'){mat.roughness=.4;mat.metalness=.15;}
    materials[key]=mat;
  }
  const glow=new THREE.MeshBasicMaterial({color:0xffd49b,toneMapped:false});materials.glow=glow;
  materials.contact=new THREE.MeshBasicMaterial({color:0x203b42,transparent:true,opacity:.19,depthWrite:false});
  materials.minorPath=new THREE.MeshStandardMaterial({color:0xc9c6ad,roughness:1,transparent:true,depthWrite:false});
  materials.minorPath.userData={day:0xc9c6ad,night:0x596b7b,facade:decorateQuarterMaterial(materials.minorPath,{paving:true,minorPath:true})};
  const nightGroup=new THREE.Group();nightGroup.name='Warm night trims';details.add(nightGroup);
  function add(key,geo,detail=false,nightOnly=false){
    const part=buildingPart&&key!=='contact',k=key+':'+detail+':'+nightOnly+':'+part;
    if(!batches.has(k))batches.set(k,{key,detail,nightOnly,part,geos:[]});
    const g=geo.index?geo.toNonIndexed():geo;if(g!==geo)geo.dispose();g.deleteAttribute('uv');
    batches.get(k).geos.push(g);
  }
  function box(key,w,h,d,x,y,z,angle=0,detail=false,nightOnly=false){
    add(key,new THREE.BoxGeometry(w,h,d).rotateY(angle).translate(x,y,z),detail,nightOnly);
  }
  function shape(points){const s=new THREE.Shape();points.forEach(([x,z],i)=>i?s.lineTo(x,-z):s.moveTo(x,-z));s.closePath();return s;}
  function slab(key,p,h,y,bevel=0,detail=false,holes=[]){
    const outline=shape(p);for(const hole of holes)outline.holes.push(shape(hole));
    const geo=h<.1?new THREE.ShapeGeometry(outline,2):new THREE.ExtrudeGeometry(outline,{depth:h,steps:1,bevelEnabled:bevel>0,bevelSize:bevel,bevelThickness:bevel,bevelSegments:1,curveSegments:2});
    add(key,geo.rotateX(-Math.PI/2).translate(0,y,0),detail);
  }
  function edge(key,a,b,y,w,h,detail=false,nightOnly=false){
    const len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<.1)return;
    box(key,w,h,len,(a[0]+b[0])/2,y,(a[1]+b[1])/2,Math.atan2(b[0]-a[0],b[1]-a[1]),detail,nightOnly);
  }
  function ribbon(key,a,b,y,w){
    const len=Math.hypot(b[0]-a[0],b[1]-a[1]);
    add(key,new THREE.PlaneGeometry(w,len).rotateX(-Math.PI/2).rotateY(Math.atan2(b[0]-a[0],b[1]-a[1])).translate((a[0]+b[0])/2,y,(a[1]+b[1])/2));
  }
  let buildings=0;const footprints=[],styledBuildings=[],buildingPlaces=[];
  // Existing OSM objects, not invented high-rises or shifted POIs. These seven
  // receive an authored facade/roof treatment while retaining their footprints.
  const heroIds=new Set(['230358855','230358856','230361902','230361900','258306586','258306587','230403274']);
  const selectedBuildings=!arena&&replacement?[...data.buildings.filter(b=>!replacement.ownsGeo([b.points])),...replacement.buildings]:data.buildings;
  for(const b of selectedBuildings){
    buildingPart=b.part===true;
    const p=b.points.map(project),holes=(b.holes||[]).map(r=>r.map(project)),c=centerOf(p);if(excludeBuilding(0,0,[b.points,...(b.holes||[])]))continue;
    if(p.length<4||Math.hypot(...c)>contextRadius)continue;
    const h=Math.min(315,number(b.height,number(b.levels,b.kind==='apartments'?8:4)*3.3));
    const base=Number.isFinite(b.minHeight)?Math.max(0,b.minHeight):0;
    const hero=heroIds.has(b.id),color=b.facadeColor||(h>70?(seed(b.id)%3===0?'amber':'glass'):!buildingPart&&seed(b.id)%5===0?'sand':seed(b.id)%7===0?'sage':'ivory');
    const inset=(k)=>p.map(v=>[c[0]+(v[0]-c[0])*k,c[1]+(v[1]-c[1])*k]);
    if(b.source==='nsdi'){
      // Same palette/windows as the current quarter; no invented setbacks.
      slab(color,p,h,base+.5,0,false,holes);slab('rooftop',p,.65,base+h+.5,0,false,holes);
    }else if(hero){
      styledBuildings.push(b.id);
      const podium=Math.min(5,h*.3),shoulder=h*.78;
      slab('glass',p,podium,base+.5,.3);slab(color,inset(.985),shoulder-podium,base+podium+.5,.35);
      slab(color,inset(.88),h-shoulder,base+shoulder+.5,.4);
      slab('trim',p,.5,base+podium+.5,.15);slab('trim',inset(.995),.5,base+shoulder+.5,.15);
      slab('rooftop',inset(.84),.4,base+h+.7);
      for(let i=1;i<p.length;i++){
        const a=p[i-1],v=p[i],len=Math.hypot(v[0]-a[0],v[1]-a[1]);
        edge('trim',a,v,base+h+.4,1.1,.6,true);
        if(len>10){const count=Math.min(12,Math.floor(len/10));for(let j=1;j<=count;j++){
          const t=j/(count+1);box('trim',.8,shoulder-podium,1,a[0]+(v[0]-a[0])*t,base+(shoulder+podium)/2+.5,a[1]+(v[1]-a[1])*t,Math.atan2(v[0]-a[0],v[1]-a[1]),true);
        }}
      }
    }else{
      const near=Math.hypot(...c)<650;
      const podium=near&&h>18&&base<1?4.6:0;
      if(podium){slab('glass',inset(.99),podium,base+.5,.25);slab('trim',p,.65,base+podium+.2,.25);}
      slab(color,p,h-podium,base+podium+.5,near?.45:0);
      slab('rooftop',inset(.94),.65,base+h+.6);
      // A small roof terrace and a broad cornice read at phone scale; unlike
      // tiny ledges they do not need hundreds of extra draw calls or textures.
      if(near&&h>24){slab('rooftop',inset(.64),Math.min(2,h*.03),base+h+1,.2,true);}
      // Distant context keeps quiet roofs, not thousands of bright edge strips.
      if(h>12&&near)for(let i=1;i<p.length;i++)edge('trim',p[i-1],p[i],base+h+.9,1.15,1.2,true);
    }
    if(base<1&&b.source!=='nsdi')slab('contact',inset(1.035),.01,GROUND.contact);
    buildings++;footprints.push(p);buildingPlaces.push({p,holes,top:base+h+1.2,id:b.source==='nsdi'?b.id:`osm-${b.id}`,sourceRef:b.sourceRef||`osm:way/${b.id}`,geoRings:[b.points,...(b.holes||[])],geometryKind:'footprint',title:(b.name||'Здание').slice(0,120),lat:origin[1]-c[1]/111320,lng:origin[0]+c[0]/lonScale});
  }
  buildingPart=false;
  const buildingIndex=createSpatialIndex(80);
  for(const b of buildingPlaces){const bounds=ringBounds(b.p);buildingIndex.insert({minX:bounds.minX-.35,maxX:bounds.maxX+.35,minZ:bounds.minZ-.35,maxZ:bounds.maxZ+.35},b);}
  // Generalised plaza surface: no new roads/venues and no tiny outline maze.
  add('pavement',new THREE.CircleGeometry(plazaRadius,64).rotateX(-Math.PI/2).scale(1,1,arena?.83:1).translate(0,GROUND.plaza,0));
  // Two broad inlays make the meeting square legible at district scale. These
  // are art treatment, not a survey of paving or a new real-world installation.
  for(const radius of arena?[148,154]:[42,58])add('trim',new THREE.RingGeometry(radius,radius+1.3,64).rotateX(-Math.PI/2).scale(1,1,arena?.83:1).translate(0,GROUND.plaza+.055,0));
  const paths=[],lampSites=[],roadSegments=[],driveRoutes=[];
  for(const r of data.roads){
    const pedestrian=['footway','pedestrian','path','steps','cycleway','corridor'].includes(r.kind);
    const w=number(r.width,pedestrian?(r.kind==='pedestrian'?8:3.4):r.kind.includes('secondary')?14:r.kind==='tertiary'?10:5);
    const p=r.points.map(project);
    const routeLength=p.slice(1).reduce((n,v,i)=>n+Math.hypot(v[0]-p[i][0],v[1]-p[i][1]),0);
    const promenade=pedestrian&&routeLength>130&&p.some(v=>Math.hypot(...v)<220);
    // Through streets only: no parking aisles, and never across the square itself.
    if(['secondary','tertiary','residential','unclassified'].includes(r.kind)&&p.every(v=>Math.hypot(...v)>plazaRadius+14))driveRoutes.push({points:p,width:w});
    for(let i=1;i<p.length;i++){
      const a=p[i-1],b=p[i],len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<.3)continue;
      // Roads are flat ribbons, not thousands of closed 12-triangle boxes.
      const distance=Math.hypot(...centerOf([a,b]));
      if(distance>(arena?145:57)){
        if(!pedestrian)ribbon('kerb',a,b,GROUND.kerb,w+.5);
        const minor=pedestrian&&r.kind!=='pedestrian'&&!r.name&&!promenade;
        ribbon(pedestrian?(minor?'minorPath':'pavement'):'road',a,b,pedestrian?GROUND.path:GROUND.road,w);
      }
      roadSegments.push({a,b,w,height:distance>(arena?145:57)?(pedestrian?GROUND.path:GROUND.road):null});
      if(pedestrian&&len>12&&Math.hypot(...centerOf([a,b]))>45)paths.push([a,b]);
      if(len>25){for(let d=14;d<len;d+=pedestrian?40:52){
        const t=d/len,nx=(b[1]-a[1])/len,nz=-(b[0]-a[0])/len;
        const x=a[0]+(b[0]-a[0])*t+nx*(w/2+1.8),z=a[1]+(b[1]-a[1])*t+nz*(w/2+1.8);
        if(Math.hypot(x,z)<(arena?600:400)&&Math.hypot(x,z)>plazaRadius)lampSites.push([x,z,pedestrian?1.3:5]);
      }}
    }
  }
  const treeSites=[];
  const inside=(x,z,p)=>{let hit=false;for(let i=0,j=p.length-1;i<p.length;j=i++){const a=p[i],b=p[j];if((a[1]>z)!==(b[1]>z)&&x<(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0])hit=!hit;}return hit;};
  const onPath=(x,z)=>roadSegments.some(({a,b,w})=>{const dx=b[0]-a[0],dz=b[1]-a[1],t=THREE.MathUtils.clamp(((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz),0,1);return Math.hypot(x-a[0]-t*dx,z-a[1]-t*dz)<w*.5+2.8;});
  // Local plaza/path height only, for the opt-in walking demo; not navigation
  // or collision against the whole city. Uses the same ribbon widths as above.
  function groundHeight(x,z){
    x-=originX;z-=originZ;
    let y=Math.hypot(x,z)<plazaRadius?GROUND.plaza:GROUND.grass+.08;
    for(const {a,b,w,height}of roadSegments){
      if(height===null||height<=y)continue;
      const dx=b[0]-a[0],dz=b[1]-a[1],t=((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz);
      if(t>=0&&t<=1&&Math.hypot(x-a[0]-t*dx,z-a[1]-t*dz)<=w/2)y=height;
    }
    return y;
  }
  function placeAt(x,z){const point=[x-originX,z-originZ];let highest=null;for(const b of buildingIndex.query(point))if((!highest||b.top>highest.top)&&onOrInPolygon(point,[b.p,...b.holes]))highest=b;return highest;}
  function surfaceHeight(x,z){return placeAt(x,z)?.top??groundHeight(x,z);}
  for(const area of data.areas){
    const p=area.points.map(project);if(p.length<4)continue;
    slab(area.kind==='water'?'water':'grass',p,.08,area.kind==='water'?GROUND.water:GROUND.grass);
    if(area.kind==='water')continue;
    const xs=p.map(v=>v[0]),zs=p.map(v=>v[1]);
    for(let x=Math.min(...xs)+6;x<Math.max(...xs);x+=18){for(let z=Math.min(...zs)+6;z<Math.max(...zs);z+=18){
      const j=seed(`${area.id}:${Math.round(x)}:${Math.round(z)}`),tx=x+(j%7)-3,tz=z+((j>>4)%7)-3;
      if(Math.hypot(tx,tz)<plazaRadius+2||Math.hypot(tx,tz)>850||!inside(tx,tz,p))continue;
      if(!arena&&Math.abs(tx+58)<15&&Math.abs(tz-75)<23)continue; // isolated demo field
      if(footprints.some(f=>inside(tx,tz,f))||onPath(tx,tz))continue;
      treeSites.push([tx,tz,1.25+(j%9)*.075]);
    }}
  }
  treeSites.sort((a,b)=>Math.hypot(a[0],a[1])-Math.hypot(b[0],b[1]));treeSites.length=Math.min(treeSites.length,QUALITY.balanced.trees);
  const dummy=new THREE.Object3D();
  function instances(geometry,material,items,transform,parent=street){
    const mesh=new THREE.InstancedMesh(geometry,material,items.length);resources.add(geometry);
    items.forEach((p,i)=>{dummy.position.set(0,0,0);dummy.rotation.set(0,0,0);dummy.scale.set(1,1,1);transform(dummy,p,i);dummy.updateMatrix();mesh.setMatrixAt(i,dummy.matrix);});
    mesh.computeBoundingSphere();mesh.instanceMatrix.needsUpdate=true;parent.add(mesh);return mesh;
  }
  // Round clustered crowns with a baked dark-to-light gradient: reads as soft
  // foliage under one directional light without textures or extra passes.
  const trunks=instances(new THREE.CylinderGeometry(.34,.7,4.6,7),materials.trunk,treeSites,(o,[x,z,s])=>{o.position.set(x,2.3*s,z);o.scale.setScalar(s);});
  const lobes=[new THREE.IcosahedronGeometry(3,1).scale(1.05,.95,1).translate(-1.9,0,.3),new THREE.IcosahedronGeometry(2.8,1).translate(1.9,.3,.5),new THREE.IcosahedronGeometry(3.1,1).scale(1,1.02,1).translate(0,2.1,-.4),
    new THREE.IcosahedronGeometry(2.1,0).translate(.3,.2,-2.3),new THREE.IcosahedronGeometry(1.9,0).translate(-.4,-.2,2.5)];
  for(const g of lobes){
    const pos=g.attributes.position,values=new Float32Array(pos.count*3);
    for(let i=0;i<pos.count;i++){const t=THREE.MathUtils.clamp((pos.getY(i)+3)/8.2,0,1),k=.72+.46*t*t*(3-2*t);values[i*3]=k*(.94+.1*t);values[i*3+1]=k;values[i*3+2]=k*(.9-.12*t);}
    g.setAttribute('color',new THREE.BufferAttribute(values,3));
  }
  const canopy=mergeGeometries(lobes.map(g=>g.index?g.toNonIndexed():g));lobes.forEach(g=>g.dispose());
  materials.canopy=new THREE.MeshStandardMaterial({color:CITY_PALETTE.canopy[0],roughness:.95,metalness:0,vertexColors:true});
  materials.canopy.userData={day:CITY_PALETTE.canopy[0],night:0x3a7d72};
  const crowns=instances(canopy,materials.canopy,treeSites,(o,[x,z,s],i)=>{o.position.set(x,5.4*s,z);o.scale.setScalar(s*1.12);o.rotation.y=i*1.7;});crowns.castShadow=true;crowns.receiveShadow=true;
  const leafTone=new THREE.Color();treeSites.forEach((p,i)=>crowns.setColorAt(i,leafTone.setHex([0xffffff,0xe6f08f,0xbfe6a8,0xf2e9a0,0xcfe7b4,0xa9d98f][i%6])));crowns.instanceColor.needsUpdate=true;
  // Soft ground contact survives economical mode without an expensive SSAO
  // fullscreen pass. One instanced draw for every tree, no transparent stacks.
  const shadowPixels=new Uint8Array(32*32*4);
  for(let y=0;y<32;y++)for(let x=0;x<32;x++){const i=(y*32+x)*4,r=Math.hypot((x-15.5)/15.5,(y-15.5)/15.5);shadowPixels.set([255,255,255,Math.round(Math.pow(Math.max(0,1-r),1.4)*100)],i);}
  const shadowTex=new THREE.DataTexture(shadowPixels,32,32);shadowTex.needsUpdate=true;
  materials.treeContact=new THREE.MeshBasicMaterial({map:shadowTex,color:0x244846,transparent:true,depthWrite:false});
  const treeContacts=instances(new THREE.PlaneGeometry(16,16).rotateX(-Math.PI/2),materials.treeContact,treeSites,(o,[x,z,s])=>{o.position.set(x,GROUND.contact+.012,z);o.scale.set(s,1,s);});
  const lamps=[];
  for(const p of lampSites.sort((a,b)=>Math.hypot(a[0],a[1])-Math.hypot(b[0],b[1]))){if(lamps.length>=64)break;if(!lamps.some(q=>Math.hypot(p[0]-q[0],p[1]-q[1])<13))lamps.push(p);}
  for(const [i,[x,z,h]]of lamps.entries()){
    box('dark',.45,h,.45,x,h/2,z,0,true);box('gold',.65,.16,.65,x,h,z,0,true);box('glow',.51,.5,.51,x,h-.4,z,0,true,true);
    if(i%3===0){box('wood',3.7,.2,1.1,x+3.2,.65,z,0,true);box('wood',3.7,.8,.16,x+3.2,1.05,z-.5,0,true);for(const dx of [1.8,4.5])box('dark',.2,.6,.9,x+dx,.3,z,0,true);}
  }
  // Real CC0 vendor meshes, on a small authored planting ring. This is street
  // dressing, not surveyed installations and never an address/entrance source.
  const propSites=[],plantSites=[];
  if(!arena)for(let i=0;i<24;i++){
    const angle=(i+.5)/24*Math.PI*2;
    // Keep the four approaches to the monument completely clear.
    if(Math.abs(Math.sin(angle*2))<.4)continue;
    const x=Math.cos(angle)*52,z=Math.sin(angle)*52,y=GROUND.plaza+.01;
    // The editor and walking circuit at radius 62m are functional space, not
    // dressing space. Keep both the avatar and its orbit camera unobstructed.
    if(Math.hypot(x+24,z-57)<14)continue;
    if(footprints.some(f=>inside(x,z,f)))continue;
    propSites.push({kind:i%2?'planter':'planter-low',x,z,y,yaw:-angle,scale:1.7});
    plantSites.push([x,z,y+1.1,angle]);
  }
  for(const [i,[x,z]]of lamps.entries()){
    if(i%2===0)propSites.push({kind:'bollard',x:x+1.2,z,y:groundHeight(x+originX,z+originZ),scale:1});
  }
  const importedProps=createQuaterniusProps({sites:propSites});street.add(importedProps.group);
  const plantingParts=[new THREE.IcosahedronGeometry(1.4,1).scale(1,.6,1),new THREE.IcosahedronGeometry(.65,1).scale(1,.7,1).translate(.6,.65,0)];
  plantingParts.forEach((g,i)=>{const color=new THREE.Color(i?0xc9a7cd:CITY_PALETTE.canopy[0]),values=new Float32Array(g.attributes.position.count*3);for(let k=0;k<values.length;k+=3)color.toArray(values,k);g.setAttribute('color',new THREE.BufferAttribute(values,3));});
  const planting=mergeGeometries(plantingParts);plantingParts.forEach(g=>g.dispose());
  materials.planting=new THREE.MeshStandardMaterial({color:0xffffff,roughness:1,vertexColors:true});materials.planting.userData={day:0xffffff,night:0x748aa7};
  const shrubs=instances(planting,materials.planting,plantSites,(o,[x,z,y,a])=>{o.position.set(x,y,z);o.rotation.y=a;});shrubs.receiveShadow=true;
  // Shared contact-light texture, no per-lamp PointLight / shadow render.
  const pixels=new Uint8Array(32*32*4);
  for(let y=0;y<32;y++)for(let x=0;x<32;x++){const i=(y*32+x)*4,r=Math.hypot((x-15.5)/15.5,(y-15.5)/15.5);pixels.set([255,210,140,Math.round(Math.pow(Math.max(0,1-r),2)*145)],i);}
  const tex=new THREE.DataTexture(pixels,32,32);tex.needsUpdate=true;
  const poolMat=new THREE.MeshBasicMaterial({map:tex,transparent:true,depthWrite:false,color:0xffd49b,toneMapped:false});materials.pool=poolMat;
  instances(new THREE.PlaneGeometry(24,24).rotateX(-Math.PI/2),poolMat,lamps,(o,[x,z])=>o.position.set(x,GROUND.lamp,z),nightGroup);
  for(const batch of batches.values()){
    if(!batch.geos.length)continue;const geo=mergeGeometries(batch.geos);batch.geos.forEach(g=>g.dispose());
    if(!geo)continue;resources.add(geo);geo.computeBoundingSphere();
    let mat=materials[batch.key];
    if(batch.part){
      if(!partMaterials[batch.key]){
        const part=mat.clone();part.polygonOffset=true;part.polygonOffsetFactor=-1;part.polygonOffsetUnits=-2;
        part.userData={day:mat.userData.day,night:mat.userData.night};
        if(mat.userData.facade)part.userData.facade=decorateQuarterMaterial(part,{glass:batch.key==='glass'||batch.key==='amber',amber:batch.key==='amber'});
        partMaterials[batch.key]=part;
      }mat=partMaterials[batch.key];
    }
    const mesh=new THREE.Mesh(geo,mat);mesh.receiveShadow=true;
    mesh.castShadow=!batch.detail&&['ivory','sage','sand','glass','amber'].includes(batch.key);
    (batch.nightOnly?nightGroup:batch.detail?details:group).add(mesh);
  }
  const crowdPaths=paths.filter(([a,b])=>Math.hypot(...centerOf([a,b]))<360);
  const crowd=createPedestrians(crowdPaths,{count:44,seed:2718,scale:1});
  // Close up, skinned animated people take over from the instanced crowd.
  const kitCrowd=createKitCrowd(crowdPaths.filter(([a,b])=>Math.hypot(...centerOf([a,b]))<200),{seed:arena?811:2718,height:(x,z)=>groundHeight(x+originX,z+originZ)});street.add(kitCrowd.group);
  crowd.group.name='Decorative pedestrians · not real users';street.add(crowd.group);
  const traffic=createTraffic(driveRoutes,{height:GROUND.road+.03,seed:arena?5309:9157});street.add(traffic.group);
  const crowdLods=[];const crowdMaterials=new Set();crowd.group.traverse(o=>{if(o.material)crowdMaterials.add(o.material);});
  for(const mat of crowdMaterials)crowdLods.push(decorateLodFade(mat));
  let night=false,winter=false,level='near';
  function setAppearance(isNight,theme){night=!!isNight;winter=theme!=='default';
    for(const [key,m]of [...Object.entries(materials),...Object.entries(partMaterials)]){
      if(m.userData.day!==undefined)m.color.setHex(night?m.userData.night:m.userData.day);
      m.userData.facade?.setNight(night);m.userData.facade?.setTheme(theme);
      if(winter&&['grass','leaf','canopy','rooftop'].includes(key))m.color.setHex(night?0x8094a8:0xe1e8e5);
    }nightGroup.visible=night;importedProps.setAppearance(night,theme);traffic.setNight(night);
  }
  function update(distance,time,reduced=false,quality='balanced'){
    const next=detailLevel(distance,level);if(next!==level){level=next;details.visible=next!=='far';street.visible=next!=='far';}
    const layers=cityLodWeights(distance,quality);
    kitCrowd.group.visible=kitCrowd.ready&&level!=='far'&&distance<KIT_CROWD_NEAR&&layers.people>0;
    if(kitCrowd.group.visible)kitCrowd.update(time,reduced,quality);
    crowd.group.visible=layers.people>0&&!kitCrowd.group.visible;for(const fade of crowdLods)fade.set(layers.people);
    if(crowd.group.visible)crowd.update(time,reduced);
    if(level!=='far')traffic.update(time,reduced,quality);
    importedProps.update(distance,quality);
    trunks.count=crowns.count=treeContacts.count=Math.min(treeSites.length,(QUALITY[quality]||QUALITY.balanced).trees);
  }
  function destroy(){group.removeFromParent();kitCrowd.destroy();importedProps.destroy();traffic.destroy();group.traverse(o=>{if(o.isInstancedMesh)o.dispose();});
    crowd.group.traverse(o=>{if(o.geometry)o.geometry.dispose();});
    const crowdMats=new Set();crowd.group.traverse(o=>{if(o.material)crowdMats.add(o.material);});crowdMats.forEach(m=>m.dispose());
    resources.forEach(g=>g.dispose());for(const m of [...Object.values(materials),...Object.values(partMaterials)]){m.userData.facade?.dispose();m.dispose();}tex.dispose();shadowTex.dispose();
  }
  setAppearance(false,'default');
  return {group,update,setAppearance,groundHeight,surfaceHeight,placeAt,destroy,center:new THREE.Vector3(originX,0,originZ),district,
    stats:()=>({district,quarterBuildings:buildings,styledBuildings:[...styledBuildings],quarterTrees:street.visible?trunks.count:0,quarterPedestrians:street.visible&&crowd.group.visible?crowd.count:0,kitPedestrians:kitCrowd.group.visible?kitCrowd.count:0,quaternius:importedProps.stats(),...traffic.stats(),lod:level})};
}
