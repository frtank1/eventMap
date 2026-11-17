import {eventStyle,eventSummary} from './event-style.js';
import {eventIcon} from './event-icons.js';

// WebView forwards DOM mutations to layout/accessibility. In a dense city,
// rewriting `hidden` on every offscreen balloon every frame is expensive even
// when its value is unchanged. Keep idle and hidden labels mutation-free.
const labelFrames=new WeakMap();
const labelFrame=label=>{let frame=labelFrames.get(label);if(!frame){frame={classes:{}};labelFrames.set(label,frame);}return frame;};
export function setLabelVisible(label,visible){const hidden=!visible,frame=labelFrame(label);if(frame.hidden!==hidden){frame.hidden=hidden;if(label.hidden!==hidden)label.hidden=hidden;}}
export function setLabelClass(label,name,enabled){const frame=labelFrame(label),value=!!enabled;if(frame.classes[name]!==value){frame.classes[name]=value;label.classList.toggle(name,value);}}
export function setLabelLevel(label,level){const frame=labelFrame(label);if(frame.level!==level){frame.level=level;label.dataset.level=level;}}
export function positionLabel(label,x,y){
  const transform=`translate(${Math.round(x*10)/10}px,${Math.round(y*10)/10}px) translate(-50%,-100%)`;
  const frame=labelFrame(label);if(frame.transform!==transform){frame.transform=transform;label.style.transform=transform;}
}

// Native passes downsampled raster data, never signed URLs or account tokens.
// Local fixture paths are accepted only by the QA caller. CSP stays unchanged.
export function safeBubbleCover(value){
  if(typeof value!=='string'||value.length>180000)return null;
  if(/^event-covers\/(sport|music|market|discovery|route|quest|community)\.svg$/.test(value))return value;
  if(/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/]+={0,2}$/.test(value))return value;
  return null;
}
export function nativeBubbleCovers(values,events){
  return new Map((Array.isArray(values)?values.slice(0,8):[])
    .filter(c=>c&&typeof c.data==='string'&&c.data.startsWith('data:image/jpeg;')&&c.data.length<44000&&safeBubbleCover(c.data)&&events.some(e=>e.id===c.id&&e.coverRef===c.ref))
    .map(c=>[c.id,c.data]));
}
export function bubbleRect(x,y,{width,height,top=12,small=false,level,festival=false}={}){
  const [w,h]=festival&&level==='city'?[56,76]:festival&&level==='district'?[80,104]:level==='city'?[56,64]:level==='district'?[104,126]:level==='street'?[124,154]:[small?104:120,small?138:150];
  const rect={x:x-w/2,y:y-h,w,h};
  // Never slide a marker sideways to fit: its tether must keep the actual point.
  return Number.isFinite(x)&&Number.isFinite(y)&&rect.x>=8&&rect.x+w<=width-8&&rect.y>=top&&y<height-120?rect:null;
}
export function createBubble(group,covers){
  const first=group.events[0],single=group.events.length===1,style=eventStyle(first),summary=eventSummary(first);
  const make=(tag,cls,text)=>{const n=document.createElement(tag);n.className=cls;if(text)n.textContent=text;return n;};
  const button=make('button','event-balloon');button.type='button';
  button.style.setProperty('--event-color',single?style.color:'#c2b0e5');button.dataset.state=single?summary.state:'cluster';
  button.setAttribute('aria-label',single?`${first.title}. ${summary.label}`:`Событий рядом: ${group.events.length}. Открыть список`);
  const head=make('span','balloon-head'),face=make('span','balloon-face'),fallback=make('span','balloon-fallback');
  fallback.innerHTML=eventIcon(single?style.key:'cluster');face.append(fallback);
  const badge=make('span',single?'balloon-category':'balloon-count',single?'':String(group.events.length));
  if(single)badge.innerHTML=eventIcon(style.key);
  head.append(face,badge);
  const title=make('span','balloon-title',single?first.title:`Событий: ${group.events.length}`);
  const status=make('span','balloon-status',single?(summary.state==='live'?'Сейчас':summary.state==='signal'?'Сигнал':summary.state==='gathering'?summary.label:eventTimeLabel(first)):'События');
  head.append(title,status);button.append(head,make('span','balloon-tether'),make('span','balloon-anchor'));
  updateBubbleCover(button,group,covers);
  return button;
}

export function eventTimeLabel(event,now=Date.now()){
  const date=new Date(event.startAt||'');if(!Number.isFinite(date.getTime()))return 'Запланировано';
  const day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Almaty',year:'numeric',month:'2-digit',day:'2-digit'});
  const time=new Intl.DateTimeFormat('ru-RU',{timeZone:'Asia/Almaty',hour:'2-digit',minute:'2-digit'}).format(date);
  return day.format(date)===day.format(now)?`Сегодня · ${time}`:`${new Intl.DateTimeFormat('ru-RU',{timeZone:'Asia/Almaty',day:'numeric',month:'short'}).format(date)} · ${time}`;
}

export function updateBubbleCover(button,group,covers){
  const cover=group.events.map(e=>safeBubbleCover(covers.get(e.id))).find(Boolean)||'';
  if(button.coverUrl===cover)return;
  button.coverUrl=cover;
  const face=button.querySelector('.balloon-face');face.classList.remove('has-cover');face.querySelector('img')?.remove();
  if(!cover)return;
  const img=document.createElement('img');img.className='balloon-image';img.alt='';img.decoding='async';img.draggable=false;img.referrerPolicy='no-referrer';
  img.onload=()=>{if(img.parentNode===face)face.classList.add('has-cover');};img.onerror=()=>{if(img.parentNode===face){face.classList.remove('has-cover');img.remove();}};
  img.src=cover;face.append(img);
}
