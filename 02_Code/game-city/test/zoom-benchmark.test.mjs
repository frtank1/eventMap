import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {createZoomBenchmark,zoomPose,ZOOM_PHASES} from '../runtime/zoom-benchmark.js';
import {festivalAllowed,debugSceneAllowed,eventGroupBucket,groupDistanceForBucket,eventGroupCell} from '../runtime/event-density.js';
import {stageNativeFestival} from '../stage-festival.mjs';
import {festivalEvents} from './festival-fixtures.js';

test('native dense mode requires explicit host debug opt-in and trusted asset origin',()=>{
  const m={preview:true,density:'festival'},context={hostname:'appassets.androidplatform.net',native:true,debugPreview:true};
  assert.equal(festivalAllowed(m,context),true);assert.equal(debugSceneAllowed(m,context),true);
  for(const change of [{debugPreview:false},{hostname:'example.com'},{native:false}])assert.equal(festivalAllowed(m,{...context,...change}),false);
  assert.equal(festivalAllowed({...m,preview:false},context),false);
  assert.equal(festivalAllowed({...m,density:'normal'},context),false);
});
test('zoom sweep is continuous, reaches building detail and returns to city overview',()=>{
  let previous=zoomPose(0).distance;
  for(let ms=0;ms<=26000;ms+=16){const p=zoomPose(ms);assert.ok(Number.isFinite(p.distance));assert.ok(p.distance>=179&&p.distance<=18001);assert.ok(Math.abs(p.distance-previous)<100);previous=p.distance;}
  assert.ok(Math.abs(zoomPose(16000).distance-180)<.001);
  assert.ok(Math.abs(zoomPose(26000).distance-18000)<.001);
  assert.equal(ZOOM_PHASES.reduce((n,p)=>n+p.ms,0),26000);
});
test('event grouping has a dead band so pinch jitter cannot repeatedly rebuild balloon DOM',()=>{
  for(const [i,threshold]of [650,1600,3000,12000].entries()){
    let bucket=i;
    for(let n=0;n<100;n++)bucket=eventGroupBucket(threshold+(n%2?10:-10),bucket);
    assert.equal(bucket,i);
    assert.equal(eventGroupBucket(threshold*1.13,i),i+1);
    assert.equal(eventGroupBucket(threshold*.87,i+1),i);
  }
  assert.equal(eventGroupBucket(18000,0),4);assert.equal(eventGroupBucket(100,4),0);
  assert.equal(eventGroupBucket(3000),2);
  assert.deepEqual([0,1,2,3,4].map(i=>eventGroupCell(groupDistanceForBucket(i))),[35,140,500,700,1500]);
});
test('zoom collector measures actual supplied frame intervals including motion stalls',()=>{
  const run=createZoomBenchmark();assert.equal(run.report().frameTiming.fps,null);
  let n=0;while(run.running&&n<1000){run.sample({dtMs:n++===400?180:1000/30,drawCalls:100,triangles:300000,quality:'balanced',pixelRatio:1.35});}
  const r=run.report();assert.equal(r.status,'complete');assert.ok(r.frameTiming.fps<30);assert.equal(r.frameTiming.slowFrames,1);
  for(const phase of Object.values(r.phases))assert.ok(phase.samples>0);
  assert.ok(r.phases['zoom-in'].slowFrames+r.phases.near.slowFrames>0);
  assert.equal(r.maxDrawCalls,100);assert.equal(r.maxTriangles,300000);assert.deepEqual(r.quality,['balanced']);
});
test('background, popup, reduced-motion and explicit cancellation never count as a completed pass',()=>{
  for(const flags of [{hidden:true},{blocked:true},{active:false},{reducedMotion:true}]){
    const run=createZoomBenchmark();run.sample({dtMs:33,...flags});assert.equal(run.report().status,'cancelled');assert.equal(run.report().frameTiming.fps,null);
  }
  const run=createZoomBenchmark();run.sample({dtMs:30000});assert.equal(run.report().elapsedMs,100);assert.equal(run.running,true);
  run.cancel('gesture');run.sample({dtMs:33});assert.equal(run.report().reason,'gesture');assert.equal(run.running,false);
});
test('Android debug fixture staging reuses all 150 browser events and only explicit SVG assets',async()=>{
  const root=await mkdtemp(path.join(os.tmpdir(),'eventmap-festival-'));
  try{
    await stageNativeFestival(root);const staged=JSON.parse(await readFile(path.join(root,'eventmap-festival/events.json'),'utf8'));
    const browser=festivalEvents({now:0});for(const e of browser){delete e.startAt;delete e.endAt;}
    assert.deepEqual(staged,browser);assert.equal(staged.length,150);
    for(const p of new Set(staged.map(e=>e.coverImage))){const asset=await readFile(path.join(root,'composeResources/eventmap.common.feature.map.ui.generated.resources/files/game-city',p),'utf8');assert.match(asset,/^<svg/);}
  }finally{await rm(root,{recursive:true,force:true});}
});
