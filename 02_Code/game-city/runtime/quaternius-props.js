/*! Quaternius Downtown City MegaKit Standard meshes, CC0-1.0.
 * Provenance and original license: art/quaternius/; flat-colour material adaptation.
 * https://quaternius.com/packs/downtowncitymegakit.html */
import * as THREE from 'three';
import data from '../source/quaternius-props.json';
import {CITY_PALETTE} from './art-direction.js';

export const QUATERNIUS_KINDS=Object.freeze(data.models.map(m=>m.kind));
export const QUATERNIUS_LIMITS=Object.freeze({sites:256,batches:5,triangles:60000,lowSites:128,lowTriangles:25000});
const roles={metal:'dark',stone:'sand',soil:'trunk'};
const finite=(value,fallback)=>value===undefined?fallback:value;

/** Sites use metres, ground-level Y, positive uniform scale, and yaw in radians.
 * Data is bundled once with the engine, never loaded from OTA or a remote URL.
 * Unrecognised/invalid sites are skipped and reported; excess sites are bounded.
 */
export function createQuaterniusProps({sites=[]}={}){
  const group=new THREE.Group();group.name='Quaternius CC0 · imported street props';
  const models=new Map(data.models.map(m=>[m.kind,m]));
  const accepted=new Map(data.models.map(m=>[m.kind,[]]));
  const batches=[],materials=new Map(),geometries=new Set(),entries=[];
  let siteCount=0,skipped=0,truncated=0,destroyed=false;
  if(!Array.isArray(sites))throw new TypeError('Quaternius sites must be an array');
  // Upper bound on iteration as well as GPU allocations for accidental huge input.
  const scanLimit=Math.min(sites.length,4096);truncated=sites.length-scanLimit;
  for(let i=0;i<scanLimit;i++){
    const site=sites[i],model=models.get(site?.kind);
    const x=site?.x,z=site?.z,y=finite(site?.y,0),yaw=finite(site?.yaw,0),scale=finite(site?.scale,1);
    if(!model||![x,y,z,yaw,scale].every(Number.isFinite)||Math.max(Math.abs(x),Math.abs(y),Math.abs(z))>100000||scale<=0||scale>10){skipped++;continue;}
    if(siteCount>=QUATERNIUS_LIMITS.sites){truncated++;continue;}
    accepted.get(site.kind).push({x,y,z,yaw,scale});siteCount++;
  }
  const transform=new THREE.Object3D();
  for(const model of data.models){
    const instances=accepted.get(model.kind);if(!instances.length)continue;
    const entry={model,instances,batches:[],visible:true};entries.push(entry);
    for(const part of model.parts){
      const geometry=new THREE.BufferGeometry();geometries.add(geometry);
      geometry.setAttribute('position',new THREE.Float32BufferAttribute(part.positions,3));
      geometry.setAttribute('normal',new THREE.Float32BufferAttribute(part.normals,3));
      geometry.setIndex(part.indices);geometry.computeBoundingBox();geometry.computeBoundingSphere();
      let material=materials.get(part.role);
      if(!material){
        material=new THREE.MeshStandardMaterial({roughness:part.role==='metal'?.68:.96,metalness:part.role==='metal'?.18:0,side:THREE.DoubleSide});
        material.name=`Quaternius flat palette · ${part.role}`;materials.set(part.role,material);
      }
      const mesh=new THREE.InstancedMesh(geometry,material,instances.length);
      mesh.name=`Quaternius ${model.kind} · ${part.role}`;
      mesh.userData={quaterniusKind:model.kind,sourceModel:model.sourceModel,sourceMaterial:part.sourceMaterial};
      mesh.instanceMatrix.setUsage(THREE.StaticDrawUsage);
      instances.forEach((site,index)=>{
        transform.position.set(site.x,site.y,site.z);transform.rotation.set(0,site.yaw,0);transform.scale.setScalar(site.scale);
        transform.updateMatrix();mesh.setMatrixAt(index,transform.matrix);
      });
      mesh.instanceMatrix.needsUpdate=true;
      // Bounds include every allocated instance, also after low-quality counts restore.
      mesh.computeBoundingBox();mesh.computeBoundingSphere();
      mesh.castShadow=false;mesh.receiveShadow=true;mesh.frustumCulled=true;
      group.add(mesh);batches.push(mesh);entry.batches.push(mesh);
    }
  }
  const winterTint=new THREE.Color();
  function setAppearance(night=false,theme='default'){
    if(destroyed)return;
    const winter=theme==='winter'||theme==='newyear';
    winterTint.setHex(night?0x9bb7c9:0xe3e9e4);
    for(const [role,material]of materials){
      material.color.setHex(CITY_PALETTE[roles[role]][night?1:0]);
      // Deliberate palette-only adaptation, not a fake snow mesh or texture detail.
      if(winter&&role!=='metal')material.color.lerp(winterTint,role==='soil'?.65:.25);
    }
  }
  function update(distance=0,quality='balanced'){
    if(destroyed)return;
    const low=quality==='low',valid=Number.isFinite(distance)&&distance>=0;
    let visibleSites=0,visibleTriangles=0;
    for(const entry of entries){
      const small=entry.model.kind==='bollard';
      const enter=small?(low?400:600):(low?600:850),exit=small?(low?480:720):(low?720:1000);
      entry.visible=valid&&distance<(entry.visible?exit:enter);
      if(entry.visible){visibleSites+=entry.instances.length;visibleTriangles+=entry.instances.length*entry.model.triangles;}
    }
    const siteBudget=low?QUATERNIUS_LIMITS.lowSites:QUATERNIUS_LIMITS.sites;
    const triangleBudget=low?QUATERNIUS_LIMITS.lowTriangles:QUATERNIUS_LIMITS.triangles;
    const fraction=Math.min(1,siteBudget/Math.max(1,visibleSites),triangleBudget/Math.max(1,visibleTriangles));
    let anyVisible=false;
    for(const entry of entries){
      const count=entry.visible?Math.floor(entry.instances.length*fraction):0;
      for(const mesh of entry.batches){mesh.count=count;mesh.visible=count>0;}
      anyVisible||=count>0;
    }
    group.visible=anyVisible;
  }
  function stats(){
    let instances=0,triangles=0;
    const byKind={};
    for(const entry of entries){
      const count=!destroyed&&group.visible?entry.batches[0].count:0;
      byKind[entry.model.kind]=count;instances+=count;triangles+=count*entry.model.triangles;
    }
    return {source:'Quaternius Downtown City MegaKit Standard',license:'CC0-1.0',sites:siteCount,skipped,truncated,
      instances,triangles,byKind,batches:destroyed?0:batches.length,drawCalls:destroyed||!group.visible?0:batches.filter(m=>m.visible&&m.count).length,
      geometries:destroyed?0:geometries.size,materials:destroyed?0:materials.size,textures:0,lights:0,destroyed};
  }
  function destroy(){
    if(destroyed)return;destroyed=true;group.removeFromParent();group.visible=false;
    for(const mesh of batches)mesh.dispose();
    for(const geometry of geometries)geometry.dispose();
    for(const material of materials.values())material.dispose();
    group.clear();
  }
  setAppearance();update();
  return {group,setAppearance,update,destroy,stats};
}
