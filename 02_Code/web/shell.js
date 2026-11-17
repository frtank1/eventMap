// Public web shell around the 3D city engine. It plays the part the native app
// plays on phones: it feeds the engine public events and reacts to taps.
// The engine bundle itself is used unchanged, through its existing bridge.
import {normalizeEvents,filterEvents,formatWhen,toCityEvent,isNight,themeFor,mapUrl,shareUrl,nearby,isLive,CATEGORIES,parseWeather,formatTemp} from './lib.js';

const params=new URLSearchParams(location.search);
const state={config:{},events:[],filter:'all',selected:null,covers:new Map(),coverFiles:{},demo:false,look:{phase:'auto',season:'auto',weather:'auto'},live:null};
// Time of day, season and weather: 'auto' follows the Astana clock and calendar.
const LOOK={phase:[['auto','Авто'],['sunrise','Рассвет'],['day','День'],['sunset','Закат'],['night','Ночь']],
  season:[['auto','Авто'],['spring','Весна'],['summer','Лето'],['autumn','Осень'],['winter','Зима']],
  weather:[['auto','Авто'],['clear','Ясно'],['cloudy','Облачно'],['rain','Дождь'],['snow','Снег']]};
const LOOK_TITLES={phase:'Время суток',season:'Сезон',weather:'Погода'};
const label=(group,key)=>(LOOK[group].find(([k])=>k===key)||[,''])[1];
try{Object.assign(state.look,JSON.parse(localStorage.getItem('em-look-2')||'{}'));}catch{/* private mode */}
for(const group of Object.keys(LOOK)){const v=params.get(group);if(v)state.look[group]=v;if(!LOOK[group].some(([k])=>k===state.look[group]))state.look[group]=LOOK[group][0][0];}
const saveLook=()=>{try{localStorage.setItem('em-look-2',JSON.stringify(state.look));}catch{/* not kept */}};
// Real Astana weather for «Авто». A failed request keeps the last known state (or clear sky).
async function refreshWeather(){
  if(!state.config.weatherUrl)return;
  try{const live=parseWeather(await json(state.config.weatherUrl));if(live){state.live={...live,at:now()};push();}}catch{/* offline: keep the last state */}
}
const sceneWeather=()=>state.look.weather==='auto'?(state.live&&now()-state.live.at<3*3600000?state.live.kind:'clear'):state.look.weather;
const resolved=()=>{try{const s=engine()?.stats?.();if(s?.phase)return s;}catch{/* engine still booting */}return null;};
const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
const json=async(url)=>{const r=await fetch(url,{cache:'no-store'});if(!r.ok)throw new Error(`${url}: ${r.status}`);return r.json();};
const engine=()=>window.EventMapMobile;
const now=()=>Date.now();

function visible(){return filterEvents(state.events,state.filter,now());}
function push(){
  if(!engine())return;
  const t=now(),{phase,season}=state.look,weather=sceneWeather(),night=phase==='auto'?isNight(t):phase==='night';
  // Holiday lights and snow follow the calendar unless a season is pinned.
  const calendar=state.config.theme&&state.config.theme!=='auto'?state.config.theme:themeFor(t);
  const theme=season==='auto'?calendar:season==='winter'?(calendar==='default'?'winter':calendar):'default';
  engine().receive({v:1,type:'sync',manifestUrl:new URL('world/manifest.json',location.href).href,
    events:visible().slice(0,150).map(e=>toCityEvent(e,t,state.coverFiles)),covers:[...state.covers.values()].slice(-8),
    night,theme,phase,season,weather,active:true,focusId:'city',focusRevision:0,updateRevision:0,interactionBlocked:false});
  document.body.classList.toggle('em-night',(resolved()?.phase??(night?'night':'day'))==='night');
  paintLook?.();
}

