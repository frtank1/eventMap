import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source=await readFile(new URL('../runtime/main.js',import.meta.url),'utf8');
const code=source.slice(source.indexOf('async function switchQuarterVariant('),source.indexOf('function finishZoomBenchmark('));
function fixture(fail){
  const calls=[],resource=name=>({group:{name},destroy(){calls.push(`destroy:${name}`);},setBackdropReady(){}});
  const old={backdrop:resource('old-backdrop'),quarter:resource('old-quarter'),city:resource('old-city')};
  const next={backdrop:resource('new-backdrop'),overlay:resource('new-overlay'),quarter:resource('new-quarter'),city:resource('new-city')};
  let creates=0;
  const c=vm.createContext({trialRequested:'current',trialBusy:false,trialRevision:0,geometryRevision:0,backdropStopped:false,trialBuild:null,
    AbortController,setTimeout:fn=>{if(fail==='timeout')fn();return 1;},clearTimeout(){},
    overviewData:{},ensureOverview:async()=>({}),trialSnapshot:fail==='read'?null:{},quarterReplacement:null,trialBackdrop:null,
    backdrop:old.backdrop,quarter:old.quarter,city:old.city,districtId:'baiterek',districts:new Map([['baiterek',old.quarter]]),models:[],withheldModels:new Set(),geoCatalog:{},NSDI_LIMITS:{bytes:10},
    debugSceneAllowed:()=>true,debugContext:()=>({native:false}),trialVariant:()=>c.quarterReplacement?'nsdi':'current',trialUi(){},finishZoomBenchmark(){},
    native:m=>calls.push(m),readBundledJSON:async()=>{throw Error('HTTP 404');},
    createNsdiReplacement:()=>({ownsGeo:()=>false,overview:{}}),makeQuarter:()=>next.quarter,createWorldStream:()=>next.city,
    createCityBackdrop:async()=>{if(fail==='prepare')throw Error('invalid geometry');if(fail==='concurrent')c.geometryRevision++;return creates++===0?next.backdrop:next.overlay;},
    scene:{add:g=>calls.push(`add:${g.name}`)},social:{refreshAnchors(){}},setQuarterMode(){},appearance(){if(fail==='appearance'&&c.backdrop===next.backdrop)throw Error('appearance failed');},backdropBuild:{abort(){}},lastAnchorLayout:'',shadowDirty:false});
  vm.runInContext(code,c);return {c,calls,old,next};
}
for(const fail of ['read','prepare','concurrent','appearance','timeout'])test(`trial ${fail} failure preserves working scene and camera/event ownership`,async()=>{
  const {c,calls,old}=fixture(fail);await c.switchQuarterVariant('nsdi');
  assert.equal(c.backdrop,old.backdrop);assert.equal(c.quarter,old.quarter);assert.equal(c.city,old.city);assert.equal(c.quarterReplacement,null);assert.equal(c.trialBusy,false);
  assert.ok(!calls.some(v=>typeof v==='string'&&v.startsWith('destroy:old-')));
  assert.ok(calls.some(m=>m.state==='quarter-trial-failed'));
});
test('successful A/B activation disposes old geometry only after the replacement is ready',async()=>{
  const {c,calls,next}=fixture();await c.switchQuarterVariant('nsdi');
  assert.equal(c.backdrop,next.backdrop);assert.equal(c.quarter,next.quarter);assert.equal(c.trialBackdrop,next.overlay);assert.equal(c.trialBusy,false);
  assert.ok(calls.indexOf('add:new-overlay')<calls.indexOf('destroy:old-backdrop'));
  assert.ok(calls.some(m=>m.state==='quarter-trial-ready'&&m.variant==='nsdi'));
});
