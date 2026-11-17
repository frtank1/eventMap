import test from 'node:test';
import assert from 'node:assert/strict';
import {applyHeights,FLOOR_HEIGHT} from '../apply-nsdi-heights.mjs';
import {WORLD_CENTER,LON_SCALE,METERS} from '../runtime/world-state.js';

const local=(x,z)=>[WORLD_CENTER[0]+x/LON_SCALE,WORLD_CENTER[1]-z/METERS];
const box=(x,z,w=20,d=12)=>[[x,z],[x+w,z],[x+w,z+d],[x,z+d]];
test('untagged buildings take NSDI floors; tagged heights and misses stay',()=>{
  const overview={schemaVersion:1,source:{provider:'x'},buildings:[[5,0,'cream',[box(0,0)]],[4,0,'sand',[box(100,0)]],[48,0,'cyan',[box(200,0)]],[5,0,'cream',[box(900,900)]],[5,3,'cream',[box(300,0)]]]};
  const nsdi={source:{name:'НИПД'},retrieved:'2026-11-17',buildings:[
    ['a',9,1,box(-2,-2,30,20).map(([x,z])=>local(x,z))],
    ['b',2,1,box(95,-5,40,30).map(([x,z])=>local(x,z))],
    ['c',12,1,box(195,-5,40,30).map(([x,z])=>local(x,z))],
    ['d',null,1,box(895,895,40,30).map(([x,z])=>local(x,z))]]};
  const {overview:out,heights}=applyHeights(overview,nsdi);
  assert.deepEqual(out.buildings.map(b=>b[0]),[9*FLOOR_HEIGHT>29.6&&9*FLOOR_HEIGHT<29.8?29.5:29.5,6.5,48,5,5]);
  assert.deepEqual([heights.untaggedBuildings,heights.fromNsdi,heights.keptOsmHeights],[3,2,2]);
  assert.equal(out.source.provider,'x');assert.equal(out.source.heights.floorHeight,FLOOR_HEIGHT);
  assert.equal(overview.buildings[0][0],5,'input is not mutated');
});
