import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {ContentStore,validateManifest,validateModel,manifestUrl,hash} from '../runtime/content-store.js';
const read=async file=>new Uint8Array(await readFile(new URL('../content/'+file,import.meta.url)));
const v1=JSON.parse(new TextDecoder().decode(await read('manifest.json')));
const v2=JSON.parse(new TextDecoder().decode(await read('releases/pilot-2.json')));
const models={};for(const e of v1.models)models[e.id]=JSON.parse(new TextDecoder().decode(await read(e.file)));
const seed={manifest:v1,models};
class Memory {async read(){return this.pack} async write(_,pack){this.pack=structuredClone(pack)}}
const url='https://content.example/world/manifest.json';
function harness({cache=new Memory(),fetcher,activate}={}) {
  const status=[],activated=[];let release=v2;
  const fetcherDefault=async u=>new Response(u===url?JSON.stringify(release):await read(new URL(u).pathname.replace('/world/','')));
  const store=new ContentStore({seed,cache,fetcher:fetcher||fetcherDefault,onStatus:s=>status.push(s),activate:activate|| (p=>activated.push(p.manifest.release))});
  return {store,cache,status,activated,release:value=>{release=value}};
}
test('exported models fit runtime schema, dimensions and integrity',async()=>{
  validateManifest(v1);assert.equal(v1.models.length,31);for(const e of v1.models){validateModel(models[e.id],e.id);assert.equal(await hash(await read(e.file)),e.sha256)}
});
test('legacy engine pack cannot silently remove the city-wide catalogue',async()=>{
  const h=harness();h.release({...v1,engineVersion:1,models:v1.models.slice(0,3)});await h.store.start(url);assert.equal(h.store.current,seed);assert.equal(h.status.at(-1).state,'kept-working');
});
test('v1 -> v2 activates atomically, survives fresh offline launch, rolls back to v1',async()=>{
  const h=harness();await h.store.start(url);assert.equal(h.store.current.manifest.release,'pilot-2');assert.equal(h.cache.pack.manifest.release,'pilot-2');
  const cold=harness({cache:h.cache,fetcher:async()=>{throw Error('offline')}});await cold.store.start(url);
  assert.equal(cold.store.current.manifest.release,'pilot-2');assert.equal(cold.status.at(-1).state,'kept-working');
  h.release(v1);await h.store.update();assert.equal(h.store.current.manifest.release,'pilot-1');
});
test('bad hash and incomplete download keep scene and cache unchanged',async()=>{
  const h=harness({fetcher:async u=>new Response(u===url?JSON.stringify(v2):'broken')});await h.store.start(url);
  assert.equal(h.store.current,seed);assert.equal(h.cache.pack,undefined);assert.deepEqual(h.activated,[]);assert.equal(h.status.at(-1).state,'kept-working');
});
test('same release label cannot hide changed files; incompatibility cannot activate',async()=>{
  const h=harness();h.release({...v2,release:'pilot-1'});await h.store.start(url);assert.equal(h.store.current.manifest.models[1].sha256,v2.models[1].sha256);
  const before=h.store.current;h.release({...v2,engineVersion:99});await h.store.update();assert.equal(h.store.current,before);
});
test('cached bytes are verified, corrupt cache falls back to built-in',async()=>{
  const h=harness();await h.store.start(url);h.cache.pack.files[v2.models[0].sha256][0]=0;
  const cold=harness({cache:h.cache,fetcher:async()=>{throw Error('offline')}});await cold.store.start(url);assert.equal(cold.store.current,seed);
});
test('GPU build failure keeps prior content; disk failure is reported honestly',async()=>{
  const h=harness({activate:()=>{throw Error('gpu')}});await h.store.start(url);assert.equal(h.store.current,seed);assert.equal(h.cache.pack,undefined);
  const c=new Memory();c.write=async()=>{throw Error('quota')};const k=harness({cache:c});await k.store.start(url);assert.equal(k.status.at(-1).state,'updated-memory-only');
});
test('manifest paths, URLs, indices, budget and finite numbers are constrained',()=>{
  assert.throws(()=>manifestUrl('http://evil.example/world.json'));assert.throws(()=>manifestUrl('https://user:secret@example.com/x'));
  for(const mutate of [m=>m.models[0].file='../secret',m=>m.models[0].bytes=7000000,m=>m.models.push(m.models[0]),m=>m.models[0].lat=NaN]){const m=structuredClone(v1);mutate(m);assert.throws(()=>validateManifest(m))}
  const model=structuredClone(models.baiterek);model.meshes[0].indices[0]=-1;assert.throws(()=>validateModel(model,'baiterek'));
});
test('oversized response and timeout cannot replace working pack',async()=>{
  const h=harness({fetcher:async()=>new Response('x',{headers:{'content-length':'999999999'}})});await h.store.start(url);assert.equal(h.status.at(-1).state,'kept-working');
  const k=harness({fetcher:(_,options)=>new Promise((_,reject)=>options.signal.addEventListener('abort',()=>reject(Error('timeout'))))});k.store.timeout=10;await k.store.start(url);assert.equal(k.store.current,seed);
});
test('parallel refresh is coalesced and stop prevents activation',async()=>{
  let finish, calls=0;const h=harness({fetcher:async()=>{calls++;return await new Promise(r=>{finish=r})}});
  const start=h.store.start(url);await new Promise(r=>setTimeout(r,0));await h.store.update();assert.equal(calls,1);
  h.store.stop();finish(new Response(JSON.stringify(v1)));await start;assert.deepEqual(h.activated,[]);
});
