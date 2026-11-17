import test from 'node:test';
import assert from 'node:assert/strict';
import {pageUrl,compact,download,CITIES} from '../import-nsdi-city.mjs';

const feature=(id,levels,coords)=>({id,properties:{levels,usage:'1'},geometry:{type:'MultiPolygon',coordinates:[[coords]]}});
const square=[[71.4,51.1],[71.4001,51.1],[71.4001,51.1001],[71.4,51.1001],[71.4,51.1]];
test('WFS request: lat,lon bbox, stable paging, only needed fields',()=>{
  const u=new URL(pageUrl('astana',2000));
  assert.equal(u.searchParams.get('bbox'),'50.95,71.1,51.4,71.85,urn:ogc:def:crs:EPSG::4326');
  assert.equal(u.searchParams.get('sortBy'),'id');assert.equal(u.searchParams.get('startIndex'),'2000');
  assert.equal(u.searchParams.get('propertyName'),'levels,usage,geom');
  assert.deepEqual(Object.keys(CITIES),['astana','almaty','shymkent']);
});
test('compact keeps outer rings and valid floor counts only',()=>{
  assert.deepEqual(compact(feature('b.1','9',square))[0].slice(0,3),['b.1',9,1]);
  assert.equal(compact(feature('b.2','abc',square))[0][1],null);
  assert.equal(compact(feature('b.3','0',square))[0][1],null);
  assert.equal(compact({id:'x',geometry:{type:'Point',coordinates:[1,2]}}).length,0);
});
test('download pages until a short page and checks the total',async()=>{
  const pages=[1000,1000,7];let calls=0;
  const fetchImpl=async url=>{const n=pages[calls++];const start=Number(new URL(url).searchParams.get('startIndex'));
    return {ok:true,json:async()=>({numberMatched:2007,features:Array.from({length:n},(_,i)=>feature(`b.${start+i}`,'5',square))})};};
  const data=await download('astana',{fetchImpl,log:()=>{},pause:0});
  assert.equal(calls,3);assert.equal(data.features,2007);assert.equal(data.buildings.length,2007);
  await assert.rejects(download('astana',{fetchImpl:async()=>({ok:true,json:async()=>({numberMatched:5,features:[feature('a','1',square)]})}),log:()=>{},pause:0}),/Incomplete/);
  await assert.rejects(download('paris',{log:()=>{}}),/Unknown city/);
});
