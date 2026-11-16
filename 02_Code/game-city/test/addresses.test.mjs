import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createAddressIndex,createAddressStore,placeMessage,LANDMARK_REFS} from '../runtime/place-addresses.js';

const ring=[[71.43,51.128],[71.4304,51.128],[71.4304,51.1283],[71.43,51.1283],[71.43,51.128]];
const row=['way/100',[71.43,51.128,71.4304,51.1283],'Тестовая улица, 7',''];
const pack=records=>({schemaVersion:1,records});
test('address resolves a matching footprint, never nearest street or neighbouring building',()=>{
  const index=createAddressIndex(pack([row]));
  assert.equal(index.lookup({geoRings:[ring]}).addressStatus,'missing','bbox alone is not an identity proof');
  assert.equal(index.lookup({geoRings:[ring.map(([x,y])=>[x+.0006,y])]}).addressStatus,'missing');
  assert.equal(index.lookup({id:'osm-100',lat:51.1281,lng:71.4302}).addressStatus,'missing','MVT IDs are not raw OSM IDs');
  assert.equal(index.lookup({sourceRef:'osm:way/100',lat:51.2,lng:71.5}).addressStatus,'missing','wrong/stale reference elsewhere');
  assert.equal(index.lookup({sourceRef:'osm:way/100',lat:51.1281,lng:71.4302}).address,row[2]);
});
test('multiple addresses remain ambiguous; courtyards do not borrow addresses',()=>{
  const nodes=[['node/1',[71.4301,51.1281,71.4301,51.1281],'Тестовая, 1',''],['node/2',[71.4302,51.1282,71.4302,51.1282],'Тестовая, 2','']];
  assert.equal(createAddressIndex(pack(nodes)).lookup({geoRings:[ring]}).addressStatus,'ambiguous');
  const hole=[[71.43005,51.12805],[71.4303,51.12805],[71.4303,51.12825],[71.43005,51.12825]];
  assert.equal(createAddressIndex(pack(nodes)).lookup({geoRings:[ring,hole]}).addressStatus,'missing');
});
test('untrusted address extract is bounded and errors leave selection usable',async()=>{
  for(const records of [[['way/1',[0,0,0,0],'Street','']],[[...row.slice(0,2),'bad\ntext','']]])assert.throws(()=>createAddressIndex(pack(records)));
  const old=globalThis.fetch;let requests=0;
  globalThis.fetch=async()=>{requests++;throw new Error('offline');};
  try{const store=createAddressStore();const r=await store.lookup({});assert.equal(r.addressStatus,'unavailable');assert.equal(placeMessage({lat:51.1,lng:71.4},r).title,'Точка на карте');await store.lookup({});assert.equal(requests,1);}finally{globalThis.fetch=old;}
});
test('real public extract retains attribution and exact landmark references',async()=>{
  const data=JSON.parse(await readFile(new URL('../source/astana-addresses.json',import.meta.url),'utf8'));
  assert.equal(data.license,'ODbL-1.0');assert.ok(data.timestamp);assert.ok(data.records.length>30000);
  const index=createAddressIndex(data),manifest=JSON.parse(await readFile(new URL('../content/manifest.json',import.meta.url),'utf8'));
  for(const [id,ref] of Object.entries(LANDMARK_REFS)){
    const place=manifest.models.find(m=>m.id===id);assert.ok(place,id);
    const address=index.lookup(place);assert.equal(address.sourceRef,`osm:${ref}`,id);assert.equal(address.addressStatus,'source',id);
  }
  assert.match(index.lookup(manifest.models.find(m=>m.id==='library')).address,/11/);
});
