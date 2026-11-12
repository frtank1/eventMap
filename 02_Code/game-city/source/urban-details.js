import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';

// Original modular scenery, not claims about real shop names or attendance.
export function createUrbanDetails({buildings = [], roads = [], canPlace = () => true, maxLamps = 320} = {}) {
  const group = new THREE.Group(), nightGroup = new THREE.Group();
  group.name = 'diorama-street-details'; group.add(nightGroup);
  const palettes = {
    stone: new THREE.MeshStandardMaterial({color: 0xc9b496, roughness: .86}),
    trim: new THREE.MeshStandardMaterial({color: 0xe7d6b7, roughness: .72}),
    dark: new THREE.MeshStandardMaterial({color: 0x33454e, roughness: .62}),
    awning: new THREE.MeshStandardMaterial({color: 0x516e68, roughness: .9}),
    glass: new THREE.MeshStandardMaterial({color: 0x6c9ca1, roughness: .3, metalness: .2, emissive: 0xf2c284}),
    warm: new THREE.MeshBasicMaterial({color: 0xffd799, toneMapped: false}),
    white: new THREE.MeshBasicMaterial({color: 0xcbe9ff, toneMapped: false}),
    red: new THREE.MeshBasicMaterial({color: 0xff6556, toneMapped: false}),
  };
  const batches = Object.fromEntries(Object.keys(palettes).map(k => [k, []]));
  const box = (key,w,h,d,x,y,z,angle=0) => batches[key].push(new THREE.BoxGeometry(w,h,d).rotateY(angle).translate(x,y,z));
  function edge(key,a,b,y,width=.3,height=.3) {
    const length = Math.hypot(b[0]-a[0],b[1]-a[1]);
    if(length < .01)return;
    box(key,width,height,length,(a[0]+b[0])/2,y,(a[1]+b[1])/2,Math.atan2(b[0]-a[0],b[1]-a[1]));
  }
  const halos=[],lampGlows=[];
  for(const [bi,building] of buildings.entries()) {
    const {points:p,height:h,base=0}=building;
    if(!p?.length)continue;
    for(let i=1;i<p.length;i++) {
      const a=p[i-1],b=p[i],length=Math.hypot(b[0]-a[0],b[1]-a[1]);
      if(length<3)continue;
      const nx=(b[1]-a[1])/length,nz=-(b[0]-a[0])/length,angle=Math.atan2(nx,nz);
      edge('trim',a,b,base+h+.45,.65,.65);
      edge('stone',a,b,base+3.5,.48,.45);
      if(bi%3!==0)edge(bi%2?'warm':'white',a,b,base+h+.9,.32,.24);
      if(h<80 && length>10 && buildings.length<450){
        for(let d=0;d<length;d+=14)box('trim',.55,h,.55,a[0]+(b[0]-a[0])*d/length,base+h/2,a[1]+(b[1]-a[1])*d/length);
      }
      // A few entrances/shop windows, not uniformly tiled walls.
      if(base<1 && length>16 && length<180 && i%2===1) {
        const count=Math.min(5,Math.floor(length/9));
        for(let j=0;j<count;j++) {
          const t=(j+1)/(count+1),x=a[0]+(b[0]-a[0])*t,z=a[1]+(b[1]-a[1])*t;
          box('dark',5.5,3.4,.65,x+nx*.4,1.9,z+nz*.4,angle);
          box('glass',4.7,2.6,.16,x+nx*.79,1.9,z+nz*.79,angle);
          box('trim',.18,3,.25,x+nx*.9,1.9,z+nz*.9,angle);
          box('awning',6,.35,2.8,x+nx*1.15,3.9,z+nz*1.15,angle);
          box('warm',4.6,.15,.18,x+nx*2.45,3.65,z+nz*2.45,angle);
        }
      }
    }
  }
  let lamps=0;const lampSites=[];
  for(const road of roads) {
    const [a,b]=road.points,length=Math.hypot(b[0]-a[0],b[1]-a[1]);
    if(length<12)continue;
    const ux=(b[0]-a[0])/length,uz=(b[1]-a[1])/length,nx=uz,nz=-ux;
    const spacing=road.pedestrian?28:34;
    for(let d=12;d<length;d+=spacing) {
      for(const side of road.pedestrian?[1]:[-1,1]) {
        const width=(road.width||7)/2+1.6,x=a[0]+ux*d+nx*width*side,z=a[1]+uz*d+nz*width*side;
        if(!canPlace([x,z]))continue;
        const h=road.pedestrian?4.3:7;
        lampSites.push({x,z,h,size:road.pedestrian?12:18,rank:Math.sin(x*12.9898+z*78.233)*43758.5453%1});
      }
    }
    if(!road.pedestrian && road.width>=8){
      for(let d=8;d<length;d+=44){
        const x=a[0]+ux*d+nx*2,z=a[1]+uz*d+nz*2,angle=Math.atan2(ux,uz);
        box('white',1.6,.24,.4,x,1.2,z,angle);
        box('red',1.6,.24,.4,x-ux*4,1.2,z-uz*4,angle);
      }
    }
  }
  // Spread a bounded budget over the whole tile, never just its first streets.
  lampSites.sort((a,b)=>a.rank-b.rank);
  const occupied=new Set();
  for(const {x,z,h,size} of lampSites){const key=`${Math.floor(x/12)},${Math.floor(z/12)}`;if(occupied.has(key))continue;occupied.add(key);box('dark',.18,h,.18,x,h/2,z);box('trim',1.1,.22,1.1,x,h,z);box('warm',.75,.2,.75,x,h-.17,z);halos.push([x,.48,z,size]);lampGlows.push(x,h,z);if(++lamps>=maxLamps)break}
  for(const [key,geometries] of Object.entries(batches)){
    if(!geometries.length)continue;
    const merged=mergeGeometries(geometries);geometries.forEach(g=>g.dispose());
    const mesh=new THREE.Mesh(merged,palettes[key]);mesh.castShadow=false;mesh.receiveShadow=true;
    (['warm','white','red'].includes(key)?nightGroup:group).add(mesh);
  }
  const pixels=new Uint8Array(32*32*4);
  for(let y=0;y<32;y++)for(let x=0;x<32;x++){const i=(y*32+x)*4,r=Math.hypot((x-15.5)/15.5,(y-15.5)/15.5);pixels.set([255,210,141,Math.round(190*Math.max(0,1-r)**2)],i)}
  const texture=new THREE.DataTexture(pixels,32,32);texture.colorSpace=THREE.SRGBColorSpace;texture.needsUpdate=true;
  const poolMaterial=new THREE.MeshBasicMaterial({map:texture,transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,toneMapped:false});
  const pools=new THREE.InstancedMesh(new THREE.PlaneGeometry(1,1).rotateX(-Math.PI/2),poolMaterial,halos.length),dummy=new THREE.Object3D();
  halos.forEach(([x,y,z,s],i)=>{dummy.position.set(x,y,z);dummy.scale.set(s,1,s);dummy.updateMatrix();pools.setMatrixAt(i,dummy.matrix)});nightGroup.add(pools);
  const glowGeometry=new THREE.BufferGeometry();glowGeometry.setAttribute('position',new THREE.Float32BufferAttribute(lampGlows,3));
  const glowMaterial=new THREE.PointsMaterial({map:texture,size:3.4,transparent:true,opacity:.95,blending:THREE.AdditiveBlending,depthWrite:false,toneMapped:false});nightGroup.add(new THREE.Points(glowGeometry,glowMaterial));
  function setNight(night){nightGroup.visible=night;palettes.glass.emissiveIntensity=night?.8:0;}
  setNight(false);
  group.userData.lamps=lamps;group.userData.buildings=buildings.length;
  let disposed=false;
  return {group,setNight,dispose(){if(disposed)return;disposed=true;group.traverse(o=>{if(o.isInstancedMesh)o.dispose();o.geometry?.dispose()});Object.values(palettes).forEach(m=>m.dispose());poolMaterial.dispose();glowMaterial.dispose();texture.dispose();group.removeFromParent();group.clear()}};
}
