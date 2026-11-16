import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {ARCHIVE_SHA256,SELECTION,sha256,convertGltf,readAccessor} from '../import-quaternius.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const source=await readFile(root+'source/quaternius-props.json');
const data=JSON.parse(source);
const result=await build({stdin:{contents:"export * from './runtime/quaternius-props.js';export * as THREE from 'three';",resolveDir:root},
  bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createQuaterniusProps,QUATERNIUS_KINDS,QUATERNIUS_LIMITS,THREE}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].text).toString('base64'));
const samples=QUATERNIUS_KINDS.flatMap(kind=>Array.from({length:12},(_,i)=>({kind,x:i*5,z:10,y:.36,yaw:i*.1,scale:1})));

test('provenance includes actual free archive, CC0 license, and original asset hashes',async()=>{
  assert.equal(data.schema,'eventmap-quaternius-props-v1');
  assert.equal(data.provenance.archiveSha256,ARCHIVE_SHA256);
  assert.ok(data.provenance.archiveBytes>200000000);
  assert.equal(data.provenance.edition,'Standard FREE');
  assert.equal(data.provenance.license,'CC0-1.0');
  assert.match(data.provenance.packUrl,/quaternius\.com\/packs\/downtowncitymegakit\.html$/);
  const license=await readFile(root+data.provenance.licenseFile);
  assert.equal(sha256(license),data.provenance.licenseSha256);
  assert.match(license.toString(),/standard FREE version/);
  assert.match(license.toString(),/CC0 1\.0 Universal/);
  assert.equal(data.provenance.sourceFiles.length,6);
  for(const file of data.provenance.sourceFiles){
    assert.match(file.archivePath,/^Exports\/glTF \(Godot\)\/(Prop_Bollard|Prop_Planter_Single|Sidewalk_Planter)\.(gltf|bin)$/);
    const bytes=await readFile(root+file.localPath);
    assert.equal(bytes.length,file.bytes);assert.equal(sha256(bytes),file.sha256);
  }
});

test('every runtime mesh is reproduced exactly from retained original vendor glTF/bin',async()=>{
  for(const spec of SELECTION){
    const gltf=JSON.parse(await readFile(root+`art/quaternius/originals/${spec.file}.gltf`));
    const bin=await readFile(root+`art/quaternius/originals/${spec.file}.bin`);
    const converted=convertGltf(gltf,[bin],spec);
    assert.deepEqual(converted,data.models.find(m=>m.kind===spec.kind));
    assert.equal(converted.bounds.min[1],0);
    for(const part of converted.parts){
      assert.equal(part.positions.length,part.normals.length);
      assert.ok(part.positions.every(Number.isFinite));
      assert.ok(part.indices.every(i=>i>=0&&i<part.positions.length/3));
      assert.equal(part.indices.length%3,0);
      for(let i=0;i<part.normals.length;i+=3)assert.ok(Math.abs(Math.hypot(...part.normals.slice(i,i+3))-1)<.000002);
    }
  }
  assert.deepEqual(data.budgets,{models:3,batches:5,triangles:906,vertices:1621});
  assert.ok(source.length<200000);
  assert.match(data.adaptation.materials,/textures, UVs and wear channels intentionally omitted/);
  assert.deepEqual(QUATERNIUS_KINDS,['bollard','planter','planter-low']);
});

test('offline reader rejects malformed ranges, unsupported geometry and nonfinite values',async()=>{
  const spec=SELECTION[0],gltf=JSON.parse(await readFile(root+`art/quaternius/originals/${spec.file}.gltf`));
  const bin=await readFile(root+`art/quaternius/originals/${spec.file}.bin`);
  const modified=()=>structuredClone(gltf);
  let bad=modified();bad.bufferViews[0].byteLength=4;assert.throws(()=>convertGltf(bad,[bin],spec),/outside buffer/);
  bad=modified();bad.accessors[0].count=1000000;assert.throws(()=>convertGltf(bad,[bin],spec),/budget/);
  bad=modified();bad.accessors[0].sparse={};assert.throws(()=>convertGltf(bad,[bin],spec),/accessor/);
  bad=modified();bad.extensionsRequired=['KHR_draco_mesh_compression'];assert.throws(()=>convertGltf(bad,[bin],spec),/static uncompressed/);
  bad=modified();bad.nodes[0].children=[0];assert.throws(()=>convertGltf(bad,[bin],spec),/Cyclic/);
  bad=modified();bad.meshes[0].primitives[0].mode=1;assert.throws(()=>convertGltf(bad,[bin],spec),/triangles/);
  const nanBuffer=Buffer.from(bin);nanBuffer.writeFloatLE(NaN,0);assert.throws(()=>readAccessor(gltf,[nanBuffer],0,'VEC3'),/Non-finite/);
});

