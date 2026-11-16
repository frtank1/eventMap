import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtemp,mkdir,readFile,readdir,rm,stat,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {stageGeography} from '../stage-geography.mjs';
import {createPreviewServer} from '../preview.mjs';
import {GEO_LIMITS,prepareGeoData,validateGeoManifest} from '../runtime/geo-catalog-store.js';

const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const addresses=()=>({schemaVersion:1,source:'OpenStreetMap',license:'ODbL-1.0',timestamp:'2026-11-16',
  records:[['way/100',[71.43,51.128,71.4304,51.1283],'Тестовая улица, 7','']]});
const places=()=>({schemaVersion:1,source:'OpenStreetMap',license:'ODbL-1.0',timestamp:'2026-11-16',
  places:[{id:'test-place',sourceRefs:['way/100'],footprints:[{sourceRef:'way/100',
    rings:[[[71.43,51.128],[71.4304,51.128],[71.4304,51.1283],[71.43,51.128]]]}],
    entrances:[{id:'node/101',lng:71.43,lat:51.128,kind:'main',access:'yes',wheelchair:'unknown'}],
    review:{status:'matched',notes:'Fixture only',sourceUrls:['https://www.openstreetmap.org/way/100']}}]});

async function fixture(t){
  const root=await mkdtemp(path.join(tmpdir(),'eventmap-stage-geography-'));
  t.after(()=>rm(root,{recursive:true,force:true}));
  const source=path.join(root,'source'),targets=[path.join(root,'content'),path.join(root,'server/world')];
  await mkdir(source,{recursive:true});
  const put=(kind,data)=>writeFile(path.join(source,`astana-${kind}.json`),typeof data==='string'||Buffer.isBuffer(data)?data:JSON.stringify(data)+'\n');
  await put('addresses',addresses());await put('places',places());
  for(const target of targets){
    await mkdir(path.join(target,'assets'),{recursive:true});
    await writeFile(path.join(target,'manifest.json'),'existing model manifest\n');
    await writeFile(path.join(target,'assets/old.json'),'previous asset\n');
  }
  const stage=()=>stageGeography({source,targets});
  return {root,source,targets,put,stage};
}

test('stages exact JSON bytes in both trees, deterministically, without touching model assets',async t=>{
  const {source,targets,stage}=await fixture(t),manifest=await stage();
  validateGeoManifest(manifest);
  assert.deepEqual(Object.keys(manifest).sort(),['addresses','catalogVersion','places','release','schemaVersion']);
  assert.match(manifest.release,/^[a-zA-Z0-9._-]{1,64}$/);
  const before=await stat(path.join(targets[0],manifest.addresses.file));
  assert.deepEqual(await stage(),manifest);
  assert.equal((await stat(path.join(targets[0],manifest.addresses.file))).ino,before.ino,'existing immutable asset is reused');
  for(const target of targets){
    assert.deepEqual(JSON.parse(await readFile(path.join(target,'places-manifest.json'),'utf8')),manifest);
    const data={};
    for(const kind of ['addresses','places']){
      const bytes=await readFile(path.join(target,manifest[kind].file));
      assert.deepEqual(bytes,await readFile(path.join(source,`astana-${kind}.json`)));
      assert.equal(manifest[kind].bytes,bytes.length);assert.equal(manifest[kind].sha256,hash(bytes));
      assert.equal(manifest[kind].file,`assets/${hash(bytes)}.json`);
      data[kind]=JSON.parse(bytes);
    }
    prepareGeoData(data.addresses,data.places);
    assert.equal(await readFile(path.join(target,'manifest.json'),'utf8'),'existing model manifest\n');
    assert.equal(await readFile(path.join(target,'assets/old.json'),'utf8'),'previous asset\n');
    assert.ok(!(await readdir(target)).some(name=>name.endsWith('.tmp')));
    assert.ok(!(await readdir(path.join(target,'assets'))).some(name=>name.endsWith('.tmp')));
  }
});

test('changed geography gets a new release and retains both previous immutable assets',async t=>{
  const {targets,put,stage}=await fixture(t),first=await stage(),next=places();
  next.places[0].review.notes='Reviewed again';await put('places',next);
  const second=await stage();assert.notEqual(second.release,first.release);
  assert.deepEqual(second.addresses,first.addresses);assert.notDeepEqual(second.places,first.places);
  for(const target of targets)for(const descriptor of [first.addresses,first.places,second.places])
    assert.equal(hash(await readFile(path.join(target,descriptor.file))),descriptor.sha256);
});

