// Presentation only. Categories never imply attendance, venue approval or safety.
export const EVENT_STYLES=Object.freeze({
  sport:{label:'Спорт',color:'#74c4a4',model:'sport',hint:'Поле и мяч'},
  music:{label:'Музыка',color:'#bca0e8',model:'music',hint:'Сцена и мягкий свет'},
  market:{label:'Еда и маркеты',color:'#e5b371',model:'market',hint:'Небольшой навес'},
  discovery:{label:'Находки',color:'#7ebfd2',model:'discovery',hint:'Телескоп и кольцо сигнала'},
  route:{label:'Маршруты',color:'#e4a593',model:'route',hint:'Стартовая арка'},
  quest:{label:'Игры и квесты',color:'#a4bfe8',model:'quest',hint:'Флажок задания'},
  community:{label:'Встречи',color:'#d7bfa1',model:'community',hint:'Условные фигурки, не число людей'},
});
const aliases={sport:'sport',sports:'sport','спорт':'sport','футбол':'sport',football:'sport',
  music:'music',concert:'music','музыка':'music','концерт':'music','концерты':'music','вечеринки':'music',
  food:'market',market:'market','еда':'market','маркет':'market','маркеты':'market','торговля':'market','ярмарка':'market',
  discovery:'discovery','находки':'discovery','наблюдение':'discovery','сигнал':'discovery',
  route:'route',marathon:'route','маршруты':'route','марафон':'route','забег':'route','прогулки':'route',
  quest:'quest',games:'quest','квест':'quest','квесты':'quest','игры':'quest'};
export function eventStyle(e){const key=aliases[String(e?.category||'').trim().toLowerCase()]||(e?.kind==='live_signal'?'discovery':'community');return {key,...EVENT_STYLES[key]};}
export function eventState(e,now=Date.now()){
  const end=Date.parse(e.endAt||'');
  if(['ended','cancelled'].includes(e.status)||(Number.isFinite(end)&&end<=now)||(Number.isFinite(e.expiresAtMs)&&e.expiresAtMs<=now))return 'ended';
  const start=Date.parse(e.startAt||'');
  if(Number.isFinite(start)&&start>now)return e.kind==='gathering'?'gathering':'planned';
  if(e.status==='live')return e.kind==='live_signal'?'signal':'live';
  if(e.kind==='gathering')return 'gathering';
  return 'planned';
}
export function eventSummary(e,now=Date.now()){
  const state=eventState(e,now),capacity=Number.isSafeInteger(e.capacity)&&e.capacity>0?e.capacity:null;
  const remaining=capacity===null?null:Math.max(0,capacity-(e.goingCount||0));
  if(state==='ended')return {state,label:'Завершено',animate:false,remaining};
  if(state==='signal')return {state,label:e.confirmationCount>0?`Сигнал · подтверждений: ${e.confirmationCount}`:'Сигнал · без подтверждений',animate:true,remaining};
  if(state==='live')return {state,label:'Идёт сейчас',animate:true,remaining};
  if(state==='gathering')return {state,label:remaining===null?'Набор участников':remaining?`Нужно ещё ${remaining}`:'Состав набран',animate:false,remaining};
  return {state,label:'Запланировано',animate:false,remaining};
}
export function canAnimateEvent(e,{distance=0,reduced=false,blocked=false,visible=true,index=0,quality='balanced',now=Date.now()}={}){
  return !reduced&&!blocked&&visible&&Number.isFinite(distance)&&distance<850&&index<(quality==='low'?2:4)&&eventSummary(e,now).animate;
}
export function isMapTap(down,up){return !!down&&!!up&&up.t>=down.t&&up.t-down.t<=600&&Math.hypot(up.x-down.x,up.y-down.y)<=8;}
