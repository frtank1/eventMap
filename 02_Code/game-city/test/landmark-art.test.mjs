import test, {after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import * as vm from 'node:vm';
import {exportStaticModel, validateModel, FACADE_COLOR, LIMITS} from '../export-models.mjs';

// Source-only tests: no renderer, bundler, export command, generated files or
// network. The VM also exercises the real Arena simulations, not export stubs.
const source=fileURLToPath(new URL('../source/',import.meta.url));
async function loadBuilders(seed=1){
  const math=Object.create(Math);let state=seed>>>0;
  math.random=()=>{state=(Math.imul(state,1664525)+1013904223)>>>0;return state/0x100000000;};
  const context=vm.createContext({console,Math:math}),modules=new Map();
  const allowed=new Set(['landmarks.js','civic-landmarks.js','civic-extras.js','baiterek.json','city-landmarks-osm.json',
    'stadium-match.js','stadium-crowd.js','vendor/three.module.js','vendor/BufferGeometryUtils.js',
    '../runtime/city-landmarks.js','../runtime/quarter-materials.js','../runtime/world-state.js','../runtime/art-direction.js']
    .map(file=>path.join(source,file)));
  async function get(filename){
    assert.ok(allowed.has(filename),`Unexpected source dependency: ${filename}`);
    if(!modules.has(filename))modules.set(filename,(async()=>{
      const contents=await readFile(filename,'utf8');
      return filename.endsWith('.json')
        ?new vm.SyntheticModule(['default'],function(){this.setExport('default',JSON.parse(contents));},{context,identifier:filename})
        :new vm.SourceTextModule(contents,{context,identifier:filename});
    })());
    return modules.get(filename);
  }
  async function evaluate(file){
    const module=await get(path.join(source,file));
    if(module.status==='unlinked')await module.link((specifier,parent)=>get(specifier==='three'
      ?path.join(source,'vendor/three.module.js'):path.resolve(path.dirname(parent.identifier),specifier)));
    if(module.status==='linked')await module.evaluate({timeout:30000});
    return module.namespace;
  }
  const landmarks=await evaluate('landmarks.js'),civic=await evaluate('civic-landmarks.js'),extras=await evaluate('civic-extras.js');
  return {THREE:await evaluate('vendor/three.module.js'),landmarks,civic,extras,
    authored:await evaluate('../runtime/city-landmarks.js'),art:await evaluate('../runtime/art-direction.js')};
}

// npm test need not change its process flags. Only this isolated child needs
// SourceTextModule; the normal explicit command below runs these tests directly.
if(typeof vm.SourceTextModule!=='function'){
  test('source landmark art and static-data compatibility',t=>{
    const env={...process.env};delete env.NODE_TEST_CONTEXT;
    const result=spawnSync(process.execPath,['--experimental-vm-modules','--test','--test-reporter=tap',fileURLToPath(import.meta.url)],
      {encoding:'utf8',env,timeout:60000,maxBuffer:2_000_000});
    assert.equal(result.status,0,result.error?.message||`${result.stdout}\n${result.stderr}`);
    assert.match(result.stdout,/# tests 9\b/,'the isolated child must actually execute all nine tests');
    t.diagnostic('All 9 isolated landmark tests passed; no generated files written.');
  });
}else{
  const {THREE,landmarks,civic,extras,authored,art}=await loadBuilders();
  const center=[71.43056,51.12822],city=civic.createCivicLandmarks(center),other=extras.createCivicExtras(center);
  const layer=authored.createCityLandmarks(),primaryIds=new Set(authored.CITY_PLACES.map(p=>p.id));
  const heroes={baiterek:landmarks.createBaiterek(),arena:landmarks.createArena()};
  const models=new Map([...Object.entries(heroes),...[...city.pois,...other.pois].map(p=>[p.id,p]),
    ...layer.entries.filter(p=>primaryIds.has(p.entry.id)).map(p=>[p.entry.id,p])]);
  const serialize=(id,model)=>{
    try{return exportStaticModel(THREE,id,model,{removeRootTranslation:true});}
    catch(error){
      const invalid=[];model.group.traverse(node=>{const n=node.geometry?.attributes.normal,p=node.geometry?.attributes.position;if(n)for(let i=0;i<n.count;i++)if(Math.hypot(n.getX(i),n.getY(i),n.getZ(i))<.5&&invalid.length<3)invalid.push(`${node.name}@${i}(${p.getX(i)},${p.getY(i)},${p.getZ(i)})`);});
      throw new Error(`${id}: ${error.message}; ${invalid.join('; ')}`,{cause:error});
    }
  };
  const data=new Map([...models].map(([id,model])=>[id,serialize(id,model)]));
  const manifest=JSON.parse(await readFile(new URL('../content/manifest.json',import.meta.url),'utf8'));
  const geometryOnly=model=>model.meshes.map(({positions,normals,indices,detail})=>({positions,normals,indices,detail}));
  const budgets={...Object.fromEntries([...primaryIds].map(id=>[id,40000])),baiterek:20000,arena:35000,akorda:25000,hazret:60000,grandmosque:110000,palace:8000,kazakheli:50000,independence:10000};

  function disposeRoot(group){
    const geometries=new Set(),materials=new Set();
    group.traverse(o=>{if(o.geometry)geometries.add(o.geometry);for(const m of [].concat(o.material||[]))materials.add(m);});
    geometries.forEach(g=>g.dispose());materials.forEach(m=>m.dispose());group.clear();
  }
  after(()=>{city.dispose();other.dispose();layer.destroy();Object.values(heroes).forEach(model=>disposeRoot(model.group));});

  test('all 31 landmarks fit finite engine-v2 static-data envelopes and mobile budgets',t=>{
    assert.equal(manifest.engineVersion,2);
    assert.equal(data.size,31);
    for(const [id,model]of data){
      const summary=validateModel(model),spec=manifest.models.find(p=>p.id===id);
      assert.ok(spec,`${id}: missing content metadata`);
      assert.ok(summary.min[1]>=-1,`${id}: underground geometry`);
      assert.ok(summary.max[1]<=spec.height+1,`${id}: height envelope`);
      assert.ok(summary.radialExtent<=spec.radius+.01,`${id}: radius envelope`);
      assert.ok(summary.vertices<=budgets[id],`${id}: ${summary.vertices} vertices`);
      assert.ok(summary.meshes<=24,`${id}: ${summary.meshes} merged draw calls`);
      assert.ok(summary.bytes<=LIMITS.bytes);
      if(!primaryIds.has(id)||['khan','expo','mega'].includes(id))assert.ok(model.meshes.some(m=>m.detail),`${id}: missing optional detail`);
      assert.ok(model.meshes.some(m=>!m.detail),`${id}: missing main architecture`);
      t.diagnostic(`${id}: ${summary.vertices} vertices / ${summary.meshes} meshes / ${summary.bytes} bytes; radius ${summary.radialExtent.toFixed(2)}m`);
    }
    assert.equal(data.get('akorda').materials.filter(m=>m.color===FACADE_COLOR).length,1,
      'pilot-2 must still find exactly one original ivory facade');
  });

  test('hero architecture and entrances remain with facade details disabled',()=>{
    for(const [id,names]of [['baiterek',['baiterek-crown-ribbons','baiterek-golden-sphere','baiterek-plinth-approaches']],
      ['arena',['arena-folded-roof','arena-structural-frames','arena-entry-portals','arena-entry-steps']]]){
      const model=heroes[id];
      for(const name of names)assert.equal(model.group.getObjectByName(name)?.parent,model.group,`${name}: must be main architecture`);
      model.detail.visible=false;
      const low=serialize(id,model);
      assert.ok(low.meshes.every(m=>!m.detail));
      assert.ok(validateModel(low).vertices<validateModel(data.get(id)).vertices);
      assert.ok(validateModel(low).max[1]>=validateModel(data.get(id)).max[1]-.1);
      model.detail.visible=true;
    }
    for(const model of [...city.pois,...other.pois]){
      const detail=model.group.getObjectByName('facade-details');assert.ok(detail);
      detail.visible=false;
      assert.deepEqual(geometryOnly(serialize(model.id,model)),geometryOnly({...data.get(model.id),meshes:data.get(model.id).meshes.filter(m=>!m.detail)}));
      detail.visible=true;
    }
  });

  test('folded Arena roof has upward faces and an unobstructed pitch aperture',()=>{
    const roof=heroes.arena.group.getObjectByName('arena-folded-roof');roof.updateMatrixWorld(true);
    const ray=new THREE.Raycaster(new THREE.Vector3(100,80,0),new THREE.Vector3(0,-1,0));
    const hits=ray.intersectObject(roof);assert.ok(hits.length>0,'roof top winding');
    assert.ok(hits[0].point.y>38&&hits[0].face.normal.y>0);
    for(const [x,z]of [[0,0],[52,34],[-52,-34],[130,0]]){
      ray.set(new THREE.Vector3(x,80,z),new THREE.Vector3(0,-1,0));
      assert.equal(ray.intersectObject(roof).length,0,`roof occludes pitch or exceeds footprint at ${x},${z}`);
    }
  });

  test('export excludes live activity even when visible, and never imports simulation geometry',()=>{
    for(const [id,model]of Object.entries(heroes)){
      model.activity.visible=true;
      assert.deepEqual(serialize(id,model),data.get(id));
      model.activity.visible=false;
    }
    assert.equal(heroes.arena.crowd.supporterCount,5120);
    assert.ok(heroes.arena.crowd.group.children.some(child=>child.isInstancedMesh));
  });

  test('civic anchors, yaw, footprint exclusion and local export remain independent of map origin',()=>{
    const shifted=[70,50],shiftedCity=civic.createCivicLandmarks(shifted),shiftedExtras=extras.createCivicExtras(shifted);
    try{
      for(const collection of [shiftedCity,shiftedExtras])for(const model of collection.pois){
        assert.deepEqual(serialize(model.id,model),data.get(model.id));
        assert.equal(collection.excludeBuilding(...model.coord),true,model.id);
      }
      assert.equal(shiftedCity.pois.find(p=>p.id==='akorda').group.rotation.y,-1.823);
      assert.equal(shiftedExtras.pois.find(p=>p.id==='independence').group.rotation.y,-.247);
      for(const collection of [shiftedCity,shiftedExtras]){
        collection.updateDetail(new THREE.Vector3(1e6,1e6,1e6));
        for(const p of collection.pois){assert.equal(p.group.visible,true);assert.equal(p.group.getObjectByName('facade-details').visible,false);}
      }
    }finally{shiftedCity.dispose();shiftedExtras.dispose();}
  });

  test('calm night accents do not add architecture or leak optional glow into static export',()=>{
    for(const collection of [city,other]){
      collection.setNight(true);
      for(const model of collection.pois)assert.deepEqual(geometryOnly(serialize(model.id,model)),geometryOnly(data.get(model.id)));
      collection.setNight(false);
    }
    for(const model of Object.values(heroes)){
      model.setNight(true);assert.equal(model.activity.visible,false);
      model.setNight(false);
    }
  });

  test('Arena calm, match and concert API retains real crowd, searchlights and animation semantics',()=>{
    const model=heroes.arena;
    model.setEvent('match');assert.equal(model.group.userData.event,'match');assert.equal(model.match.group.visible,true);
    assert.equal(model.crowd.group.visible,true);assert.equal(model.group.getObjectByName('arena-roof-searchlights').visible,true);
    for(const t of [0,1,1,Infinity,NaN,2])model.animate(t);
    model.setNight(true);model.setEvent('concert');
    assert.equal(model.group.userData.event,'concert');assert.equal(model.match.group.visible,false);
    assert.equal(model.group.getObjectByName('arena-roof-searchlights').visible,false);model.animate(3);
    model.setEvent('unknown');assert.equal(model.group.userData.event,'calm');assert.equal(model.activity.visible,false);
    model.setNight(false);
    validateModel(serialize('arena',model));
  });

  test('all-city builders share day/night palette and keep complete silhouettes at overview LOD',()=>{
    const paletteKey={ivory:'ivory',glass:'glass',roof:'roof',gold:'gold',green:'leaf',dark:'dark',emerald:'emerald'};
    for(const night of [false,true]){
      layer.setAppearance(night,'default');
      for(const {entry,group}of layer.entries.filter(p=>primaryIds.has(p.entry.id)))group.traverse(node=>{
        if(!node.isMesh)return;
        const key=node.material.name.slice(5);
        assert.equal(node.material.color.getHex(),art.CITY_PALETTE[paletteKey[key]][night?1:0],`${entry.id}/${key}`);
      });
    }
    layer.setAppearance(false,'default');layer.update(new THREE.Vector3(0,8000,0),20000);
    for(const record of layer.entries)assert.equal(record.group.visible,true,`${record.entry.id}: overview must retain the place`);
    for(const id of ['khan','expo','mega']){
      const record=layer.entries.find(p=>p.entry.id===id);assert.equal(record.detail.visible,false);
      const low=serialize(id,record);assert.ok(low.meshes.every(m=>!m.detail));
      assert.ok(validateModel(low).vertices<validateModel(data.get(id)).vertices);
      assert.ok(validateModel(low).max[1]>=validateModel(data.get(id)).max[1]-.7);
    }
  });

  test('static architecture is deterministic across independent source VMs',async()=>{
    const second=await loadBuilders(0xfeed1234),c=second.civic.createCivicLandmarks(center),e=second.extras.createCivicExtras(center),l=second.authored.createCityLandmarks();
    const h={baiterek:second.landmarks.createBaiterek(),arena:second.landmarks.createArena()};
    try{
      for(const [id,model]of [...Object.entries(h),...[...c.pois,...e.pois].map(p=>[p.id,p]),
        ...l.entries.filter(p=>primaryIds.has(p.entry.id)).map(p=>[p.entry.id,p])]){
        assert.deepEqual(exportStaticModel(second.THREE,id,model,{removeRootTranslation:true}),data.get(id));
      }
    }finally{c.dispose();e.dispose();l.destroy();Object.values(h).forEach(model=>disposeRoot(model.group));}
  });
}
