'use strict';
(() => {
  const $=id=>document.getElementById(id), admin=window.EventMapAdmin;
  const paths={building:'M4 21V7l8-4 8 4v14M9 21v-5h6v5M8 8h1m6 0h1M8 12h1m6 0h1M2 21h20',briefcase:'M8 6V4h8v2M3 7h18v13H3zM3 12c6 4 12 4 18 0M10 12h4v3h-4z',route:'M6 5a2 2 0 1 0 0 .1M18 19a2 2 0 1 0 0 .1M8 5h8a4 4 0 0 1 0 8H8a3 3 0 0 0 0 6h8',help:'M9 9a3 3 0 0 1 6 0c0 2-3 2-3 4M12 17h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',lock:'M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5zM12 14v3',logout:'M9 3H4v18h5M10 12h11m-4-4 4 4-4 4',arrow:'M5 12h14m-5-5 5 5-5 5',back:'M19 12H5m5-5-5 5 5 5',search:'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',refresh:'M20 7a9 9 0 1 0 1 8M20 2v5h-5',layers:'m3 7 9-4 9 4-9 4-9-4m0 5 9 4 9-4M3 17l9 4 9-4',plus:'M12 5v14M5 12h14',eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12m13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0',upload:'M12 16V3m-4 4 4-4 4 4M4 15v6h16v-6',pen:'m15 3 6 6-12 12H3v-6L15 3m-3 3 6 6',undo:'M4 4v6h6M4 10a8 8 0 1 1 1 8',sliders:'M5 3v6m0 4v8M12 3v11m0 4v3M19 3v3m0 4v11M2 9h6M9 14h6M16 6h6',shield:'m12 2 9 4v6c0 6-9 10-9 10S3 18 3 12V6l9-4m-5 10 3 3 7-7',close:'m6 6 12 12M18 6 6 18',calendar:'M4 5h16v16H4zM4 10h16M8 3v4m8-4v4'};
  function paint(root=document){for(const el of root.querySelectorAll('[data-icon]')){const path=paths[el.dataset.icon];if(!path)continue;const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');const p=document.createElementNS(svg.namespaceURI,'path');p.setAttribute('d',path);svg.append(p);el.replaceChildren(svg);}}
  window.EventMapIcons=Object.freeze({paint});paint();$('serverHost').textContent=location.host;
  // Remove the legacy console's persisted shared credential; never read it back.
  try{sessionStorage.removeItem('adminKey');}catch(_){}
  let page=location.pathname==='/admin'?'business':'venues',status='',rows=[],generation=0,review=null,reviewing=false;
  const names={venues:'Здания и планы',business:'Организации',quests:'Квесты',events:'Предложения событий'};
  const statuses=()=>page==='business'?[['pending','На проверке'],['approved','Одобрены'],['rejected','Отклонены']]:page==='events'?[['pending','На проверке'],['published','Опубликованы'],['rejected','Отклонены']]:[['moderation','На проверке'],['active','Активные'],['rejected','Отклонены']];
  const pendingStatus=()=>page==='quests'?'moderation':'pending';
  const api={business:'/admin/api/business',quests:'/admin/api/quests',events:'/admin/api/events'},reviewPath={business:'/admin/business/',quests:'/admin/quests/',events:'/admin/events/'};
  const intro={business:['АККРЕДИТАЦИЯ','Проверьте сведения и документы перед одобрением заявки.'],quests:['ГОРОДСКИЕ АКТИВНОСТИ','Проверьте точки, условия прохождения и награду перед публикацией.'],events:['СОБЫТИЯ ГОРОДА','Предложения гостей без аккаунта. На карту попадает только то, что вы опубликуете.']};
  const categories={sport:'Спорт',music:'Музыка',market:'Маркеты и еда',discovery:'Находки',route:'Забеги и прогулки',quest:'Игры и квесты',community:'Встречи',newyear:'Новый год',kids:'Детям',art:'Выставки',theatre:'Театр и кино',education:'Лекции и мастер-классы'};
  // Astana is UTC+5 all year: the moderator sees the time the guest meant, whatever the browser's zone.
  function astana(iso){const t=Date.parse(iso);if(!Number.isFinite(t))return '';const d=new Date(t+5*3600000),p=n=>String(n).padStart(2,'0');return p(d.getUTCDate())+'.'+p(d.getUTCMonth()+1)+'.'+d.getUTCFullYear()+' '+p(d.getUTCHours())+':'+p(d.getUTCMinutes());}
  function node(tag,text,cls){const el=document.createElement(tag);if(text!==undefined)el.textContent=String(text??'');if(cls)el.className=cls;return el;}
  function changePage(next,push=true){
    if(!(next in names))return;page=next;generation++;rows=[];status=pendingStatus();$('moderationSearch').value='';$('notice').hidden=true;
    document.querySelectorAll('[data-page]').forEach(el=>{el.classList.toggle('selected',el.dataset.page===page);if(el.dataset.page===page)el.setAttribute('aria-current','page');else el.removeAttribute('aria-current');});
    $('sectionLabel').textContent=names[page];$('venuesPage').hidden=page!=='venues';$('moderationPage').hidden=page==='venues';
    if(push)history.pushState({page},'',page==='venues'?'/admin/venues':'/admin#'+page);
    if(page!=='venues'){
      $('moderationEyebrow').textContent=intro[page][0];$('moderationTitle').textContent=names[page];$('moderationDescription').textContent=intro[page][1];
      renderTabs();if(admin.connected())load();
    }
  }
  function renderTabs(){
    $('statusTabs').replaceChildren();for(const [value,label]of statuses()){const b=node('button',label,status===value?'selected':'');b.setAttribute('aria-pressed',String(status===value));b.onclick=()=>{status=value;renderTabs();load();};$('statusTabs').append(b);}
  }
  async function load(){
    if(!admin.connected())return;const current=++generation,kind=page,selected=status;
    $('moderationCount').textContent='';$('moderationList').replaceChildren(node('p','Загружаем данные с сервера…','empty-state'));$('refreshModeration').disabled=true;
    try{const data=await admin.request(api[kind]+'?status='+encodeURIComponent(selected));if(current!==generation)return;if(!Array.isArray(data))throw new Error('Неподдерживаемый ответ сервера');rows=data;renderRows();}
    catch(error){if(current!==generation)return;rows=[];const box=node('div',undefined,'unavailable');box.append(node('h2','Не удалось загрузить данные'),node('p',error.message));$('moderationList').replaceChildren(box);}
    finally{if(current===generation)$('refreshModeration').disabled=false;}
  }
  function detail(dl,label,value){const item=node('div');item.append(node('dt',label),node('dd',value||'Не указано'));dl.append(item);}
  function documentLink(url,label){try{const parsed=new URL(url);if(parsed.protocol!=='https:')return node('span','Неподдерживаемая ссылка на документ','muted');const a=node('a',label);a.href=parsed.href;a.target='_blank';a.rel='noopener noreferrer';a.referrerPolicy='no-referrer';return a;}catch(_){return node('span','Некорректный адрес документа','muted');}}
  function renderRows(){
    const query=$('moderationSearch').value.trim().toLocaleLowerCase('ru-RU'),visible=rows.filter(row=>[row.brandName,row.legalName,row.bin,row.title,row.businessBrand,row.description,row.location?.name].filter(Boolean).join(' ').toLocaleLowerCase('ru-RU').includes(query));
    $('moderationCount').textContent='Показано '+visible.length+' из '+rows.length;$('moderationList').replaceChildren();
    if(!visible.length){$('moderationList').append(node('p',query?'По запросу ничего не найдено.':'В этой очереди пока нет заявок.','empty-state'));return;}
    for(const row of visible){
      const card=node('article',undefined,'moderation-card'),head=node('div',undefined,'card-title');head.append(node('h2',page==='business'?row.brandName:row.title),node('span',statuses().find(s=>s[0]===status)?.[1]||status,'badge '+(status==='approved'||status==='active'||status==='published'?'success':'neutral')));card.append(head);
      if(page==='business'){
        card.append(node('p',row.legalName,'card-meta'));const dl=node('dl',undefined,'details-grid');detail(dl,'БИН',row.bin);detail(dl,'Категория',row.category);detail(dl,'Телефон',row.phone);detail(dl,'Соцсеть / сайт',row.social);card.append(dl);
        const docs=node('div',undefined,'documents');for(const [i,doc]of(row.documents||[]).entries())docs.append(documentLink(doc.fileUrl,doc.kind||'Документ '+(i+1)));if(!docs.children.length)docs.append(node('p','Документы не приложены','muted'));card.append(docs);if(row.rejectReason)card.append(node('p','Причина отказа: '+row.rejectReason,'card-description'));
      }else if(page==='events'){
        card.append(node('p',[categories[row.category]||row.category,row.sourceType==='guest'?'от гостя без аккаунта':row.sourceType].filter(Boolean).join(' · '),'card-meta'));
        const dl=node('dl',undefined,'details-grid'),loc=row.location||{};detail(dl,'Когда',[astana(row.startAt),row.endAt?'— '+astana(row.endAt):''].filter(Boolean).join(' ')+' (Астана)');
        detail(dl,'Место',[loc.name,Number.isFinite(loc.lat)&&Number.isFinite(loc.lng)?loc.lat.toFixed(5)+', '+loc.lng.toFixed(5):''].filter(Boolean).join(' · '));
        detail(dl,'Цена',row.price?.type==='paid'?row.price.amount+' ₸':'Бесплатно');detail(dl,'Здание на карте',row.buildingId);detail(dl,'Контакт (только для модератора)',row.contact);detail(dl,'Поступило',row.createdAtMs?astana(new Date(row.createdAtMs).toISOString()):'');card.append(dl);
        if(row.sourceUrl){const docs=node('div',undefined,'documents');docs.append(row.sourceUrl.startsWith('https:')?documentLink(row.sourceUrl,'Источник: '+row.sourceUrl):node('span','Источник (без https, откройте вручную): '+row.sourceUrl,'muted'));card.append(docs);}
        if(row.description)card.append(node('p',row.description,'card-description'));if(row.rejectReason)card.append(node('p','Причина отказа: '+row.rejectReason,'card-description'));
      }else{
        card.append(node('p','Организатор: '+row.businessBrand,'card-meta'));const dl=node('dl',undefined,'details-grid');detail(dl,'Награда',row.prizeTitle);detail(dl,'Завершение',row.endsAt);detail(dl,'Прохождение',row.orderedChain?'По порядку':'В любом порядке');card.append(dl);
        const points=node('ol',undefined,'quest-points');for(const point of row.points||[]){const condition={geo:'Посещение',photo:'Фото',purchase:'Покупка'}[point.condition]||point.condition;const location=point.placeName||[point.lat,point.lng].filter(Number.isFinite).map(n=>n.toFixed(5)).join(', ');points.append(node('li',location+' · '+condition));}card.append(points);
      }
      if(status===pendingStatus()){const actions=node('div',undefined,'review-actions');for(const [approved,label]of[[false,'Отклонить'],[true,page==='events'?'Опубликовать':'Одобрить']]){const b=node('button',label,approved?'primary':'secondary');b.onclick=()=>showReview(row,approved);actions.append(b);}card.append(actions);}
      $('moderationList').append(card);
    }
  }
  function showReview(row,approved){
    const noun={business:'организацию',quests:'квест',events:'событие'}[page];
    review={id:row.id,kind:page,approved};$('reviewTitle').textContent=(approved?(page==='events'?'Опубликовать':'Одобрить'):'Отклонить')+' '+noun+'?';$('reviewDescription').textContent=(page==='business'?row.brandName:row.title)+(approved?(page==='events'?' — событие появится на карте.':' — решение будет сохранено на сервере.'):' — заявка будет отклонена.');$('reasonLabel').hidden=page==='quests'||approved;$('reviewReason').value='';$('reviewError').hidden=true;$('submitReview').textContent=approved?(page==='events'?'Опубликовать':'Одобрить'):'Отклонить';$('reviewDialog').showModal();
  }
  $('submitReview').onclick=async()=>{
    if(reviewing||!review)return;const action={...review},reason=$('reviewReason').value.trim();if(!action.approved&&action.kind==='business'&&!reason){$('reviewError').hidden=false;$('reviewError').textContent='Укажите причину отказа для заявителя.';$('reviewReason').focus();return;}
    reviewing=true;$('submitReview').disabled=true;$('cancelReview').disabled=true;$('reviewError').hidden=true;
    try{const body={decision:action.approved?'approved':'rejected'};if(!action.approved&&action.kind!=='quests'&&reason)body.reason=reason;await admin.request(reviewPath[action.kind]+encodeURIComponent(action.id)+'/review','POST',body);$('reviewDialog').close();review=null;admin.notice('Решение сохранено на сервере.');await load();}
    catch(error){$('reviewError').hidden=false;$('reviewError').textContent=error.message;}
    finally{reviewing=false;$('submitReview').disabled=false;$('cancelReview').disabled=false;}
  };
  $('cancelReview').onclick=()=>{if(!reviewing){$('reviewDialog').close();review=null;}};$('reviewDialog').addEventListener('cancel',event=>{if(reviewing)event.preventDefault();});
  $('refreshModeration').onclick=load;$('moderationSearch').oninput=renderRows;
  document.querySelectorAll('[data-page]').forEach(button=>button.onclick=()=>changePage(button.dataset.page));
  window.addEventListener('admin-connected',()=>{if(page!=='venues')load();});window.addEventListener('admin-disconnected',()=>{generation++;rows=[];$('moderationList').replaceChildren();if(!reviewing)$('reviewDialog').close();});
  const fromHash=()=>location.hash==='#quests'?'quests':location.hash==='#events'?'events':null;
  window.addEventListener('popstate',()=>changePage(location.pathname==='/admin/venues'?'venues':fromHash()||'business',false));
  $('help').onclick=()=>$('helpDialog').showModal();$('closeHelp').onclick=()=>$('helpDialog').close();
  changePage(fromHash()||page,false);
})();
