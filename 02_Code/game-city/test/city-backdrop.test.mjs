import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {createFramePacer} from '../runtime/frame-pacer.js';
import {cameraDepth} from '../runtime/quarter-state.js';
import {clipBridgeLine,joinBridgePaths} from '../source/bridge-paths.js';
const root=fileURLToPath(new URL('../',import.meta.url));
const bundled=await build({stdin:{contents:"export * from './runtime/city-backdrop.js';export * from './runtime/bridge-geometry.js';export * as THREE from 'three';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createCityBackdrop,validateOverview,bridgeProfile,bridgeGeometry,BRIDGE_LIMITS,THREE}=await import('data:text/javascript;base64,'+Buffer.from(bundled.outputFiles[0].text).toString('base64'));
const square=(x,z,w)=>[[x,z],[x+w,z],[x+w,z+w],[x,z+w]];
const fixture=()=>({schemaVersion:1,center:[71.43056,51.12822],buildings:[[125,0,'cyan',[square(0,0,50),square(10,10,20).reverse()]],[5,0,'cream',[square(100,100,10)]]],surfaces:[],roads:[],bridges:[]});
test('overview keeps tall silhouettes and courtyards; night/winter reuse geometry',async()=>{
  const backdrop=await createCityBackdrop(fixture());
  try{
    assert.equal(backdrop.placeAt(2,2).top,125);assert.equal(backdrop.placeAt(20,20),null);assert.equal(backdrop.placeAt(102,102).top,5);
    let roofArea=0,maxY=0;
    backdrop.group.traverse(o=>{const g=o.geometry;if(!g)return;const p=g.attributes.position,n=g.attributes.normal,ids=g.index.array;
      for(let i=0;i<p.count;i++)maxY=Math.max(maxY,p.getY(i));
      for(let i=0;i<ids.length;i+=3){const [a,b,c]=Array.from(ids.slice(i,i+3));if(n.getY(a)>.9&&p.getY(a)===125){
        const cross=(p.getZ(b)-p.getZ(a))*(p.getX(c)-p.getX(a))-(p.getX(b)-p.getX(a))*(p.getZ(c)-p.getZ(a));assert.ok(cross>0,'roof faces up');roofArea+=cross/2;
      }}
    });
    assert.equal(maxY,125);assert.equal(roofArea,2500-400);
    const meshes=[...backdrop.group.children],geometries=meshes.map(m=>m.geometry);
    const buildingMesh=meshes.find(m=>m.geometry.attributes.nightLighting.array.includes(1));assert.ok(buildingMesh);
    const shader={uniforms:{},vertexShader:'#include <color_vertex>',fragmentShader:'#include <clipping_planes_fragment>\n#include <emissivemap_fragment>'};
    buildingMesh.material.onBeforeCompile(shader);assert.ok(shader.fragmentShader.includes('fwidth(uv.x)'),'distant windows have a minification filter');
    assert.ok(shader.fragmentShader.includes('totalEmissiveRadiance'),'night lighting uses emissive materials');
    backdrop.setAppearance(true,'newyear');backdrop.setAppearance(false,'default');assert.deepEqual(meshes.map(m=>m.geometry),geometries);
    const mid=new THREE.PerspectiveCamera();mid.position.set(0,3500,3500);backdrop.update(mid,[[0,0,150,150]],null,.4);
    assert.equal(shader.uniforms.coverageWeight.value,.4);
    assert.ok(shader.fragmentShader.includes('cityKeep<coverageWeight'),'coverage is complementary, not transparent overlap');
    assert.ok(shader.fragmentShader.includes('pane.x*pane.y*detail'),'midrange daytime facades are filtered');
    assert.ok(meshes.some(m=>m.geometry.attributes.minorBuilding.array.includes(1)));
    const camera=new THREE.PerspectiveCamera();camera.position.set(0,20000,20000);backdrop.update(camera,[[0,0,150,150]]);
    assert.ok(meshes.some(m=>!m.visible),'small buildings culled far away, big silhouettes stay');
    assert.equal(backdrop.stats().backdropBuildings,2);
  }finally{backdrop.destroy();assert.equal(backdrop.group.children.length,0);}
});
test('snapshot validates geographic coordinates, heights and work limits before rendering',async()=>{
  for(const mutate of [d=>d.center[0]=0,d=>d.buildings[0][0]=Infinity,d=>d.buildings[0][3][0][0][0]=NaN,d=>d.buildings[0][2]='invalid',d=>d.roads=[['invalid',[[0,0],[1,1]]]]]){
    const d=fixture();mutate(d);assert.throws(()=>validateOverview(d));
  }
  const controller=new AbortController();controller.abort();await assert.rejects(()=>createCityBackdrop(fixture(),{signal:controller.signal}),{name:'AbortError'});
});
test('real overview snapshot retains provenance, source heights, all city quadrants and bounded geometry',async()=>{
  const data=validateOverview(JSON.parse(await readFile(new URL('../source/astana-overview.json',import.meta.url),'utf8')));
  assert.equal(data.source.buildingZoom,14);assert.equal(data.source.license,'ODbL-1.0');assert.equal(data.source.missingHeights,0);
  assert.ok(data.buildings.length>70000);assert.ok(data.buildings.some(b=>b[0]>100));
  for(const [sx,sz]of [[-1,-1],[-1,1],[1,-1],[1,1]])assert.ok(data.buildings.some(b=>b[3][0][0][0]*sx>5000&&b[3][0][0][1]*sz>5000));
});
test('30 FPS pacing is refresh-rate independent and never catches up after suspension',()=>{
  for(const hz of [60,75,90,120,144]){
    const pacer=createFramePacer(30);let frames=0;
    for(let i=0;i<hz*10;i++)frames+=Number(pacer.due(i*1000/hz));
    assert.ok(Math.abs(frames-300)<=1,`${hz} Hz: ${frames} frames`);
    assert.ok(pacer.due(60000));assert.equal(pacer.due(60001),false);
  }
});
test('city-wide depth precision grows with distance without clipping the near quarter',()=>{
  assert.equal(cameraDepth(38000,false,true).far,80000);
  assert.ok(cameraDepth(38000,false,true).near>400);
  assert.ok(cameraDepth(300,true,true).near<4);
  assert.equal(cameraDepth(3,true,true).near,.12);
});
test('bridge clipping removes tile overlap and joining does not put ramps at tile seams',()=>{
  const source=[[-10,0],[40,0],[110,0]],a=clipBridgeLine(source,[0,-10,50,10]),b=clipBridgeLine(source,[50,-10,100,10]);
  assert.deepEqual(a,[[[0,0],[40,0],[50,0]]]);assert.deepEqual(b,[[[50,0],[100,0]]]);
  const base={kind:'secondary',width:13,layer:1};
  const joined=joinBridgePaths([...a,...b].map(points=>({...base,points})));
  assert.equal(joined.length,1);assert.equal(joined[0].points.length,4);assert.deepEqual(joined[0].ramps,[true,true]);
  const profile=bridgeProfile(joined[0]);assert.equal(profile.height(0),.6);assert.equal(profile.height(100),.6);assert.ok(profile.height(50)>8);
  const network=joinBridgePaths([[[0,0],[50,0]],[[50,0],[100,0]],[[50,0],[50,40]]].map(points=>({...base,points})));
  assert.equal(network.length,3);assert.ok(network.every(b=>b.ramps.filter(Boolean).length===1),'junctions are not dropped to ground');
});
test('bridges have solid decks, piers and LOD railings with finite bounded geometry',async()=>{
  const d=fixture();d.bridges=[{kind:'secondary',width:13,layer:1,ramps:[true,true],points:[[0,0],[120,0],[200,50]]}];
  const city=await createCityBackdrop(d);
  try{
    const solid=city.group.children.filter(m=>m.name==='bridge-structure'),rails=city.group.children.filter(m=>m.name==='bridge-railings');assert.ok(solid.length&&rails.length);
    assert.ok(city.stats().bridgeTriangles>100&&city.stats().bridgeTriangles<1000);
    for(const m of [...solid,...rails])assert.ok([...m.geometry.attributes.position.array].every(Number.isFinite));
    const camera=new THREE.PerspectiveCamera();camera.position.set(0,20000,20000);city.update(camera,[[0,0,200,200]]);assert.ok(rails.every(m=>!m.visible));assert.ok(solid.every(m=>m.visible));
    camera.position.set(0,20,200);city.update(camera);assert.ok(rails.every(m=>m.visible));
  }finally{city.destroy();}
  const real=JSON.parse(await readFile(new URL('../source/astana-overview.json',import.meta.url),'utf8'));let parts=0;
  assert.ok(real.bridges.length>100);assert.equal(real.source.bridgeZoom,14);
  for(const b of real.bridges)for(const part of bridgeGeometry(b)){if(!part.geometry)continue;parts++;assert.ok([...part.geometry.attributes.position.array].every(Number.isFinite));part.geometry.dispose();}
  assert.ok(parts<BRIDGE_LIMITS.parts);
});
