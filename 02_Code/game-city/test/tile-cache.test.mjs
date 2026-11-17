import test from 'node:test';
import assert from 'node:assert/strict';
import {createTileCache,readTileBytes,parseTileMetadata} from '../runtime/tile-cache.js';
test('cache disabled or denied never blocks map networking',async()=>{
  for(const indexedDB of [undefined,{open(){throw Error('denied');}}]){
    const cache=createTileCache({indexedDB});assert.equal(await cache.get('x'),null);await cache.put('x',new ArrayBuffer(10));assert.equal(await cache.get('x'),null);assert.equal(cache.stats().available,false);assert.equal(cache.stats().writes,0);
  }
});
test('bounded tile reads cancel oversized streams, including missing Content-Length',async()=>{
  assert.equal((await readTileBytes(new Response(new Uint8Array(4)),4)).byteLength,4);
  let cancelled=false;
  const response=new Response(new ReadableStream({pull(c){c.enqueue(new Uint8Array(5));},cancel(){cancelled=true;}}));
  await assert.rejects(()=>readTileBytes(response,4),/budget/);assert.equal(cancelled,true);
  await assert.rejects(()=>readTileBytes(new Response('x',{headers:{'Content-Length':'100'}}),4),/budget/);
});
test('tile metadata keeps advertised version/TMS; never accepts arbitrary hosts',()=>{
  const url='https://tiles.openfreemap.org/planet/version/{z}/{x}/{y}.pbf';
  assert.deepEqual(parseTileMetadata({tiles:[url],scheme:'tms'}),{template:url,scheme:'tms',attribution:null});
  for(const input of [{tiles:['http://tiles.openfreemap.org/{z}/{x}/{y}']},{tiles:['https://bad.invalid/{z}/{x}/{y}']},{tiles:['https://tiles.openfreemap.org/planet/latest']},{}])assert.throws(()=>parseTileMetadata(input));
});
