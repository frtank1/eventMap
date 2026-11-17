// Pure helpers for the public web shell. No DOM, no network: unit-tested in Node.
// Astana uses UTC+5 all year (no daylight saving), so dates are derived with a
// fixed offset instead of the visitor's own time zone.
export const ASTANA_OFFSET_MS=5*3600*1000;
export const BOUNDS=Object.freeze({south:50.9,north:51.4,west:71.1,east:71.9});
export const CATEGORIES=Object.freeze({
  sport:{label:'Спорт',engine:'спорт'},music:{label:'Музыка',engine:'музыка'},market:{label:'Маркеты и еда',engine:'маркет'},
  discovery:{label:'Находки',engine:'находки'},route:{label:'Забеги и прогулки',engine:'маршруты'},quest:{label:'Игры и квесты',engine:'квесты'},
  community:{label:'Встречи',engine:'встречи'},newyear:{label:'Новый год',engine:'встречи'},kids:{label:'Детям',engine:'встречи'},
  art:{label:'Выставки',engine:'находки'},theatre:{label:'Театр и кино',engine:'музыка'},education:{label:'Лекции и мастер-классы',engine:'встречи'},
});
const text=(v,max)=>typeof v==='string'?v.replace(/[\u0000-\u001f\u007f]/g,' ').trim().slice(0,max):'';
const time=v=>{const t=typeof v==='string'?Date.parse(v):NaN;return Number.isFinite(t)?t:null;};
const httpUrl=v=>{try{const u=new URL(v);return u.protocol==='https:'||u.protocol==='http:'?u.href.slice(0,500):'';}catch{return '';}};
// Accepts the curated events file. Anything malformed is dropped, never guessed.
export function normalizeEvents(raw){
  if(!Array.isArray(raw))return [];
  const seen=new Set(),out=[];
  for(const e of raw.slice(0,500)){
    if(!e||typeof e!=='object')continue;
    const id=text(e.id,100),title=text(e.title,120),start=time(e.startAt),end=time(e.endAt);
    if(!id||!/^[A-Za-z0-9_-]+$/.test(id)||seen.has(id)||!title||start===null)continue;
    if(typeof e.lat!=='number'||typeof e.lng!=='number'||e.lat<BOUNDS.south||e.lat>BOUNDS.north||e.lng<BOUNDS.west||e.lng>BOUNDS.east)continue;
    if(end!==null&&end<=start)continue;
    seen.add(id);
    out.push({id,title,category:CATEGORIES[e.category]?e.category:'community',lat:e.lat,lng:e.lng,startMs:start,
      // A missing end means "about three hours": enough to show it as running, not all night.
      endMs:end??start+3*3600*1000,hasEnd:end!==null,place:text(e.place,120),address:text(e.address,200),description:text(e.description,1200),
      price:text(e.price,60),url:httpUrl(e.url),source:text(e.source,80),cover:typeof e.cover==='string'&&/^covers\/[a-z0-9-]+\.(jpg|webp|png)$/.test(e.cover)?e.cover:''});
  }
  return out.sort((a,b)=>a.startMs-b.startMs);
}
export const isOver=(e,now)=>e.endMs<=now;
export const isLive=(e,now)=>e.startMs<=now&&now<e.endMs;
const dayStart=ms=>Math.floor((ms+ASTANA_OFFSET_MS)/86400000)*86400000-ASTANA_OFFSET_MS;
const weekday=ms=>new Date(ms+ASTANA_OFFSET_MS).getUTCDay(); // 0 Sunday … 6 Saturday, Astana
// Half-open [from,to) window for a filter chip.
export function rangeFor(filter,now){
  const today=dayStart(now),day=86400000;
  if(filter==='today')return [today,today+day];
  if(filter==='tomorrow')return [today+day,today+2*day];
  if(filter==='weekend'){const w=weekday(now),from=w===0?today:w===6?today:today+(6-w)*day,to=w===0?today+day:today+(6-w+2)*day;return [from,to];}
  return [-Infinity,Infinity];
}
export function filterEvents(events,filter,now){
  const [from,to]=rangeFor(filter,now);
  return events.filter(e=>!isOver(e,now)&&e.startMs<to&&e.endMs>from);
}
const MONTHS=['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];
const clock=ms=>{const d=new Date(ms+ASTANA_OFFSET_MS);return `${String(d.getUTCHours()).padStart(2,'0')}:${String(d.getUTCMinutes()).padStart(2,'0')}`;};
export function formatWhen(e,now){
  if(isOver(e,now))return 'Завершено';
  if(isLive(e,now)){
    // Exhibitions run for weeks: "until 23:59" would be wrong, the closing day is what matters.
    if(e.hasEnd&&dayStart(e.endMs)>dayStart(now)){const d=new Date(e.endMs+ASTANA_OFFSET_MS);return `Идёт до ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;}
    return e.hasEnd?`Идёт сейчас · до ${clock(e.endMs)}`:'Идёт сейчас';
  }
  const days=Math.round((dayStart(e.startMs)-dayStart(now))/86400000),d=new Date(e.startMs+ASTANA_OFFSET_MS);
  const date=days===0?'Сегодня':days===1?'Завтра':`${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
  return `${date} · ${clock(e.startMs)}`;
}
// What the 3D engine receives: public scene data only, in its existing contract.
export function toCityEvent(e,now,covers={}){
  const cover=e.cover||covers[e.category]||'';
  return {id:e.id,lat:e.lat,lng:e.lng,title:e.title,category:CATEGORIES[e.category].engine,status:isLive(e,now)?'live':'soon',goingCount:0,
    kind:'planned',sourceType:'initiator',visibility:'public',startAt:new Date(e.startMs).toISOString(),endAt:new Date(e.endMs).toISOString(),
    ...(cover?{coverRef:cover}:{})};
}
export const isNight=now=>{const h=new Date(now+ASTANA_OFFSET_MS).getUTCHours();return h<8||h>=18;};
export function themeFor(now){
  const d=new Date(now+ASTANA_OFFSET_MS),m=d.getUTCMonth()+1,day=d.getUTCDate();
  if((m===12&&day>=20)||(m===1&&day<=10))return 'newyear';
  return m===12||m<=2?'winter':'default';
}
export const mapUrl=e=>`https://2gis.kz/astana?m=${e.lng.toFixed(6)}%2C${e.lat.toFixed(6)}%2F17`;
export function shareUrl(base,id){const u=new URL(base);u.search='';u.hash='';if(id)u.searchParams.set('event',id);return u.href;}
export function nearby(events,lat,lng,metres=150){
  const k=111320*Math.cos(lat*Math.PI/180);
  return events.filter(e=>Math.hypot((e.lng-lng)*k,(e.lat-lat)*111320)<=metres);
}
