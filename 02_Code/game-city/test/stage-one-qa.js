// Loopback-only fixtures, never included in mobile release assets or sent to API.
// Keep the local entrance contract aligned with parseCityMessage; no runtime/store changes.
function stageOnePlace(raw){
  try{
    const serialized=typeof raw==='string'?raw:JSON.stringify(raw);
    if(typeof serialized!=='string'||serialized.length>4096)return null;
    const input=JSON.parse(serialized);
    if(!input||Array.isArray(input)||input.v!==1||input.type!=='place')return null;
    const p={id:'',title:'',release:'',state:'',ids:[],address:'',addressStatus:'missing',addressSource:'',sourceRef:'',geometryKind:'point',entranceStatus:'unreviewed',entrances:[],...input};
    const text=(v,max)=>typeof v==='string'&&v.length<=max;
    const point=(lat,lng)=>typeof lat==='number'&&typeof lng==='number'&&Number.isFinite(lat)&&Number.isFinite(lng)&&lat>=50.9&&lat<=51.4&&lng>=71.1&&lng<=71.9;
    if(!text(p.title,120)||!p.title.trim()||!text(p.id,100)||!text(p.release,64)||!text(p.state,64)||!point(p.lat,p.lng))return null;
    if(!Array.isArray(p.ids)||p.ids.length>8||p.ids.some(id=>!text(id,100)||!id.trim())||new Set(p.ids).size!==p.ids.length)return null;
    if(!text(p.address,202)||!text(p.addressSource,40)||!text(p.sourceRef,80)||[p.address,p.addressSource,p.sourceRef].some(s=>/[\x00-\x1f\x7f]/.test(s)))return null;
    if(!['missing','source','ambiguous','unavailable'].includes(p.addressStatus)||!['point','footprint','stylized'].includes(p.geometryKind))return null;
    if(p.addressStatus==='source'&&(!p.address.trim()||!p.addressSource.trim()))return null;
    if(!['mapped','not_mapped','unreviewed','unavailable'].includes(p.entranceStatus)||!Array.isArray(p.entrances)||p.entrances.length>8)return null;
    if((p.entranceStatus==='mapped')!==(p.entrances.length>0))return null;
    if(p.entrances.some(e=>!e||!text(e.id,100)||!/^node\/[1-9][0-9]*$/.test(e.id)||!point(e.lat,e.lng)||!['main','yes','staircase','home','unknown'].includes(e.kind)||!['yes','no','limited','unknown'].includes(e.wheelchair)))return null;
    if(new Set(p.entrances.map(e=>e.id)).size!==p.entrances.length)return null;
    return p;
  }catch{return null;}
}
function stageOneCreationPoint(p,selectedEntranceId=null){
  const entrance=p.entranceStatus==='mapped'?p.entrances?.find(e=>e.id===selectedEntranceId):null;
  const title=p.title||'Точка на карте',address=p.address||'';
  const originalLabel=!address.trim()?title:['Здание','Точка на карте'].includes(title)?address:`${address} · ${title}`.slice(0,255);
  const note=entrance?` · вход OSM ${entrance.id}`:'';
  return {lat:entrance?.lat??p.lat,lng:entrance?.lng??p.lng,locationLabel:originalLabel.length+note.length<=255?originalLabel+note:originalLabel,entranceId:entrance?.id??null};
}
function stageOneSelectionLabel(point){
  return point.entranceId?`Выбран вход OSM ${point.entranceId}: ${point.lat}, ${point.lng}. Для события будут использованы эти координаты.`:'Вход не выбран: сохраняется исходная точка на карте, не подтверждённый вход. Уточни место встречи в описании.';
}
function stageOneEntranceLabel(e){
  const kind={main:'Главный вход',yes:'Вход',staircase:'Вход к лестнице',home:'Вход в жильё',unknown:'Тип входа неизвестен'};
  const wheelchair={yes:'да',no:'нет',limited:'ограниченно',unknown:'неизвестно'};
  return `${kind[e.kind]} · OSM ${e.id} · ${e.lat}, ${e.lng} · Коляска по данным OSM: ${wheelchair[e.wheelchair]}`;
}
const params=new URLSearchParams(location.search);
if(params.has('stage1')){
  const send=m=>window.EventMapMobile.receive({v:1,...m});
  const panel=document.querySelector('#qa');panel.replaceChildren();panel.className='journey-qa';
  const label=document.createElement('output');label.textContent='ЭТАП 1 · ДЕМО, БЕЗ GPS И ЗАПИСИ В API';panel.append(label);
  const later=new Date(Date.now()+3600000).toISOString(),end=new Date(Date.now()+7200000).toISOString();
  const point=(x,z)=>({lat:51.12822-z/111320,lng:71.43056+x/(111320*Math.cos(51.12822*Math.PI/180))});
  const fixture=(id,title,category,x,z,extra={})=>({id,title,category,...point(x,z),status:'soon',startAt:later,endAt:end,visibility:'public',goingCount:0,...extra});
  let events=[
    fixture('preview-football','Футбол · нужны двое','Спорт',-58,75,{kind:'gathering',capacity:6,goingCount:4}),
    fixture('preview-concert','Открытая сцена','Музыка',55,65,{status:'live',startAt:'',goingCount:28}),
    fixture('preview-market','Фермерский маркет','Маркет',-85,-20,{status:'live',startAt:''}),
    fixture('preview-telescope','Смотрим на звёзды','Находки',70,-45,{kind:'live_signal',sourceType:'witness',status:'live',startAt:'',confirmationCount:3}),
    fixture('preview-route','Утренняя прогулка','Маршруты',-25,-98,{kind:'gathering',capacity:12,goingCount:5}),
    fixture('preview-quest','Найди детали города','Квесты',115,85),
    {...fixture('preview-arena','Астана — Тобол · пример','Спорт',0,0,{status:'live',startAt:'',goingCount:120}),lat:51.10806,lng:71.4025},
    {...fixture('preview-expo','Музыка у EXPO · пример','Музыка',0,0,{status:'live',startAt:'',goingCount:18}),lat:51.08865,lng:71.41690},
    {...fixture('preview-khan','Маркет у Хан Шатыра · пример','Маркет',0,0,{status:'live',startAt:''}),lat:51.13100,lng:71.40420},
    {...fixture('preview-north','Пробежка · север города','Маршруты',0,0,{kind:'gathering',capacity:12,goingCount:5}),lat:51.205,lng:71.43},
    fixture('preview-private','НЕ ПОКАЗЫВАТЬ: закрытый день рождения','Встречи',20,20,{visibility:'private'}),
  ];
  let night=false,overlay=null,presentation=params.has('bubbles')?'bubbles':'models';const joined=new Set();
  const demoCovers={'Спорт':'sport','Музыка':'music','Маркет':'market'};
  for(const e of events)if(demoCovers[e.category])e.coverImage=`event-covers/${demoCovers[e.category]}.svg`;
  const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;};
  function toolbar(title,fn){const b=el('button','',title);b.onclick=fn;panel.append(b);}
  function sync(){send({type:'events',preview:true,presentation,events});}
  function close(){overlay?.querySelector('video')?.pause();overlay?.remove();overlay=null;}
  function sheet(title){close();overlay=el('div','journey-backdrop');const box=el('section','city-sheet journey-sheet');box.setAttribute('role','dialog');box.setAttribute('aria-label',title);
    const head=el('div','sheet-heading');head.append(el('small','','ЛОКАЛЬНОЕ ДЕМО'));const done=el('button','close-sheet','Закрыть');done.onclick=close;head.append(done);box.append(head,el('h2','',title));overlay.append(box);document.body.append(overlay);return box;}
  function action(box,title,fn){const b=el('button','demo-join',title);b.onclick=fn;box.append(b);return b;}
  function openEvent(e){
    const box=sheet(e.title);box.append(el('p','muted',`${e.category} · ${e.status==='live'?'Идёт сейчас':'Запланировано'} · тестовые данные`));
    if(e.locationLabel)box.append(el('p','muted',`${e.locationLabel} · ${e.lat}, ${e.lng}`));
    const count=el('p','','');const update=()=>{count.textContent=e.kind==='live_signal'?`Подтверждений: ${e.confirmationCount}`:`Планируют прийти: ${e.goingCount}${e.capacity?` из ${e.capacity}`:''}`;};update();box.append(count);
    box.append(el('p','muted',presentation==='bubbles'?'Шарик показывает обложку; линия привязана к месту события. Иллюстрации здесь тестовые, не реальные фотографии мероприятий.':'Миниатюра на карте обозначает тип события. Она не подтверждает реальную площадку, участников или бронь.'));
    const video=el('video');video.controls=true;video.muted=true;video.playsInline=true;video.preload='metadata';video.src='/demo.mp4';box.append(video,el('p','muted','Локальный тестовый ролик — не съёмка мероприятия.'));
    const join=action(box,joined.has(e.id)?'Отменить · демо':e.kind==='live_signal'?'Подтвердить · демо':'Присоединиться · демо',()=>{
      const has=joined.has(e.id);if(!has&&e.capacity&&e.goingCount>=e.capacity){count.textContent='Свободных мест нет · демо';return;}
      has?joined.delete(e.id):joined.add(e.id);const field=e.kind==='live_signal'?'confirmationCount':'goingCount';e[field]=(e[field]||0)+(has?-1:1);join.textContent=has?(e.kind==='live_signal'?'Подтвердить · демо':'Присоединиться · демо'):'Отменить · демо';update();sync();
    });
    action(box,'Завершить событие · демо',()=>{e.status='ended';sync();close();});
    box.append(el('p','muted','Изменения только в памяти этой вкладки. Нет записи в API, передачи местоположения и настоящих заявок.'));
  }
  function createAt(p,selectedEntranceId=null){
    const point=stageOneCreationPoint(p,selectedEntranceId);
    const box=sheet('Новое событие · демо'),form=el('form','demo-event-form');box.append(el('p','muted',`${point.locationLabel} · ${point.lat}, ${point.lng}`),el('p','muted',stageOneSelectionLabel(point)),form);
    const field=(title,control)=>{const label=el('label','',title);label.append(control);form.append(label);return control;};
    const name=field('Название',el('input'));name.name='title';name.required=true;name.maxLength=120;name.placeholder='Например, играем в футбол';
    const category=el('select');for(const t of ['Спорт','Музыка','Маркет','Находки','Маршруты','Квесты','Встречи']){const o=el('option','',t);o.value=t;category.append(o);}field('Категория',category);
    const kind=el('select');for(const [value,title]of [['planned','Запланировано'],['gathering','Собираю участников'],['live_signal','Сигнал: сейчас здесь']]){const o=el('option','',title);o.value=value;kind.append(o);}field('Формат',kind);
    const limit=field('Лимит участников',el('input'));limit.type='number';limit.min='2';limit.max='100000';limit.value='6';
    const submit=el('button','demo-join','Добавить на демо-карту');submit.type='submit';form.append(submit);
    let coverImage=null;
    const cover=el('input');cover.type='file';cover.accept='image/jpeg,image/png,image/webp';
    const coverLabel=el('label','','Обложка для шарика · необязательно');coverLabel.append(cover);form.insertBefore(coverLabel,submit);
    const coverNote=el('span','muted','Только локальный просмотр, без отправки на сервер. До 4 МБ.');coverLabel.append(coverNote);
    cover.onchange=async()=>{
      coverImage=null;const file=cover.files?.[0];if(!file)return;
      if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>4*1024*1024){coverNote.textContent='Нужна картинка JPG, PNG или WebP до 4 МБ.';return;}
      submit.disabled=true;
      try{const bitmap=await createImageBitmap(file);
        if(bitmap.width*bitmap.height>16000000){bitmap.close();throw new Error('Image too large');}
        const edge=Math.min(bitmap.width,bitmap.height),canvas=document.createElement('canvas');canvas.width=canvas.height=256;
        canvas.getContext('2d').drawImage(bitmap,(bitmap.width-edge)/2,(bitmap.height-edge)/2,edge,edge,0,0,256,256);bitmap.close();coverImage=canvas.toDataURL('image/jpeg',.8);
        coverNote.textContent='Обложка готова · останется только в этой вкладке.';
      }catch{coverNote.textContent='Не удалось прочитать изображение. Будет иконка категории.';}
      finally{submit.disabled=false;}
    };
    form.onsubmit=event=>{event.preventDefault();const title=name.value.trim();if(!title)return;
      const e={id:`preview-created-${Date.now()}`,title,category:category.value,lat:point.lat,lng:point.lng,locationLabel:point.locationLabel,kind:kind.value,sourceType:'initiator',status:kind.value==='live_signal'?'live':'soon',startAt:kind.value==='live_signal'?'':later,endAt:end,goingCount:0,confirmationCount:0,capacity:kind.value==='gathering'?Number(limit.value):null,visibility:'public'};
      if(coverImage)e.coverImage=coverImage;events.push(e);sync();close();label.textContent='СОЗДАНО В ДЕМО · НЕ ОПУБЛИКОВАНО В API';
    };
    box.append(el('p','muted','Это проверка карты: событие исчезнет после перезагрузки. В мобильном приложении здесь открывается настоящая форма публикации.'));
  }
  function openPlace(p){
    const box=sheet(p.title);let selectedEntranceId=null;
    box.append(el('p','place-address',p.addressStatus==='source'?`${p.address} · источник: ${p.addressSource}`:p.addressStatus==='ambiguous'?'У здания несколько адресов — уточни вход':p.addressStatus==='unavailable'?'Адресные данные недоступны':'Адрес не найден в данных карты'));
    if(p.id)box.append(el('p','muted',`Объект: ${p.id}`));
    if(p.sourceRef)box.append(el('p','muted',`Источник объекта: ${p.sourceRef}`));
    if(p.geometryKind==='stylized')box.append(el('p','muted','Стилизованная модель, не точная копия здания'));
    box.append(el('p','muted',`Исходная точка: ${p.lat}, ${p.lng}`),el('h3','','Входы'));
    const status={
      mapped:'Входы отмечены в OpenStreetMap (OSM). Это не официальные данные и не результат обследования на месте; общедоступность и доступность для коляски не гарантированы.',
      not_mapped:'Входы не отмечены в данных OSM. Это не означает, что входов нет.',
      unreviewed:'Данные о входах не проверены. Подтверждённого входа здесь нет.',
      unavailable:'Данные о входах сейчас недоступны. Подтверждённого входа здесь нет.',
    };
    box.append(el('p','muted',status[p.entranceStatus]));
    const summary=el('p','muted',stageOneSelectionLabel(stageOneCreationPoint(p)));summary.setAttribute('aria-live','polite');
    const buttons=[];let reset=null;
    const select=id=>{selectedEntranceId=id;for(const [e,b]of buttons){b.setAttribute('aria-pressed',String(e.id===id));b.textContent=`${e.id===id?'Выбран':'Выбрать'} · ${stageOneEntranceLabel(e)}`;}if(reset)reset.disabled=id===null;summary.textContent=stageOneSelectionLabel(stageOneCreationPoint(p,id));};
    for(const entrance of p.entrances){const b=action(box,`Выбрать · ${stageOneEntranceLabel(entrance)}`,()=>select(entrance.id));b.setAttribute('aria-pressed','false');buttons.push([entrance,b]);}
    if(buttons.length){reset=action(box,'Сбросить выбор входа',()=>select(null));reset.disabled=true;}
    box.append(summary,el('p','muted','Выбор места не означает разрешение на мероприятие или бронирование.'));
    action(box,'Создать здесь · демо',()=>createAt(p,selectedEntranceId));
  }
  toolbar('Вся Астана',()=>{close();send({type:'focus',id:'city'});});toolbar('Байтерек',()=>{close();send({type:'focus',id:'baiterek'});});toolbar('Арена',()=>{close();send({type:'focus',id:'arena'});});
  toolbar('День / ночь',()=>{night=!night;send({type:'appearance',night,theme:'default'});});
  toolbar('Создать · демо',()=>createAt({...point(-35,30),title:'Демо-точка на площади'}));
  toolbar('Шарики / 3D',()=>{close();presentation=presentation==='bubbles'?'models':'bubbles';sync();});
  addEventListener('eventmap-message',event=>{const m=event.detail;if(m?.type==='event'){const e=events.find(e=>e.id===m.id);if(e&&e.visibility==='public')openEvent(e);}else if(m?.type==='place'){const p=stageOnePlace(m);if(p)openPlace(p);}});
  sync();
}