// The engine asks for small cover thumbnails only for bubbles in view.
async function thumbnail(ref){
  const image=await createImageBitmap(await (await fetch(ref)).blob());
  const size=192,canvas=Object.assign(document.createElement('canvas'),{width:size,height:size}),side=Math.min(image.width,image.height);
  canvas.getContext('2d').drawImage(image,(image.width-side)/2,(image.height-side)/2,side,side,0,0,size,size);
  for(const quality of [.82,.7,.55,.4]){const data=canvas.toDataURL('image/jpeg',quality);if(data.length<44000)return data;}
  return null;
}
async function answerCovers(ids){
  const t=now();let changed=false;
  for(const id of (Array.isArray(ids)?ids:[]).slice(0,8)){
    const event=state.events.find(e=>e.id===id);if(!event)continue;
    const ref=toCityEvent(event,t,state.coverFiles).coverRef;if(!ref||state.covers.get(id)?.ref===ref)continue;
    try{const data=await thumbnail(ref);if(data){state.covers.set(id,{id,ref,data});changed=true;}}catch{/* the category icon stays */}
  }
  if(changed)push();
}

// ---------- event card ----------
let sheet;
function closeSheet(){sheet?.remove();sheet=null;state.selected=null;history.replaceState(null,'',shareUrl(location.href,''));}
function link(label,href,cls='em-action'){const a=el('a',cls,label);a.href=href;a.target='_blank';a.rel='noopener noreferrer';return a;}
function openSheet(title,build){
  sheet?.remove();sheet=el('section','em-sheet');sheet.setAttribute('role','dialog');sheet.setAttribute('aria-label',title);
  const close=el('button','em-close','Закрыть');close.onclick=closeSheet;sheet.append(close);build(sheet);document.body.append(sheet);close.focus({preventScroll:true});
}
function openEvent(id){
  const e=state.events.find(x=>x.id===id);if(!e)return;
  state.selected=id;history.replaceState(null,'',shareUrl(location.href,id));
  openSheet(e.title,box=>{
    const cover=e.cover||state.coverFiles[e.category];
    if(cover){const img=el('img','em-cover');img.src=cover;img.alt='';img.loading='lazy';box.append(img);}
    const t=now(),head=el('div','em-head');
    head.append(el('span','em-chip'+(isLive(e,t)?' em-live':''),CATEGORIES[e.category].label),el('h2','',e.title),el('p','em-when',formatWhen(e,t)));
    if(e.place||e.address)head.append(el('p','em-where',[e.place,e.address].filter(Boolean).join(' · ')));
    if(e.price)head.append(el('p','em-where',e.price));
    box.append(head);
    if(e.description)box.append(el('p','em-text',e.description));
    const actions=el('div','em-actions'),share=el('button','em-action em-primary','Поделиться');
    share.onclick=async()=>{
      const url=shareUrl(location.href,e.id);
      try{if(navigator.share){await navigator.share({title:e.title,text:formatWhen(e,now()),url});return;}await navigator.clipboard.writeText(url);share.textContent='Ссылка скопирована';}
      catch{share.textContent='Не удалось поделиться';}
    };
    actions.append(share,link('Открыть в 2ГИС',mapUrl(e)));if(e.url)actions.append(link(e.source?`Источник: ${e.source}`:'Подробнее',e.url));
    box.append(actions);
    if(state.demo)box.append(el('p','em-note','Демо-данные: это не настоящее событие.'));
  });
}
function openPlace(place){
  const here=nearby(visible(),place.lat,place.lng);
  openSheet(place.title||'Место',box=>{
    const head=el('div','em-head');head.append(el('span','em-chip','Место'),el('h2','',place.title||'Точка на карте'));
    if(place.address)head.append(el('p','em-where',place.address));
    box.append(head);
    if(here.length){const list=el('div','em-list');for(const e of here.slice(0,6)){const b=el('button','em-row');b.append(el('strong','',e.title),el('small','',formatWhen(e,now())));b.onclick=()=>openEvent(e.id);list.append(b);}box.append(list);}
    else box.append(el('p','em-text','Здесь пока нет событий.'));
    if(state.config.suggestUrl)box.append(link('Предложить событие здесь',state.config.suggestUrl,'em-action em-primary'));
  });
}

