// Logical/synthetic tests only: no browser, GPU, network or device FPS claims.
import test from 'node:test';
import assert from 'node:assert/strict';
import {createFrameGovernor, summarizeFrames, FRAME_GOVERNOR_DEFAULTS, PIXEL_RATIO_LIMITS} from '../runtime/frame-governor.js';
import {createPerformanceRun, mountVisualPerformanceQA} from './visual-performance-qa.js';

function feed(governor, count, interval=1000/30, flags={}) {
  let result=governor.snapshot();
  for (let i=0;i<count;i++) result=governor.sample(interval,flags);
  return result;
}
const near=(actual,expected) => assert.ok(Math.abs(actual-expected)<1e-8,`${actual} ≈ ${expected}`);

test('summaries use exact nearest-rank p95 and frames / elapsed time, without mutating input', () => {
  const values=Array.from({length:20},(_,i)=>i+1).concat([NaN,Infinity,-1,0,'33',60001]);
  const before=[...values], result=summarizeFrames(values);
  assert.deepEqual(values,before); assert.equal(result.samples,20); assert.equal(result.durationMs,210);
  assert.equal(result.p95Ms,19); near(result.fps,20000/210); assert.equal(result.meanMs,10.5);
  assert.equal(result.slowFrames,0);
  assert.deepEqual(summarizeFrames([]),{samples:0,durationMs:0,fps:null,meanMs:null,p95Ms:null,slowFrames:0});
});

test('normal 30 FPS cap and 33/34 ms jitter do not trigger low quality', () => {
  const governor=createFrameGovernor({devicePixelRatio:3});
  let changed=0;
  for (let i=0;i<3000;i++) changed+=Number(governor.sample(i%2 ? 33 : 34).changed);
  assert.equal(changed,0); assert.equal(governor.quality,'balanced'); assert.equal(governor.pixelRatio,1.35);
  const t=governor.snapshot().frameTiming;
  assert.equal(t.samples,120); assert.equal(t.p95Ms,34); near(t.fps,1000/33.5);
  assert.equal(t.slowRatio,0);
});

test('startup warmup and a single cold frame cannot spend seconds of slow evidence', () => {
  const governor=createFrameGovernor();
  const cold=governor.sample(12000);
  assert.equal(cold.accepted,false); assert.equal(cold.warmingUp,true); assert.equal(cold.frameTiming.samples,0);
  assert.equal(cold.cooldownRemainingMs,7900);
  feed(governor,300); assert.equal(governor.quality,'balanced');
  governor.sample(2000); const stalled=governor.snapshot().frameTiming;
  assert.ok(stalled.meanMs>33.4,'Unflagged stalls remain visible in real telemetry');
  assert.equal(stalled.slowFrames,1);
  feed(governor,180); assert.equal(governor.quality,'balanced'); assert.equal(governor.snapshot().slowDurationMs,0);
});

test('rolling ring evicts old values exactly, handles duplicates and retains full raw duration', () => {
  const governor=createFrameGovernor({warmupMs:0,windowSize:4,minSamples:4,cooldownMs:1e9});
  for (const n of [10,20,20,40,1000]) governor.sample(n);
  const timing=governor.snapshot().frameTiming;
  const expected=summarizeFrames([20,20,40,1000]);
  for (const key of Object.keys(expected)) near(timing[key],expected[key]);
  governor.sample(20); governor.sample(20); governor.sample(20); governor.sample(20);
  const recovered=governor.snapshot().frameTiming;
  assert.equal(recovered.meanMs,20); assert.equal(recovered.p95Ms,20); assert.equal(recovered.slowFrames,0);
});

test('EMA is deterministic and time-weighted for eligible evidence', () => {
  const governor=createFrameGovernor({warmupMs:0});
  governor.sample(30); const result=governor.sample(60);
  near(result.frameTiming.emaMs,30+(1-Math.exp(-60/1000))*30);
});

