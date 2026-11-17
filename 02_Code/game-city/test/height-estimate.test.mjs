import test from 'node:test';
import assert from 'node:assert/strict';
import {estimatedTop,footprintSize,nearLandmark,ESTIMATE,OMT_DEFAULT_HEIGHT} from '../runtime/height-estimate.js';

const rect=(w,d,angle=0)=>{const c=Math.cos(angle),s=Math.sin(angle);return [[0,0],[w,0],[w,d],[0,d]].map(([x,z])=>[x*c-z*s+500,x*s+z*c-300]);};
test('only the OpenMapTiles default height is estimated; real heights are untouched',()=>{
  for(const top of [3,4,6,9,27,125])assert.equal(estimatedTop(top,0,rect(80,14)),top);
  assert.equal(estimatedTop(OMT_DEFAULT_HEIGHT,2,rect(80,14)),OMT_DEFAULT_HEIGHT,'a building part on a podium keeps its data');
  assert.equal(estimatedTop(OMT_DEFAULT_HEIGHT,0,[[0,0],[1,1]]),OMT_DEFAULT_HEIGHT);
});
test('footprint shape decides the estimate, independent of rotation',()=>{
  for(const angle of [0,.3,1.2]){
    assert.equal(estimatedTop(5,0,rect(10,8,angle)),ESTIMATE.shed);
    assert.equal(estimatedTop(5,0,rect(90,14,angle)),ESTIMATE.highRise);
    assert.equal(estimatedTop(5,0,rect(40,15,angle)),ESTIMATE.midRise);
    assert.equal(estimatedTop(5,0,rect(80,60,angle)),ESTIMATE.lowRise);
    const size=footprintSize(rect(90,14,angle));assert.ok(Math.abs(size.long-90)<1e-6&&Math.abs(size.short-14)<1e-6&&Math.abs(size.area-1260)<1e-6);
  }
  // Degrees in, metres out: the same slab given in lon/lat with its scales.
  const kx=69918,kz=111320,deg=rect(90,14).map(([x,z])=>[x/kx,z/kz]);
  assert.equal(estimatedTop(5,0,deg,kx,kz),ESTIMATE.highRise);
});
test('leftover outlines inside a landmark keep their source height',()=>{
  const landmarks=[{x:0,z:0,radius:95}];
  assert.equal(nearLandmark(40,0,landmarks),true);assert.equal(nearLandmark(130,0,landmarks),false);
  assert.equal(estimatedTop(5,0,rect(90,14),1,1,true),5);
});