// ---------- demo event scene (fictional, clearly labelled) ----------
async function toggleDemoScene(){
  if(!engine())return;
  if(state.demoScene){state.demoScene=null;engine().receive({v:1,type:'eventScene',scene:null});if(state.lookBeforeDemo)state.look=state.lookBeforeDemo;state.lookBeforeDemo=null;push();return;}
  try{state.demoScene=await json('scenes/demo-concert.json');}catch{return;}
  // Shown at night, where its lights carry the scene; the saved look is not changed.
  state.lookBeforeDemo={...state.look};state.look={...state.look,phase:'night'};push();
  engine().receive({v:1,type:'eventScene',scene:state.demoScene});
}

// ---------- look: time of day, season, weather ----------
let paintLook=null;
function openLook(){
  openSheet('Вид города',box=>{
    box.append(el('h2','em-look-title','Вид города'));
    for(const group of Object.keys(LOOK)){
      const row=el('div','em-seg');row.setAttribute('role','group');row.setAttribute('aria-label',LOOK_TITLES[group]);
      for(const [key,text] of LOOK[group]){
        const b=el('button','em-chipbtn',text);b.setAttribute('aria-pressed',String(state.look[group]===key));
        b.onclick=()=>{state.look[group]=key;saveLook();push();for(const x of row.children)x.setAttribute('aria-pressed',String(x===b));};
        row.append(b);
      }
      box.append(el('p','em-look-label',LOOK_TITLES[group]),row);
    }
    const demo=el('button','em-chipbtn',state.demoScene?'Скрыть демо-сцену':'★ Демо: 3D-сцена мероприятия');demo.setAttribute('aria-pressed',String(!!state.demoScene));
    demo.onclick=async()=>{await toggleDemoScene();closeSheet();};
    box.append(el('p','em-look-label','Мероприятие'),demo,el('p','em-note','Демо-сцена — вымышленный концерт для показа возможностей, не настоящее событие.'));
    const live=state.live?` Сейчас в Астане ${[formatTemp(state.live.temp),label('weather',state.live.kind).toLowerCase()].filter(Boolean).join(', ')}.`:'';
    box.append(el('p','em-note',`«Авто» — по часам, календарю и реальной погоде в Астане.${live}${state.config.weatherCredit?` Погода: ${state.config.weatherCredit}.`:''}`));
  });
}

// ---------- chrome ----------
function chrome(){
  const bar=el('header','em-bar'),brand=el('div','em-brand');brand.append(el('strong','',state.config.title||'EventMap'),el('span','',state.config.city||'Астана'));
  const chips=el('nav','em-chips');chips.setAttribute('aria-label','Когда');
  const count=el('output','em-count');
  const render=()=>{
    chips.replaceChildren();
    for(const [key,label] of [['all','Все'],['today','Сегодня'],['tomorrow','Завтра'],['weekend','Выходные']]){
      const b=el('button','em-chipbtn',label);b.setAttribute('aria-pressed',String(state.filter===key));b.onclick=()=>{state.filter=key;render();push();};chips.append(b);
    }
    const n=visible().length;count.textContent=state.events.length?(n?`Событий: ${n}`:'На это время событий нет'):'Событий пока нет';
  };
  const mode=el('button','em-mode');mode.setAttribute('aria-label','Время суток, сезон и погода');
  paintLook=()=>{const s=resolved(),{phase,season,weather}=state.look;
    const p=phase==='auto'&&s?label('phase',s.phase):label('phase',phase),z=season==='auto'&&s?label('season',s.season):label('season',season);
    const live=weather==='auto'&&state.live?[formatTemp(state.live.temp),state.live.kind!=='clear'?label('weather',state.live.kind):''].filter(Boolean).join(' '):'';
    mode.textContent=[p,z,weather==='auto'?live:weather!=='clear'?label('weather',weather):''].filter(Boolean).join(' · ');};
  mode.onclick=openLook;
  bar.append(brand,chips,mode);document.body.append(bar,count);render();paintLook();
  if(state.config.suggestUrl)document.body.append(link('+ Предложить событие',state.config.suggestUrl,'em-suggest'));
  if(state.demo)document.body.append(el('div','em-demo','Демо-данные'));
  document.addEventListener('keydown',e=>{if(e.key==='Escape')closeSheet();});
  return render;
}

