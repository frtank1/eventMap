// Explicit Android debug QA. Clears ONLY this renderer's public tile cache;
// never pm clear, account storage, event data, or the model/address caches.
import {execFileSync} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url)),serial=process.argv[2],adb='/Users/market/Library/Android/sdk/platform-tools/adb';
if(serial!=='emulator-5556')throw Error('This acceptance is scoped to the owner-approved API 36 emulator');
const out=path.join(root,'test-results/2026-11-17-prepared-city');await mkdir(out,{recursive:true});
const shell=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:20000,maxBuffer:4*1024*1024}).trim();
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const inspect=()=>JSON.parse(execFileSync(process.execPath,['test/android-qa.mjs','inspect',serial],{cwd:root,encoding:'utf8',timeout:12000,stdio:['ignore','pipe','pipe']}));
async function read(expression){
  const pid=shell('shell','pidof','com.eventmap').split(' ')[0],port=shell('forward','tcp:0',`localabstract:webview_devtools_remote_${pid}`);let ws;
  try{const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),page=pages.find(p=>p.url.includes('/game-city/index.html'));
    if(!page)throw Error('No city page');ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
    return await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP deadline')),15000);ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id!==1)return;clearTimeout(timer);if(m.error||m.result.exceptionDetails)reject(Error(JSON.stringify(m)));else resolve(m.result.result.value);};ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));});
  }finally{ws?.close();shell('forward','--remove',`tcp:${port}`);}
}
async function wait(fn,ms=30000){const end=Date.now()+ms;while(Date.now()<end){try{const r=fn();if(r)return r;}catch{}await pause(250);}throw Error('Android cache deadline');}
function tap(text){shell('shell','uiautomator','dump','/sdcard/eventmap-cache-ui.xml');const xml=shell('shell','cat','/sdcard/eventmap-cache-ui.xml');const tag=xml.match(/<node\b[^>]+>/g).find(t=>t.includes(`text="${text}"`)),b=tag?.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);if(!b)throw Error(`Button missing: ${text}`);shell('shell','input','tap',String(Math.round((+b[1]+ +b[3])/2)),String(Math.round((+b[2]+ +b[4])/2)));}
await read(`new Promise((resolve,reject)=>{const r=indexedDB.deleteDatabase('eventmap-public-tiles-v1');r.onsuccess=()=>resolve('public tile cache removed for cold test');r.onerror=()=>reject(r.error);})`);
const results=[];
for(const mode of ['cold','warm']){
  shell('shell','am','force-stop','com.eventmap');const started=Date.now();shell('shell','am','start','-W','-n','com.eventmap/.CityPreviewActivity');
  const initial=await wait(()=>{const s=inspect();return s.stats.backdropReady?s:null;});const readyWallMs=Date.now()-started;
  if(!initial.stats.backdropPrepared)throw Error('Runtime triangulation fallback instead of baked geometry');
  if(initial.stats.cityNetworkTiles!==0)throw Error('City overview fetched unnecessary detail tiles');
  const detailStarted=Date.now();tap('Байтерек');const detail=await wait(()=>{const s=inspect();return s.stats.cityTiles>0&&s.stats.cityRequests===0?s:null;},45000);
  const detailWallMs=Date.now()-detailStarted;await pause(1000);
  const disk=await read(`new Promise((resolve,reject)=>{const r=indexedDB.open('eventmap-public-tiles-v1',2);r.onerror=()=>reject(r.error);r.onsuccess=()=>{const d=r.result,t=d.transaction('usage'),q=t.objectStore('usage').getAll();q.onsuccess=()=>{const rows=q.result;resolve({entries:rows.length,bytes:rows.reduce((n,r)=>n+r.size,0)});d.close();};};})`);
  if(disk.entries>256||disk.bytes>32*1024*1024)throw Error('Disk LRU budget exceeded');
  if(mode==='cold'&&detail.stats.cityNetworkTiles===0)throw Error('Cold test did not reach real provider');
  if(mode==='warm'&&(detail.stats.cityNetworkTiles!==0||detail.stats.cityDiskCache.hits<2))throw Error('Warm disk cache was not used');
  const result={mode,readyWallMs,detailWallMs,initial:initial.stats,detail:detail.stats,disk};results.push(result);
  await writeFile(path.join(out,'cache-startup.json'),JSON.stringify(results,null,2));console.log(JSON.stringify({mode,readyWallMs,detailWallMs,preparedMs:initial.stats.backdropLoadMs,readyAtMs:initial.stats.backdropReadyAtMs,networkTiles:detail.stats.cityNetworkTiles,cache:detail.stats.cityDiskCache,disk}));
}
console.log('PASS: cold/warm process launches, baked geometry, zero far-view tile requests, persistent district cache and bounded disk usage');
