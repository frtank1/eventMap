// Owner-approved, debug-only Android input/performance acceptance. No app data
// clearing, accounts, GPS, production writes, or calls to JS click handlers.
import {execFileSync} from 'node:child_process';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url)),serial=process.argv[2];
if(!/^emulator-\d+$/.test(serial||''))throw Error('Pass the explicitly authorized emulator serial');
const adb='/Users/market/Library/Android/sdk/platform-tools/adb',out=process.argv.includes('--prepared')?path.join(root,'test-results/2026-11-17-prepared-city',process.argv.includes('--final')?'final':'.'):path.join(root,'test-results/2026-11-16-city-lod',process.argv.includes('--optimized')?'optimized':process.argv.includes('--foreground')?'foreground':'initial');
await mkdir(out,{recursive:true});
const shell=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',maxBuffer:4*1024*1024}).trim();
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const inspect=()=>{for(let i=0;i<2;i++)try{return JSON.parse(execFileSync(process.execPath,['test/android-qa.mjs','inspect',serial],{cwd:root,encoding:'utf8',timeout:10000,stdio:['ignore','pipe','pipe']}));}catch(e){if(i||e.code!=='ETIMEDOUT')throw e;console.warn('One CDP inspection timeout; retrying transport once.');}};
const wait=async(fn,ms=45000)=>{const end=Date.now()+ms;while(Date.now()<end){const r=await fn();if(r)return r;await pause(800);}throw Error('QA condition deadline');};
const xml=()=>{shell('shell','uiautomator','dump','/sdcard/eventmap-city-lod-ui.xml');return shell('shell','cat','/sdcard/eventmap-city-lod-ui.xml');};
function bounds(tag){const v=tag?.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);if(!v)throw Error('UI bounds missing');return v.slice(1).map(Number);}
function nativeTap(text){const tag=xml().match(/<node\b[^>]+>/g).find(t=>t.includes(`text="${text}"`));if(!tag)throw Error(`UI text not visible: ${text}`);const [x,y,xx,yy]=bounds(tag);shell('shell','input','tap',`${Math.round((x+xx)/2)}`,`${Math.round((y+yy)/2)}`);}
function webTap(point,state){if(!point)throw Error('Missing web control');const tag=xml().match(/<node\b[^>]+>/g).find(t=>t.includes('class="android.webkit.WebView"'));const [x,y,xx,yy]=bounds(tag);shell('shell','input','tap',`${Math.round(x+point.x*(xx-x)/state.viewport[0])}`,`${Math.round(y+point.y*(yy-y)/state.viewport[1])}`);}
async function capture(name){const bytes=execFileSync(adb,['-s',serial,'exec-out','screencap','-p'],{maxBuffer:8*1024*1024});await writeFile(path.join(out,`${name}.png`),bytes);await writeFile(path.join(out,`${name}.json`),JSON.stringify(inspect(),null,2));}
async function settleCamera(){let last=null,stable=0;await wait(()=>{const distance=inspect().stats.cameraDistance;stable=last!==null&&Math.abs(distance-last)<.05?stable+1:0;last=distance;return stable>=3;},15000);}
shell('shell','am','start','-n','com.eventmap/.CityPreviewActivity');
await wait(()=>{try{return inspect().stats.backdropReady;}catch{return false;}},90000);
if(inspect().stats.lodVersion!=='astana-six-layers-1')throw Error('Wrong renderer build');
if(process.argv.includes('--prepared')&&!inspect().stats.backdropPrepared)throw Error('Baked city not active');
const pid=shell('shell','pidof','com.eventmap').split(' ')[0];
const logs=()=>shell('logcat',`--pid=${pid}`,'-d','-s','EventMapCityQA:I');
const reports=()=>logs().split('\n').filter(l=>l.includes('ZOOM_RESULT ')).map(l=>JSON.parse(l.split('ZOOM_RESULT ')[1]));
const results=process.argv.includes('--resume')?JSON.parse(await readFile(path.join(out,'comparisons.json'),'utf8')):[];
async function benchmark(name,area){if(results.some(r=>r.name===name)){console.log(`Retained measured run: ${name}`);return;}const count=reports().length;nativeTap('Прогон зума');await wait(()=>reports().length>count,55000);const r=reports().at(-1);
  if(r.status!=='complete'||r.area!==area||r.lodVersion!=='astana-six-layers-1'||r.events!==150)throw Error(`Unexpected run: ${JSON.stringify(r)}`);
  for(const band of ['building','street','quarter','district','city'])if(!r.lodTransitions.some(t=>t.band===band))throw Error(`Missed LOD ${band}`);
  results.push({name,...r});await writeFile(path.join(out,'comparisons.json'),JSON.stringify(results,null,2));
  console.log(JSON.stringify({name,area:r.area,night:r.night,...r.frameTiming,coverage:r.coverage,peakDraws:r.maxDrawCalls,peakTriangles:r.maxTriangles}));
}
const interactionsOnly=process.argv.includes('--interactions-only');
if(inspect().stats.quarterVariant!=='current'){nativeTap('НИПД → текущая');await wait(()=>!inspect().stats.trialBusy&&inspect().stats.quarterVariant==='current');}
if(inspect().stats.night)nativeTap('День');
if(!interactionsOnly){
  // Run on a freshly opened launcher, whose benchmark area defaults to Center.
  if(!results.some(r=>r.name==='center-day-warm')){nativeTap('Байтерек');await settleCamera();await capture('center-day-near');
    await benchmark('center-day-first','center');await benchmark('center-day-warm','center');await capture('city-day');}
  for(const [from,area,name] of [['Центр','north-city','north'],['Север','east-city','east'],['Восток','west-city','west']]){
    nativeTap(from);if(results.some(r=>r.name===`${name}-day`))continue;await settleCamera();await pause(2500);await capture(`${name}-day-street`);
    await benchmark(`${name}-day`,area);await capture(`${name}-day-city`);
  }
  nativeTap('Ночь');await benchmark('west-night','west-city');await capture('west-night-city');
  nativeTap('Запад'); // back to Center and Baiterek
}
nativeTap('Байтерек');await settleCamera();
for(const action of ['pinch-out','pinch-in']){
  const before=inspect().stats.cameraDistance;
  const after=JSON.parse(execFileSync(process.execPath,['test/android-qa.mjs',action,serial],{cwd:root,encoding:'utf8'}));
  if(action==='pinch-out'?after.stats.cameraDistance<=before:after.stats.cameraDistance>=before)throw Error(`Gesture did not zoom: ${action}`);
  await writeFile(path.join(out,`${action}.json`),JSON.stringify({before,...after},null,2));
}
nativeTap('Байтерек');await settleCamera();
let s=inspect();const balloon=s.balloons.find(b=>b.cover&&b.x>35&&b.x<s.viewport[0]-35&&b.y>80&&b.y<s.viewport[1]-90);
if(!balloon)throw Error('No tappable event cover');const taps=logs().split('EVENT_TAP ').length;webTap(balloon,s);
await wait(()=>logs().split('EVENT_TAP ').length>taps,8000);
await wait(()=>xml().includes('ПРИМЕР СОБЫТИЯ · НЕ РЕАЛЬНОЕ ПРИГЛАШЕНИЕ'),8000);await pause(500);await capture('event-card');
shell('shell','input','keyevent','4');await pause(600);s=inspect();webTap(s.controls['self-button'],s);
await wait(()=>inspect().stats.avatarEditor,8000);await settleCamera();await capture('avatar');
if(inspect().stats.layer!=='person')throw Error('Avatar did not reach person detail band');
s=inspect();webTap(s.controls['avatar-done'],s);await wait(()=>!inspect().stats.avatarEditor,8000);await settleCamera();
if(inspect().stats.night)nativeTap('День');nativeTap('Байтерек');await settleCamera();await capture('final-day');
await writeFile(path.join(out,'android-qa.log'),logs());
if(!interactionsOnly&&results.some(r=>!r.coverage.maxTiles))console.warn('PARTIAL COVERAGE: some sweeps used only the persistent fallback; detailed-stream performance/handover is NOT verified.');
console.log(`FUNCTIONAL PASS: six LOD bands, 150 events retained, real pinch in/out, visible native event card and avatar. ${interactionsOnly?'Measured sweeps retained, not rerun.':'Six measured center/north/east/west sweeps completed; evaluate FPS separately.'}`);
