// Builds characters/people-{men,women}.glb from the Quaternius "Ultimate Modular Men/Women"
// packs (CC0), folder "Individual Characters/glTF" (Blender exports with correct animation).
// Sources stay outside git: data/quaternius-modular/{men,women}-gltf/glTF/*.gltf.
// Every outfit part (Head, Body, Legs, Feet, Backpack) is moved onto the first outfit's
// skeleton (all files share the same 62 joints), so the runtime can mix any parts.
// The FBX "Separate Skeletal Meshes" route was dropped on 17.11: three's FBXLoader turned
// the IK feet backwards in Walk.
import {mkdirSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {NodeIO} from '@gltf-transform/core';
import {KHRMeshQuantization} from '@gltf-transform/extensions';
import {mergeDocuments,unpartition,weld,dedup,quantize,resample,prune} from '@gltf-transform/functions';

const here=path.dirname(fileURLToPath(import.meta.url));
const src=path.resolve(process.argv[2]||path.join(here,'data/quaternius-modular'));
const out=path.join(here,'characters');
// City-appropriate outfits only (no kings, space suits, armour, witches or weapons).
export const OUTFITS={men:['Casual_2','Casual_Hoodie','Suit','Worker','Beach','Farmer','Punk','Adventurer'],
  women:['Casual','Formal','Suit','Worker','Punk','Adventurer']};
export const CLIPS=['Idle','Idle_Neutral','Walk','Run','Wave','Interact'];
const SLOT={Body:'Body',Feet:'Feet',Head:'Head',Legs:'Legs',Pants:'Legs',Backpack:'Backpack'};
const io=new NodeIO().registerExtensions([KHRMeshQuantization]);
// Disposing an Animation leaves its samplers holding accessors: drop them explicitly.
const dropAnimation=a=>{for(const c of a.listChannels())c.dispose();for(const s of a.listSamplers())s.dispose();a.dispose();};

function slotOf(node){const s=SLOT[node.getName().split('_').pop()];return s||null;}
async function pack(gender){
  const dir=path.join(src,`${gender}-gltf`,'glTF'),[first,...rest]=OUTFITS[gender];
  const doc=await io.read(path.join(dir,`${first}.gltf`)),root=doc.getRoot(),skin=root.listSkins()[0];
  const parts=root.listNodes().filter(n=>n.getMesh());
  const anchor=parts.find(n=>slotOf(n)==='Body');if(!anchor)throw new Error(`${first}: no body`);
  const parent=anchor.getParentNode(),scene=root.listScenes()[0];
  const place=(n,outfit)=>{const slot=slotOf(n);if(!slot){n.getMesh()?.dispose();n.dispose();return;}
    n.setName(`${outfit}|${slot}`).setSkin(skin).setTranslation(anchor.getTranslation()).setRotation(anchor.getRotation()).setScale(anchor.getScale());
    if(parent)parent.addChild(n);else scene.addChild(n);};
  for(const n of parts)place(n,first);
  for(const a of root.listAnimations())if(!CLIPS.includes(a.getName()))dropAnimation(a);
  for(const outfit of rest){
    const other=await io.read(path.join(dir,`${outfit}.gltf`)),o=other.getRoot();
    const names=o.listSkins()[0].listJoints().map(j=>j.getName()),base=skin.listJoints().map(j=>j.getName());
    if(names.join()!==base.join())throw new Error(`${outfit}: skeleton differs from ${first}`);
    const meshNodes=new Set(o.listNodes().filter(n=>n.getMesh())),before=new Set([...root.listNodes(),...root.listSkins(),...root.listAnimations(),...root.listScenes()]);
    const map=mergeDocuments(doc,other);
    for(const n of meshNodes)place(map.get(n),outfit);
    // Drop the merged copy's skeleton, skin, clips and scene; keep only its parts.
    const kept=new Set([...meshNodes].map(n=>map.get(n)));
    for(const a of root.listAnimations())if(!before.has(a))dropAnimation(a);
    for(const p of [...root.listSkins(),...root.listScenes()])if(!before.has(p))p.dispose();
    for(const n of root.listNodes())if(!before.has(n)&&!kept.has(n))n.dispose();
  }
  // prune last: quantize leaves the original float accessors behind.
  await doc.transform(unpartition(),weld(),dedup(),resample({tolerance:5e-4}),
    quantize({quantizePosition:14,quantizeNormal:8,quantizeWeight:16}) /* 8-bit weights tore hands */,
    prune({keepAttributes:false,keepLeaves:true}),dedup());
  const report=root.listNodes().filter(n=>n.getMesh()).map(n=>`${n.getName()} ${n.getMesh().listPrimitives().map(p=>p.getMaterial()?.getName()).join(',')}`);
  return {glb:Buffer.from(await io.writeBinary(doc)),report,clips:root.listAnimations().map(a=>a.getName())};
}

mkdirSync(out,{recursive:true});
for(const g of ['men','women']){const {glb,report,clips}=await pack(g);writeFileSync(path.join(out,`people-${g}.glb`),glb);
  console.log(g,(glb.length/1024).toFixed(0),'KB clips',clips.join(','));if(process.argv.includes('--verbose'))for(const r of report)console.log(' ',r);}
