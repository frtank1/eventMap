import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';

const root=fileURLToPath(new URL('../',import.meta.url));
async function load(contents){
  const b=await build({stdin:{contents,resolveDir:root},bundle:true,platform:'node',format:'esm',write:false,alias:{three:root+'source/vendor/three.module.js'}});
  return import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].text).toString('base64'));
}
const routes=[
  {points:[[-300,100],[0,100],[300,100]],width:14},
  {points:[[200,-250],[200,250]],width:10},
  {points:[[0,0],[40,0]],width:10},               // too short
  {points:[[900,0],[1200,0]],width:10},           // outside the quarter
];
test('decorative traffic is bounded, deterministic and stays on its own route',async()=>{
  const {planTraffic,carPose,TRAFFIC_LIMITS}=await load("export * from './runtime/traffic.js';");
  const cars=planTraffic(routes,{count:500,seed:7});
  assert.equal(cars.length,TRAFFIC_LIMITS.balanced);
  assert.deepEqual(planTraffic(routes,{count:500,seed:7}).map(c=>[c.offset,c.speed,c.paint]),cars.map(c=>[c.offset,c.speed,c.paint]));
  assert.equal(new Set(cars.map(c=>c.route)).size,2,'short and out-of-area routes are never used');
  for(const car of cars)for(const t of [0,3.5,60,3600,-12]){
    const p=carPose(car,t);
    assert.ok([p.x,p.z,p.yaw].every(Number.isFinite));
    const horizontal=car.route.points[0][1]===100;
    // Lane offset only: a car never leaves the carriageway of its route.
    if(horizontal){assert.ok(Math.abs(p.z-100)<=car.route.width/2);assert.ok(p.x>=-300-1e-6&&p.x<=300+1e-6);}
    else{assert.ok(Math.abs(p.x-200)<=car.route.width/2);assert.ok(p.z>=-250-1e-6&&p.z<=250+1e-6);}
  }
  assert.equal(planTraffic([],{count:10}).length,0);
});
test('traffic and sky each add a fixed number of draws and dispose cleanly',async()=>{
  const {createTraffic,createSky,THREE}=await load("export {createTraffic} from './runtime/traffic.js';export {createSky} from './runtime/sky.js';export * as THREE from 'three';");
  const traffic=createTraffic(routes,{seed:7});let meshes=0,lights=0;
  traffic.group.traverse(o=>{if(o.isMesh)meshes++;if(o.isLight)lights++;});
  assert.equal(meshes,1);assert.equal(lights,0);assert.equal(traffic.stats().trafficDraws,1);
  traffic.update(10,false,'low');assert.equal(traffic.stats().trafficCars,12);
  traffic.update(10,false,'balanced');assert.equal(traffic.stats().trafficCars,28);
  let disposed=0;traffic.group.children[0].geometry.addEventListener('dispose',()=>disposed++);traffic.destroy();assert.equal(disposed,1);
  const empty=createTraffic([]);empty.update(1);assert.equal(empty.stats().trafficCars,0);empty.destroy();
  const sky=createSky();meshes=0;sky.group.traverse(o=>{if(o.isMesh)meshes++;if(o.isLight)lights++;});
  assert.equal(meshes,2);assert.equal(lights,0);
  const camera=new THREE.PerspectiveCamera(42,1,.15,6500);camera.position.set(100,50,-20);
  sky.setMood({top:0x4c9ee0,horizon:0xcfe5f1,cloud:0xffffff,glow:0xfff0c8,night:false,sun:new THREE.Vector3(-1,1,1)});sky.update(camera,3);
  assert.deepEqual(sky.group.position.toArray(),[100,50,-20]);
  assert.ok(sky.group.children[0].scale.x<camera.far,'dome stays inside the far plane');
  sky.destroy();
});
