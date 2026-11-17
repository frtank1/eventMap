import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const b=await build({stdin:{contents:"export * from './runtime/prepared-city.js';export {createCityBackdrop} from './runtime/city-backdrop.js';",resolveDir:root},bundle:true,platform:'node',format:'esm',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createCityBackdrop,decodePreparedCity,cityGeometryKey}=await import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].text).toString('base64'));
const fixture={schemaVersion:1,center:[71.43056,51.12822],buildings:[[90,0,'cream',[[[0,0],[50,0],[50,50],[0,50]],[[10,10],[10,20],[20,20],[20,10]]]],[5,0,'cyan',[[[100,100],[110,100],[110,110],[100,110]]]]],surfaces:[['grass',[[[-100,-100],[200,-100],[200,200],[-100,200]]]]],roads:[],bridges:[]};
test('baked districts match source triangles, holes, picking and shader attributes',async()=>{
  const source=await createCityBackdrop(fixture);let restored;
  try{const bytes=source.bake(),prepared=decodePreparedCity(bytes.buffer);restored=await createCityBackdrop(null,{prepared});
    assert.equal(restored.stats().backdropPrepared,true);assert.equal(restored.stats().backdropTriangles,source.stats().backdropTriangles);
    for(const p of [[2,2],[15,15],[105,105],[500,500]])assert.deepEqual(restored.placeAt(...p),source.placeAt(...p));
    assert.equal(restored.group.children.length,source.group.children.length);
    for(let i=0;i<source.group.children.length;i++)for(const name of Object.keys(source.group.children[i].geometry.attributes))assert.deepEqual(restored.group.children[i].geometry.attributes[name].array,source.group.children[i].geometry.attributes[name].array);
    const geometry=restored.group.children[0].geometry;restored.setAppearance(true,'newyear');restored.setAppearance(false,'default');assert.equal(restored.group.children[0].geometry,geometry);
    await assert.rejects(()=>createCityBackdrop(null,{prepared,entries:[{id:'new',lng:71.4,lat:51.1,radius:10}]}),/incompatible/);
    const controller=new AbortController();controller.abort();await assert.rejects(()=>createCityBackdrop(null,{prepared,signal:controller.signal}),{name:'AbortError'});
    assert.throws(()=>decodePreparedCity(bytes.buffer.slice(0,-4)),/buffer/);const corrupt=bytes.slice();corrupt[0]=0;assert.throws(()=>decodePreparedCity(corrupt.buffer),/header/);
  }finally{source.destroy();restored?.destroy();}
});
test('geometry compatibility ignores address-only edits but includes model placement',()=>{
  const entries=[{id:'x',lng:71.4,lat:51.1,radius:10}],catalog={places:[{id:'x',review:{status:'matched'},footprints:[]}]};
  const before=cityGeometryKey(entries,catalog);catalog.places[0].address='new';assert.equal(cityGeometryKey(entries,catalog),before);
  entries[0].lng+=.001;assert.notEqual(cityGeometryKey(entries,catalog),before);
});