test('runtime uses five InstancedMesh batches, shares material roles, has no texture/light',()=>{
  const props=createQuaterniusProps({sites:samples});
  assert.equal(props.stats().instances,36);assert.equal(props.stats().drawCalls,5);
  assert.equal(props.stats().materials,3);assert.equal(props.stats().triangles,906*12);
  let meshes=0,lights=0;const materials=new Set();
  props.group.traverse(o=>{
    if(o.isLight)lights++;
    if(!o.isMesh)return;meshes++;
    assert.equal(o.isInstancedMesh,true);assert.equal(o.count,12);
    assert.ok(o.geometry.index);assert.deepEqual(Object.keys(o.geometry.attributes).sort(),['normal','position']);
    assert.equal(o.material.map,null);assert.equal(o.material.normalMap,null);
    assert.equal(o.frustumCulled,true);assert.equal(o.castShadow,false);
    assert.ok(Number.isFinite(o.boundingSphere.radius));materials.add(o.material);
  });
  assert.equal(meshes,5);assert.equal(lights,0);assert.equal(materials.size,3);
  const stone=props.group.children.filter(o=>o.userData.sourceMaterial==='MI_Trim_MetalConcrete'&&o.userData.quaterniusKind!=='bollard');
  assert.equal(stone[0].material,stone[1].material);
  assert.notEqual(stone[0].geometry,stone[1].geometry);
  props.destroy();
});

test('instance transforms use metre scale, ground Y and yaw without mutating input',()=>{
  const sites=[{kind:'planter',x:11,z:-4,y:.6,yaw:Math.PI/2,scale:2}],original=structuredClone(sites);
  const props=createQuaterniusProps({sites});
  const expected=new THREE.Matrix4().compose(new THREE.Vector3(11,.6,-4),new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0,1,0),Math.PI/2),new THREE.Vector3(2,2,2));
  const actual=new THREE.Matrix4();
  for(const mesh of props.group.children){
    mesh.getMatrixAt(0,actual);
    actual.elements.forEach((v,i)=>assert.ok(Math.abs(v-expected.elements[i])<1e-6));
    assert.ok(mesh.boundingBox.min.y>=.59999);assert.ok(mesh.boundingBox.max.y<=1.80001);
  }
  assert.deepEqual(sites,original);props.destroy();
});

test('invalid, unknown and excessive sites stay within bounded geometry and triangle budgets',()=>{
  const invalid=[null,{}, {kind:'bench',x:0,z:0},{kind:'bollard',x:NaN,z:0},{kind:'bollard',x:0,z:0,y:Infinity},
    {kind:'bollard',x:0,z:0,scale:-1},{kind:'planter',x:0,z:0,scale:0},{kind:'planter',x:0,z:0,scale:11},{kind:'bollard',x:1e9,z:0}];
  const sites=invalid.concat(Array.from({length:5000},(_,i)=>({kind:'planter-low',x:i,z:0})));
  const props=createQuaterniusProps({sites});let stats=props.stats();
  assert.equal(stats.sites,QUATERNIUS_LIMITS.sites);assert.equal(stats.skipped,invalid.length);
  assert.equal(stats.sites+stats.skipped+stats.truncated,sites.length);
  assert.ok(stats.triangles<=60000);assert.equal(stats.batches,2);
  props.update(0,'low');stats=props.stats();assert.ok(stats.instances<=128);assert.ok(stats.triangles<=25000);
  assert.throws(()=>createQuaterniusProps({sites:{}}),TypeError);
  props.destroy();
});

