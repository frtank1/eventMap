import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
import Pbf from '../source/vendor/pbf.js';
import {VectorTile} from '../source/vendor/vector-tile.js';
import {CITY_PALETTE} from '../runtime/art-direction.js';
import {inPolygon,ringBounds} from '../source/city-overview.js';

const root=fileURLToPath(new URL('../',import.meta.url));
const bundled=await build({stdin:{contents:"export {createCityStream} from './source/city-stream.js';export * as THREE from 'three';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createCityStream,THREE}=await import('data:text/javascript;base64,'+Buffer.from(bundled.outputFiles[0].text).toString('base64'));
const center=[71.43056,51.12822],lonScale=111320*Math.cos(center[1]*Math.PI/180);
const square=(x,z,w)=>[[x,z],[x+w,z],[x+w,z+w],[x,z+w]];
const fixtureRings=[square(1500,1500,1000),square(1800,1800,400).reverse()];

// Minimal real MVT encoding, not a mocked mesh builder: exercises parsing,
// MultiPolygon/hole conversion, materials, batching and lifecycle together.
function tileBytes(layers){
  const tile=new Pbf();
  for(const [name,features]of Object.entries(layers))tile.writeMessage(3,(_,layer)=>{
    layer.writeVarintField(15,2);layer.writeStringField(1,name);layer.writeVarintField(5,4096);
    const keys=[],values=[];
    for(const feature of features){
      const tags=[];
      for(const [key,value]of Object.entries(feature.props||{})){
        let at=keys.indexOf(key);if(at<0){at=keys.length;keys.push(key);}
        tags.push(at,values.length);values.push(value);
      }
      layer.writeMessage(2,(_,f)=>{
        f.writeVarintField(1,feature.id||123);f.writePackedVarint(2,tags);f.writeVarintField(3,feature.type||3);
        const geometry=[];let x=0,z=0;const zig=n=>n<0?-n*2-1:n*2;
        for(const ring of feature.rings){
          geometry.push(9,zig(ring[0][0]-x),zig(ring[0][1]-z));[x,z]=ring[0];
          geometry.push(((ring.length-1)<<3)|2);
          for(const p of ring.slice(1)){geometry.push(zig(p[0]-x),zig(p[1]-z));[x,z]=p;}
          if((feature.type||3)===3)geometry.push(15);
        }
        f.writePackedVarint(4,geometry);
      });
    }
    for(const key of keys)layer.writeStringField(3,key);
    for(const value of values)layer.writeMessage(4,(_,v)=>{
      if(typeof value==='string')v.writeStringField(1,value);else v.writeDoubleField(3,value);
    });
  });
  return tile.finish().slice().buffer;
}
const bytes=tileBytes({
  park:[{rings:[square(100,100,3800)],props:{class:'park'}}],
  water:[{rings:[square(300,300,600)]}],
  transportation:[{type:2,rings:[[[1000,0],[1000,4096]]],props:{class:'primary'}}],
  building:[{rings:fixtureRings,props:{height:42,name:'Fixture building'}},
    {id:125,rings:[square(3000,3000,150)],props:{height:1,name:'Fixture building'}},
    {id:124,rings:[square(2600,1500,300)],props:{height:150,hide_3d:1}}],
});
async function settle(city){
  const end=Date.now()+4000;
  while(Date.now()<end){
    await new Promise(r=>setTimeout(r,2));
    assert.ok(city.stats().cityTiles<=9);assert.ok(city.stats().cityCache<=16);assert.ok(city.stats().cityRequests<=2);
    if(city.stats().cityRequests===0&&city.stats().cityTiles>0)return;
  }
  assert.fail('city stream did not settle');
}
async function withCity(run){
  const original=globalThis.fetch;let maxRequests=0,active=0;
  globalThis.fetch=async(url,{signal})=>{
    if(String(url).endsWith('/planet/latest'))return {ok:true,json:async()=>({tiles:['https://tiles.openfreemap.org/test/{z}/{x}/{y}.pbf']})};
    assert.equal(signal.aborted,false);maxRequests=Math.max(maxRequests,++active);
    await new Promise(r=>setTimeout(r,0));active--;
    return {ok:true,arrayBuffer:async()=>bytes};
  };
  const city=createCityStream({center});
  try{await city.ready;await settle(city);await run(city);assert.ok(maxRequests<=2);}
  finally{city.destroy();globalThis.fetch=original;}
}
const worldPoint=(tile,p)=>({x:tile.position.x+p[0],z:tile.position.z+p[1]});

test('baked-city startup defers unnecessary coarse tiles and resumes at the actual camera',async()=>{
  const original=globalThis.fetch;let requests=0;
  globalThis.fetch=async url=>String(url).endsWith('/planet/latest')?{ok:true,json:async()=>({tiles:['https://tiles.openfreemap.org/test/{z}/{x}/{y}.pbf']})}:{ok:true,arrayBuffer:async()=>{requests++;return bytes;}};
  const city=createCityStream({center,deferTiles:true});
  try{await city.ready;assert.equal(requests,0);city.setBackdropReady(true);city.update(new THREE.Vector3(),22000);await new Promise(r=>setTimeout(r,10));assert.equal(requests,0);city.update(new THREE.Vector3(),1500);await settle(city);assert.ok(requests>0);
    const root=city.group.children[0],traverse=root.traverse;let visits=0;root.traverse=function(fn){visits++;return traverse.call(this,fn);};
    city.setLod(1500);city.setLod(1501);assert.equal(visits,0,'warm geometry is not retraversed for uniform-only fades');
    city.setLod(300);assert.ok(visits>0,'visibility boundary still updates people/props');
  }finally{city.destroy();globalThis.fetch=original;}
});

test('city detail channels and overview handover reuse geometry across six zoom bands',async()=>withCity(async city=>{
  city.setBackdropReady(true);city.update(new THREE.Vector3(),5500);await settle(city);
  const roots=[...city.group.children],geometries=[];for(const r of roots)r.traverse(o=>{if(o.geometry)geometries.push(o.geometry);});
  for(const d of [5,180,700,1800,5100,6100,5100,1800,180]){
    city.setLod(d);assert.ok(city.detailWeight()>=0&&city.detailWeight()<=1);
    if(d===5100)assert.ok(city.detailWeight()>0&&city.detailWeight()<1);
    if(d===6100)assert.ok(roots.every(r=>!r.visible));
    const current=[];for(const r of roots)r.traverse(o=>{if(o.geometry)current.push(o.geometry);});assert.deepEqual(current,geometries);
    for(const r of roots)r.traverse(o=>{if(o.material?.userData.lod)assert.ok(o.material.userData.lod.value<=city.detailWeight());});
  }
  city.setBackdropReady(false);city.setLod(6000);assert.equal(city.detailWeight(),1,'failure fallback cannot fade into a missing backdrop');
}));

test('persistent backdrop removes far network churn; nearby detail keeps z14 source heights',async()=>withCity(async city=>{
  city.setBackdropReady(true);city.update(new THREE.Vector3(),22000);
  assert.equal(city.stats().cityTiles,0);assert.deepEqual(city.coverageBounds(),[]);
  city.update(new THREE.Vector3(),5500);await settle(city);assert.equal(city.stats().cityZoom,14);
  assert.ok(city.coverageBounds().length>0);assert.ok(city.coverageBounds().length<=9);
  const meshes=[...city.group.children],cached=city.stats().cityCache;
  city.update(new THREE.Vector3(),22000);assert.equal(city.stats().cityTiles,0);
  assert.equal(city.stats().cityCache,cached,'zooming out keeps bounded warm detail');
  city.update(new THREE.Vector3(),5500);
  assert.equal(city.stats().cityRequests,0,'zooming back does not refetch warm tiles');
  assert.deepEqual(new Set(city.group.children),new Set(meshes),'same geometry, not a rebuild');
  city.setBackdropReady(false);city.update(new THREE.Vector3(),18000);await settle(city);assert.equal(city.stats().cityZoom,12);
}));

test('near real footprints keep heights/holes/picking and share the material palette',async()=>withCity(async city=>{
  assert.ok(city.stats().cityBuildings>0);
  for(const tile of city.group.children){
    assert.ok(tile.userData.architectureDetails<=320);assert.ok(tile.userData.trees<=240);
    for(const place of tile.userData.places){
      assert.ok([2.2,43.2].includes(place.top));assert.equal(place.title,'Fixture building');
      assert.ok(place.id.startsWith('footprint-'));assert.equal(place.geometryKind,'footprint');assert.ok(place.geoRings.length);
      const b=ringBounds(place.rings[0]);
      const wall=worldPoint(tile,[b.minX+10,b.minZ+10]);
      assert.equal(city.placeAt(wall.x,wall.z)?.id,place.id);
      const facade=worldPoint(tile,place.rings[0][0]);
      assert.equal(city.placeAt(facade.x,facade.z)?.id,place.id,'facade boundary remains pickable');
      if(place.rings[1]){
        const hole=ringBounds(place.rings[1]),courtyard=worldPoint(tile,[(hole.minX+hole.maxX)/2,(hole.minZ+hole.maxZ)/2]);
        assert.equal(city.placeAt(courtyard.x,courtyard.z),null);
      }
    }
    tile.traverse(o=>{
      if(!o.userData.cityBuilding)return;
      assert.equal(o.castShadow,true);assert.equal(o.receiveShadow,true);
      const palette={cream:'ivory',cyan:'glass',sand:'sand',mint:'sage',roof:'rooftop',plinth:'trim'};
      assert.equal(o.material.color.getHex(),CITY_PALETTE[palette[o.name.slice(5)]][0]);
      for(let i=0;i<o.geometry.attributes.position.count;i++)assert.ok(o.geometry.attributes.position.getY(i)<44);
    });
  }
  city.setNight(true);
  for(const tile of city.group.children)for(const mesh of tile.children.filter(m=>m.name==='city-roof')){
    assert.equal(mesh.material.color.getHex(),CITY_PALETTE.rooftop[1]);assert.equal(mesh.material.emissive.getHex(),0);
  }
  city.update(new THREE.Vector3(),3500);await settle(city);
  city.group.traverse(o=>{if(o.userData.cityBuilding){assert.equal(o.castShadow,false);assert.equal(o.receiveShadow,false);}});
  const canopies=[];city.group.traverse(o=>{if(o.userData.cityCanopy)canopies.push(o);});
  assert.ok(canopies.length>0);assert.ok(canopies.every(o=>o.visible));
  for(const tile of city.group.children){
    const foliage=tile.children.find(m=>m.name==='city-foliage');if(!foliage)continue;
    const matrix=new THREE.Matrix4();
    for(let i=0;i<foliage.count;i++){
      foliage.getMatrixAt(i,matrix);const p=[matrix.elements[12],matrix.elements[14]];
      assert.ok(!tile.userData.places.some(b=>inPolygon(p,b.rings)));
    }
  }
}));

test('overview uses bounded real low relief, preserves courtyard geometry and never picks it',async()=>withCity(async city=>{
  city.update(new THREE.Vector3(),17000);await settle(city);
  assert.equal(city.stats().cityZoom,12);assert.ok(city.stats().cityOverviewBlocks>0);assert.equal(city.stats().cityBuildings,0);
  city.group.updateMatrixWorld(true);
  const sourceFeature=new VectorTile(new Pbf(new Uint8Array(bytes))).layers.building.feature(0);
  let courtyardProbes=0;
  for(const tile of city.group.children){
    assert.equal(tile.userData.places.length,0);
    let vertices=0;
    for(const mesh of tile.children.filter(m=>m.name==='city-overview')){
      assert.equal(mesh.isInstancedMesh,undefined,'not bounding-box substitutions');
      const p=mesh.geometry.attributes.position;vertices+=p.count;
      for(let i=0;i<p.count;i++)assert.ok(p.getY(i)<=6.551);
      const hits=[];mesh.raycast({},hits);assert.deepEqual(hits,[]);
      assert.equal(mesh.material.emissive.getHex(),0);
    }
    assert.ok(vertices<=180000);assert.ok(tile.userData.overviewBlocks<=2200);
    // Bypass the deliberately disabled public raycast to inspect actual roof
    // triangles. A bounding-box stand-in would incorrectly fill each hole.
    const [,tx,ty]=tile.userData.key.split('/').map(Number);
    for(let dx=0;dx<2;dx++)for(let dy=0;dy<2;dy++){
      const rings=sourceFeature.toGeoJSON(tx*2+dx,ty*2+dy,13).geometry.coordinates;
      if(!rings[1])continue;
      const outer=ringBounds(rings[0]),hole=ringBounds(rings[1]);
      const rayAt=(lng,lat)=>{
        const ray=new THREE.Raycaster(new THREE.Vector3((lng-center[0])*lonScale,100,-(lat-center[1])*111320),new THREE.Vector3(0,-1,0));
        const hits=[];
        for(const mesh of tile.children.filter(m=>m.name==='city-overview'))THREE.Mesh.prototype.raycast.call(mesh,ray,hits);
        return hits;
      };
      const wall=rayAt(outer.minX+(outer.maxX-outer.minX)*.1,outer.minZ+(outer.maxZ-outer.minZ)*.1);
      if(wall.length){courtyardProbes++;assert.equal(rayAt((hole.minX+hole.maxX)/2,(hole.minZ+hole.maxZ)/2).length,0);}
    }
  }
  assert.ok(courtyardProbes>0,'verified roofs around actual open courtyards');
  assert.equal(city.placeAt(0,0),null);
  city.update(new THREE.Vector3(),40000);await settle(city);
  assert.equal(city.stats().cityZoom,10);assert.equal(city.stats().cityOverviewBlocks,0);assert.equal(city.stats().cityBuildings,0);
  assert.ok(city.group.children.every(t=>t.children.every(m=>m.name!=='city-overview')),'no synthetic urban masses');
  city.update(new THREE.Vector3(),1000);await settle(city);
  assert.ok(city.stats().cityBuildings>0);assert.ok(city.group.children.every(t=>t.userData.zoom===14));
}));

test('all city directions remain reachable with the same streaming budgets',async()=>withCity(async city=>{
  for(const [lng,lat]of [[71.20,51.15],[71.48,51.30],[71.40957,51.07242],[71.7,51.15]]){
    const p=new THREE.Vector3((lng-center[0])*lonScale,0,-(lat-center[1])*111320);
    city.update(p,1500);await settle(city);
    assert.ok(city.coverageAt(p.x,p.z),`coverage at ${lng},${lat}`);
    assert.ok(city.stats().cityBuildings>0);
  }
}));
