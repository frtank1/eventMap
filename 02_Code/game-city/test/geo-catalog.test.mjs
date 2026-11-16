import test from 'node:test';
import assert from 'node:assert/strict';
import {GeoCatalogStore,validateGeoManifest,validatePlaceCatalog,prepareGeoData} from '../runtime/geo-catalog-store.js';
import {hash} from '../runtime/content-store.js';
import {placeMessage} from '../runtime/place-addresses.js';

const ring=[[71.43,51.128],[71.4304,51.128],[71.4304,51.1283],[71.43,51.1283],[71.43,51.128]];
const at={id:'test',lng:71.4302,lat:51.1281};
const door=(id,kind='yes',access='unknown')=>({id:`node/${id}`,lng:71.43,lat:51.1281,kind,access,wheelchair:'unknown'});
const data=(n=1)=>({addresses:{schemaVersion:1,records:[['way/1',[71.43,51.128,71.4304,51.1283],`Тестовая, ${n}`,'']]},
  places:{schemaVersion:1,timestamp:'2026-11-16T00:00:00Z',source:'OpenStreetMap',license:'ODbL-1.0',places:[
    {id:'test',sourceRefs:['way/1'],footprints:[{sourceRef:'way/1',rings:[ring]}],entrances:[door(1,'main'),door(2,'service'),door(3,'yes','private'),door(4,'emergency')],review:{status:'matched',notes:'Test fixture only',sourceUrls:['https://www.openstreetmap.org/way/1']}}
  ]}});
async function pack(n=1){const d=data(n),manifest={schemaVersion:1,catalogVersion:1,release:`geo-${n}`},files={};
  for(const kind of ['addresses','places']){const bytes=new TextEncoder().encode(JSON.stringify(d[kind])),sha256=await hash(bytes);files[sha256]=bytes;manifest[kind]={sha256,file:`assets/${sha256}.json`,bytes:bytes.length};}return {manifest,files};}