test('sustained slow frames downgrade once, respecting initial cooldown and then latch', () => {
  const governor=createFrameGovernor({devicePixelRatio:2});
  let changes=0, switchedAt=0;
  for (let i=1;i<=400;i++) {
    const result=governor.sample(50);
    if (result.changed) { changes++; switchedAt=i*50; assert.equal(result.reason,'sustained-slow'); }
  }
  assert.equal(changes,1); assert.ok(switchedAt>=FRAME_GOVERNOR_DEFAULTS.cooldownMs);
  assert.equal(governor.quality,'low'); assert.equal(governor.pixelRatio,1);
  for (let i=0;i<5000;i++) assert.equal(governor.sample(16).changed,false);
  for (let i=0;i<600;i++) assert.equal(governor.sample(i%2 ? 66 : 33).changed,false);
  assert.equal(governor.quality,'low','Fast low-quality rendering must not be used to infer balanced is affordable');
  governor.reset(); assert.equal(governor.quality,'low');
  governor.reset({quality:'balanced'}); assert.equal(governor.quality,'balanced');
  assert.equal(governor.snapshot().frameTiming.fps,null); assert.equal(governor.snapshot().warmingUp,true);
});

test('sustained jitter is detected but rare periodic stalls cannot drive quality changes', () => {
  const jitter=createFrameGovernor(), occasional=createFrameGovernor();
  for (let i=0;i<1000;i++) {
    jitter.sample(i%2 ? 66 : 33);
    occasional.sample(i%30===0 ? 250 : 33);
  }
  assert.equal(jitter.quality,'low'); assert.equal(occasional.quality,'balanced');
});

test('minimum sample count and sustained duration are independent downgrade gates', () => {
  const governor=createFrameGovernor({warmupMs:0,cooldownMs:0,minSamples:10,sustainedSlowMs:500});
  feed(governor,9,100); assert.equal(governor.snapshot().slowDurationMs,0);
  feed(governor,4,100); assert.equal(governor.quality,'balanced');
  assert.equal(governor.sample(100).quality,'low');
});

for (const flags of [{active:false},{blocked:true},{hidden:true},{reducedMotion:true},{warmingUp:true}]) {
  test(`excluded frames freeze telemetry/time, break slow streak and discard resume gap: ${JSON.stringify(flags)}`, () => {
    const governor=createFrameGovernor({warmupMs:0,resumeWarmupMs:100,windowSize:10,minSamples:5,sustainedSlowMs:500});
    const before=feed(governor,12,50); assert.equal(before.slowDurationMs,400);
    const paused=feed(governor,30,6000,flags);
    assert.deepEqual(paused.frameTiming,before.frameTiming);
    assert.equal(paused.cooldownRemainingMs,before.cooldownRemainingMs);
    assert.equal(paused.slowDurationMs,0); assert.equal(paused.quality,'balanced');
    const resume=governor.sample(6000);
    assert.equal(resume.accepted,false); assert.equal(resume.reason,'resume-boundary');
    assert.equal(resume.frameTiming.samples,0);
    assert.equal(governor.sample(100).accepted,false);
    assert.equal(governor.sample(33).accepted,true);
    assert.equal(governor.snapshot().frameTiming.p95Ms,33);
  });
}

test('invalid intervals never poison telemetry or clocks and require a new timing boundary', () => {
  for (const bad of [0,-1,NaN,Infinity,-Infinity,'33',undefined,null,60001,Number.MAX_VALUE]) {
    const governor=createFrameGovernor({warmupMs:0,resumeWarmupMs:0});
    const before=governor.sample(33), invalid=governor.sample(bad);
    assert.equal(invalid.reason,'invalid-interval'); assert.deepEqual(invalid.frameTiming,before.frameTiming);
    assert.equal(governor.sample(5000).reason,'resume-boundary');
    assert.equal(governor.sample(33).frameTiming.samples,1);
  }
});

