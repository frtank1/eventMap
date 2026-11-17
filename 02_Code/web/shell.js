// Public web shell around the 3D city engine. It plays the part the native app
// plays on phones: it feeds the engine public events and reacts to taps.
// The engine bundle itself is used unchanged, through its existing bridge.
import {normalizeEvents,filterEvents,formatWhen,toCityEvent,isNight,themeFor,mapUrl,shareUrl,nearby,isLive,CATEGORIES} from './lib.js';

const params=new URLSearchParams(location.search);
const state={config:{},events:[],filter:'all',night:null,selected:null,covers:new Map(),coverFiles:{},demo:false};
const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
const json=async(url)=>{const r=await fetch(url,{cache:'no-store'});if(!r.ok)throw new Error(`${url}: ${r.status}`);return r.json();};
const engine=()=>window.EventMapMobile;
const now=()=>Date.now();

function visible(){return filterEvents(state.events,state.filter,now());}
function push(){
  if(!engine())return;
  const t=now(),night=state.night??isNight(t);
  engine().receive({v:1,type:'sync',manifestUrl:new URL('world/manifest.json',location.href).href,
    events:visible().slice(0,150).map(e=>toCityEvent(e,t,state.coverFiles)),covers:[...state.covers.values()].slice(-8),
    night,theme:state.config.theme&&state.config.theme!=='auto'?state.config.theme:themeFor(t),active:true,focusId:'baiterek',focusRevision:0,updateRevision:0,interactionBlocked:false});
  document.body.classList.toggle('em-night',night);
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
  const mode=el('button','em-mode');mode.setAttribute('aria-label','День или ночь');
  const paint=()=>{mode.textContent=(state.night??isNight(now()))?'Ночь':'День';};
  mode.onclick=()=>{state.night=!(state.night??isNight(now()));paint();push();};
  bar.append(brand,chips,mode);document.body.append(bar,count);render();paint();
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
}
start();
