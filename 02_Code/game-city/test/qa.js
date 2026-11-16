const panel=document.createElement('div');panel.id='qa';document.body.append(panel);
const style=document.createElement('link');style.rel='stylesheet';style.href='mobile.css'; // existing stylesheet includes QA-only selectors
const line=document.createElement('output');panel.append(line);
function button(text,click){const b=document.createElement('button');b.textContent=text;b.onclick=click;panel.append(b);}
const send=m=>window.EventMapMobile.receive({v:1,...m});
if(new URLSearchParams(location.search).has('stage1')){
  // The isolated stage-one fixtures are loaded by the loopback server only.
}else if(new URLSearchParams(location.search).has('journey')){
  panel.classList.add('journey-qa');line.textContent='ДЕМО · без GPS и записи в API';
  const event={id:'preview-football',lat:51.12755,lng:71.42972,title:'Футбол · не хватает двоих',category:'Спорт',status:'soon',goingCount:4,kind:'gathering',capacity:6};
  let joined=false;
  const show=()=>send({type:'events',preview:true,events:[{...event,goingCount:joined?5:4}]});
  button('Демо-встреча',show);button('День / ночь',()=>{night=!night;send({type:'appearance',night,theme:'default'});});
  addEventListener('eventmap-message',e=>{
    if(e.detail.type!=='event'||e.detail.id!==event.id)return;
    const sheet=document.createElement('section');sheet.className='city-sheet journey-sheet';
    sheet.innerHTML='<div class="sheet-heading"><small>ДЕМО · НЕ НАСТОЯЩАЯ ВСТРЕЧА</small><button class="close-sheet">Закрыть</button></div><h2>Футбол. Не хватает двоих</h2><p class="muted">Демо-площадка у Байтерека · 19:00–20:00<br>Любительская игра · бесплатно · до 6 человек</p><p class="participants"></p><p class="muted">Данияр · Алия · Илья · Санжар — вымышленные участники.</p><video controls playsinline muted preload="metadata" src="/demo.mp4"></video><p class="muted">Локальный графический ролик для теста плеера, не съёмка события.</p><button class="demo-join"></button><p class="muted">Участие меняется только в этом примере. Никаких заявок и передачи местоположения.</p>';
    const update=()=>{sheet.querySelector('.participants').textContent=joined?'5 планируют прийти · ты в демо-списке':'4 планируют прийти · 2 места';sheet.querySelector('.demo-join').textContent=joined?'Отменить участие · демо':'Присоединиться · демо';};update();
    sheet.querySelector('.close-sheet').onclick=()=>{sheet.querySelector('video').pause();sheet.remove();};
    sheet.querySelector('.demo-join').onclick=()=>{joined=!joined;update();show();};document.body.append(sheet);
  });
  show();
}else{
button('Байтерек',()=>send({type:'focus',id:'baiterek'}));button('Ак Орда',()=>send({type:'focus',id:'akorda'}));button('Арена',()=>send({type:'focus',id:'arena'}));
button('День / ночь',()=>{night=!night;send({type:'appearance',night,theme:'default'})});
for(const r of ['pilot-1','pilot-2','offline','incompatible'])button(r,async()=>{await fetch('/__qa/release?value='+r,{method:'POST'});send({type:'update'});});
addEventListener('eventmap-message',e=>{line.textContent=JSON.stringify(e.detail);});
}
let night=false;
setInterval(()=>{const s=window.EventMapMobile?.stats();panel.title=JSON.stringify(s);},2000);