test('manual quality overrides are explicit and automatic mode restarts with fresh evidence', () => {
  const governor=createFrameGovernor({devicePixelRatio:3,mode:'balanced'});
  feed(governor,500,100); assert.equal(governor.quality,'balanced');
  assert.equal(governor.setMode('low').pixelRatio,1); assert.equal(governor.quality,'low');
  governor.setMode('balanced'); const auto=governor.setMode('auto');
  assert.equal(auto.quality,'balanced'); assert.equal(auto.frameTiming.samples,0); assert.equal(auto.warmingUp,true);
  feed(governor,500,100); assert.equal(governor.quality,'low');
});

test('pixel ratio follows hardware changes without exceeding hardware or quality cap', () => {
  for (const quality of ['low','balanced']) {
    const governor=createFrameGovernor({initialQuality:quality,devicePixelRatio:4});
    assert.equal(governor.pixelRatio,PIXEL_RATIO_LIMITS[quality]);
    assert.equal(governor.setDevicePixelRatio(.75).pixelRatio,.75);
    assert.equal(governor.quality,quality);
    for (const invalid of [0,-1,NaN,Infinity,'2',null]) assert.equal(governor.setDevicePixelRatio(invalid).pixelRatio,1);
    assert.equal(governor.reset({devicePixelRatio:3}).pixelRatio,PIXEL_RATIO_LIMITS[quality]);
  }
});

test('invalid policy/reset requests fail before mutating state', () => {
  for (const options of [{mode:'high'},{initialQuality:'auto'},{windowSize:2},{windowSize:1.5},
    {windowSize:4000},{minSamples:0},{warmupMs:-1},{slowRatio:0},{slowRatio:1.01},
    {emaTimeConstantMs:0},{sustainedSlowMs:0},{slowEmaMs:NaN}])
    assert.throws(()=>createFrameGovernor(options),RangeError);
  const governor=createFrameGovernor(); const before=governor.snapshot();
  assert.throws(()=>governor.reset({mode:'low',quality:'high'}),RangeError);
  assert.deepEqual(governor.snapshot(),before);
});

test('identical input histories produce identical outputs, with isolated snapshots and bounded memory', () => {
  const a=createFrameGovernor(), b=createFrameGovernor();
  for (let i=0;i<2500;i++) {
    const dt=17+(i*31)%77, flags={blocked:i%701===0};
    assert.deepEqual(a.sample(dt,flags),b.sample(dt,flags));
  }
  const snapshot=a.snapshot(); snapshot.frameTiming.p95Ms=-1; snapshot.quality='invalid';
  assert.deepEqual(a.snapshot(),b.snapshot()); assert.ok(a.snapshot().frameTiming.samples<=120);
});

test('benchmark collector reports only supplied rendered frames, counters and actual mixed qualities', () => {
  const run=createPerformanceRun({warmupMs:200,durationMs:1000});
  run.sample({dtMs:100,drawCalls:999}); run.sample({dtMs:100,drawCalls:999});
  assert.equal(run.report().frameTiming.fps,null);
  for (let i=1;i<=10;i++) run.sample({dtMs:100,drawCalls:i,triangles:i*100,
    quality:i<4 ? 'balanced' : 'low',pixelRatio:i<4 ? 1.35 : 1});
  const report=run.report(); assert.equal(report.status,'complete');
  assert.equal(report.frameTiming.fps,10); assert.equal(report.frameTiming.p95Ms,100);
  assert.equal(report.frameTiming.samples,10); assert.equal(report.excluded.warmup,2);
  assert.deepEqual(report.drawCalls,{samples:10,mean:5.5,p95:10,max:10});
  assert.deepEqual(report.triangles,{samples:10,mean:550,p95:1000,max:1000});
  assert.deepEqual(report.qualitySamples,{balanced:3,low:7});
  assert.deepEqual(report.pixelRatio,{samples:10,min:1,max:1.35});
  run.sample({dtMs:1000}); assert.deepEqual(run.report(),report,'Completed runs cannot accumulate extra frames');
});

