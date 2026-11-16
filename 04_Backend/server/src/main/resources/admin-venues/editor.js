'use strict';
(() => {
  const $=id=>document.getElementById(id), clone=x=>JSON.parse(JSON.stringify(x));
  let key='', state=null, draft=null, fi=-1, ri=-1, dirty=false, busy=false, drawing=false, points=[], underlay=null, catalog=[];
  const floor=()=>draft?.floors[fi], room=()=>floor()?.rooms[ri];
  const publishedFloor=()=>state?.published?.floors.find(f=>f.id===floor()?.id);
  const publishedRoom=()=>publishedFloor()?.rooms.find(r=>r.id===room()?.id);
  const notice=(message,error=false)=>{ $('notice').hidden=false;$('notice').textContent=message;$('notice').classList.toggle('error',error); };
  function session(connected){document.body.dataset.auth=connected?'on':'off';$('login').hidden=connected;$('authenticated').hidden=!connected;$('connectionDot').classList.toggle('online',connected);$('connectionLabel').textContent=connected?'Подключено к серверу':'Нет подключения';window.dispatchEvent(new Event(connected?'admin-connected':'admin-disconnected'));}
  function validationErrors(value=draft){
    const errors=[], text=v=>typeof v==='string'&&v.trim().length>0&&v.length<=120&&!/[\u0000-\u001f\u007f]/.test(v), id=v=>typeof v==='string'&&/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/.test(v);
    if(!value||value.schemaVersion!==1||value.buildingId!==draft?.buildingId||!text(value.name)||!Array.isArray(value.floors))return ['Укажите название здания и корректную схему версии 1.'];
    if(value.floors.length>32)errors.push('Не более 32 этажей.');
    let totalRooms=0,vertices=0;const floorIds=new Set();
    for(const f of value.floors){
      if(!f||!id(f.id)||!text(f.name)||!Number.isInteger(f.order)||f.order< -100||f.order>200||!Array.isArray(f.rooms)){errors.push('Проверьте название, ID и порядок каждого этажа.');continue;}
      if(floorIds.has(f.id))errors.push('ID этажей должны быть уникальными.');floorIds.add(f.id);totalRooms+=f.rooms.length;
      if(f.rooms.length>200)errors.push('Не более 200 помещений на этаж.');
      const p=f.plan, goodPlan=p&&Number.isFinite(p.width)&&Number.isFinite(p.height)&&p.width>=1&&p.width<=10000&&p.height>=1&&p.height<=10000;
      if(p&&!goodPlan)errors.push(f.name+': размеры схемы должны быть от 1 до 10000.');
      const polygon=(points,label)=>{if(!Array.isArray(points)){errors.push(label+': некорректный контур.');return;}vertices+=points.length;if(!points.length)return;
        if(!goodPlan||points.length<3||points.length>256||points.some(a=>!a||!Number.isFinite(a.x)||!Number.isFinite(a.y)||a.x<0||a.y<0||a.x>p.width||a.y>p.height)){errors.push(label+': контур должен содержать 3–256 точек внутри схемы.');return;}
        const area=Math.abs(points.reduce((n,a,i)=>{const b=points[(i+1)%points.length];return n+a.x*b.y-b.x*a.y;},0));if(area<=.0001)errors.push(label+': контур должен иметь ненулевую площадь.');};
      if(p)polygon(p.outline||[],f.name);const roomIds=new Set();
      for(const r of f.rooms){if(!r||!id(r.id)||!text(r.name)){errors.push(f.name+': проверьте названия и ID помещений.');continue;}if(roomIds.has(r.id))errors.push(f.name+': ID помещений повторяются.');roomIds.add(r.id);polygon(r.polygon||[],r.name);}
    }
    if(totalRooms>1000||vertices>20000)errors.push('Лимит здания: 1000 помещений и 20 000 точек.');
    if(new TextEncoder().encode(JSON.stringify(value)).length>1000000)errors.push('Схема превышает допустимый размер 1 MB.');
    return [...new Set(errors)].slice(0,12);
  }
  function showValidation(){const errors=validationErrors();$('validation').hidden=!errors.length;$('validation').replaceChildren();if(errors.length){const title=document.createElement('strong');title.textContent='Исправьте перед сохранением';const list=document.createElement('ul');for(const message of errors){const item=document.createElement('li');item.textContent=message;list.append(item);}$('validation').append(title,list);}return errors;}
  function controls(){
    for(const el of document.querySelectorAll('#workspace button,#workspace input'))el.disabled=busy;
    for(const el of document.querySelectorAll('#workspace .file'))el.classList.toggle('disabled',busy);
    for(const id of ['open','connect','logout','buildings','refreshCatalog'])$(id).disabled=busy || (['buildings','refreshCatalog'].includes(id)&&!key);
    if(!draft)return;
    const invalid=showValidation().length>0;
    $('save').disabled=busy||!dirty||drawing||invalid;
    const different=JSON.stringify({...draft,revision:''})!==JSON.stringify({...state.published,revision:''});
    $('publish').disabled=busy||dirty||state.version===0||!different||drawing||invalid;
    $('floorId').disabled=busy||!!publishedFloor();$('roomId').disabled=busy||!!publishedRoom();
    $('deleteFloor').disabled=busy||!!publishedFloor();$('deleteRoom').disabled=busy||!!publishedRoom();
    for(const id of ['draw','clearPolygon'])$(id).disabled=busy||!room()||!floor()?.plan;
    $('finish').disabled=busy||!drawing||points.length<3;$('undo').disabled=busy||!drawing||!points.length;$('cancelDraw').disabled=busy||!drawing;
    $('enablePlan').disabled=busy||!floor()||!!floor()?.plan;
    for(const id of ['width','height','underlay','removePlan'])$(id).disabled=busy||!floor()?.plan;
    $('addFloor').disabled=busy||drawing||draft.floors.length>=32;
    $('addRoom').disabled=busy||drawing||!floor()||floor().rooms.length>=200;
    $('inspectorEmpty').hidden=!!floor();
    $('finish').hidden=!drawing;$('undo').hidden=!drawing;$('cancelDraw').hidden=!drawing;
    $('enablePlan').hidden=!!floor()?.plan;$('draw').hidden=drawing;
    const published=state.history.length>0;
    $('documentStatus').textContent=dirty?'Не сохранено':different&&state.version>0?'Черновик':published?'Опубликовано':'Без плана';
    $('documentStatus').className='badge '+(dirty||different&&state.version>0?'warning':published?'success':'neutral');
  }
  async function request(path,method='GET',body){
    if(!path.startsWith('/admin/'))throw new Error('Недопустимый адрес запроса');
    const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),15000);
    try {
      const response=await fetch(path,{method,headers:{'X-Admin-Key':key,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',credentials:'omit',signal:controller.signal});
      if(response.status===403){key='';session(false);throw new Error('Ключ отклонён сервером. Войдите снова; несохранённый план остаётся в памяти этой вкладки.');}
      const data=await response.json().catch(()=>null);
      if(!response.ok)throw new Error(data?.error?.message||(response.status===404?'Этот раздел недоступен на подключённом сервере. Требуется обновить backend.':'Ошибка сервера HTTP '+response.status));
      if(data===null)throw new Error('Сервер вернул неподдерживаемый ответ');
      return data;
    } catch(e) { if(e.name==='AbortError')throw new Error('Нет ответа. Результат записи неизвестен: сначала перезагрузите состояние, не повторяйте публикацию вслепую.');throw e; }
    finally { clearTimeout(timer); }
  }
  const api=(path,method='GET',body)=>request('/admin/api/venues'+path,method,body);
  async function task(fn){if(busy)return;busy=true;controls();try{await fn()}catch(e){notice(e.message,true)}finally{busy=false;controls()}}
  function releaseUnderlay(){if(underlay)URL.revokeObjectURL(underlay);underlay=null;$('underlay').value='';}
  function ask(message){return new Promise(resolve=>{const dialog=$('confirmation');$('confirmText').textContent=message;const done=value=>{dialog.close();resolve(value);};$('confirmYes').onclick=()=>done(true);$('confirmNo').onclick=()=>done(false);dialog.oncancel=event=>{event.preventDefault();done(false)};dialog.showModal();});}
  async function discard(){return !dirty&&!drawing || await ask('В форме есть несохранённые изменения. Отменить их? При необходимости сначала экспортируйте JSON.');}
  function selectState(next){state=next;draft=clone(next.draft);dirty=false;drawing=false;points=[];fi=draft.floors.length?0:-1;ri=-1;releaseUnderlay();render();}
  function changed(){dirty=true;$('revision').textContent='Есть несохранённые изменения · версия редактирования '+state.version;draft.floors.forEach((f,i)=>{const button=$('floors').children[i];if(button?.firstChild)button.firstChild.textContent=f.name;});floor()?.rooms.forEach((r,i)=>{const button=$('rooms').children[i];if(button?.firstChild)button.firstChild.textContent=(i+1)+'. '+r.name;});controls();}
  const uid=prefix=>prefix+'-'+crypto.randomUUID().slice(0,8);
  function listButton(parent,title,subtitle,selected,action){const b=document.createElement('button');b.className='list-item'+(selected?' selected':'');b.type='button';b.textContent=title;const sub=document.createElement('small');sub.textContent=subtitle;b.append(sub);b.addEventListener('click',action);parent.append(b);}
  function render(){
    $('workspace').hidden=!draft;$('catalog').hidden=!!draft;if(!draft)return;
    $('buildingId').value=draft.buildingId;$('buildingName').value=draft.name;
    $('buildings').value=draft.buildingId;
    $('revision').textContent=state.history.length?'Последняя публикация: '+new Date(state.history[0].publishedAtMs).toLocaleString('ru-RU'):'В приложении пока нет опубликованной схемы';
    $('floors').replaceChildren();$('noFloors').hidden=!!draft.floors.length;
    draft.floors.forEach((f,i)=>listButton($('floors'),f.name,f.rooms.length+' помещений'+(f.plan?' · схема':' · список'),i===fi,()=>{if(drawing){notice('Завершите или отмените контур перед сменой этажа.',true);return;}fi=i;ri=-1;releaseUnderlay();render()}));
    $('history').replaceChildren();
    if(!state.history.length)$('history').textContent='Публикаций ещё нет';
    state.history.forEach(h=>listButton($('history'),new Date(h.publishedAtMs).toLocaleString('ru-RU'),h.revision,false,()=>task(async()=>{
      if(!await discard())return;const old=await api('/'+encodeURIComponent(draft.buildingId)+'/revisions/'+encodeURIComponent(h.revision));
      draft=clone(old);dirty=true;fi=draft.floors.length?0:-1;ri=-1;drawing=false;points=[];releaseUnderlay();render();notice('История загружена в форму. Проверьте, сохраните черновик и опубликуйте. Опубликованные ID должны сохраниться.');
    })));
    $('floorInspector').hidden=!floor();$('roomInspector').hidden=!room();$('rooms').replaceChildren();
    if(floor()){
      const f=floor();$('floorId').value=f.id;$('floorName').value=f.name;$('floorOrder').value=f.order;
      $('width').value=f.plan?.width??'';$('height').value=f.plan?.height??'';
      f.rooms.forEach((r,i)=>listButton($('rooms'),String(i+1)+'. '+r.name,r.polygon.length?'Контур задан':'Без контура',i===ri,()=>{if(drawing){notice('Завершите или отмените текущий контур.',true);return;}ri=i;render()}));
      if(room()){$('roomId').value=room().id;$('roomName').value=room().name;}
    }
    drawPlan();controls();
  }
  function svgElement(tag,attrs){const el=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const [k,v]of Object.entries(attrs))el.setAttribute(k,String(v));return el;}
  function drawPlan(){
    const svg=$('plan'),f=floor();svg.replaceChildren();svg.classList.toggle('drawing',drawing);
    $('canvasTitle').textContent=f?f.name:'Этаж не выбран';$('mode').textContent=drawing?'Контур: '+points.length+' точек':'Просмотр';$('planStats').textContent=f?f.rooms.filter(r=>r.polygon.length>=3).length+' контуров':'';
    $('emptyPlan').hidden=!!f?.plan;if(!f?.plan){$('emptyPlan').textContent=f?'Этаж есть, рисунок пока не опубликован. Добавьте схему или используйте список помещений.':'Добавьте этаж, затем схему и помещения.';return;}
    const p=f.plan;if(!Number.isFinite(p.width)||!Number.isFinite(p.height)||p.width<1||p.height<1||p.width>10000||p.height>10000){$('emptyPlan').hidden=false;$('emptyPlan').textContent='Укажите размеры от 1 до 10000';return;}svg.setAttribute('viewBox','0 0 '+p.width+' '+p.height);
    svg.append(svgElement('rect',{x:0,y:0,width:p.width,height:p.height,fill:'#ffffff',stroke:'#c4d1d5','stroke-width':.2}));
    if(underlay)svg.append(svgElement('image',{href:underlay,x:0,y:0,width:p.width,height:p.height,opacity:'.55',preserveAspectRatio:'none'}));
    if(p.outline?.length)svg.append(svgElement('polygon',{points:p.outline.map(a=>a.x+','+a.y).join(' '),fill:'none',stroke:'#466c72','stroke-width':.5}));
    f.rooms.forEach((r,i)=>{
      if(r.polygon.length<3)return;
      const poly=svgElement('polygon',{points:r.polygon.map(a=>a.x+','+a.y).join(' '),fill:i===ri?'#78b6a5':'#dfebe6','fill-opacity':underlay?.45:1,stroke:i===ri?'#22735e':'#a0beb2','stroke-width':Math.min(p.width,p.height)*.005});
      poly.addEventListener('click',()=>{if(!drawing&&!busy){ri=i;render();}});svg.append(poly);
      const text=svgElement('text',{x:r.polygon.reduce((n,a)=>n+a.x,0)/r.polygon.length,y:r.polygon.reduce((n,a)=>n+a.y,0)/r.polygon.length,'text-anchor':'middle','dominant-baseline':'middle',fill:'#17373e','font-size':Math.min(p.width,p.height)*.05,'pointer-events':'none'});text.textContent=String(i+1);svg.append(text);
    });
    if(points.length)svg.append(svgElement('polyline',{points:points.map(a=>a.x+','+a.y).join(' '),fill:'none',stroke:'#b4531d','stroke-width':Math.min(p.width,p.height)*.008}));
    for(const a of points)svg.append(svgElement('circle',{cx:a.x,cy:a.y,r:Math.min(p.width,p.height)*.012,fill:'#b4531d'}));
  }
  function edit(id,apply,redraw=false){$(id).addEventListener('input',()=>{if(!draft||busy)return;apply($(id).value);changed();if(redraw)drawPlan()});}
  edit('buildingName',v=>draft.name=v);
  edit('floorId',v=>floor().id=v);edit('floorName',v=>floor().name=v,true);edit('floorOrder',v=>floor().order=Number(v));
  edit('roomId',v=>room().id=v);edit('roomName',v=>room().name=v);
  for(const [id,prop] of [['width','width'],['height','height']])edit(id,v=>{floor().plan[prop]=Number(v)},true);
  for(const id of ['floorName','roomName','floorId','roomId'])$(id).addEventListener('change',()=>render());
  async function loadCatalog(){catalog=await api('');$('buildings').replaceChildren(new Option('Выберите здание',''));for(const b of catalog)$('buildings').add(new Option(b.name,b.buildingId));renderCatalog();}
  function renderCatalog(){
    const query=$('buildingSearch').value.trim().toLocaleLowerCase('ru-RU'),rows=catalog.filter(b=>(b.name+' '+b.buildingId).toLocaleLowerCase('ru-RU').includes(query));
    $('catalogCount').textContent=catalog.length+' зданий';$('buildingRows').replaceChildren();$('catalogEmpty').hidden=rows.length>0;
    for(const b of rows){const button=document.createElement('button');button.className='building-row';const cell=document.createElement('span');cell.className='building-cell';const icon=document.createElement('span');icon.dataset.icon='building';const labels=document.createElement('span'),name=document.createElement('strong'),id=document.createElement('small');name.textContent=b.name;id.textContent=b.buildingId;labels.append(name,id);cell.append(icon,labels);const version=document.createElement('span');version.className='row-version';version.textContent=b.version?'Версия '+b.version:'Ещё не редактировалось';const arrow=document.createElement('span');arrow.className='row-chevron';arrow.dataset.icon='arrow';button.append(cell,version,arrow);button.onclick=()=>task(()=>open(b.buildingId));$('buildingRows').append(button);}
    window.EventMapIcons?.paint($('buildingRows'));
  }
  $('loginForm').addEventListener('submit',event=>{event.preventDefault();task(async()=>{
    key=$('key').value.trim();if(!key)throw new Error('Введите админ-ключ');const rows=await api('');
    catalog=rows;$('buildings').replaceChildren(new Option('Выберите здание',''));for(const b of rows)$('buildings').add(new Option(b.name,b.buildingId));$('key').value='';renderCatalog();session(true);$('notice').hidden=true;
  });});
  $('refreshCatalog').addEventListener('click',()=>task(loadCatalog));$('buildingSearch').addEventListener('input',renderCatalog);
  $('backToCatalog').addEventListener('click',()=>task(async()=>{if(!await discard())return;draft=null;state=null;dirty=false;drawing=false;points=[];releaseUnderlay();render();await loadCatalog();}));
  $('logout').addEventListener('click',async()=>{if(busy||!await discard())return;key='';$('key').value='';state=null;draft=null;dirty=false;drawing=false;points=[];releaseUnderlay();$('workspace').hidden=true;$('catalog').hidden=false;$('buildings').replaceChildren();session(false);controls();notice('Вы вышли. Ключ удалён из памяти.');});
  async function open(id){if(!key)throw new Error('Сначала подключитесь с админ-ключом');if(!await discard())return;selectState(await api('/'+encodeURIComponent(id)));notice('План загружен. Изменения в форме не видны пользователям до публикации.');}
  $('open').addEventListener('click',()=>task(()=>open($('buildingId').value.trim())));
  $('buildings').addEventListener('change',()=>{if($('buildings').value)task(()=>open($('buildings').value));});
  $('reload').addEventListener('click',()=>task(()=>open(draft.buildingId)));
  $('addFloor').addEventListener('click',()=>{draft.floors.push({id:uid('floor'),name:'Новый этаж',order:draft.floors.length+1,plan:null,rooms:[]});fi=draft.floors.length-1;ri=-1;releaseUnderlay();changed();render()});
  $('addRoom').addEventListener('click',()=>{floor().rooms.push({id:uid('room'),name:'Новое помещение',polygon:[]});ri=floor().rooms.length-1;changed();render()});
  $('deleteFloor').addEventListener('click',async()=>{if(!publishedFloor()&&await ask('Удалить неопубликованный этаж из черновика?')){draft.floors.splice(fi,1);fi=draft.floors.length?0:-1;ri=-1;releaseUnderlay();changed();render()}});
  $('deleteRoom').addEventListener('click',async()=>{if(!publishedRoom()&&await ask('Удалить неопубликованное помещение из черновика?')){floor().rooms.splice(ri,1);ri=-1;drawing=false;points=[];changed();render()}});
  $('enablePlan').addEventListener('click',()=>{floor().plan={width:100,height:60,outline:[]};changed();render()});
  $('removePlan').addEventListener('click',async()=>{if(await ask('Убрать рисунок и все контуры этого этажа? Этаж и помещения останутся в списке.')){floor().plan=null;floor().rooms.forEach(r=>r.polygon=[]);drawing=false;points=[];releaseUnderlay();changed();render()}});
  $('clearPolygon').addEventListener('click',()=>{room().polygon=[];changed();render()});
  $('draw').addEventListener('click',()=>{drawing=true;points=[];notice('Нажимайте углы помещения. Старый контур заменится только после замыкания.');drawPlan();controls()});
  $('plan').addEventListener('click',event=>{if(!drawing||busy||!floor()?.plan)return;const svg=$('plan'),matrix=svg.getScreenCTM();if(!matrix)return;const raw=new DOMPoint(event.clientX,event.clientY).matrixTransform(matrix.inverse());const p=floor().plan;if(raw.x<0||raw.y<0||raw.x>p.width||raw.y>p.height)return;if(points.length>=256){notice('Не более 256 точек на помещение',true);return;}points.push({x:Math.round(raw.x*100)/100,y:Math.round(raw.y*100)/100});drawPlan();controls()});
  $('finish').addEventListener('click',()=>{const trial=clone(draft);trial.floors[fi].rooms[ri].polygon=clone(points);const errors=validationErrors(trial);if(errors.length){notice(errors[0],true);return;}room().polygon=clone(points);drawing=false;points=[];changed();render()});
  $('undo').addEventListener('click',()=>{points.pop();drawPlan();controls()});
  $('cancelDraw').addEventListener('click',()=>{drawing=false;points=[];drawPlan();controls()});
  $('save').addEventListener('click',()=>task(async()=>{if(drawing)throw new Error('Сначала завершите или отмените рисование контура');if(showValidation().length)throw new Error('Исправьте ошибки схемы перед сохранением');const next=await api('/'+encodeURIComponent(draft.buildingId)+'/draft','PUT',{expectedVersion:state.version,building:draft});const f=fi,r=ri;selectState(next);fi=f;ri=r;render();notice('Черновик сохранён. Пользователи по-прежнему видят предыдущую публикацию.');}));
  $('publish').addEventListener('click',()=>task(async()=>{if(dirty||drawing)throw new Error('Сначала сохраните черновик');if(!await ask('Опубликовать сохранённый план «'+draft.name+'»? Пользователи увидят его при следующей загрузке плана.'))return;selectState(await api('/'+encodeURIComponent(draft.buildingId)+'/publications','POST',{expectedVersion:state.version}));notice('Опубликовано. Новая версия доступна приложению без деплоя backend.');}));
  $('underlay').addEventListener('change',()=>{const file=$('underlay').files[0];if(!file)return;if(!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>8*1024*1024){notice('Выберите PNG/JPEG/WebP не больше 8 MiB',true);return;}releaseUnderlay();underlay=URL.createObjectURL(file);drawPlan();});
  $('clearUnderlay').addEventListener('click',()=>{releaseUnderlay();drawPlan()});
  $('import').addEventListener('change',()=>task(async()=>{const file=$('import').files[0];if(!file)return;try{if(file.size>1024*1024)throw new Error('JSON больше 1 MiB');const imported=JSON.parse(await file.text());if(imported.buildingId!==draft.buildingId||imported.schemaVersion!==1||!Array.isArray(imported.floors)||imported.floors.length>32)throw new Error('Нужна схема v1 для открытого здания');if(!imported.floors.every(f=>typeof f.name==='string'&&typeof f.id==='string'&&Array.isArray(f.rooms)&&f.rooms.length<=200&&f.rooms.every(r=>typeof r.name==='string'&&typeof r.id==='string'&&Array.isArray(r.polygon)&&r.polygon.length<=256&&r.polygon.every(p=>Number.isFinite(p.x)&&Number.isFinite(p.y)))&&(!f.plan||(Number.isFinite(f.plan.width)&&f.plan.width>=1&&Number.isFinite(f.plan.height)&&f.plan.height>=1&&Array.isArray(f.plan.outline)&&f.plan.outline.length<=256&&f.plan.outline.every(p=>Number.isFinite(p.x)&&Number.isFinite(p.y))))))throw new Error('Некорректная структура этажей/помещений');if(!await discard())return;draft=imported;fi=draft.floors.length?0:-1;ri=-1;drawing=false;points=[];releaseUnderlay();changed();render();notice('JSON загружен в форму. Сервер проверит его при сохранении.')}finally{$('import').value=''}}));
  $('export').addEventListener('click',()=>{const url=URL.createObjectURL(new Blob([JSON.stringify(draft,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=draft.buildingId.replace(/:/g,'-')+'-draft.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
  window.addEventListener('beforeunload',event=>{if(dirty||drawing){event.preventDefault();event.returnValue=''}});
  function preview(){
    const building=$('previewSource').value==='published'?state.published:draft;
    const selected=$('previewFloor').value;$('previewFloor').replaceChildren();for(const f of building?.floors||[])$('previewFloor').add(new Option(f.name,f.id));if(building?.floors.some(f=>f.id===selected))$('previewFloor').value=selected;
    const f=building?.floors.find(f=>f.id===$('previewFloor').value);$('previewCanvas').replaceChildren();$('previewRooms').replaceChildren();
    if(!f?.plan){$('previewCanvas').textContent=f?'Схема не добавлена. Доступен список помещений.':'Нет этажей. В приложении будут события всего здания.';}
    else{const svg=svgElement('svg',{viewBox:'0 0 '+f.plan.width+' '+f.plan.height,role:'img','aria-label':'Предпросмотр схемы этажа'});svg.append(svgElement('rect',{width:f.plan.width,height:f.plan.height,fill:'white'}));if(f.plan.outline?.length)svg.append(svgElement('polygon',{points:f.plan.outline.map(p=>p.x+','+p.y).join(' '),fill:'none',stroke:'#6a9084','stroke-width':.3}));f.rooms.forEach((r,i)=>{if(r.polygon.length<3)return;svg.append(svgElement('polygon',{points:r.polygon.map(p=>p.x+','+p.y).join(' '),fill:'#deece5',stroke:'#83aa99','stroke-width':.3}));const text=svgElement('text',{x:r.polygon.reduce((n,p)=>n+p.x,0)/r.polygon.length,y:r.polygon.reduce((n,p)=>n+p.y,0)/r.polygon.length,'text-anchor':'middle','font-size':Math.min(f.plan.width,f.plan.height)*.045,fill:'#3e6959'});text.textContent=String(i+1);svg.append(text);});$('previewCanvas').append(svg);}
    for(const [i,r]of(f?.rooms||[]).entries()){const chip=document.createElement('span');chip.textContent=(i+1)+'. '+r.name;$('previewRooms').append(chip);}
  }
  $('preview').addEventListener('click',()=>{if(showValidation().length){notice('Исправьте ошибки перед предпросмотром.',true);return;}$('previewSource').value='draft';preview();$('previewDialog').showModal();});
  $('previewSource').addEventListener('change',preview);$('previewFloor').addEventListener('change',preview);$('closePreview').onclick=()=>$('previewDialog').close();
  window.EventMapAdmin=Object.freeze({request,notice,ask,connected:()=>document.body.dataset.auth==='on'});
  controls();
})();
