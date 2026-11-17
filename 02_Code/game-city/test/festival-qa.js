import {festivalEvents} from './festival-fixtures.js';

if(new URLSearchParams(location.search).get('festival')==='1'&&['127.0.0.1','localhost','[::1]','::1'].includes(location.hostname)&&!window.EventMapNative&&!window.webkit?.messageHandlers?.EventMapNative){
  const panel=document.querySelector('#qa');panel.replaceChildren();panel.className='festival-toolbar';
  const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;};
  const heading=el('div','festival-heading');heading.append(el('strong','','ASTANA / ALL AT ONCE'),el('span','','ФЕСТИВАЛЬ · ВИЗУАЛЬНОЕ ДЕМО'));panel.append(heading);
  const line=el('output','','Загружаем сцену…'),actions=el('div','festival-actions');panel.append(line,actions);
  const all=festivalEvents(),joined=new Set();let events=all,dense=true,night=true,overlay=null,ready=false,benchmarkRevision=0,benchmarkRunning=false;
  const send=m=>window.EventMapMobile?.receive({v:1,...m});
  const sync=()=>{send({type:'events',preview:true,presentation:'bubbles',density:dense?'festival':'normal',events});line.textContent=`${events.length} вымышленных событий · обложки-иллюстрации · без записи в API`;};
  function close(){overlay?.remove();overlay=null;}
  function action(text,fn){const b=el('button','',text);b.type='button';b.onclick=fn;actions.append(b);return b;}
  action('Вся Астана',()=>{close();send({type:'focus',id:'city'});});
  action('В гуще событий',()=>{close();send({type:'focus',id:'baiterek'});});
  const nightButton=action('Ночь включена',()=>{night=!night;send({type:'appearance',night,theme:'default'});nightButton.textContent=night?'Ночь включена':'День включён';});nightButton.setAttribute('aria-label','День / ночь');
  const denseButton=action('Максимум событий',()=>{dense=!dense;denseButton.textContent=dense?'Максимум событий':'Спокойный режим';denseButton.setAttribute('aria-pressed',String(dense));sync();});denseButton.setAttribute('aria-pressed','true');
  const benchmarkButton=action('Прогон зума',()=>send({type:'benchmark',preview:true,benchmarkRevision:++benchmarkRevision,benchmarkCommand:benchmarkRunning?'stop':'start'}));
  const select=el('select');select.setAttribute('aria-label','Категории фестиваля');
  for(const [value,text]of [['','Все категории'],['Музыка','Музыка'],['Спорт','Спорт'],['Маркет','Еда и маркеты'],['Маршруты','Забеги и прогулки'],['Находки','Находки'],['Квесты','Квесты'],['Встречи','Встречи']]){const o=el('option','',text);o.value=value;select.append(o);}actions.append(select);
  select.onchange=()=>{close();events=all.filter(e=>!select.value||e.category===select.value);sync();};
  addEventListener('eventmap-message',event=>{
    if(event.detail?.type==='status'&&event.detail.state==='benchmark-progress'){benchmarkRunning=true;benchmarkButton.textContent='Стоп';line.textContent=event.detail.title;return;}
    if(event.detail?.type==='status'&&event.detail.state==='benchmark-result'){benchmarkRunning=false;benchmarkButton.textContent='Прогон зума';const r=event.detail.benchmark,t=r?.frameTiming;line.textContent=t?.fps?`${r.status} · ${t.fps.toFixed(1)} FPS · p95 ${t.p95Ms.toFixed(1)} мс · >50 мс: ${t.slowFrames} · ${r.events} демо-событий`:`Прогон не завершён: ${r?.reason}`;line.title=JSON.stringify(r);return;}
    if(!ready||event.detail?.type!=='event')return;
    const e=events.find(e=>e.id===event.detail.id);if(!e)return;close();
    overlay=el('div','journey-backdrop');const box=el('section','city-sheet journey-sheet festival-card');box.setAttribute('role','dialog');box.setAttribute('aria-label',e.title);
    const head=el('div','sheet-heading');head.append(el('small','','ВЫМЫШЛЕННОЕ СОБЫТИЕ'));const done=el('button','close-sheet','Закрыть');done.onclick=close;head.append(done);box.append(head);
    const img=el('img','festival-cover');img.src=e.coverImage;img.alt='Демонстрационная иллюстрация категории';box.append(img,el('h2','',e.title),el('p','muted',`${e.category} · ${e.status==='live'?'Идёт сейчас · пример':'Набор участников · пример'}`));
    const count=el('p','',''),join=el('button','demo-join');const update=()=>{count.textContent=`${e.goingCount} участников в сценарии · не реальные люди`;join.textContent=joined.has(e.id)?'Убрать из демо-плана':'Добавить в демо-план';};update();
    join.onclick=()=>{const has=joined.has(e.id);has?joined.delete(e.id):joined.add(e.id);e.goingCount+=has?-1:1;update();sync();};
    box.append(count,join,el('p','muted','Это нагрузочный показ карты. Событие, участники и место условные; запись только в памяти вкладки. Ничего не публикуется и не бронируется.'));overlay.append(box);document.body.append(overlay);done.focus();
  });
  addEventListener('keydown',e=>{if(e.key==='Escape')close();});
  const start=()=>{if(!window.EventMapMobile)return false;ready=true;sync();send({type:'appearance',night:true,theme:'default'});send({type:'focus',id:'city'});return true;};
  if(!start()){let attempts=0;const timer=setInterval(()=>{if(start()||++attempts>120){clearInterval(timer);if(!ready)line.textContent='Сцена не загрузилась — обнови страницу.';}},250);}
}
