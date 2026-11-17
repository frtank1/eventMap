// Explicitly opt-in Android WebView QA through an ADB-forwarded debug socket.
// No server/API/GPS calls. Run only with the owner's permission to drive this emulator.
// Usage: node test/android-qa.mjs inspect|timing|pinch-in|pinch-out [emulator-5556]
import {execFileSync} from 'node:child_process';
const adb=process.env.EVENTMAP_QA_ADB||'/Users/market/Library/Android/sdk/platform-tools/adb';
const [action='inspect',serial='emulator-5556']=process.argv.slice(2);
if(!['inspect','timing','pinch-in','pinch-out'].includes(action))throw Error('Unknown QA action');
const shell=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8'}).trim();
const pid=shell('shell','pidof','com.eventmap').split(' ')[0];
if(!/^\d+$/.test(pid))throw Error('Open EventMap City Preview first');
const port=shell('forward','tcp:0',`localabstract:webview_devtools_remote_${pid}`);
let ws;
try{
  const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const page=pages.find(p=>p.url.startsWith('https://appassets.androidplatform.net/')&&p.url.endsWith('/game-city/index.html'));
  if(!page)throw Error('City WebView is not open');
  ws=new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
  let sequence=0;const pending=new Map();
  ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(Error(JSON.stringify(m.error))):p.resolve(m.result);}}};
  const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
  const read=async expression=>{
    const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});
    if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));
    return r.result.value;
  };
  if(action==='timing')console.error(JSON.stringify(await read(`(async()=>{const timeout=[],raf=[];for(let i=0;i<10;i++){let t=performance.now();await new Promise(r=>setTimeout(r,0));timeout.push(performance.now()-t);t=performance.now();await new Promise(requestAnimationFrame);raf.push(performance.now()-t);}return {visibility:document.visibilityState,focus:document.hasFocus(),timeout,raf};})()`)));
  if(action.startsWith('pinch-')){
    // Pick a visibly unobstructed canvas strip, not a marker/control. This is
    // a real two-pointer WebView input sequence, not a call to the zoom function.
    const strip=await read(`(()=>{const spread=Math.min(innerWidth*.28,100);for(const ratio of [.12,.25,.4,.6]){
      const x=innerWidth*.5,y=innerHeight*ratio;
      if([-spread,-20,0,20,spread].every(dx=>document.elementFromPoint(x+dx,y)?.tagName==='CANVAS'))return {x,y,spread};
    }return null;})()`);
    if(!strip)throw Error('No unobstructed canvas strip for a two-finger gesture');
    const {x,y,spread}=strip;
    const move=async radius=>call('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x-radius,y,id:0},{x:x+radius,y,id:1}]});
    const zoomIn=action==='pinch-in',a=zoomIn?20:spread,b=zoomIn?spread:20;
    await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:x-a,y,id:0},{x:x+a,y,id:1}]});
    for(let i=1;i<=24;i++){await move(a+(b-a)*i/24);await new Promise(r=>setTimeout(r,35));}
    await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await new Promise(r=>setTimeout(r,600));
  }
  const report=await read(`JSON.stringify({stats:window.EventMapMobile?.stats(),viewport:[innerWidth,innerHeight],visibility:document.visibilityState,focused:document.hasFocus(),
    gpu:(()=>{const g=document.querySelector('canvas')?.getContext('webgl2');if(!g)return 'unavailable';const e=g.getExtension('WEBGL_debug_renderer_info');return e?g.getParameter(e.UNMASKED_RENDERER_WEBGL):'unavailable';})(),
    balloons:[...document.querySelectorAll('.event-balloon:not([hidden])')].map(b=>{const r=b.querySelector('.balloon-head').getBoundingClientRect();return {label:b.getAttribute('aria-label'),x:r.x+r.width/2,y:r.y+Math.min(r.height/2,22),w:r.width,h:r.height,cover:!!b.querySelector('.has-cover'),level:b.dataset.level};}),
    note:document.querySelector('#scene-note')?.textContent,
    controls:Object.fromEntries(['self-button','avatar-done','scene-button','scene-done','quarter-close','quarter-overview','quarter-source','zoom-in','zoom-out'].map(id=>{const e=document.getElementById(id),r=e?.getBoundingClientRect();return [id,r&&r.width&&r.height?{x:r.x+r.width/2,y:r.y+r.height/2}:null];})),
    list:document.querySelector('#nearby-list')?.hidden!==false?null:{text:document.querySelector('#nearby-list').innerText,
      buttons:[...document.querySelectorAll('#nearby-list button')].map(b=>{const r=b.getBoundingClientRect();return {text:b.innerText,x:r.x+r.width/2,y:r.y+r.height/2};})},
    bodySize:[document.documentElement.scrollWidth,document.documentElement.scrollHeight]})`);
  console.log(report);
}finally{ws?.close();shell('forward','--remove',`tcp:${port}`);}
