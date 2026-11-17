import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {createPreviewServer} from '../preview.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const b=await build({stdin:{contents:'export * from "./runtime/nsdi-quarter-data.js";export * from "./runtime/city-backdrop.js";export * from "./runtime/baiterek-quarter.js";export * from "./runtime/place-addresses.js";export * as THREE from "three";',resolveDir:root},bundle:true,write:false,format:'esm',platform:'node',alias:{three:root+'source/vendor/three.module.js'}});
const {createNsdiReplacement,validateNsdiSnapshot,simplifyNsdiRing,NSDI_BOUNDS,createCityBackdrop,createBaiterekQuarter,readBundledJSON,THREE}=await import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].text).toString('base64'));
const ring=(x=71.431,y=51.128,w=.0005)=>[[x,y],[x+w,y],[x+w,y+w],[x,y+w],[x,y]];
const snapshot=()=>({schemaVersion:1,datasetId:1316,usage:'local-evaluation-only',license:'not_specified',bounds:[...NSDI_BOUNDS],totalFeatures:1,features:[{id:'buildings_a.1',geometry:{type:'MultiPolygon',coordinates:[[ring(),ring(71.4311,51.1281,.0001)]]},properties:{levels:'12',height:null}}]});
const world=([x,y])=>[(x-71.43056)*111320*Math.cos(51.12822*Math.PI/180),(51.12822-y)*111320];
test('NSDI snapshot is bounded, complete and explicit about evaluation-only rights',()=>{
  assert.equal(validateNsdiSnapshot(snapshot()).features.length,1);
  for(const change of [s=>s.totalFeatures=2,s=>s.license='odbl',s=>s.bounds[0]=0,s=>s.features[0].properties.levels=null,s=>s.features[0].properties.height=999,s=>s.features[0].geometry.coordinates[0][0][0][0]=0,s=>s.features.push(s.features[0])]){
    const s=snapshot();change(s);assert.throws(()=>validateNsdiSnapshot(s));
  }
});
test('raw null heights remain null; source IDs are not presented as OSM identities',()=>{
  const s=snapshot(),p=createNsdiReplacement(s);
  assert.equal(s.features[0].properties.height,null);assert.equal(p.buildings[0].height,39.599999999999994);
  assert.equal(p.buildings[0].heightBasis,'estimated-from-levels');assert.match(p.buildings[0].sourceRef,/^nsdi:1316\//);
  assert.equal(p.stats.estimated,1);s.features[0].properties.height=47;assert.equal(createNsdiReplacement(s).buildings[0].height,47);
});
test('local replacement preserves exterior city and rejected-source areas',()=>{
  const s=snapshot(),p=createNsdiReplacement(s);
  assert.equal(p.ownsGeo([ring()]),true);assert.equal(p.ownsGeo([ring(71.5,51.2)]),false);
  s.rejected=[{id:'buildings_a.2',bounds:[71.431,51.128,71.432,51.129]}];s.totalFeatures=2;
  assert.equal(createNsdiReplacement(s).ownsGeo([ring()]),false);
});
test('circular outlines simplify without turning into rectangular substitutes',()=>{
  const circle=Array.from({length:361},(_,i)=>[71.43+Math.cos(i*Math.PI/180)*.0001,51.128+Math.sin(i*Math.PI/180)*.0001]);circle[360]=[...circle[0]];
  const simple=simplifyNsdiRing(circle);assert.ok(simple.length>12&&simple.length<100);assert.deepEqual(simple[0],simple.at(-1));
});
test('same height and courtyard survive near/medium/whole-city LOD and day/night',async()=>{
  const p=createNsdiReplacement(snapshot()),far=await createCityBackdrop(p.overview),near=createBaiterekQuarter({entries:[],replacement:p});
  try{
    const edge=world([71.43104,51.12804]),hole=world([71.43115,51.12815]);
    assert.equal(far.placeAt(...hole),null);
    const close=near.placeAt(...edge);assert.equal(close.sourceRef,'nsdi:1316/buildings_a.1');
    assert.ok(Math.abs(close.top-far.placeAt(...edge).top)<.06);
    const camera=new THREE.PerspectiveCamera(),geometry=far.group.children.map(m=>m.geometry);
    for(const distance of [350,1500,6200,18000,38000,6200,1500,350]){
      camera.position.set(0,distance*.65,distance*.75);far.update(camera);near.update(distance,0,false,'balanced');
      assert.ok(far.group.children.some(m=>m.visible&&m.geometry.attributes.nightLighting.array.includes(1)));
      for(const night of [true,false]){far.setAppearance(night,'default');near.setAppearance(night,'default');assert.deepEqual(far.group.children.map(m=>m.geometry),geometry);}
    }
  }finally{far.destroy();near.destroy();}
});
test('baseline backdrop only excludes geometry with explicit test replacement',async()=>{
  const p=createNsdiReplacement(snapshot()),normal=await createCityBackdrop(p.overview),excluded=await createCityBackdrop(p.overview,{excludeBuilding:p.ownsGeo});
  try{assert.equal(normal.stats().backdropBuildings,1);assert.equal(excluded.stats().backdropBuildings,0);}finally{normal.destroy();excluded.destroy();}
});
test('missing local data returns actual HTTP failure rather than silently substituting fixtures',async()=>{
  const empty=await mkdtemp(path.join(tmpdir(),'eventmap-nsdi-http-')),server=createPreviewServer({root:empty});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  try{await assert.rejects(readBundledJSON(`http://127.0.0.1:${server.address().port}/test/nsdi-quarter.json`,2000000));}
  finally{server.closeAllConnections();await new Promise(r=>server.close(r));}
});
test('snapshot is never an executable import or a release/backend content asset',async()=>{
  const main=await readFile(root+'runtime/main.js','utf8'),stage=await readFile(root+'stage-nsdi.mjs','utf8'),build=await readFile(root+'build.mjs','utf8');
  assert.match(main,/debugSceneAllowed\(m,debugContext\(\)\)/);assert.ok(!main.includes("from '../test/nsdi-local"));
  assert.ok(stage.includes('../androidApp/src/debug/assets/eventmap-nsdi'));assert.ok(!stage.includes('resources/world'));
  assert.ok(build.includes('--native-only'));assert.ok(!build.includes('nsdi-local/quarter.json'));
});
