import test from 'node:test';
import assert from 'node:assert/strict';
import {ART_VERSION,CITY_PALETTE,eventDisplayLevel,sceneMood} from '../runtime/art-direction.js';
import {bubbleRect,eventTimeLabel} from '../runtime/event-bubbles.js';
import {groupEvents} from '../runtime/social-state.js';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';

test('city palette is bounded shared day/night data',()=>{
  assert.equal(ART_VERSION,'nurjol-garden-2');
  for(const colors of Object.values(CITY_PALETTE)){
    assert.equal(colors.length,2);for(const c of colors)assert.ok(Number.isInteger(c)&&c>=0&&c<=0xffffff);
  }
  assert.notEqual(sceneMood(false).land,sceneMood(true).land);
  assert.notEqual(sceneMood(false).land,sceneMood(false,'winter').land);
  assert.deepEqual(sceneMood(true,'winter'),sceneMood(true,'newyear'));
});
test('event visual hierarchy uses hysteresis and preserves tappable anchors',()=>{
  assert.equal(eventDisplayLevel(3300),'city');assert.equal(eventDisplayLevel(2700,'city'),'city');
  assert.equal(eventDisplayLevel(2300,'city'),'district');assert.equal(eventDisplayLevel(400),'street');
  assert.equal(eventDisplayLevel(520,'street'),'street');assert.equal(eventDisplayLevel(600,'street'),'district');
  assert.equal(eventDisplayLevel(NaN),'city');
  const opts={width:390,height:844,top:90};
  for(const level of ['city','district','street']){const r=bubbleRect(195,400,{...opts,level});assert.ok(r.w>=44);assert.equal(r.x+r.w/2,195);assert.equal(r.y+r.h,400);}
  assert.ok(bubbleRect(195,400,{...opts,level:'city'}).h<bubbleRect(195,400,{...opts,level:'street'}).h);
});
test('planned event date is explicit and uses Astana time, not device zone',()=>{
  const now=Date.parse('2026-11-16T17:00:00Z');
  assert.equal(eventTimeLabel({startAt:'2026-11-16T18:00:00Z'},now),'Сегодня · 23:00');
  assert.match(eventTimeLabel({startAt:'2026-11-16T20:00:00Z'},now),/17.*01:00/);
  assert.equal(eventTimeLabel({startAt:'bad'},now),'Запланировано');
});
test('overview clustering retains every event without fake visitor counts',()=>{
  const events=Array.from({length:40},(_,i)=>({id:String(i),lng:71.43+i*.0006,lat:51.128}));
  const close=groupEvents(events,300),far=groupEvents(events,17000);
  assert.ok(far.length<close.length);
  assert.equal(far.flatMap(g=>g.events).length,events.length);
  assert.equal(new Set(far.flatMap(g=>g.events.map(e=>e.id))).size,events.length);
});

test('landmark ground light uses one bounded draw, no PointLights, and disposes once',async()=>{
  const root=fileURLToPath(new URL('../',import.meta.url));
  const b=await build({stdin:{contents:"export {createLandmarkGround} from './runtime/landmark-ground.js';export * as THREE from 'three';",resolveDir:root},bundle:true,platform:'node',format:'esm',write:false,alias:{three:root+'source/vendor/three.module.js'}});
  const {createLandmarkGround,THREE}=await import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].text).toString('base64'));
  const stage=createLandmarkGround([{lat:51,lng:71,radius:42},{lat:51.1,lng:71.1,radius:155}],(x,z)=>new THREE.Vector3(x,0,z));
  assert.equal(stage.group.visible,false);stage.setNight(true);assert.equal(stage.group.visible,true);
  const mesh=stage.group.children[0];assert.equal(mesh.count,2);assert.equal(mesh.geometry.attributes.position.count,4);assert.equal(stage.group.children.length,1);
  let disposed=0;mesh.geometry.addEventListener('dispose',()=>disposed++);mesh.material.addEventListener('dispose',()=>disposed++);mesh.material.map.addEventListener('dispose',()=>disposed++);
  stage.destroy();stage.destroy();assert.equal(disposed,3);
});