test('benchmark pauses exclude gaps and rewarm without erasing already measured frames', () => {
  for (const flags of [{active:false},{blocked:true},{hidden:true},{reducedMotion:true},{warmingUp:true}]) {
    const run=createPerformanceRun({warmupMs:100,durationMs:1000});
    run.sample({dtMs:100}); run.sample({dtMs:50}); run.sample({dtMs:10000,...flags});
    run.sample({dtMs:10000}); run.sample({dtMs:100}); run.sample({dtMs:50});
    const report=run.report(); assert.equal(report.frameTiming.samples,2); assert.equal(report.frameTiming.durationMs,100);
    assert.equal(report.frameTiming.p95Ms,50); assert.equal(report.excluded['resume-boundary'],1);
  }
});

test('benchmark missing renderer counters remain unknown; zero is a legitimate measured counter', () => {
  const run=createPerformanceRun({warmupMs:0,durationMs:100});
  run.sample({dtMs:50,drawCalls:-1,triangles:Infinity});
  run.sample({dtMs:50,drawCalls:0,triangles:0});
  assert.deepEqual(run.report().drawCalls,{samples:1,mean:0,p95:0,max:0});
  assert.equal(run.report().pixelRatio.min,null);
  const empty=createPerformanceRun().stop(); assert.equal(empty.status,'cancelled');
  assert.equal(empty.frameTiming.fps,null); assert.equal(empty.drawCalls.mean,null);
});

test('benchmark rejects invalid bounds and stops at its sample limit', () => {
  for (const options of [{warmupMs:-1},{durationMs:0},{durationMs:Infinity},{maxSamples:0},{maxSamples:1.5},{maxSamples:100001}])
    assert.throws(()=>createPerformanceRun(options),RangeError);
  const run=createPerformanceRun({warmupMs:0,maxSamples:3});
  for (let i=0;i<8;i++) run.sample({dtMs:1});
  assert.equal(run.report().status,'sample-limit'); assert.equal(run.report().frameTiming.samples,3);
  const report=run.report(); report.excluded.fake=99; assert.equal(run.report().excluded.fake,undefined);
});

// Minimal UI state mock only. No browser layout, WebGL or real rendering.
class Element {
  constructor(tag) { this.tag=tag; this.children=[]; this.style={}; this.attributes={}; this.value=''; }
  append(child) { child.parent=this; this.children.push(child); if (this.tag==='select'&&this.children.length===1) this.value=child.value; }
  setAttribute(key,value) { this.attributes[key]=value; }
  remove() { this.parent.children=this.parent.children.filter(n=>n!==this); }
}
function harness({hostname='127.0.0.1',search='?visualperf=1',native=false}={}) {
  const messages=[], timers=new Map(), listeners=new Map();
  let timerId=0;
  const doc={body:new Element('body'),hidden:false,createElement:tag=>new Element(tag),
    addEventListener:(name,fn)=>listeners.set(name,fn),removeEventListener:name=>listeners.delete(name)};
  const media={matches:false,addEventListener:(name,fn)=>listeners.set(`media:${name}`,fn),removeEventListener:name=>listeners.delete(`media:${name}`)};
  const stats={night:true,theme:'winter',quality:'balanced',events:10,avatar:{secret:true}};
  const win={location:{hostname,search},innerWidth:390,innerHeight:844,devicePixelRatio:3,
    matchMedia:()=>media,setTimeout:fn=>{timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id),
    EventMapMobile:{receive:message=>{messages.push(message);if(message.type==='appearance')Object.assign(stats,{night:message.night,theme:message.theme});},stats:()=>stats}};
  if (native) win.EventMapNative={postMessage(){throw new Error('Native transport must never be called');}};
  const hook=mountVisualPerformanceQA({window:win,document:doc});
  const panel=doc.body.children[0];
  const button=text=>panel?.children.find(n=>n.tag==='button'&&n.textContent===text);
  return {hook,win,doc,media,messages,timers,listeners,panel,button};
}

