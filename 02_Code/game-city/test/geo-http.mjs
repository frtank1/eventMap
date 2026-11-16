// Run the REAL client store against the local Postman HTTP stand-in.
// postman simulation run postman/simulations/geography/geography.sim.yaml --port 4016 --no-history
// node 02_Code/game-city/test/geo-http.mjs
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {GeoCatalogStore} from '../runtime/geo-catalog-store.js';
const base=process.env.GEO_SIM_URL||'http://127.0.0.1:4016/geography';
assert.equal(new URL(base).hostname,'127.0.0.1','Local simulation only');
const control=async mode=>{const r=await fetch(`${base}/__control`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mode})});assert.equal(r.status,200);};
const fallback=async()=>({addresses:JSON.parse(await readFile(new URL('../source/astana-addresses.json',import.meta.url),'utf8')),places:JSON.parse(await readFile(new URL('../source/astana-places.json',import.meta.url),'utf8'))});
const cache={pack:null,async read(){return this.pack;},async write(_,p){this.pack=structuredClone(p);}};
const states=[],store=new GeoCatalogStore({fallback,cache,onStatus:s=>states.push(s),timeout:1000});
const point={id:'test',lng:71.4302,lat:51.1281},url=`${base}/world/places-manifest.json`;
try{
  await control('v1');await store.start(url);assert.equal((await store.lookup(point)).address,'Simulation street, 1');
  for(const mode of ['hash','incompatible','offline','slow']){
    await control(mode);await store.update();assert.equal(store.manifest.release,'simulation-1',mode);assert.equal(cache.pack.manifest.release,'simulation-1',mode);assert.equal(states.at(-1).state,'kept-working',mode);
  }
  await control('v2');await store.update();assert.equal((await store.lookup(point)).address,'Simulation street, 2');
  await control('offline');const restarted=new GeoCatalogStore({fallback,cache,timeout:1000});await restarted.start(url);assert.equal(restarted.manifest.release,'simulation-2');restarted.stop();
  await control('v1');await store.update();assert.equal((await store.lookup(point)).address,'Simulation street, 1');
  console.log('PASS real HTTP: v1, corrupt hash, incompatible schema, 503, timeout, v2, offline cached restart, rollback.');
}finally{store.stop();await control('v1');}
