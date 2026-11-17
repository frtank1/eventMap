// Pure bounded presentation rules; no network, accounts or location collection.
import {eventGroupCell} from './event-density.js';
import {CITY_PALETTE} from './art-direction.js';
// New entries are only ever appended: saved looks store indices.
export const palettes = Object.freeze({
  skin:[0xf0c9a8,0xd8a078,0xb87d59,0x764d39,0xf7dcc6,0x5a3826],
  hair:[0x292833,0x674632,0xb98a55,0xd7d4cb,0x8c3f24,0xe3c27e],
  coat:[0x9b7bd5,0x4e9f92,0xd99d58,0x608fbd,0xc7758f,0x34495e,0xece4d4,0x7d8f4e,0xe07a5f],
  pants:[CITY_PALETTE.dark[0],0x2a2a30,0x4a6fa5,0xc9b48f,0x5d6b3c],
  shoes:[CITY_PALETTE.sand[0],0xf2efe8,0x2b2b2e,0xc0503f],
});
// Hair styles of the procedural fallback avatar (avatar.js); the people kit uses outfit heads.
export const HAIR_STYLES = Object.freeze(['Короткие','Длинные','Пучки','Ёжик','Без волос']);
export const BODIES = Object.freeze(['мужское','женское']);
export const HEIGHTS = Object.freeze(['ниже','средний','выше']);
export const BUILDS = Object.freeze(['стройная','обычная','крупнее']);
// coat/pants/shoes: -1 keeps the outfit's own colours; head/top/bottom/feet pick an outfit part.
export const OUTFIT_SLOTS = Object.freeze(['head','top','bottom','feet']);
export const defaultLook = Object.freeze({v:1,skin:1,hair:0,coat:-1,style:0,bag:false,pants:-1,shoes:-1,glasses:false,height:1,build:1,body:0,beard:false,sleeves:0,head:0,top:0,bottom:0,feet:0});
export function validateLook(raw) {
  if(!raw || raw.v!==1 || !['skin','hair'].every(k=>Number.isInteger(raw[k])&&raw[k]>=0&&raw[k]<palettes[k].length) ||
    !Number.isInteger(raw.coat)||raw.coat<-1||raw.coat>=palettes.coat.length ||
    !Number.isInteger(raw.style)||raw.style<0||raw.style>=HAIR_STYLES.length||typeof raw.bag!=='boolean') return null;
  // Fields added later are optional: a look saved by an older app stays valid.
  const index=(key,count,min=0)=>raw[key]===undefined?defaultLook[key]:Number.isInteger(raw[key])&&raw[key]>=min&&raw[key]<count?raw[key]:null;
  const look={v:1,skin:raw.skin,hair:raw.hair,coat:raw.coat,style:raw.style,bag:raw.bag,
    pants:index('pants',palettes.pants.length,-1),shoes:index('shoes',palettes.shoes.length,-1),
    glasses:raw.glasses===undefined?false:raw.glasses,height:index('height',HEIGHTS.length),build:index('build',BUILDS.length),
    body:index('body',BODIES.length),beard:raw.beard===undefined?false:raw.beard,sleeves:index('sleeves',2),
    ...Object.fromEntries(OUTFIT_SLOTS.map(k=>[k,index(k,16)]))};
  if(typeof look.glasses!=='boolean'||typeof look.beard!=='boolean'||[look.pants,look.shoes,look.height,look.build,look.body,look.sleeves,...OUTFIT_SLOTS.map(k=>look[k])].includes(null))return null;
  return look;
}
export function visibleEvents(input, now=Date.now()) {
  if(!Array.isArray(input))return [];
  const ids=new Set();
  return input.slice(0,150).filter(e=>{
    if(!e || typeof e.id!=='string'||!e.id.trim()||e.id.length>100||ids.has(e.id)||!Number.isFinite(e.lat)||!Number.isFinite(e.lng)||
      e.lat<50.9||e.lat>51.4||e.lng<71.1||e.lng>71.9||e.status==='ended'||e.status==='cancelled'||(e.visibility!==undefined&&e.visibility!=='public'))return false;
    if(Number.isFinite(e.expiresAtMs)&&e.expiresAtMs<=now)return false;
    if(typeof e.endAt==='string'&&e.endAt&&Number.isFinite(Date.parse(e.endAt))&&Date.parse(e.endAt)<=now)return false;
    ids.add(e.id);return true;
  }).map(e=>({id:e.id,lat:e.lat,lng:e.lng,title:typeof e.title==='string'?e.title.slice(0,120):'Событие',
    category:typeof e.category==='string'?e.category.slice(0,40):'',status:e.status==='live'?'live':'soon',
    goingCount:Number.isSafeInteger(e.goingCount)?Math.max(0,Math.min(e.goingCount,1000000)):0,
    kind:['planned','gathering','live_signal'].includes(e.kind)?e.kind:'planned',sourceType:e.sourceType==='witness'?'witness':'initiator',
    capacity:Number.isSafeInteger(e.capacity)&&e.capacity>0&&e.capacity<=100000?e.capacity:null,
    confirmationCount:Number.isSafeInteger(e.confirmationCount)?Math.max(0,Math.min(e.confirmationCount,1000000)):0,
    startAt:typeof e.startAt==='string'&&Number.isFinite(Date.parse(e.startAt))?e.startAt:'',
    endAt:typeof e.endAt==='string'&&Number.isFinite(Date.parse(e.endAt))?e.endAt:'',
    expiresAtMs:Number.isFinite(e.expiresAtMs)?e.expiresAtMs:null,
    coverRef:typeof e.coverRef==='string'&&e.coverRef.length<=512?e.coverRef:null,
  }));
}
export function groupEvents(events,distance,festival=false) {
  const cell=eventGroupCell(distance,festival);
  const buckets=new Map();
  for(const e of events){
    const x=(e.lng-71.43056)*111320*Math.cos(51.12822*Math.PI/180),z=-(e.lat-51.12822)*111320;
    const key=`${Math.floor(x/cell)}:${Math.floor(z/cell)}`;
    const b=buckets.get(key)||{key,x:0,z:0,events:[]};b.events.push(e);b.x+=x;b.z+=z;buckets.set(key,b);
  }
  return [...buckets.values()].map(b=>({...b,x:b.x/b.events.length,z:b.z/b.events.length}));
}
export function labelsOverlap(a,b,gap=8) {return a.x<b.x+b.w+gap&&a.x+a.w+gap>b.x&&a.y<b.y+b.h+gap&&a.y+a.h+gap>b.y;}