test('day/night/winter appearance is reversible without material, geometry or texture allocation',()=>{
  const props=createQuaterniusProps({sites:samples});
  const meshes=[...props.group.children],materials=meshes.map(m=>m.material),geometries=meshes.map(m=>m.geometry);
  const day=materials.map(m=>m.color.getHex());
  props.setAppearance(true,'default');assert.notDeepEqual(materials.map(m=>m.color.getHex()),day);
  const night=materials.map(m=>m.color.getHex());
  props.setAppearance(true,'newyear');assert.notDeepEqual(materials.map(m=>m.color.getHex()),night);
  props.setAppearance(false,'default');assert.deepEqual(materials.map(m=>m.color.getHex()),day);
  props.setAppearance(false);assert.deepEqual(materials.map(m=>m.color.getHex()),day);
  meshes.forEach((m,i)=>{assert.equal(m.material,materials[i]);assert.equal(m.geometry,geometries[i]);});
  props.destroy();
});

test('distance culling has hysteresis, handles invalid input and restores counts and full bounds',()=>{
  const props=createQuaterniusProps({sites:samples});
  const bounds=props.group.children.map(m=>m.boundingSphere.clone());
  props.update(700);assert.equal(props.stats().byKind.bollard,12);
  props.update(721);assert.equal(props.stats().byKind.bollard,0);assert.equal(props.stats().byKind.planter,12);
  props.update(650);assert.equal(props.stats().byKind.bollard,0);
  props.update(599);assert.equal(props.stats().byKind.bollard,12);
  props.update(1001);assert.equal(props.group.visible,false);assert.equal(props.stats().drawCalls,0);
  props.update(900);assert.equal(props.group.visible,false);
  props.update(849);assert.equal(props.stats().byKind.planter,12);
  for(const distance of [NaN,Infinity,-1]){props.update(distance);assert.equal(props.stats().instances,0);}
  props.update(0,'low');assert.ok(props.stats().instances>0);
  props.update(0,'balanced');assert.equal(props.stats().instances,36);
  props.group.children.forEach((m,i)=>{assert.ok(m.boundingSphere.equals(bounds[i]));});
  props.destroy();
});

test('destroy releases every shared resource once and does not affect another controller',()=>{
  const props=createQuaterniusProps({sites:samples}),other=createQuaterniusProps({sites:samples});
  const resources=new Set();for(const mesh of props.group.children){resources.add(mesh);resources.add(mesh.geometry);resources.add(mesh.material);}
  const disposed=new Map();for(const item of resources)item.addEventListener('dispose',()=>disposed.set(item,(disposed.get(item)??0)+1));
  const parent=new THREE.Group();parent.add(props.group);
  props.destroy();props.destroy();props.update(0);props.setAppearance(true,'winter');
  assert.equal(props.group.parent,null);assert.equal(props.group.children.length,0);
  assert.equal(disposed.size,resources.size);for(const count of disposed.values())assert.equal(count,1);
  assert.equal(props.stats().destroyed,true);assert.equal(props.stats().drawCalls,0);assert.equal(props.stats().materials,0);
  assert.equal(other.stats().instances,36);other.destroy();
  const empty=createQuaterniusProps();assert.equal(empty.stats().batches,0);assert.equal(empty.group.visible,false);empty.destroy();
});

test('runtime bundles with existing engine alias for mobile targets and has no loader/network dependency',async()=>{
  const browser=await build({entryPoints:[root+'runtime/quaternius-props.js'],bundle:true,format:'iife',target:['safari16','chrome100'],
    minify:true,write:false,metafile:true,alias:{three:root+'source/vendor/three.module.js'},legalComments:'eof'});
  const inputs=Object.keys(browser.metafile.inputs);
  assert.ok(inputs.every(p=>!/(GLTFLoader|node_modules\/three|originals|\.png$)/.test(p)));
  const code=await readFile(root+'runtime/quaternius-props.js','utf8');
  assert.doesNotMatch(code,/\b(fetch|XMLHttpRequest|TextureLoader|PointLight|eval)\b/);
  assert.match(browser.outputFiles[0].text,/CC0-1\.0/);
});
