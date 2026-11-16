import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
import {eventStyle,eventState,eventSummary,canAnimateEvent,isMapTap,EVENT_STYLES} from '../runtime/event-style.js';
import {visibleEvents} from '../runtime/social-state.js';
const now=Date.parse('2026-11-12T12:00:00Z');
const live={id:'live',title:'Test',category:'Спорт',kind:'planned',status:'live',lat:51.128,lng:71.43};
test('known categories choose a model; unknown input never becomes markup or a false venue',()=>{
  assert.equal(eventStyle(live).key,'sport');assert.equal(eventStyle({...live,category:'Музыка'}).key,'music');
  assert.equal(eventStyle({...live,category:'<script>football</script>'}).key,'community');
  assert.equal(eventStyle({category:'unknown',kind:'live_signal'}).key,'discovery');
});
test('schedule, cancellation and expiry override live animation',()=>{
  assert.equal(eventState(live,now),'live');
  for(const extra of [{status:'cancelled'},{status:'ended'},{endAt:'2026-11-12T11:00:00Z'},{expiresAtMs:now-1}])assert.equal(eventState({...live,...extra},now),'ended');
  assert.equal(eventState({...live,startAt:'2026-11-13T12:00:00Z'},now),'planned');
  assert.equal(eventState({...live,status:'soon',kind:'gathering'},now),'gathering');
});
test('labels use actual slots and confirmations, never fabricate viewers or friends',()=>{
  assert.equal(eventSummary({...live,status:'soon',kind:'gathering',capacity:6,goingCount:4},now).label,'Нужно ещё 2');
  assert.equal(eventSummary({...live,status:'soon',kind:'gathering',capacity:6,goingCount:7},now).label,'Состав набран');
  assert.equal(eventSummary({...live,kind:'live_signal',confirmationCount:0},now).label,'Сигнал · без подтверждений');
  assert.equal(eventSummary({...live,kind:'live_signal',confirmationCount:3},now).label,'Сигнал · подтверждений: 3');
});
test('public event bridge retains timing/counters and refuses private fixtures',()=>{
  const raw={...live,startAt:'2026-11-12T10:00:00Z',endAt:'2026-11-13T10:00:00Z',capacity:6,confirmationCount:3,sourceType:'witness',kind:'live_signal',visibility:'public'};
  const values=visibleEvents([raw,{...raw,id:'private',visibility:'private'},{...raw,id:'invite',visibility:'invite_only'}],now);
  assert.equal(values.length,1);assert.equal(values[0].confirmationCount,3);assert.equal(values[0].capacity,6);assert.equal(values[0].endAt,raw.endAt);
  assert.equal(eventState(values[0],Date.parse('2026-11-14T00:00:00Z')),'ended');
});
test('motion has explicit visible, reduced-motion, distance and per-quality budgets',()=>{
  assert.ok(canAnimateEvent(live,{now}));
  for(const flags of [{visible:false},{reduced:true},{blocked:true},{distance:900},{distance:NaN},{index:4},{quality:'low',index:2}])assert.equal(canAnimateEvent(live,{now,...flags}),false);
  assert.equal(canAnimateEvent({...live,status:'soon'},{now}),false);
});
test('camera dragging, long press and reverse time are not map taps',()=>{
  const down={x:100,y:100,t:10};assert.ok(isMapTap(down,{x:101,y:103,t:100}));
  for(const up of [{x:130,y:100,t:100},{x:100,y:100,t:700},{x:100,y:100,t:9}])assert.equal(isMapTap(down,up),false);
  assert.equal(isMapTap(null,down),false);
});
const root=fileURLToPath(new URL('../',import.meta.url));
const bundled=await build({stdin:{contents:"export {createEventModels} from './runtime/event-models.js';export {createBaiterekQuarter} from './runtime/baiterek-quarter.js';export * as THREE from 'three';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createEventModels,createBaiterekQuarter,THREE}=await import('data:text/javascript;base64,'+Buffer.from(bundled.outputFiles[0].text).toString('base64'));
test('all miniature signs have finite shared geometry, bounded batches and no real lights',()=>{
  const pool=createEventModels(),instances=[];const camera=new THREE.PerspectiveCamera();
  for(const [key,s]of Object.entries(EVENT_STYLES)){
    const e={...live,category:s.label},g={key,x:0,z:0,events:[e]},a=pool.create(g),b=pool.create(g);instances.push(a,b);
    let draws=0,triangles=0;a.root.traverse(o=>{assert.ok(!o.isLight);if(o.isMesh){draws++;triangles+=(o.geometry.index?.count||o.geometry.attributes.position.count)/3;for(const n of o.geometry.attributes.position.array)assert.ok(Number.isFinite(n));}});
    assert.ok(draws<=10,`${key}: ${draws}`);assert.ok(triangles<6000,`${key}: ${triangles}`);
    a.update(1,{camera,distance:100,visible:true,index:0});assert.ok(a.animated);
    a.update(2,{camera,distance:100,visible:true,index:0,reduced:true});assert.equal(a.animated,false);
    const first=a.root.children[0].children[0],second=b.root.children[0].children[0];assert.equal(first.geometry,second.geometry);
  }
  const resources=new Set();instances.forEach(a=>a.root.traverse(o=>{if(o.geometry)resources.add(o.geometry);if(o.material)resources.add(o.material);}));
  const counts=new Map();resources.forEach(r=>r.addEventListener('dispose',()=>counts.set(r,(counts.get(r)||0)+1)));
  instances.forEach(a=>a.destroy());pool.destroy();pool.destroy();resources.forEach(r=>assert.equal(counts.get(r),1));
});
test('Arena district is bundled, positioned consistently and uses the same bounded art system',()=>{
  const district=createBaiterekQuarter({district:'arena'});assert.equal(district.district,'arena');
  assert.ok(district.stats().quarterBuildings>5);assert.ok(district.center.length()>2000);assert.equal(district.group.position.x,district.center.x);
  assert.ok(Number.isFinite(district.groundHeight(district.center.x,district.center.z)));assert.ok(Number.isFinite(district.surfaceHeight(district.center.x,district.center.z)));
  let meshes=0;district.group.traverse(o=>{if(o.isMesh){meshes++;for(const n of o.geometry.attributes.position.array)assert.ok(Number.isFinite(n));}});assert.ok(meshes<45);
  district.setAppearance(true,'default');district.update(500,0,false,'low');assert.ok(district.stats().quarterTrees<=180);district.destroy();
});
