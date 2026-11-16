import test from 'node:test';
import assert from 'node:assert/strict';
import {inPolygon,ringBounds,footprintStyle,overviewRelief,createSpatialIndex,treeCandidates,distanceSegment} from '../source/city-overview.js';
const square=(x,z,w)=>[[x,z],[x+w,z],[x+w,z+w],[x,z+w],[x,z]];
const rect={minX:0,maxX:3000,minZ:0,maxZ:3000};
test('overview relief never exaggerates source height or invents a minimum',()=>{
  for(const top of [1,4,9,18,70,310])for(const base of [0,top-1]){
    const height=overviewRelief(top,base);assert.ok(height<=top-base);assert.ok(height<=6);assert.ok(height>0);
  }
  assert.equal(overviewRelief(0),0);
});
test('architecture uses stable footprint identity, not ring order, zoom or feature position',()=>{
  const ring=square(71.43,51.12,.001),style=footprintStyle([ring],123,42);
  assert.deepEqual(style,footprintStyle([[...ring].reverse()],123,42));
  assert.deepEqual(style,footprintStyle([ring.slice(2).concat(ring.slice(0,2))],123,42));
  assert.notEqual(style.key,footprintStyle([square(71.44,51.12,.001)],123,42).key);
  assert.ok(style.plinth>0&&style.plinth<=3.2);assert.ok(style.roof<.6);
  assert.equal(footprintStyle([ring],123,42,10).plinth,0);
  assert.equal(footprintStyle([ring],123,100).material,'cyan');
});
test('spatial queries stay local across ten thousand features and retain large footprints',()=>{
  const grid=createSpatialIndex();
  for(let x=0;x<100;x++)for(let z=0;z<100;z++)grid.insert(ringBounds(square(x*100,z*100,20)),`${x}:${z}`);
  grid.insert(ringBounds(square(-1000,-1000,20000)),'large');
  assert.deepEqual([...grid.query([10,10])],['0:0','large']);
  assert.deepEqual([...grid.query([55,55])],['large']);
  assert.deepEqual([...grid.query([30000,30000])],[]);
  assert.equal(distanceSegment([10,5],[0,0],[20,0]),5);
  assert.equal(distanceSegment([3,4],[0,0],[0,0]),5);
});
test('trees are deterministic, deduplicated, hole-aware and globally work-bounded',()=>{
  const rings=[square(0,0,3000),square(800,800,400)];
  const args={areas:[rings,rings],rect,maxAttempts:1500};
  const candidates=[...treeCandidates(args)],trees=candidates.filter(Boolean);
  assert.equal(candidates.length,1500);assert.ok(trees.length>100);
  assert.deepEqual(candidates,[...treeCandidates(args)]);
  assert.equal(new Set(trees.map(t=>t.point.join(':'))).size,trees.length);
  for(const {point,rank}of trees){assert.ok(inPolygon(point,rings));assert.ok(rank>=0&&rank<1);}
  assert.deepEqual([...treeCandidates({areas:[],rect})],[]);
  const rejected=[...treeCandidates({areas:[[square(0,0,1e6),square(-1,-1,1e6+2)]],rect,maxAttempts:128})];
  assert.equal(rejected.length,128);assert.ok(rejected.every(t=>t===null),'rejected work also yields for cancellation');
});
test('tree sites and size ranks survive a change of tile origin',()=>{
  const areas=[[square(0,0,560)]];
  const a=[...treeCandidates({areas,rect:{minX:0,maxX:560,minZ:0,maxZ:560}})].filter(Boolean);
  const shifted=areas.map(rings=>rings.map(r=>r.map(([x,z])=>[x-280,z-280])));
  const b=[...treeCandidates({areas:shifted,origin:[280,280],rect:{minX:-280,maxX:280,minZ:-280,maxZ:280}})].filter(Boolean);
  assert.equal(a.length,b.length);
  for(let i=0;i<a.length;i++){
    assert.equal(a[i].rank,b[i].rank);
    assert.ok(Math.abs(a[i].point[0]-b[i].point[0]-280)<1e-9);
    assert.ok(Math.abs(a[i].point[1]-b[i].point[1]-280)<1e-9);
  }
});