async function start(){
  state.config=await json('config.json').catch(()=>({}));
  state.coverFiles=state.config.covers||{};
  state.demo=params.get('demo')==='1'&&state.config.demoEvents===true;
  const refresh=chrome();
  const load=async()=>{
    // The published list is the baseline; a live API, when configured, replaces it.
    let raw=await json(state.demo?'events.sample.json':'events.json').catch(()=>[]);
    if(!state.demo&&state.config.apiEvents){try{raw=await json(state.config.apiEvents);}catch{/* keep the published list */}}
    state.events=normalizeEvents(raw);refresh();push();
  };
  window.addEventListener('eventmap-message',e=>{
    const m=e.detail;if(!m||m.v!==1)return;
    if(m.type==='event')openEvent(m.id);
    if(m.type==='place')openPlace(m);
    if(m.type==='covers')void answerCovers(m.ids);
    if(m.type==='ready')push();
  });
  await load();
  const wanted=params.get('event');if(wanted)openEvent(wanted);
  // Statuses (soon / live / over) change with the clock even if nothing else does.
  setInterval(()=>{refresh();push();},60000);
  setInterval(load,10*60000);
  void refreshWeather();setInterval(refreshWeather,15*60000);
}
start();

// ?debug=1: on-screen diagnostics for phones/emulators without devtools
// (WebGL support, GPU, lost context, engine FPS and the last errors).
if(params.get('debug')==='1'){
  const box=el('pre','em-debug');document.body.append(box);const errors=[];
  const note=m=>{errors.push(String(m).slice(0,160));if(errors.length>6)errors.shift();};
  addEventListener('error',e=>note(`error: ${e.message||e.target?.src||e.type}`),true);
  addEventListener('unhandledrejection',e=>note(`promise: ${e.reason?.message||e.reason}`));
  const warn=console.warn,error=console.error;console.warn=(...a)=>{note(`warn: ${a.join(' ')}`);warn(...a);};console.error=(...a)=>{note(`error: ${a.join(' ')}`);error(...a);};
  const probe=document.createElement('canvas').getContext('webgl2'),info=probe?.getExtension('WEBGL_debug_renderer_info');
  const gpu=probe?(info?probe.getParameter(info.UNMASKED_RENDERER_WEBGL):probe.getParameter(probe.RENDERER)):'WebGL2 недоступен';
  const floats=probe?['EXT_color_buffer_float','EXT_color_buffer_half_float'].map(e=>`${e.replace('EXT_color_buffer_','')}:${probe.getSupportedExtensions().includes(e)?'да':'нет'}`).join(' '):'?';
  const highp=probe?['VERTEX_SHADER','FRAGMENT_SHADER'].map(k=>probe.getShaderPrecisionFormat(probe[k],probe.HIGH_FLOAT)?.precision).join('/'):'?';
  let lost=false;setTimeout(()=>document.querySelector('canvas')?.addEventListener('webglcontextlost',()=>{lost=true;},false),0);
  setInterval(()=>{let s={};try{s=engine()?.stats?.()||{};}catch(e){note(`stats: ${e.message}`);}
    const c=document.querySelector('canvas'),gl=c?.getContext('webgl2');
    box.textContent=[`GPU: ${gpu}`,`highp (вершины/пиксели): ${highp} · ${floats}`,`canvas: ${c?`${c.width}×${c.height}`:'нет'} · context ${gl?(gl.isContextLost()?'LOST':'ok'):'нет'}${lost?' (был потерян)':''}`,
      `fps ${s.fps??'?'} · draws ${s.drawCalls??'?'} · tris ${s.triangles??'?'} · ${s.quarterMode?'квартал':'город'} · backdrop ${s.backdropReady?'да':'нет'}`,
      `память JS: ${performance.memory?Math.round(performance.memory.usedJSHeapSize/1048576)+' MB':'?'}`,...errors].join('\n');},1000);
}
