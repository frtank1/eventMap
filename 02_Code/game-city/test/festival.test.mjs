import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {festivalEvents} from './festival-fixtures.js';
import {festivalAllowed,eventLabelBudget,eventGroupCell} from '../runtime/event-density.js';
import {visibleEvents,groupEvents} from '../runtime/social-state.js';
import {safeBubbleCover,bubbleRect} from '../runtime/event-bubbles.js';
import {eventStyle,canAnimateEvent} from '../runtime/event-style.js';
import {createPreviewServer} from '../preview.mjs';

test('festival fixtures are deterministic, bounded, diverse and explicitly fictional',async()=>{
  const now=Date.now(),events=festivalEvents({now});assert.equal(events.length,150);assert.deepEqual(events,festivalEvents({now}));
  assert.equal(visibleEvents(events,now).length,150);assert.equal(new Set(events.map(e=>e.id)).size,150);
  assert.equal(new Set(events.map(e=>eventStyle(e).key)).size,7);
  assert.ok(events.every(e=>e.demo&&e.id.startsWith('festival-demo-')&&safeBubbleCover(e.coverImage)));
  assert.equal(festivalEvents({count:1e6}).length,150);assert.equal(festivalEvents({count:-1}).length,0);
  for(const path of new Set(events.map(e=>e.coverImage)))assert.match(await readFile(new URL(path,import.meta.url),'utf8'),/^<svg/);
  assert.ok(groupEvents(events,18000,true).length>80,'dense overview retains many distinct activities');
  assert.ok(eventGroupCell(18000,true)<eventGroupCell(18000,false));
  assert.equal(events.filter((e,i)=>canAnimateEvent(e,{index:i,distance:100,now})).length,4);
});
test('dense preview requires loopback plus explicit preview; native without host opt-in is rejected',()=>{
  const m={preview:true,density:'festival'};
  assert.equal(festivalAllowed(m,{hostname:'127.0.0.1'}),true);
  for(const context of [{hostname:'eventmap.kz'},{hostname:'localhost',native:true},{}])assert.equal(festivalAllowed(m,context),false);
  for(const message of [{density:'festival'},{preview:true},{preview:false,density:'festival'}])assert.equal(festivalAllowed(message,{hostname:'localhost'}),false);
  assert.equal(eventLabelBudget(),8);assert.equal(eventLabelBudget({quality:'low'}),5);
  assert.equal(eventLabelBudget({festival:true}),64);assert.equal(eventLabelBudget({festival:true,small:true}),28);
  assert.equal(eventLabelBudget({festival:true,small:true,quality:'low'}),16);
  assert.deepEqual(bubbleRect(195,400,{width:390,height:844,top:140,festival:true,level:'city'}),{x:167,y:324,w:56,h:76});
  assert.equal(bubbleRect(195,180,{width:390,height:844,top:140,festival:true,level:'city'}),null);
});
test('festival beacons remain two instanced static meshes and release GPU resources',async()=>{
  const root=fileURLToPath(new URL('../',import.meta.url));
  const bundle=await build({stdin:{contents:"export * from './runtime/event-beacons.js';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
  const {createEventBeacons}=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].text).toString('base64'));
  const beacons=createEventBeacons();const groups=groupEvents(visibleEvents(festivalEvents()),18000,true);let disposed=0;
  for(const mesh of beacons.group.children){assert.ok(mesh.isInstancedMesh);mesh.geometry.addEventListener('dispose',()=>disposed++);mesh.material.addEventListener('dispose',()=>disposed++);}
  beacons.setEvents(groups,()=>20);beacons.update(18000,true);
  assert.equal(beacons.group.children.length,2);assert.equal(beacons.stats().beaconDrawCalls,2);
  assert.ok(beacons.stats().beaconGroups>80);
  for(const mesh of beacons.group.children){assert.ok(mesh.count<=150);assert.ok([...mesh.instanceMatrix.array].every(Number.isFinite));}
  beacons.refreshHeights(()=>90);beacons.update(18000,false);
  assert.ok(Math.abs(beacons.group.children[0].instanceMatrix.array[13]-90.18)<.01);
  beacons.update(18000,false,false);assert.equal(beacons.stats().beaconDrawCalls,0);
  beacons.destroy();beacons.destroy();assert.equal(disposed,4);
});
test('fixture modules/covers are loopback routes, never injected in plain or native HTML',async()=>{
  const server=createPreviewServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  try{
    const html=await (await fetch(base+'/?festival=1')).text();assert.match(html,/test\/festival-qa.js/);assert.doesNotMatch(html,/src="stage-one-qa.js"/);
    assert.doesNotMatch(await (await fetch(base+'/')).text(),/src="\/test\/festival-qa.js"/);
    for(const path of ['/test/festival-qa.js','/test/festival-fixtures.js','/event-covers/quest.svg','/event-covers/route.svg','/event-covers/community.svg','/event-covers/discovery.svg'])assert.equal((await fetch(base+path)).status,200);
    assert.equal((await fetch(base+'/event-covers/private.svg')).status,404);
    assert.equal((await fetch(base+'/test/festival-fixtures.js',{method:'POST'})).status,405);
    assert.doesNotMatch(await readFile(new URL('../runtime/index.html',import.meta.url),'utf8'),/festival-fixtures|festival-qa/);
  }finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
});
