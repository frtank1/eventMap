// Run explicitly with the local Postman simulation; not part of npm test.
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url)),url=process.argv[2];
if(!/^http:\/\/127\.0\.0\.1:\d+\/city-tiles$/.test(url||''))throw Error('Pass local simulation URL');
const bundle=await build({stdin:{contents:"export {createCityStream} from './source/city-stream.js';export {parseTileMetadata} from './runtime/tile-cache.js';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:root+'source/vendor/three.module.js'}});
const {createCityStream,parseTileMetadata}=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].text).toString('base64'));
const realFetch=globalThis.fetch,rows=new Map();let requests=0,hits=0;
const disk={get:async key=>{const v=rows.get(key);if(v)hits++;return v;},put:async(key,bytes)=>rows.set(key,{bytes,created:Date.now()}),remove:async key=>rows.delete(key),stats:()=>({hits})};
globalThis.fetch=(input,options)=>{const u=new URL(input);assert.equal(u.hostname,'tiles.openfreemap.org');requests++;return realFetch(url+u.pathname,options);};
const control=mode=>realFetch(url+'/__control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mode})});
async function ready(city,ms=12000){const end=Date.now()+ms;while(Date.now()<end){await new Promise(r=>setTimeout(r,25));assert.ok(city.stats().cityRequests<=2);assert.ok(city.stats().cityCache<=16);if(city.stats().cityTiles===6&&city.stats().cityRequests===0)return;}throw Error(JSON.stringify(city.stats()));}
let city;
try{
  await control('retry');city=createCityStream({center:[71.43056,51.12822],diskCache:disk});await ready(city);
  assert.ok(requests>=14,'503s actually hit the real HTTP caller before recovery');assert.equal(city.stats().cityTiles,6);city.destroy();city=null;
  if(process.argv.includes('--recovery')){
    rows.clear();await control('offline');city=createCityStream({center:[71.43056,51.12822],diskCache:disk});await city.ready;
    assert.equal(city.stats().cityTiles,0);await control('healthy');await ready(city,40000);city.destroy();city=null;
    console.log('PASS: automatic recovery after all three metadata attempts fail, without reload or an online event');
  }
  await control('offline');requests=0;city=createCityStream({center:[71.43056,51.12822],diskCache:disk});await ready(city);
  assert.equal(requests,0);assert.ok(hits>=7);city.destroy();city=null;
  for(const host of ['invalid.example','user:password@tiles.openfreemap.org','tiles.openfreemap.org:123'])assert.throws(()=>parseTileMetadata({tiles:[`https://${host}/{z}/{x}/{y}.pbf`]}));
  console.log(JSON.stringify({result:'PASS',retry:'HTTP 503 then recovery',offlineTiles:6,offlineRequests:requests,cacheHits:hits,geometry:'synthetic empty MVT; not a visual test'}));
}finally{city?.destroy();globalThis.fetch=realFetch;}
