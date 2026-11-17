// Builds characters/people-{men,women}.glb from the Quaternius "Ultimate Modular Men/Women"
// packs (CC0, data/quaternius-modular/, gitignored). Every outfit part (Head, Body, Legs,
// Feet) is rebound to one shared skeleton, so the runtime can mix head, top, bottom and shoes.
// Needs the full three package (devDependency) — runs in Node, no native converter.
import {readFileSync,writeFileSync,mkdirSync,readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import {FBXLoader} from 'three/examples/jsm/loaders/FBXLoader.js';
import {GLTFExporter} from 'three/examples/jsm/exporters/GLTFExporter.js';
import {mergeVertices} from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import {WebIO} from '@gltf-transform/core';
import {KHRMeshQuantization} from '@gltf-transform/extensions';
import {weld,dedup,quantize,resample,prune} from '@gltf-transform/functions';

const here=path.dirname(fileURLToPath(import.meta.url));
const src=path.resolve(process.argv[2]||path.join(here,'data/quaternius-modular'));
const out=path.join(here,'characters');
// City-appropriate outfits only (no kings, space suits, armour or horse heads).
export const OUTFITS={men:['Casual_2','Casual_Hoodie','Suit','Worker','Beach','Farmer','Punk','Adventurer'],
  women:['Casual','Formal','Suit','Worker','Punk','Adventurer']};
export const CLIPS=['Idle','Idle_Neutral','Walk','Run','Wave','Interact'];
globalThis.FileReader??=class{readAsArrayBuffer(b){b.arrayBuffer().then(r=>{this.result=r;this.onloadend?.();});}
  readAsDataURL(b){b.arrayBuffer().then(r=>{this.result=`data:${b.type||'application/octet-stream'};base64,${Buffer.from(r).toString('base64')}`;this.onloadend?.();});}};
const quiet=console.warn;console.warn=(...a)=>{if(!/^(THREE\.FBXLoader|GLTFExporter: Use|THREE\.GLTFExporter: Could not export animation track "CharacterArmature)/.test(String(a[0])))quiet(...a);};

function load(file){const b=readFileSync(file);return new FBXLoader().parse(b.buffer.slice(b.byteOffset,b.byteOffset+b.byteLength),'');}
async function pack(gender){
  const dir=path.join(src,gender,'Separate Skeletal Meshes and Animations'),anim=load(path.join(dir,'Animations.fbx'));
  // The shared skeleton comes from an outfit part, not from Animations.fbx: its rest pose
  // differs, and bones the clips do not animate would pull skinned vertices away.
  const first=OUTFITS[gender][0],root=load(path.join(dir,first,readdirFbx(path.join(dir,first))[0]));root.animations=anim.animations;
  root.name=`People ${gender}`;const remove=[];root.traverse(o=>{if(o.isMesh)remove.push(o);});remove.forEach(o=>o.removeFromParent());
  const bones=new Map();root.traverse(o=>{if(o.isBone)bones.set(o.name,o);});root.updateMatrixWorld(true);
  const report=[],materials=new Map();
  for(const outfit of OUTFITS[gender]){
    for(const file of readdirFbx(path.join(dir,outfit))){
      const part=load(path.join(dir,outfit,file));part.updateMatrixWorld(true);
      const meshes=[];part.traverse(o=>{if(o.isSkinnedMesh)meshes.push(o);});
      for(const m of meshes){
        const slot=file.replace(/\.fbx$/,'').split('_').pop();m.name=`${outfit}|${slot}`;
        m.geometry=regroup(mergeVertices(m.geometry.deleteAttribute('uv').deleteAttribute('color')));
        const skeleton=new THREE.Skeleton(m.skeleton.bones.map(b=>{const c=bones.get(b.name);if(!c)throw new Error(`${file}: bone ${b.name} missing`);return c;}),m.skeleton.boneInverses);
        m.normalizeSkinWeights(); // FBX drops weights past four; renormalise the rest
        const local=m.matrix.clone();root.add(m);m.matrix.copy(local);m.matrix.decompose(m.position,m.quaternion,m.scale);m.bind(skeleton,m.bindMatrix);
        // FBX Phong materials arrive transparent (opacity 0) with colour in emissive:
        // export plain opaque PBR colours, one shared material per name.
        const own=x=>{const name=x.name||'Material';if(!materials.has(name))materials.set(name,new THREE.MeshStandardMaterial({name,color:x.color.clone(),roughness:.82,metalness:0}));return materials.get(name);};
        m.material=Array.isArray(m.material)?m.material.map(own):own(m.material);
        report.push({mesh:m.name,tris:(m.geometry.index?m.geometry.index.count:m.geometry.attributes.position.count)/3,materials:[].concat(m.material).map(x=>`${x.name}#${x.color.getHexString()}`)});
      }
    }
  }
  const clips=root.animations.map(c=>{c.name=c.name.replace(/^.*\|/,'');return c;}).filter(c=>CLIPS.includes(c.name));
  const glb=await new GLTFExporter().parseAsync(root,{binary:true,animations:clips,onlyVisible:false});
  return {glb:await optimise(glb),report,clips:clips.map(c=>c.name)};
}
function readdirFbx(d){return readdirSync(d).filter(f=>f.endsWith('.fbx')).sort();}
// FBX splits a part into dozens of runs per material; one group per material keeps
// a part at a few primitives (draw calls) instead of 30–90.
function regroup(g){if(!g.groups.length)return g;const idx=g.index.array,byMat=new Map();
  for(const gr of g.groups){const a=byMat.get(gr.materialIndex)||[];for(let i=gr.start;i<gr.start+gr.count;i++)a.push(idx[i]);byMat.set(gr.materialIndex,a);}
  const all=[];g.clearGroups();for(const [mi,a]of [...byMat].sort((x,y)=>x[0]-y[0])){g.addGroup(all.length,a.length,mi);for(const v of a)all.push(v);}
  g.setIndex(all);return g;}
async function optimise(glb){const io=new WebIO().registerExtensions([KHRMeshQuantization]);const doc=await io.readBinary(new Uint8Array(glb));
  await doc.transform(weld(),dedup(),resample({tolerance:5e-4}),quantize({quantizePosition:14,quantizeNormal:8,quantizeWeight:16}) /* 8-bit weights tore hands */,prune({keepAttributes:true}));
  return Buffer.from(await io.writeBinary(doc));}

mkdirSync(out,{recursive:true});
for(const g of ['men','women']){const {glb,report,clips}=await pack(g);writeFileSync(path.join(out,`people-${g}.glb`),glb);
  console.log(g,(glb.length/1024).toFixed(0),'KB clips',clips.join(','));for(const r of report)console.log(' ',r.mesh,r.tris,r.materials.join(' '));}
