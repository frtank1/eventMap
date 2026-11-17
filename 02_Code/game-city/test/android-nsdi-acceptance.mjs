// Owner-approved ADB acceptance, isolated debug launcher only. Does not clear app
// data, edit accounts, access GPS, publish events, or send anything to a backend.
import {execFileSync} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url)),serial=process.argv[2];
if(!/^emulator-\d+$/.test(serial||''))throw Error('Pass the explicitly authorized emulator serial');
const adb='/Users/market/Library/Android/sdk/platform-tools/adb',out=path.join(root,'test-results/2026-11-16-nsdi-quarter');await mkdir(out,{recursive:true});
const shell=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8'}).trim();
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const inspect=()=>JSON.parse(execFileSync(process.execPath,['test/android-qa.mjs','inspect',serial],{cwd:root,encoding:'utf8'}));
const wait=async(fn,ms=45000)=>{const end=Date.now()+ms;while(Date.now()<end){const r=await fn();if(r)return r;await pause(800);}throw Error('QA condition deadline');};
const xml=()=>{shell('shell','uiautomator','dump','/sdcard/eventmap-nsdi-ui.xml');return shell('shell','cat','/sdcard/eventmap-nsdi-ui.xml');};
function bounds(tag){const v=tag.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);if(!v)throw Error('UI bounds missing');return v.slice(1).map(Number);}
function nativeTap(text){const tag=xml().match(/<node\b[^>]+>/g).find(t=>t.includes(`text="${text}"`));if(!tag)throw Error(`UI text not visible: ${text}`);const [x,y,xx,yy]=bounds(tag);shell('shell','input','tap',`${Math.round((x+xx)/2)}`,`${Math.round((y+yy)/2)}`);}
function webTap(point,state){const tag=xml().match(/<node\b[^>]+>/g).find(t=>t.includes('class="android.webkit.WebView"'));const [x,y,xx,yy]=bounds(tag);shell('shell','input','tap',`${Math.round(x+point.x*(xx-x)/state.viewport[0])}`,`${Math.round(y+point.y*(yy-y)/state.viewport[1])}`);}
async function capture(name){const bytes=execFileSync(adb,['-s',serial,'exec-out','screencap','-p'],{maxBuffer:8*1024*1024});await writeFile(path.join(out,`${name}.png`),bytes);await writeFile(path.join(out,`${name}.json`),JSON.stringify(inspect(),null,2));}
shell('shell','am','start','-n','com.eventmap/.CityPreviewActivity');
await wait(()=>{try{return inspect().stats.backdropReady;}catch{return false;}});
const pid=shell('shell','pidof','com.eventmap').split(' ')[0];
const logs=()=>shell('logcat',`--pid=${pid}`,'-d','-s','EventMapCityQA:I');
const reports=()=>logs().split('\n').filter(l=>l.includes('ZOOM_RESULT ')).map(l=>JSON.parse(l.split('ZOOM_RESULT ')[1]));
const results=[];
async function settleCamera(){let last=null,stable=0;await wait(()=>{const distance=inspect().stats.cameraDistance;stable=last!==null&&Math.abs(distance-last)<.05?stable+1:0;last=distance;return stable>=3;},12000);}
async function benchmark(name){const count=reports().length;nativeTap('Прогон зума');await wait(()=>reports().length>count,38000);const r=reports().at(-1);if(r.status!=='complete')throw Error(`Incomplete run: ${r.reason}`);results.push({name,...r});await writeFile(path.join(out,'comparisons.json'),JSON.stringify(results,null,2));console.log(JSON.stringify({name,variant:r.quarterVariant,night:r.night,...r.frameTiming,lod:r.lodTransitions}));}
async function variant(wanted){let s=inspect();if(s.stats.quarterVariant===wanted)return;
  await settleCamera();s=inspect();
  const camera=s.stats.cameraDistance;nativeTap(wanted==='nsdi'?'Текущая → НИПД':'НИПД → текущая');
  await wait(()=>{const t=inspect().stats;return !t.trialBusy&&t.quarterVariant===wanted;});
  s=inspect();if(Math.abs(s.stats.cameraDistance-camera)>1)throw Error('A/B changed the camera');if(s.stats.events!==150)throw Error('A/B lost events');
}
const interactionsOnly=process.argv.includes('--interactions-only');
console.log(interactionsOnly?'Checking actual input and A/B restore (existing sweeps retained).':'Checking current map, then five measured sweeps and actual input.');
if(!interactionsOnly){
await variant('current');if(inspect().stats.night)nativeTap('День');nativeTap('Байтерек');await pause(1200);await capture('current-day');
await benchmark('current-day-first');await benchmark('current-day-warm');
nativeTap('Байтерек');await pause(800);await variant('nsdi');await capture('nsdi-day');
await benchmark('nsdi-day-first');await benchmark('nsdi-day-warm');
await capture('nsdi-city-day');nativeTap('Ночь');await benchmark('nsdi-night');await capture('nsdi-city-night');
}else{await variant('nsdi');if(!inspect().stats.night)nativeTap('Ночь');}
nativeTap('Байтерек');await pause(800);await capture('nsdi-night');
for(const action of ['pinch-out','pinch-in']){
  const before=inspect().stats.cameraDistance;
  const after=JSON.parse(execFileSync(process.execPath,['test/android-qa.mjs',action,serial],{cwd:root,encoding:'utf8'}));
  if(action==='pinch-out'?after.stats.cameraDistance<=before:after.stats.cameraDistance>=before)throw Error(`Gesture did not zoom: ${action}`);
  await writeFile(path.join(out,`${action}.json`),JSON.stringify({before,...after},null,2));
}
nativeTap('Байтерек');await pause(700);
let s=inspect();const balloon=s.balloons.find(b=>b.cover&&b.x>35&&b.x<s.viewport[0]-35&&b.y>80&&b.y<s.viewport[1]-90);
if(!balloon)throw Error('No tappable near cover');const taps=logs().split('EVENT_TAP ').length;webTap(balloon,s);
await wait(()=>logs().split('EVENT_TAP ').length>taps,8000);
await wait(()=>xml().includes('ПРИМЕР СОБЫТИЯ · НЕ РЕАЛЬНОЕ ПРИГЛАШЕНИЕ'),8000);
await pause(500);await capture('nsdi-event-card');
shell('shell','input','keyevent','4');await pause(600);s=inspect();webTap(s.controls['self-button'],s);
await wait(()=>inspect().stats.avatarEditor,8000);await settleCamera();await capture('nsdi-avatar');s=inspect();webTap(s.controls['avatar-done'],s);
await wait(()=>!inspect().stats.avatarEditor,8000);
await variant('current');await variant('nsdi');if(inspect().stats.night)nativeTap('День');nativeTap('Байтерек');await pause(700);await capture('nsdi-final');
await writeFile(path.join(out,'android-qa.log'),logs());
console.log(`PASS: A/B restore, 150 events, day/night, two-pointer gestures, visible native event sheet and avatar editor. ${interactionsOnly?'Earlier measured camera sweeps retained, not rerun.':'Five LOD sweeps completed.'}`);