test('QA is inert outside explicit loopback opt-in or in a native WebView', () => {
  for (const options of [{hostname:'example.com'},{search:''},{search:'?visualperf=0'},{native:true}]) {
    const h=harness(options); assert.equal(h.hook,null); assert.equal(h.doc.body.children.length,0);
    assert.equal(h.messages.length,0); assert.equal(h.win.EventMapPerformanceQA,undefined);
  }
});

test('QA requires explicit Start and reports missing render hook without fabricated FPS', () => {
  const h=harness(); assert.equal(h.messages.length,0); assert.equal(h.hook.report(),null);
  h.hook.sampleFrame({dtMs:33}); assert.equal(h.hook.report(),null);
  h.button('Запустить').onclick(); assert.equal(h.messages.length,2);
  for (const fn of h.timers.values()) fn();
  assert.ok(h.panel.children.some(n=>n.textContent?.includes('Нет render-hook')));
  h.button('Остановить').onclick(); const report=h.hook.report();
  assert.equal(report.frameTiming.fps,null); assert.equal(report.frameTiming.p95Ms,null);
  assert.equal(report.status,'cancelled'); assert.equal(report.startContext.avatar,undefined);
  assert.deepEqual(h.messages.at(-1),{v:1,type:'appearance',night:true,theme:'winter'});
  h.hook.destroy(); assert.equal(h.doc.body.children.length,0); assert.equal(h.listeners.size,0);
  assert.equal(h.win.EventMapPerformanceQA,undefined);
});

test('QA explicit scenario run completes on rendered samples, not timers or polled stats FPS', () => {
  const h=harness(); h.win.EventMapMobile.stats=()=>({night:false,theme:'default',fps:999,quality:'balanced'});
  h.panel.children.find(n=>n.tag==='select').value='baiterek-night'; h.button('Запустить').onclick();
  for (let i=0;i<150;i++) h.hook.sampleFrame({dtMs:100,drawCalls:96,triangles:205000,quality:'balanced',pixelRatio:1.35});
  const report=h.hook.report(); assert.equal(report.status,'complete'); assert.equal(report.scenario,'baiterek-night');
  assert.equal(report.frameTiming.fps,10,'Synthetic 100 ms input, NOT a device performance measurement');
  assert.equal(report.frameTiming.samples,120); assert.equal(report.drawCalls.mean,96); assert.equal(report.triangles.mean,205000);
  assert.equal(report.startContext.fps,undefined); assert.equal(h.button('Запустить').disabled,false);
  report.frameTiming.fps=999; assert.equal(h.hook.report().frameTiming.fps,10);
  assert.equal(mountVisualPerformanceQA({window:h.win,document:h.doc}),h.hook);
  h.hook.destroy();
});

test('QA respects reduced motion without changing it and excludes visibility resume gaps', () => {
  const h=harness(); h.media.matches=true; h.button('Запустить').onclick(); assert.equal(h.messages.length,0);
  h.media.matches=false; h.button('Запустить').onclick();
  for (let i=0;i<31;i++) h.hook.sampleFrame({dtMs:100});
  h.doc.hidden=true; h.listeners.get('visibilitychange')(); h.hook.sampleFrame({dtMs:10000});
  h.doc.hidden=false; h.listeners.get('visibilitychange')(); h.hook.sampleFrame({dtMs:10000});
  h.button('Остановить').onclick(); const report=h.hook.report();
  assert.equal(report.frameTiming.samples,1); assert.equal(report.frameTiming.p95Ms,100);
  assert.equal(report.excluded.hidden,1); assert.equal(report.excluded['resume-boundary'],1);
  h.hook.destroy();
});

test('QA frame-level exclusion flags agree with the visible paused status', () => {
  const h=harness(); h.button('Запустить').onclick();
  for (let i=0;i<10;i++) h.hook.sampleFrame({dtMs:100,reducedMotion:true});
  assert.ok(h.panel.children.some(n=>n.textContent==='Пауза: reduced-motion'));
  h.button('Остановить').onclick();
  assert.equal(h.hook.report().frameTiming.samples,0);
  assert.equal(h.hook.report().excluded['reduced-motion'],10);
  h.hook.destroy();
});
