import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
import {WORLD_BOUNDS,worldPosition,containsCoordinate,clampTarget,fallbackDistrict,referenceDistrict} from '../runtime/world-state.js';
test('city viewport covers north/south/east/west, not just the two pilot districts',()=>{
  for(const [lng,lat]of [[71.43,51.128],[71.40,51.108],[71.40957,51.07242],[71.48,51.30],[71.7,51.15],[71.2,51.15]])assert.ok(containsCoordinate(lng,lat));
  assert.equal(containsCoordinate(NaN,51),false);assert.equal(containsCoordinate(72,51.1),false);
  const p=clampTarget({x:1e9,y:0,z:-1e9}),limit=worldPosition(WORLD_BOUNDS.east,WORLD_BOUNDS.north);assert.equal(p.x,limit.x);assert.equal(p.z,limit.z);
});
test('offline district is only a nearby fallback, never a navigation cage or double layer',()=>{
  const centers=[{id:'baiterek',x:0,z:0},{id:'arena',x:-1900,z:2200}];
  assert.equal(fallbackDistrict({x:20,z:30},600,centers,false),'baiterek');
  assert.equal(fallbackDistrict({x:20,z:30},600,centers,true),null);
  assert.equal(fallbackDistrict({x:6000,z:0},600,centers,false),null);
  assert.equal(fallbackDistrict({x:0,z:0},20000,centers,false),null);
});
test('authored Nurjol does not disappear on successful tile loading, but releases the whole city',()=>{
  assert.equal(referenceDistrict({x:0,z:0},400),'baiterek');
  assert.equal(referenceDistrict({x:0,z:0},900),null);
  assert.equal(referenceDistrict({x:0,z:0},900,true),'baiterek');
  assert.equal(referenceDistrict({x:0,z:0},1100,true),null);
  assert.equal(referenceDistrict({x:400,z:0},400,true),null);
  assert.equal(referenceDistrict({x:0,z:0},18000),null);
  assert.equal(referenceDistrict({x:0,z:0},NaN),null);
});
const root=fileURLToPath(new URL('../',import.meta.url));
const bundled=await build({stdin:{contents:"export {createCityLandmarks} from './runtime/city-landmarks.js';export {createCityStream,chooseZoom} from './source/city-stream.js';export * as THREE from 'three';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createCityLandmarks,createCityStream,chooseZoom,THREE}=await import('data:text/javascript;base64,'+Buffer.from(bundled.outputFiles[0].text).toString('base64'));
test('whole-city view chooses coarse tiles and returns to detail with hysteresis',()=>{
  assert.equal(chooseZoom(35000,12),10);assert.equal(chooseZoom(32000,10),10);
  assert.equal(chooseZoom(26000,14),11);assert.equal(chooseZoom(18000,11),11);
  assert.equal(chooseZoom(8000,14),13);assert.equal(chooseZoom(500,11),14);
});
test('city streaming bounds requests/cache, picks loaded coverage and aborts when destroyed',async()=>{
  const original=globalThis.fetch;let active=0,maxActive=0,requests=0;const pending=[];
  globalThis.fetch=async(url,{signal})=>{
    if(String(url).endsWith('/planet/latest'))return {ok:true,json:async()=>({tiles:['https://tiles.openfreemap.org/test/{z}/{x}/{y}.pbf']})};
    active++;requests++;maxActive=Math.max(maxActive,active);
    return new Promise((resolve,reject)=>{let done=false;const finish=(abort=false)=>{if(done)return;done=true;active--;signal.removeEventListener('abort',onAbort);abort?reject(new DOMException('aborted','AbortError')):resolve({ok:true,arrayBuffer:async()=>new ArrayBuffer(0)});};const onAbort=()=>finish(true);signal.addEventListener('abort',onAbort,{once:true});pending.push(finish);});
  };
  const city=createCityStream({center:[71.43056,51.12822]});
  try{
    await city.ready;
    for(let i=0;i<15;i++){pending.splice(0).forEach(f=>f());await new Promise(resolve=>setTimeout(resolve,0));}
    assert.ok(requests>0);assert.ok(maxActive<=2);assert.ok(city.stats().cityTiles<=9);assert.ok(city.stats().cityCache<=16);assert.ok(city.coverageAt(0,0));assert.equal(city.placeAt(0,0),null);
    city.update(new THREE.Vector3(),17000);
    for(let i=0;i<90;i++){pending.splice(0).forEach(f=>f());await new Promise(resolve=>setTimeout(resolve,0));}
    assert.equal(city.stats().cityZoom,12);assert.ok(maxActive<=2,'overview children share the HTTP budget');
    assert.ok(city.stats().cityTiles<=9);assert.ok(city.stats().cityCache<=16);assert.ok(requests<=60,'bounded coarse tiles plus four children each');
    city.update(new THREE.Vector3(14000,0,-16000),600);city.destroy();await new Promise(resolve=>setTimeout(resolve,0));assert.equal(active,0);assert.equal(city.stats().cityCache,0);
  }finally{city.destroy();globalThis.fetch=original;}
});
test('city landmark catalogue is finite, pickable, geographically placed and bounded',()=>{
  const layer=createCityLandmarks(),ids=new Set(),geos=new Set();let triangles=0,meshes=0;
  for(const {entry,group}of layer.entries){assert.ok(!ids.has(entry.id));ids.add(entry.id);assert.ok(containsCoordinate(entry.lng,entry.lat));assert.equal(group.userData.place,entry);assert.ok(group.children.length);}
  for(const id of ['adp','ansar','library','khan','mega','expo','museum','hazret','grandmosque','independence'])assert.ok(ids.has(id),id);
  layer.group.traverse(o=>{assert.ok(!o.isLight);if(o.isMesh){meshes++;geos.add(o.geometry);triangles+=(o.geometry.index?.count||o.geometry.attributes.position.count)/3;for(const n of o.geometry.attributes.position.array)assert.ok(Number.isFinite(n));}});
  assert.ok(meshes<160,`draw batches ${meshes}`);assert.ok(triangles<350000,`triangles ${triangles}`);
  layer.setAppearance(true,'newyear');layer.update(new THREE.Vector3(0,300,400),600);
  const expo=layer.entries.find(p=>p.entry.id==='expo');assert.ok(layer.excludeBuilding(expo.entry.lng,expo.entry.lat));
  const disposeCounts=new Map();geos.forEach(g=>g.addEventListener('dispose',()=>disposeCounts.set(g,(disposeCounts.get(g)||0)+1)));
  layer.destroy();layer.destroy();geos.forEach(g=>assert.equal(disposeCounts.get(g),1));
});