class MemoryCache{async read(){return this.pack;}async write(_,pack){this.pack=structuredClone(pack);}}
const base='https://maps.example.test/world/places-manifest.json';
const network=p=>async(url,options)=>{assert.equal(options.credentials,'omit');assert.equal(options.redirect,'error');return new Response(url===base?JSON.stringify(p.manifest):p.files[url.split('/').at(-1).replace('.json','')],{status:200});};
test('catalog associations, safe doors, explicit missing and no fabricated door',()=>{
  const d=data(),index=prepareGeoData(d.addresses,d.places).index,r=index.lookup(at);
  assert.equal(r.address,'Тестовая, 1');assert.equal(r.entranceStatus,'mapped');assert.deepEqual(r.entrances.map(e=>e.id),['node/1']);
  assert.equal(index.lookup({...at,lng:71.5}).entranceStatus,'unreviewed');
  assert.equal(index.lookup({lng:at.lng,lat:at.lat,geoRings:[ring]}).entranceStatus,'mapped');
  const message=placeMessage({...at,title:'Точка'},r);assert.equal(message.lng,at.lng);assert.notEqual(message.lng,r.entrances[0].lng);
  d.places.places[0].entrances=[];assert.equal(prepareGeoData(d.addresses,d.places).index.lookup(at).entranceStatus,'not_mapped');
  d.places.places[0].review.status='needs_review';assert.equal(prepareGeoData(d.addresses,d.places).index.lookup(at).entranceStatus,'unreviewed');
});
test('remote catalog overrides compiled references and enforces uniqueness/door geometry',()=>{
  const d=data();d.places.places[0].id='baiterek';
  assert.equal(prepareGeoData(d.addresses,d.places).index.lookup({...at,id:'baiterek'}).address,'Тестовая, 1');
  const bad=structuredClone(d.places);bad.places[0].entrances[0].lng=71.5;assert.throws(()=>validatePlaceCatalog(bad),/entrance-not-on-building/);
  const dup=structuredClone(d);dup.addresses.records.push(dup.addresses.records[0]);assert.throws(()=>prepareGeoData(dup.addresses,dup.places));
  bad.places[0].entrances=[];bad.places.push(bad.places[0]);assert.throws(()=>validatePlaceCatalog(bad),/place-id/);
});
test('eight door bridge budget; main first, no private/service/emergency doors',()=>{
  const d=data();d.places.places[0].entrances=[...Array.from({length:20},(_,i)=>door(i+1)),door(30,'main')];
  const result=prepareGeoData(d.addresses,d.places).index.lookup(at);assert.equal(result.entrances.length,8);assert.equal(result.entrances[0].id,'node/30');
  assert.ok(JSON.stringify({v:1,...placeMessage({...at,title:'x'.repeat(120)},result)}).length<4096);
});
test('version/path/size limits reject incompatible catalogs',async()=>{
  const p=await pack();assert.equal(validateGeoManifest(p.manifest),p.manifest);
  for(const mutate of [m=>m.catalogVersion=2,m=>m.addresses.file='https://attacker.test/a.json',m=>m.places.bytes=2*1024*1024]){const m=structuredClone(p.manifest);mutate(m);assert.throws(()=>validateGeoManifest(m));}
  const store=new GeoCatalogStore({fallback:async()=>data()});await assert.rejects(store.start('http://not-local.test/world/places-manifest.json'));
});
test('atomic update, cached restart offline, refresh and rollback without app rebuild',async()=>{
  let p=await pack(),cache=new MemoryCache(),activations=0;
  const store=new GeoCatalogStore({fallback:async()=>data(0),cache,fetcher:(...args)=>network(p)(...args),activate:()=>{activations++;}});
  await store.start(base);assert.equal((await store.lookup(at)).address,'Тестовая, 1');
  p=await pack(2);await store.update();assert.equal(store.manifest.release,'geo-2');assert.equal((await store.lookup(at)).address,'Тестовая, 2');
  p=await pack(1);await store.update();assert.equal(store.manifest.release,'geo-1');assert.equal((await store.lookup(at)).address,'Тестовая, 1');
  const restart=new GeoCatalogStore({fallback:async()=>data(0),cache,fetcher:async()=>{throw new Error('offline');}});
  await restart.start(base);assert.equal(restart.manifest.release,'geo-1');assert.equal((await restart.lookup(at)).address,'Тестовая, 1');
  assert.equal(activations,4);store.stop();restart.stop();
});
test('tampered asset, invalid pair and incompatible manifest keep working cached version',async()=>{
  let p=await pack(),cache=new MemoryCache();const store=new GeoCatalogStore({fallback:async()=>data(0),cache,fetcher:(...a)=>network(p)(...a)});
  await store.start(base);const original=structuredClone(cache.pack);
  p=await pack(2);p.files[p.manifest.addresses.sha256][0]=0;await store.update();assert.equal(store.manifest.release,'geo-1');
  p=await pack(2);p.manifest.catalogVersion=99;await store.update();assert.equal(store.manifest.release,'geo-1');
  assert.deepEqual(cache.pack,original);assert.equal((await store.lookup(at)).address,'Тестовая, 1');
});
test('disk write failure still activates valid in-memory pair and throttles resume',async()=>{
  const p=await pack(),states=[];let calls=0;
  const store=new GeoCatalogStore({fallback:async()=>data(0),cache:{read:async()=>null,write:async()=>{throw Error('quota');}},fetcher:(...a)=>{calls++;return network(p)(...a);},onStatus:s=>states.push(s.state)});
  await store.start(base);assert.equal(store.manifest.release,'geo-1');assert.ok(states.includes('updated-memory-only'));
  const previous=calls;await store.update({force:false});assert.equal(calls,previous);store.stop();await store.update();assert.equal(calls,previous);
});
test('delayed startup cache cannot roll a concurrent fresh refresh back to old data',async()=>{
  const old=await pack(1),fresh=await pack(2);let releaseRead,readStarted;
  const started=new Promise(resolve=>readStarted=resolve),gate=new Promise(resolve=>releaseRead=resolve);
  let reads=0,calls=0;const cache=new MemoryCache();cache.pack=old;
  cache.read=async()=>{if(++reads===1){readStarted();await gate;return old;}return cache.pack;};
  const store=new GeoCatalogStore({fallback:async()=>data(0),cache,fetcher:(...a)=>{calls++;return network(fresh)(...a);}});
  const boot=store.start(base);await started;const refresh=store.update();
  await Promise.resolve();assert.equal(calls,0,'network refresh waits for startup cache');
  releaseRead();await Promise.all([boot,refresh]);assert.equal(store.manifest.release,'geo-2');assert.equal((await store.lookup(at)).address,'Тестовая, 2');
});
test('equal bounding boxes on disjoint real polygons do not assign another building address',()=>{
  const d=data(),a=[ring[0],ring[1],ring[3],ring[0]],b=[ring[1],ring[2],ring[3],ring[1]];
  d.places.places[0].footprints[0].rings=[a];d.places.places[0].entrances=[];
  const index=prepareGeoData(d.addresses,d.places).index;
  assert.equal(index.lookup({geoRings:[a]}).address,'Тестовая, 1');
  assert.equal(index.lookup({geoRings:[b]}).addressStatus,'missing');
});
test('entrance identity does not require an address record; selected corps stays isolated',()=>{
  const d=data();d.addresses.records=[];
  const p=d.places.places[0],second=ring.map(([x,y])=>[x+.002,y]);
  p.sourceRefs.push('way/2');p.footprints.push({sourceRef:'way/2',rings:[second]});p.entrances.push({...door(5,'main'),lng:71.432});
  const index=prepareGeoData(d.addresses,d.places).index,result=index.lookup({sourceRef:'osm:way/1',lat:at.lat,lng:at.lng});
  assert.equal(result.addressStatus,'missing');assert.equal(result.entranceStatus,'mapped');assert.deepEqual(result.entrances.map(e=>e.id),['node/1']);
});
test('canonical address references cannot overflow the native envelope',()=>{
  const d=data();d.addresses.records[0][0]='way/'+'1'.repeat(4000);assert.throws(()=>prepareGeoData(d.addresses,d.places));
});
