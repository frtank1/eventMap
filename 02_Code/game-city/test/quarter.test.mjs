import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
import {detailLevel,effectState,effectBudget,frameSummary,QUALITY,GROUND,cameraDepth} from '../runtime/quarter-state.js';
import {decorateQuarterMaterial} from '../runtime/quarter-materials.js';

test('quarter depth precision separates intersecting path/road layers at every supported zoom',()=>{
  assert.deepEqual(cameraDepth(3),{near:.12,far:6500});
  assert.equal(cameraDepth(500).near,4);assert.equal(cameraDepth(19000,false).near,32);
  for(const distance of [3,30,100,500,1200,2100]){
    const {near,far}=cameraDepth(distance);assert.ok(near>0&&near<far);
    const step=distance*distance/(near*2**24);
    assert.ok(GROUND.road-GROUND.path>step*3);
    assert.ok(GROUND.plaza-GROUND.grass>step*3);
  }
});

test('quarter LOD has hysteresis instead of flickering at a single boundary',()=>{
  assert.equal(detailLevel(650,'near'),'near');assert.equal(detailLevel(700,'near'),'block');
  assert.equal(detailLevel(550,'block'),'block');assert.equal(detailLevel(510,'block'),'near');
  assert.equal(detailLevel(1550,'block'),'block');assert.equal(detailLevel(1650,'block'),'far');
  assert.equal(detailLevel(1450,'far'),'far');assert.equal(detailLevel(1390,'far'),'block');
  assert.equal(detailLevel(NaN),'far');
});
test('effects are opt-in, bounded and paused when unavailable',()=>{
  assert.deepEqual(effectState({snow:'true',fireworks:1}),{light:false,fireworks:false,snow:false});
  for(const flags of [{visible:false},{reduced:true},{blocked:true},{distance:1800},{distance:NaN}]){
    assert.deepEqual(effectBudget(flags),{snow:0,sparks:0,active:false});
  }
  assert.equal(effectBudget({quality:'low'}).snow,QUALITY.low.snow);
  assert.equal(effectBudget({quality:'unknown'}).sparks,300);
});
test('frame timing reports actual p95 and slow samples without mutating input',()=>{
  const values=[33,17,100,NaN,-1,Infinity];const before=[...values];
  assert.deepEqual(frameSummary(values),{p95Ms:100,slowFrames:1,samples:3});assert.deepEqual(values,before);
  assert.deepEqual(frameSummary([]),{p95Ms:0,slowFrames:0,samples:0});
});

const root=fileURLToPath(new URL('../',import.meta.url));
const bundled=await build({stdin:{contents:"export {createBaiterekQuarter} from './runtime/baiterek-quarter.js';export {createQuarterEffects} from './runtime/quarter-effects.js';export * as THREE from 'three';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createBaiterekQuarter,createQuarterEffects,THREE}=await import('data:text/javascript;base64,'+Buffer.from(bundled.outputFiles[0].text).toString('base64'));
test('bundled quarter builds finite geometry with bounded batches and no PointLights',()=>{
  const q=createBaiterekQuarter();let meshes=0,points=0;
  q.group.traverse(o=>{if(o.isMesh){meshes++;for(const n of o.geometry.attributes.position.array)assert.ok(Number.isFinite(n));}if(o.isPointLight)points++;});
  assert.ok(q.stats().quarterBuildings>100);assert.equal(q.stats().styledBuildings.length,7);assert.ok(meshes<48,`mesh batches: ${meshes}`);assert.equal(points,0);
  assert.equal(q.groundHeight(0,0),GROUND.plaza);
  for(let i=0;i<120;i++){const a=i/120*Math.PI*2,h=q.groundHeight(Math.cos(a)*62,Math.sin(a)*62);assert.ok(h>=GROUND.plaza&&h<=GROUND.road);}
  let offsetParts=0;q.group.traverse(o=>{if(o.isMesh&&o.material.polygonOffset){assert.ok(o.material.polygonOffsetFactor<0);offsetParts++;}});assert.ok(offsetParts>0);
  q.setAppearance(true,'newyear');q.update(500,1,false,'low');assert.ok(q.stats().quarterTrees<=QUALITY.low.trees);
  q.update(1700,2);assert.equal(q.stats().lod,'far');assert.equal(q.stats().quarterPedestrians,0);
  q.destroy();
});
test('imported planters leave the avatar editor and walking circuit clear',()=>{
  const q=createBaiterekQuarter(),matrix=new THREE.Matrix4(),position=new THREE.Vector3();
  let checked=0;
  q.group.traverse(mesh=>{
    if(!['planter','planter-low'].includes(mesh.userData.quaterniusKind))return;
    for(let i=0;i<mesh.count;i++){
      mesh.getMatrixAt(i,matrix);position.setFromMatrixPosition(matrix);
      assert.ok(Math.hypot(position.x+24,position.z-57)>=14,'editor clearance');
      assert.ok(Math.abs(Math.hypot(position.x,position.z)-62)>=9,'walk clearance');
      checked++;
    }
  });
  assert.ok(checked>0);q.destroy();
});
test('art materials supply independent day/night and winter uniforms and restore hooks',()=>{
  for(const options of [{},{glass:true},{paving:true},{paving:true,minorPath:true}]){
    const mat=new THREE.MeshStandardMaterial();const previous=mat.onBeforeCompile;
    const controller=decorateQuarterMaterial(mat,options);
    const shader={vertexShader:THREE.ShaderLib.standard.vertexShader,fragmentShader:THREE.ShaderLib.standard.fragmentShader,uniforms:{}};
    mat.onBeforeCompile(shader,{});assert.match(shader.vertexShader,/vQuarterWorld/);assert.match(shader.fragmentShader,/uQuarterNight/);
    controller.setNight(true);controller.setTheme('winter');assert.equal(shader.uniforms.uQuarterNight.value,1);assert.equal(shader.uniforms.uQuarterSnow.value,1);
    controller.setNight(false);controller.setTheme('default');assert.equal(shader.uniforms.uQuarterNight.value,0);assert.equal(shader.uniforms.uQuarterSnow.value,0);
    if(options.minorPath)assert.match(shader.fragmentShader,/diffuseColor.a\*=1.0-smoothstep/);
    controller.dispose();assert.equal(mat.onBeforeCompile,previous);mat.dispose();
  }
});
test('fireworks finish once, freeze while blocked and dispose cleanly',()=>{
  const e=createQuarterEffects(),options={camera:{position:new THREE.Vector3(100,100,100)},target:new THREE.Vector3(),distance:250};
  assert.equal(e.stats().particles,0);e.set('fireworks',true);e.set('snow',true);
  for(let i=0;i<70;i++)e.update(.1,{...options,blocked:true});assert.equal(e.state.fireworks,true);assert.equal(e.stats().particles,0);
  e.update(.1,{...options,quality:'low'});assert.equal(e.stats().particles,480);
  for(let i=0;i<70;i++)e.update(.1,options);assert.equal(e.state.fireworks,false);assert.equal(e.stats().particles,700);
  e.update(.1,{...options,visible:false});assert.equal(e.stats().particles,0);assert.equal(e.group.visible,false);
  e.destroy();
});
