import test, {after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import * as vm from 'node:vm';
import {exportStaticModel, validateModel} from '../export-models.mjs';
import {LON_SCALE, METERS} from '../runtime/world-state.js';

// Source-only: real animation modules, no renderer/network or pack generation.
async function loadArena() {
  const source=fileURLToPath(new URL('../source/',import.meta.url));
  const context=vm.createContext({console,Math}),modules=new Map();
  const allowed=new Set(['landmarks.js','stadium-match.js','stadium-crowd.js',
    'vendor/three.module.js','vendor/BufferGeometryUtils.js'].map(f=>path.join(source,f)));
  async function get(filename) {
    assert.ok(allowed.has(filename),`Unexpected dependency: ${filename}`);
    if(!modules.has(filename))modules.set(filename,(async()=>new vm.SourceTextModule(
      await readFile(filename,'utf8'),{context,identifier:filename}))());
    return modules.get(filename);
  }
  const module=await get(path.join(source,'landmarks.js'));
  await module.link((specifier,parent)=>get(specifier==='three'
    ?path.join(source,'vendor/three.module.js'):path.resolve(path.dirname(parent.identifier),specifier)));
  await module.evaluate({timeout:30000});
  return {createArena:module.namespace.createArena,
    THREE:(await get(path.join(source,'vendor/three.module.js'))).namespace};
}

// Principal axis from polygon area moments, not vertex sampling (OSM nodes
// are unevenly spaced). Work near the origin to avoid cancellation in lon/lat.
function footprintYaw(points) {
  const [lon,lat]=points[0];
  const local=points.map(p=>[(p[0]-lon)*LON_SCALE,(p[1]-lat)*METERS]);
  let twiceArea=0,cx=0,cy=0,xx=0,yy=0,xy=0;
  for(let i=0;i<local.length;i++) {
    const [x,y]=local[i],[u,v]=local[(i+1)%local.length],cross=x*v-u*y;
    twiceArea+=cross;cx+=(x+u)*cross;cy+=(y+v)*cross;
    xx+=(x*x+x*u+u*u)*cross;yy+=(y*y+y*v+v*v)*cross;
    xy+=(2*x*y+x*v+u*y+2*u*v)*cross;
  }
  const area=twiceArea/2;
  assert.ok(Math.abs(area)>30000,'expected the full stadium footprint');
  cx/=6*area;cy/=6*area;
  xx=xx/(12*area)-cx*cx;yy=yy/(12*area)-cy*cy;xy=xy/(24*area)-cx*cy;
  return .5*Math.atan2(2*xy,xx-yy);
}

function near(actual,expected,tolerance,label) {
  assert.ok(Math.abs(actual-expected)<=tolerance,`${label}: ${actual} vs ${expected}`);
}

// Keep compatible with the existing npm test command, without changing it.
if(typeof vm.SourceTextModule!=='function') {
  test('isolated Arena geography checks',t=>{
    const env={...process.env};delete env.NODE_TEST_CONTEXT;
    const result=spawnSync(process.execPath,['--experimental-vm-modules','--test',
      '--test-reporter=tap',fileURLToPath(import.meta.url)],
    {encoding:'utf8',env,timeout:60000,maxBuffer:2_000_000});
    assert.equal(result.status,0,result.error?.message||`${result.stdout}\n${result.stderr}`);
    assert.match(result.stdout,/# tests 3\b/);
    t.diagnostic('All 3 Arena geography tests passed; no generated files written.');
  });
} else {
  const {THREE,createArena}=await loadArena();
  const snapshot=JSON.parse(await readFile(new URL('../source/arena.json',import.meta.url),'utf8'));
  const footprint=snapshot.buildings.find(b=>b.id==='166223262');
  assert.equal(footprint?.kind,'stadium');
  const yaw=footprintYaw(footprint.points),models=[];
  const make=()=>{const model=createArena();models.push(model);return model;};
  after(()=>{
    for(const {group,match,crowd} of models) {
      match.dispose();crowd.dispose();
      const geometries=new Set(),materials=new Set();
      group.traverse(o=>{
        if(o.geometry)geometries.add(o.geometry);
        for(const m of [].concat(o.material||[]))materials.add(m);
      });
      geometries.forEach(g=>g.dispose());materials.forEach(m=>m.dispose());group.clear();
    }
  });

  test('Arena long axis follows bundled OSM way 166223262 without moving its anchor',async()=>{
    const model=make(),manifest=JSON.parse(await readFile(new URL('../content/manifest.json',import.meta.url),'utf8'));
    const entry=manifest.models.find(m=>m.id==='arena');
    assert.deepEqual(snapshot.center,[71.4025,51.10806]);
    assert.deepEqual([entry.lng,entry.lat],snapshot.center);
    assert.deepEqual([...model.group.position.toArray()],[0,0,0]);
    assert.deepEqual([...model.group.scale.toArray()],[1,1,1]);
    near(yaw*180/Math.PI,76.59927,.001,'snapshot principal axis');
    near(model.group.rotation.y,yaw,1e-6,'root yaw');
    // THREE +Y yaw takes +X east towards -Z north, not +Z south.
    model.group.updateMatrixWorld(true);
    const direction=new THREE.Vector3(1,0,0).transformDirection(model.group.matrixWorld);
    near(Math.atan2(-direction.z,direction.x),yaw,1e-6,'world long axis');
  });

  test('whole Arena and real animations inherit one root yaw in every event mode',()=>{
    const rotated=make(),baseline=make();baseline.group.rotation.y=0;
    const rotation=new THREE.Matrix4().makeRotationY(yaw),expected=new THREE.Matrix4();
    const nodes=model=>{const list=[];model.group.traverse(o=>list.push(o));return list;};
    const actualNodes=nodes(rotated),baseNodes=nodes(baseline);
    assert.equal(actualNodes.length,baseNodes.length);
    assert.equal(rotated.match.group.parent,rotated.activity);
    assert.equal(rotated.crowd.group.parent,rotated.activity);
    assert.equal(rotated.activity.parent,rotated.group);
    assert.ok(actualNodes.some(o=>o.name==='arena-roof-searchlights'));
    assert.ok(actualNodes.some(o=>o.name==='match-football-45cm'));
    let firstBall,lastBall;
    for(const mode of ['calm','match','concert','calm']) {
      for(const model of [rotated,baseline]){model.setEvent(mode);model.setNight(true);}
      for(const time of [0,1,1,4,Infinity,NaN,8]) {
        for(const model of [rotated,baseline]){model.animate(time);model.group.updateMatrixWorld(true);}
        near(rotated.group.rotation.y,yaw,1e-6,`${mode}: persistent yaw`);
        for(let i=0;i<actualNodes.length;i++) {
          const a=actualNodes[i],b=baseNodes[i];
          assert.equal(a.visible,b.visible,`${mode}/${a.name}: visibility`);
          expected.multiplyMatrices(rotation,b.matrixWorld);
          for(let k=0;k<16;k++)near(a.matrixWorld.elements[k],expected.elements[k],2e-6,`${mode}/${a.name}: matrix ${k}`);
          if(a.isInstancedMesh)assert.deepEqual(a.instanceMatrix.array,b.instanceMatrix.array,
            `${mode}/${a.name}: animation stays in stadium-local coordinates`);
        }
        if(mode==='match'&&Number.isFinite(time)) {
          lastBall=rotated.group.getObjectByName('match-football-45cm').position.clone();
          firstBall??=lastBall.clone();
        }
      }
    }
    assert.ok(firstBall.distanceTo(lastBall)>.1,'real match animation must advance');
  });

  test('static export bakes yaw exactly once and retains height/radial envelope',()=>{
    const rotated=make(),baseline=make();baseline.group.rotation.y=0;
    const plain=exportStaticModel(THREE,'arena',baseline);
    const baked=exportStaticModel(THREE,'arena',rotated);
    assert.deepEqual(baked.materials,plain.materials);
    assert.equal(baked.meshes.length,plain.meshes.length);
    const rotation=new THREE.Matrix4().makeRotationY(yaw),point=new THREE.Vector3();
    for(let i=0;i<baked.meshes.length;i++) {
      const a=baked.meshes[i],b=plain.meshes[i];
      assert.equal(a.positions.length,b.positions.length);
      assert.equal(a.detail,b.detail);assert.deepEqual(a.indices,b.indices);
      for(let j=0;j<a.positions.length;j+=3) {
        point.fromArray(b.positions,j).applyMatrix4(rotation);
        for(const [axis,key] of ['x','y','z'].entries())near(a.positions[j+axis],point[key],.0015,'baked position');
        point.fromArray(b.normals,j).transformDirection(rotation);
        for(const [axis,key] of ['x','y','z'].entries())near(a.normals[j+axis],point[key],.0002,'baked normal');
      }
    }
    const before=validateModel(plain),after=validateModel(baked);
    near(after.radialExtent,before.radialExtent,.002,'radial envelope');
    assert.equal(after.max[1],before.max[1]);assert.equal(after.min[1],before.min[1]);
    assert.ok(after.dimensions[2]>after.dimensions[0],'exported long axis must be predominantly north/south');
    // Geographic root translation is stripped, but never its yaw.
    rotated.group.position.set(300,0,-400);
    assert.deepEqual(exportStaticModel(THREE,'arena',rotated,{removeRootTranslation:true}),baked);
  });
}
