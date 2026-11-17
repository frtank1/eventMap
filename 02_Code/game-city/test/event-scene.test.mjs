import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import * as THREE from 'three';
import {createEventScene,validateEventScene,EVENT_SCENE_LIMITS} from '../runtime/event-scene.js';

// Labels draw on a canvas; tests only need a stand-in that records nothing.
globalThis.document??={createElement:()=>({width:0,height:0,getContext:()=>({measureText:()=>({width:120}),beginPath(){},roundRect(){},fill(){},fillText(){}})})};
const demo=JSON.parse(await readFile(new URL('../../web/scenes/demo-concert.json',import.meta.url),'utf8'));
const project=(lng,lat)=>new THREE.Vector3((lng-71.43056)*69900,0,-(lat-51.12822)*111320);

test('demo event scene is marked as fiction and builds from config only',()=>{
  assert.equal(demo.demo,true);assert.match(demo.note,/Вымышленное/);
  const s=createEventScene(demo,project);
  assert.ok(s.group.children.length>20&&s.group.children.length<120,'bounded number of objects');
  const st=s.stats();assert.ok(st.eventCrowd>2000&&st.eventCrowd<=EVENT_SCENE_LIMITS.crowd);
  assert.ok(Math.abs(s.focus.target.x-s.group.position.x)<1e-6);
  s.update(12.5,2,false);s.update(0,1,true);
  s.destroy();assert.equal(s.group.parent,null);
});

test('invalid scene configs are rejected',()=>{
  assert.throws(()=>validateEventScene(null));
  assert.throws(()=>validateEventScene({anchor:[71,51],territory:[[0,0],[1,1]]}));
  assert.throws(()=>validateEventScene({anchor:[71,51],territory:[[0,0],[1,0],[0,1]],zones:Array(9).fill({polygon:[[0,0],[1,0],[0,1]]})}));
});