test('runtime validation, malformed JSON, UTF-8 and byte budgets fail before either pointer changes',async t=>{
  const {targets,put,stage}=await fixture(t);await stage();
  const before=await Promise.all(targets.map(target=>readFile(path.join(target,'places-manifest.json'))));
  const badAddresses=addresses();badAddresses.records[0][1][0]=0;
  const badPlaces=places();badPlaces.places[0].entrances[0].lng=0;
  const duplicatePlaces=places();duplicatePlaces.places.push(structuredClone(duplicatePlaces.places[0]));
  const cases=[['addresses',badAddresses],['places',badPlaces],['places',duplicatePlaces],
    ['places','globalThis.executed=true;'],['places',Buffer.from([0xff,0xfe])],
    ['places',' '.repeat(GEO_LIMITS.places+1)],['addresses',' '.repeat(GEO_LIMITS.addresses+1)]];
  for(const [kind,invalid] of cases){
    await put('addresses',addresses());await put('places',places());await put(kind,invalid);
    await assert.rejects(stage);
    for(let i=0;i<targets.length;i++)assert.deepEqual(await readFile(path.join(targets[i],'places-manifest.json')),before[i]);
  }
  assert.equal(globalThis.executed,undefined);
});

test('missing places source fails without creating either output tree',async t=>{
  const {root,source}=await fixture(t);await rm(path.join(source,'astana-places.json'));
  const targets=[path.join(root,'absent-content'),path.join(root,'absent-server')];
  await assert.rejects(stageGeography({source,targets}),{code:'ENOENT'});
  for(const target of targets)await assert.rejects(stat(target),{code:'ENOENT'});
});

test('a collision in either tree preserves both pointers and never overwrites the colliding asset',async t=>{
  const {source,targets,put,stage}=await fixture(t);await stage();
  const pointers=await Promise.all(targets.map(target=>readFile(path.join(target,'places-manifest.json'))));
  const next=places();next.places[0].review.notes='Next release';await put('places',next);
  const bytes=await readFile(path.join(source,'astana-places.json')),file=`assets/${hash(bytes)}.json`;
  for(const target of targets){
    await writeFile(path.join(target,file),'corrupt immutable asset');
    await assert.rejects(stage,/Immutable geography asset mismatch/);
    assert.equal(await readFile(path.join(target,file),'utf8'),'corrupt immutable asset');
    for(let i=0;i<targets.length;i++)assert.deepEqual(await readFile(path.join(targets[i],'places-manifest.json')),pointers[i]);
    const other=targets.find(item=>item!==target);
    await assert.rejects(stat(path.join(other,file)),{code:'ENOENT'});
    await rm(path.join(target,file));
  }
});

test('concurrent identical staging does not expose partial files or replace immutable assets',async t=>{
  const {targets,stage}=await fixture(t),manifests=await Promise.all([stage(),stage(),stage()]);
  assert.deepEqual(manifests[0],manifests[1]);assert.deepEqual(manifests[1],manifests[2]);
  for(const target of targets){
    const manifest=JSON.parse(await readFile(path.join(target,'places-manifest.json'),'utf8'));
    assert.deepEqual(manifest,manifests[0]);
    for(const kind of ['addresses','places'])assert.equal(hash(await readFile(path.join(target,manifest[kind].file))),manifest[kind].sha256);
    assert.ok(!(await readdir(path.join(target,'assets'))).some(name=>name.endsWith('.tmp')));
  }
});

test('preview serves the geography pointer, immutable JSON and both local fallbacks on isolated loopback',async t=>{
  const {root,source,stage}=await fixture(t),manifest=await stage();
  await mkdir(path.join(root,'preview-dist'));
  for(const kind of ['addresses','places'])await writeFile(path.join(root,`preview-dist/${kind}.json`),await readFile(path.join(source,`astana-${kind}.json`)));
  const server=createPreviewServer({root});
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  t.after(()=>new Promise((resolve,reject)=>{server.close(error=>error?reject(error):resolve());server.closeAllConnections();}));
  const base=`http://127.0.0.1:${server.address().port}`;
  const response=await fetch(`${base}/world/places-manifest.json`,{headers:{Origin:'null'}});
  assert.equal(response.status,200);assert.equal(response.headers.get('access-control-allow-origin'),'*');
  assert.match(response.headers.get('content-type'),/^application\/json/);
  assert.deepEqual(await response.json(),manifest);
  for(const kind of ['addresses','places']){
    const remote=await fetch(`${base}/world/${manifest[kind].file}`),local=await fetch(`${base}/${kind}.json`);
    assert.equal(remote.status,200);assert.equal(local.status,200);
    const bytes=Buffer.from(await remote.arrayBuffer());
    assert.equal(hash(bytes),manifest[kind].sha256);assert.equal(bytes.length,manifest[kind].bytes);
    assert.deepEqual(Buffer.from(await local.arrayBuffer()),bytes);
  }
  for(const route of ['/world/places.json','/world/places-manifest.js','/world/assets/secret.json'])
    assert.equal((await fetch(base+route)).status,404);
  await fetch(`${base}/__qa/release?value=offline`,{method:'POST'});
  assert.equal((await fetch(`${base}/world/places-manifest.json`)).status,503);
  await fetch(`${base}/__qa/release?value=incompatible`,{method:'POST'});
  assert.equal((await (await fetch(`${base}/world/places-manifest.json`)).json()).catalogVersion,99);
});
