import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,statSync} from 'node:fs';
import {registerHooks} from 'node:module';
import {validateLook,defaultLook,OUTFIT_SLOTS} from '../runtime/social-state.js';

const threeURL=new URL('../source/vendor/three.module.js',import.meta.url).href;
registerHooks({resolve(specifier,context,next){return next(specifier==='three'?threeURL:specifier,context);}});
const {PEOPLE_FILES,OUTFITS,SLOTS,PEOPLE_CLIPS,outfitIndex}=await import('../runtime/people-kit.js');
const {randomLook,walkerPose,crowdRandom,KIT_CROWD}=await import('../runtime/kit-crowd.js');

const dir=new URL('../characters/',import.meta.url);
function glb(name){const b=readFileSync(new URL(name,dir));assert.equal(b.readUInt32LE(0),0x46546c67,`${name} is not GLB`);return JSON.parse(b.subarray(20,20+b.readUInt32LE(12)).toString('utf8'));}

test('people packs exist, stay under the loader limit and carry the CC0 note',()=>{
  for(const f of PEOPLE_FILES)assert.ok(statSync(new URL(f,dir)).size<4*1024*1024,`${f} too big or missing — run build-people.mjs`);
  assert.match(readFileSync(new URL('LICENSE-quaternius-modular.txt',dir),'utf8'),/CC0/);
});
test('every outfit has head, top, bottom and shoes, and every clip is present',()=>{
  for(const [i,f]of PEOPLE_FILES.entries()){const j=glb(f),names=new Set(j.nodes.map(n=>n.name)),clips=new Set(j.animations.map(a=>a.name));
    for(const [outfit]of OUTFITS[i?'women':'men'])for(const [,slot]of SLOTS)assert.ok(names.has(`${outfit}|${slot}`),`${f}: ${outfit}|${slot}`);
    assert.ok(names.has('Adventurer|Backpack'),`${f}: backpack`);
    for(const c of Object.values(PEOPLE_CLIPS))assert.ok(clips.has(c),`${f}: ${c}`);
    assert.equal(j.images,undefined,`${f} must stay textureless`);}
});
test('looks: old saves still load, outfit colours may stay original, outfit indices wrap per body',()=>{
  const old={v:1,skin:2,hair:1,coat:3,style:1,bag:false,pants:1,shoes:0,glasses:true,height:2,build:0};
  assert.deepEqual(validateLook(old),{...old,body:0,beard:false,sleeves:0,head:0,top:0,bottom:0,feet:0});
  assert.ok(validateLook({...defaultLook,coat:-1,pants:-1,shoes:-1}));
  assert.equal(validateLook({...defaultLook,coat:-2}),null);assert.equal(validateLook({...defaultLook,top:16}),null);
  assert.equal(outfitIndex({top:7},'top',1),7%OUTFITS.women.length);assert.deepEqual(OUTFIT_SLOTS,SLOTS.map(([k])=>k));
});
test('random passers-by are valid looks and walk their segment back and forth',()=>{
  const r=crowdRandom(7);for(let i=0;i<300;i++)assert.ok(validateLook(randomLook(r)));
  const seg={a:[0,0],length:10,ux:1,uz:0};
  assert.deepEqual(walkerPose(seg,1,0,0),{x:0,z:0,yaw:Math.PI/2});
  const back=walkerPose(seg,1,0,15);assert.ok(Math.abs(back.x-5)<1e-9);assert.ok(Math.abs(back.yaw-Math.PI*1.5)<1e-9);
  assert.ok(KIT_CROWD.low<KIT_CROWD.balanced&&KIT_CROWD.balanced<=KIT_CROWD.high);
});
