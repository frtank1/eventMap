import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {createBaiterek,createArena} from './landmarks.js';
import {createCityLandmarks} from './city-landmarks.js';
import {createCityStream} from './city-stream.js';
import {createSeasonLayer} from './season-world.js';
import {createStadiumAudio} from './stadium-audio.js';
import {createCityEvents,EVENT_MODES} from './city-events.js';

const $=s=>document.querySelector(s);
const canvas=$('#scene'), labelsRoot=$('#labels');
const THEMES=['default','winter','newyear'];
let savedAppearance={};try{savedAppearance=JSON.parse(localStorage.getItem('eventmap-appearance-v1')||'{}')||{}}catch{}
const appearanceParams=new URLSearchParams(location.search);
const requestedTheme=appearanceParams.get('theme')||savedAppearance.theme;
const state={place:'baiterek',night:appearanceParams.has('night')?appearanceParams.get('night')==='1':savedAppearance.night===true,theme:THEMES.includes(requestedTheme)?requestedTheme:'default',event:'calm',lod:'district',collapsed:false};
const reduceMotion=matchMedia('(prefers-reduced-motion: reduce)').matches;
let renderer,scene,camera,controls,sun,ambient,world,frame,tween,backdrop,lastTime=0;
const cache=new Map();
const raycaster=new THREE.Raycaster(),pointer=new THREE.Vector2();
const stadiumAudio=createStadiumAudio(),audioForward=new THREE.Vector3(),audioPosition=new THREE.Vector3();
const mat=(color,opts={})=>new THREE.MeshStandardMaterial({color,roughness:.82,metalness:0,...opts});
const clamp=THREE.MathUtils.clamp;

function project(p,center){return [(p[0]-center[0])*111320*Math.cos(center[1]*Math.PI/180),-(p[1]-center[1])*111320]}
function makeLabel(text,position,kind='',level=0){const el=document.createElement('div');el.className='world-label '+kind;el.textContent=text;labelsRoot.append(el);return {el,position:new THREE.Vector3(...position),level}}

// Landmarks are overlays; ALL ordinary buildings, streets and greenery now
// come from the same streaming renderer. There are no prebuilt district plates.
function buildLandmarkWorld(data,key,extended=false){
  const root=new THREE.Group(),fine=new THREE.Group(),near=new THREE.Group();root.add(fine,near);
  const model=key==='baiterek'?createBaiterek():createArena();root.add(model.group);model.group.userData.landmark=true;
  const landmarks=key==='baiterek'?createCityLandmarks(data,data.center,{extended}):null;if(landmarks)root.add(landmarks.group);
  const cityEvents=key==='baiterek'?createCityEvents():null;if(cityEvents)root.add(cityEvents.group);
  const season=createSeasonLayer({kind:key,roofs:landmarks?.roofs||[],trees:[],paths:[]});root.add(season.group);
  const pois=[{id:'main',title:key==='baiterek'?'Байтерек':'Астана Арена',p:[0,key==='baiterek'?50:25,0],label:[0,key==='baiterek'?117:65,0],height:key==='baiterek'?105:55,radius:key==='baiterek'?40:150,description:key==='baiterek'?'Золотой шар над бульваром. Исследуй город и здания вокруг.':'Стадион на общей карте города. Запусти матч и приближайся, чтобы услышать трибуны.'},...(landmarks?.pois||[])];
  return {root,fine,near,model,landmarks,season,cityEvents,pois,center:data.center,labelInfo:[]};
}

function activeModel(){return state.place==='arena'?world.arena.model:world.model}
function placeOrigin(){return state.place==='arena'?world.arena.root.position:new THREE.Vector3()}

function resetLabels(){labelsRoot.replaceChildren();world.labels=[...world.pois.map(p=>({...makeLabel(p.title,p.label,p.id==='main'?'hero':'',p.id==='main'?0:1),poi:p.id})),...world.labelInfo.map(l=>makeLabel(l.text,l.p,'',l.level))];const origin=placeOrigin();world.eventLabel=makeLabel(state.place!=='arena'?'Вечер на площади · демо':'Матч · демо',[origin.x,state.place!=='arena'?132:85,origin.z],'event',0);world.eventLabel.el.hidden=state.event==='calm';}

async function buildCity(){
  const [data,arenaData]=await Promise.all(['baiterek','arena'].map(async key=>{const r=await fetch(`./${key}.json`);if(!r.ok)throw Error('Не удалось загрузить участок');return r.json()}));
  const w=buildLandmarkWorld(data,'baiterek',true),arena=buildLandmarkWorld(arenaData,'arena');const offset=project(arenaData.center,data.center);arena.root.position.set(offset[0],0,offset[1]);w.root.add(arena.root);w.children=[arena];w.arena=arena;w.city=true;
  w.pois.push({id:'arena',title:'Астана Арена',p:[offset[0],25,offset[1]],label:[offset[0],75,offset[1]],height:55,radius:155,description:'Стадион на общей карте Астаны. Вкладка «Арена» открывает управление матчем.'});
  w.pois.push({id:'pitch',title:'Поле · посмотреть игру',p:[offset[0],3,offset[1]],label:[offset[0],7,offset[1]],height:4,radius:48,description:'Астана — Тобол. Открой «Арена» и нажми «Запустить матч». Игровая демонстрация, не реальная трансляция.'});
  for(const [id,title,coord] of [['rightbank','Правый берег · обзор',[71.43,51.17]],['southeast','Юго-восток · обзор',[71.50,51.13]],['airport','Район аэропорта · обзор',[71.466,51.027]]]){const p=project(coord,data.center);w.pois.push({id,title,p:[p[0],0,p[1]],label:[p[0],70,p[1]],height:0,radius:400,description:'Тот же игровой город: фасады, крыши, витрины, фонари, деревья и люди появляются при приближении. Геометрия по открытым данным; детали стилизованы.'})}
  const min=project([71.22,51.25],data.center),max=project([71.70,50.99],data.center);w.bounds={minX:min[0],minZ:min[1],maxX:max[0],maxZ:max[1]};
  w.stream=createCityStream({center:data.center,excludeBuilding:(lon,lat)=>{const p=project([lon,lat],data.center);return Math.hypot(...p)<30||Math.hypot(p[0]-offset[0],p[1]-offset[1])<140||w.landmarks.excludeBuilding(lon,lat)},onStatus:s=>{$('#stream-status').textContent=s.failed?`Не все кварталы доступны · ${s.loaded} загружено`:s.pending?`Загрузка кварталов · ${s.loaded}/${s.loaded+s.pending}`:`Единый город · ${s.buildings} зданий рядом`;$('#stream-status').classList.toggle('has-error',s.failed>0);document.body.dataset.tiles=s.loaded;document.body.dataset.pendingTiles=s.pending;document.body.dataset.cityBuildings=s.buildings;if(renderer)renderer.shadowMap.needsUpdate=true;}});
  w.allPois=[...w.pois];w.root.add(w.stream.group);return w;
}

function fillPOIs(){const select=$('#poi-select');select.replaceChildren();const start=document.createElement('option');start.value='';start.textContent=state.place==='city'?'Перелететь к зданию…':'Здания и места…';select.append(start);for(const p of world.pois){const option=document.createElement('option');option.value=p.id;option.textContent=p.title;select.append(option)}}
function focusPOI(id,close=false){if(!world)return;const p=world.pois.find(p=>p.id===id);if(!p)return;state.focus=id;$('#poi-select').value=id;const target=new THREE.Vector3(...p.p),d=Math.max(18,Math.max(22,p.height*1.55,p.radius*2.4)*(close?.65:1));const offset=p.viewDirection?new THREE.Vector3(...p.viewDirection).multiplyScalar(d):id==='people'?new THREE.Vector3(d*.3,d*1.35,d*.55):id==='pitch'?new THREE.Vector3(d*.25,d*.95,d*.6):new THREE.Vector3(d*.75,d*.6,d);flyTo(target.clone().add(offset),target,1400);$('#card-title').textContent=p.title;$('#card-description').textContent=p.description;$('#card-kicker').textContent=id==='people'?'ИГРОВЫЕ ПЕРСОНАЖИ':id==='pitch'?'ПОЛЕ СТАДИОНА':'ГОРОДСКОЙ ОРИЕНТИР';$('#activity-options').hidden=id!=='main'&&id!=='pitch';$('.activity-label').hidden=id!=='main'&&id!=='pitch';$('#activity-note').textContent=id==='main'||id==='pitch'?'Сценарии вымышлены. Люди и свет — анимация прототипа.':'Стилизация для прототипа. Нажми «Об источниках» для подробностей.';}

async function choosePlace(key){
  if(!['baiterek','arena','city'].includes(key))return;
  const request=Symbol();choosePlace.request=request;
  state.place=key;state.focus='main';state.event='calm';document.body.dataset.event='calm';document.body.dataset.ready='loading';tween=null;$('#loader').classList.remove('done');$('#activity-options').hidden=false;$('.activity-label').hidden=false;
  stadiumAudio.setActive(false);stadiumAudio.mute();
  world?.cityEvents?.setMode('none');state.cityEvent='none';$('#city-event-select').value='none';document.body.dataset.cityEvent='none';
  $('#city-event-play').textContent='Запустить салют';$('#city-event-focus').disabled=true;$('#city-event-description').textContent='Выбери сценарий: салют, концерт, марафон и другие игровые события.';
  $('#city-event-panel').hidden=true;$('#city-event-toggle').hidden=key==='arena';$('#city-event-toggle').setAttribute('aria-expanded','false');
  $('#match-panel').hidden=key!=='arena';
  if(key==='arena')$('#match-scenarios').append($('#activity-options'));
  else $('#activity-note').before($('#activity-options'));
  document.querySelectorAll('[data-place]').forEach(b=>{b.classList.toggle('active',b.dataset.place===key);b.setAttribute('aria-pressed',b.dataset.place===key)});
  try{
    if(!cache.has('city')){if(!cache.has('city-pending'))cache.set('city-pending',buildCity());cache.set('city',await cache.get('city-pending'))}
    if(choosePlace.request!==request)return;
    world=cache.get('city');if(world.root.parent!==scene)scene.add(world.root);world.model.setEvent('calm');world.arena.model.setEvent('calm');world.pois=world.allPois.map(p=>({...p,id:key==='arena'?(p.id==='main'?'baiterek':p.id==='arena'?'main':p.id):p.id}));resetLabels();
    $('#place-title').textContent=key==='city'?'Астана · живой город':key==='baiterek'?'Вокруг Байтерека':'Астана Арена';
    $('#place-subtitle').textContent='Одна карта · одинаковая детализация во всех районах.';
    $('#card-title').textContent=key!=='arena'?'Байтерек':'Астана Арена';
    $('#card-kicker').textContent=key!=='arena'?'СЕРДЦЕ ЛЕВОГО БЕРЕГА':'ГОРОДСКАЯ АРЕНА';
    $('#card-description').textContent=key!=='arena'?'Золотой шар над бульваром. Выбери здание в списке или исследуй город свободно.':'Днём — архитектура. Во время матча — свет и движение. Переключи сценарий места.';
    $('#building-count').textContent=world.landmarks.pois.length+2;$('#building-count').nextSibling.textContent=' моделей ориентиров';$('#area-size').textContent='Единый городской мир';
    $('#activity-options').replaceChildren();const options=key!=='arena'?[['calm','Обычный день'],['live','Вечер на площади']]:[['calm','Тихо'],['match','Матч'],['concert','Концерт']];
    options.forEach(([value,text])=>{const b=document.createElement('button');b.dataset.event=value;b.textContent=text;b.classList.toggle('active',value==='calm');b.setAttribute('aria-pressed',value==='calm');$('#activity-options').append(b)});
    $('#activity-note').textContent='Сценарии вымышлены. Люди и свет — анимация прототипа.';
    controls.maxDistance=30000;controls.maxTargetRadius=40000;camera.far=120000;camera.updateProjectionMatrix();scene.fog.near=18000;scene.fog.far=45000;$('#stream-status').hidden=false;fillPOIs();document.body.dataset.cityRenderer='shared-v2';
    state.collapsed=innerWidth<900;$('#place-card').classList.toggle('collapsed',state.collapsed);$('#card-toggle').textContent=state.collapsed?'+':'−';$('#card-toggle').setAttribute('aria-expanded',!state.collapsed);$('#card-toggle').setAttribute('aria-label',state.collapsed?'Развернуть карточку':'Свернуть карточку');
    applyAppearance();setView('block',false);updateDetails();$('#loader').classList.add('done');document.body.dataset.ready='true';document.body.dataset.place=key;
  }catch(error){showError(error)}
}

function applyLight(){
  const night=state.night,winter=state.theme!=='default';document.body.classList.toggle('night',night);document.body.dataset.night=String(night);$('#night').setAttribute('aria-pressed',night);$('#night').textContent=night?'☀':'☾';$('#night').setAttribute('aria-label',night?'Включить дневное освещение':'Включить вечернее освещение');
  scene.background=new THREE.Color(night?0x080e19:winter?0xcbd8df:0xc8d1cd);scene.fog.color.copy(scene.background);
  if(backdrop)backdrop.material.color.set(world?.city?(winter?(night?0x9baec0:0xe4eced):(night?0x303c42:0xb5b79d)):(night?0x111d28:winter?0xcbd7dc:0xc0c7b5));
  ambient.intensity=night?.78:1.8;ambient.color.set(night?0xa9b8c6:0xe7f1ff);ambient.groundColor.set(night?0x283034:0x9b967e);
  sun.intensity=night?.34:3.4;sun.color.set(night?0xc3cedb:0xffe4b9);renderer.toneMappingExposure=night?.98:1.04;
  renderer.shadowMap.enabled=!night;renderer.shadowMap.needsUpdate=true;
  if(world)for(const w of [world,...(world.children||[])]){w.model.setNight(night);w.landmarks?.setNight(night);w.season?.setNight(night);w.stream?.setNight(night)}
  document.querySelectorAll('[data-light]').forEach(b=>{const active=(b.dataset.light==='night')===night;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active)});
  world?.cityEvents?.setNight(night);
  saveAppearance();
}

function saveAppearance(){try{localStorage.setItem('eventmap-appearance-v1',JSON.stringify({theme:state.theme,night:state.night}))}catch{}}
function applyAppearance(){
  document.body.dataset.theme=state.theme;
  document.querySelectorAll('[data-theme]').forEach(b=>{const active=b.dataset.theme===state.theme;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active)});
  $('#appearance-current').textContent=({default:'Обычное',winter:'Зима',newyear:'Новый год'})[state.theme];
  $('#appearance-note').textContent=state.theme==='newyear'?'Снег, ёлка и тёплые гирлянды. Праздничная декорация, не реальное мероприятие.':state.theme==='winter'?'Снег на крышах и деревьях. Время суток выбирается отдельно.':'Диорамные фасады и детали улиц. Время суток выбирается отдельно.';
  if(world)for(const w of [world,...(world.children||[])]){w.season?.setTheme(state.theme);w.stream?.setTheme(state.theme);w.landmarks?.setTheme(state.theme)}
  applyLight();
}

function eventMode(mode){if(!world)return;if(state.place!=='arena'&&['calm','live'].includes(mode)){setCityEvent(mode==='live'?'fireworks':'none',false);return}const allowed=state.place!=='arena'?['calm','live']:['calm','match','concert'];if(!allowed.includes(mode))return;state.event=mode;activeModel().setEvent(mode);stadiumAudio.setActive(state.place==='arena'&&mode==='match');if(mode!=='match')stadiumAudio.mute();document.querySelectorAll('[data-event]').forEach(b=>{b.classList.toggle('active',b.dataset.event===mode);b.setAttribute('aria-pressed',b.dataset.event===mode)});if(mode!=='calm'){state.night=true;applyLight()}world.eventLabel.el.hidden=mode==='calm';world.eventLabel.el.textContent=({match:'Астана — Тобол · демо',concert:'Концерт · демо',live:'Вечер на площади · демо'})[mode]||'';$('#activity-note').textContent=mode==='calm'?'Сценарии вымышлены. Люди и свет — анимация прототипа.':'Демо-событие: здесь появятся описание, участники и ролики.';document.body.dataset.event=mode;updateMatchUI();}

function focusCityEvent(close=false){
  const mode=EVENT_MODES.find(m=>m.id===state.cityEvent);if(!mode||!world?.cityEvents)return;
  const target=new THREE.Vector3(...mode.focus),d=Math.max(65,mode.radius*(close?2.3:3.4));
  state.focus='main';$('#poi-select').value='main';flyTo(target.clone().add(new THREE.Vector3(d*.7,d*.75,d)),target,1500);
}
function setCityEvent(id,focus=true){
  if(!world?.cityEvents)return;
  const mode=EVENT_MODES.find(m=>m.id===id);id=mode?mode.id:'none';
  state.cityEvent=id;state.event=mode?'live':'calm';world.cityEvents.setMode(id);world.model.setEvent(state.event);
  if(mode){state.collapsed=true;$('#place-card').classList.add('collapsed');$('#card-toggle').textContent='+';$('#card-toggle').setAttribute('aria-expanded','false');$('#card-toggle').setAttribute('aria-label','Развернуть карточку')}
  $('#city-event-select').value=id;$('#city-event-play').textContent=mode?'Остановить анимацию':'Запустить салют';
  $('#city-event-focus').disabled=!mode;
  $('#city-event-description').textContent=mode?mode.description:'Выбери сценарий: салют, концерт, марафон и другие игровые события.';
  world.eventLabel.el.hidden=!mode;if(mode){world.eventLabel.el.textContent=`${mode.title} · демо`;world.eventLabel.position.set(mode.focus[0],Math.max(16,mode.focus[1]+14),mode.focus[2]);if(mode.night){state.night=true;applyLight()}}
  document.querySelectorAll('[data-event]').forEach(b=>{const active=b.dataset.event===state.event;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active)});
  $('#activity-note').textContent=mode?`${mode.title}: игровая анимация, не реальное мероприятие.`:'Сценарии вымышлены. Люди и свет — анимация прототипа.';
  document.body.dataset.cityEvent=id;document.body.dataset.event=state.event;
  if(mode&&focus)focusCityEvent();
}

let lastSoundStatus='';
function updateMatchUI(){
  const s=stadiumAudio.getState(),playing=state.place==='arena'&&state.event==='match';
  $('#match-start').setAttribute('aria-pressed',playing);$('#match-start span').textContent=playing?'Остановить матч':'Запустить матч';
  $('#match-start svg path').setAttribute('d',playing?'M3 3h10v10H3Z':'M4 2 13 8 4 14Z');
  $('#match-sound').disabled=!playing||s.status==='loading';$('#match-sound').setAttribute('aria-pressed',s.enabled);
  const soundLabel=s.enabled?'Выключить звук трибун':'Включить звук трибун';$('#match-sound').setAttribute('aria-label',soundLabel);$('#match-sound').title=soundLabel;
  $('#match-distance').textContent=`Камера · ${Number.isFinite(s.distance)?Math.round(s.distance).toLocaleString('ru-RU'):'—'} м`;
  $('#match-level').textContent=!s.enabled?'Звук выключен':s.gain===0?'За пределами слышимости':`Слышимость ${Math.round(s.gain*100)}%`;
  $('#match-meter-fill').style.width=`${Math.min(100,s.rms*750)}%`;
  $('#match-caption').textContent=playing?'Матч идёт · трибуны скандируют «Астана!» и «Тобол!»':'Две команды. Полные трибуны. Город слышит игру.';
  const message=!playing?'Запусти матч со звуком. Камера остаётся свободной.':s.status==='loading'?'Готовим шум и кричалки трибун…':s.status==='error'?'Звук не загрузился. Нажми на динамик, чтобы повторить.':!s.enabled?'Звук выключен. Включи динамик, чтобы услышать трибуны.':s.status==='partial'?'Шум работает; часть кричалок не загрузилась.':s.context==='interrupted'||s.status==='interrupted'?'Браузер приостановил звук. Нажми на динамик для перезапуска.':'Приближайся — громче и яснее. Отлетай — тише.';
  if(lastSoundStatus!==message){$('#match-audio-status').textContent=message;lastSoundStatus=message}
  document.body.dataset.audio=s.context;document.body.dataset.audioEnabled=String(s.enabled);document.body.dataset.audioGain=s.gain.toFixed(4);document.body.dataset.audioRms=s.rms.toFixed(6);document.body.dataset.stadiumDistance=Number.isFinite(s.distance)?s.distance.toFixed(1):'none';
}
function updateMatchAudio(){
  if(state.place!=='arena'||!world)return;
  audioPosition.copy(camera.position);world.arena.root.worldToLocal(audioPosition);camera.getWorldDirection(audioForward);
  stadiumAudio.update(audioPosition,audioForward,camera.up);
}
async function enableMatchSound(){await stadiumAudio.enable();updateMatchUI()}
function startMatch(){
  if(state.place!=='arena'||!world)return;
  if(state.event==='match'){eventMode('calm');return}
  eventMode('match');enableMatchSound();
  state.focus='main';$('#poi-select').value='main';const origin=placeOrigin();flyTo(new THREE.Vector3(260,230,320).add(origin),new THREE.Vector3(0,15,0).add(origin),1500);
}

function flyTo(pos,target,duration=1100){tween={from:camera.position.clone(),to:pos.clone(),fromTarget:controls.target.clone(),toTarget:target.clone(),start:performance.now(),duration:reduceMotion?0:duration};}
function setView(level,animate=true){
  if(level!=='district'&&state.focus&&state.focus!=='main'){focusPOI(state.focus,level==='near');return;}
  const arena=state.place==='arena';const views=arena?{district:{p:[760,710,940],t:[0,0,0]},block:{p:[350,320,410],t:[0,12,0]},near:{p:[145,160,220],t:[0,20,0]}}:{district:state.place==='city'?{p:[6000,7500,9800],t:[0,0,-1000]}:{p:[800,760,950],t:[0,0,180]},block:{p:[245,250,340],t:[0,35,0]},near:{p:[90,100,145],t:[0,55,0]}};
  const v=views[level];if(!v)return;const origin=placeOrigin(),pos=new THREE.Vector3(...v.p).add(origin),target=new THREE.Vector3(...v.t).add(origin);if(animate)flyTo(pos,target);else{camera.position.copy(pos);controls.target.copy(target);controls.update()}document.querySelectorAll('[data-scale]').forEach(b=>{b.classList.toggle('active',b.dataset.scale===level);b.setAttribute('aria-pressed',b.dataset.scale===level)});
}
function zoom(factor){const delta=camera.position.clone().sub(controls.target);delta.multiplyScalar(factor);const d=clamp(delta.length(),controls.minDistance,controls.maxDistance);delta.setLength(d);flyTo(controls.target.clone().add(delta),controls.target,400)}
function updateDetails(){if(!world)return;const distance=controls.getDistance();const level=distance>850?'district':distance>360?'block':'near';const changed=state.lod!==level;state.lod=level;world.fine.visible=distance<2500;world.near.visible=distance<1000;if(world.model.detail)world.model.detail.visible=distance<700;for(const child of world.children||[]){const d=camera.position.distanceTo(child.root.position);child.fine.visible=d<2200;child.near.visible=d<900;child.model.detail.visible=d<700;}
  $('#lod-label').textContent=({district:world.city?'Обзор города':'Обзор района',block:'Внутри квартала',near:'Детали места'})[level];$('#lod-description').textContent=({district:'Контуры и объёмы',block:'Окна и деревья',near:'Люди, свет, объекты'})[level];$('#distance').value=clamp((Math.log(controls.maxDistance)-Math.log(distance))/(Math.log(controls.maxDistance)-Math.log(18))*100,0,100);document.body.dataset.lod=level;
  if(changed)document.querySelectorAll('[data-scale]').forEach(b=>{b.classList.toggle('active',b.dataset.scale===level);b.setAttribute('aria-pressed',b.dataset.scale===level)});
}
function updateLabels(){if(!world?.labels)return;const w=innerWidth,h=innerHeight,occupied=[];[...world.labels,world.eventLabel].sort((a,b)=>(b.poi===state.focus)-(a.poi===state.focus)).forEach(l=>{const p=l.position.clone().project(camera),nearEnough=l.level===0||controls.getDistance()>3000||camera.position.distanceTo(l.position)<1800,px=(p.x+1)/2*w,py=(-p.y+1)/2*h;let visible=p.z<1&&p.z>-1&&nearEnough&&py>(w<650?230:105)&&py<h-70&&px>55&&px<w-65;if(occupied.some(q=>Math.abs(px-q[0])<130&&Math.abs(py-q[1])<32))visible=false;if(visible)occupied.push([px,py]);l.el.style.opacity=visible?'1':'0';l.el.style.left=px+'px';l.el.style.top=py+'px';});$('#compass-arrow').style.transform=`rotate(${-controls.getAzimuthalAngle()*180/Math.PI}deg)`;}
const localCamera=new THREE.Vector3(),shadowAnchor=new THREE.Vector3(Infinity,0,Infinity);let lastUI=0,lastStats=0;
function draw(time){
  frame=requestAnimationFrame(draw);
  if(document.hidden||innerWidth<650&&time-lastTime<1000/30)return;
  lastTime=time;
  if(tween){let t=clamp((time-tween.start)/Math.max(1,tween.duration),0,1);t=t*t*(3-2*t);camera.position.lerpVectors(tween.from,tween.to,t);controls.target.lerpVectors(tween.fromTarget,tween.toTarget,t);if(t>=1)tween=null}
  controls.update();
  if(world){
    const b=world.bounds,old=controls.target.clone();controls.target.x=clamp(controls.target.x,b.minX,b.maxX);controls.target.z=clamp(controls.target.z,b.minZ,b.maxZ);camera.position.add(controls.target.clone().sub(old));
    world.model.animate(reduceMotion?0:time/1000);
    for(const child of world.children||[])child.model.animate(reduceMotion?0:time/1000);
    world.cityEvents?.update(time/1000,reduceMotion);
    world.stream?.update(controls.target,controls.getDistance());world.stream?.animate(time/1000,reduceMotion);
    if(!state.night&&Math.hypot(controls.target.x-shadowAnchor.x,controls.target.z-shadowAnchor.z)>120){shadowAnchor.set(controls.target.x,0,controls.target.z);sun.position.copy(shadowAnchor).add(new THREE.Vector3(-420,800,350));sun.target.position.copy(shadowAnchor);sun.target.updateMatrixWorld();renderer.shadowMap.needsUpdate=true}
    for(const w of [world,...(world.children||[])]){w.root.updateWorldMatrix(true,false);localCamera.copy(camera.position);w.root.worldToLocal(localCamera);w.season?.update(time/1000,localCamera,controls.getDistance(),reduceMotion);w.landmarks?.updateDetail(localCamera)}
    updateMatchAudio();
    if(time-lastUI>80){updateDetails();updateLabels();updateMatchUI();lastUI=time}
  }
  renderer.render(scene,camera);
  if(time-lastStats>2000){document.body.dataset.drawCalls=renderer.info.render.calls;document.body.dataset.triangles=renderer.info.render.triangles;lastStats=time}
}
function resize(){if(!renderer)return;renderer.setSize(innerWidth,innerHeight);camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();}
function showError(error){console.error(error);$('#loader').classList.add('done');$('#failure').hidden=false;$('#failure-text').textContent=error.message||'Браузеру требуется поддержка WebGL 2. Попробуй другой браузер.';document.body.dataset.ready='error';}

async function init(){try{
  renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false,powerPreference:'high-performance'});renderer.setPixelRatio(Math.min(devicePixelRatio,innerWidth<650?1.25:1.5));renderer.setSize(innerWidth,innerHeight);renderer.shadowMap.enabled=true;renderer.shadowMap.autoUpdate=false;renderer.shadowMap.type=THREE.PCFSoftShadowMap;renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;
  scene=new THREE.Scene();scene.background=new THREE.Color(0xe7eee4);scene.fog=new THREE.Fog(0xe7eee4,1800,4200);
  camera=new THREE.PerspectiveCamera(43,innerWidth/innerHeight,2,6500);
  controls=new OrbitControls(camera,canvas);controls.enableDamping=true;controls.dampingFactor=.085;controls.screenSpacePanning=false;controls.minPolarAngle=.15;controls.maxPolarAngle=Math.PI*.46;controls.minDistance=18;controls.maxDistance=2600;controls.zoomSpeed=.9;controls.panSpeed=.7;controls.maxTargetRadius=1600;controls.addEventListener('start',()=>{tween=null;$('#gesture-hint').style.opacity='0'});controls.addEventListener('change',updateDetails);
  ambient=new THREE.HemisphereLight(0xf2f7ff,0xb2b792,2.1);scene.add(ambient);sun=new THREE.DirectionalLight(0xffedd3,3.2);sun.position.set(-420,800,350);sun.castShadow=true;sun.shadow.mapSize.set(2048,2048);sun.shadow.camera.left=-850;sun.shadow.camera.right=850;sun.shadow.camera.top=850;sun.shadow.camera.bottom=-850;sun.shadow.camera.near=10;sun.shadow.camera.far=2400;sun.shadow.normalBias=1.3;sun.shadow.bias=-.00015;scene.add(sun);scene.add(sun.target);
  backdrop=new THREE.Mesh(new THREE.PlaneGeometry(140000,140000),mat(0xe4ece1));backdrop.rotation.x=-Math.PI/2;backdrop.position.y=-.1;backdrop.receiveShadow=true;scene.add(backdrop);
  const params=new URLSearchParams(location.search);await choosePlace(['arena','baiterek'].includes(params.get('place'))?params.get('place'):'city');if(state.place==='arena'&&['match','concert'].includes(params.get('event')))eventMode(params.get('event'));if(['district','block','near'].includes(params.get('view')))setView(params.get('view'),false);if(params.get('focus'))focusPOI(params.get('focus'));if(state.place!=='arena'&&EVENT_MODES.some(m=>m.id===params.get('effect'))){setCityEvent(params.get('effect'));$('#city-event-panel').hidden=false;$('#city-event-toggle').setAttribute('aria-expanded','true')}requestAnimationFrame(draw);
}catch(error){showError(error)}}

document.querySelectorAll('[data-place]').forEach(b=>b.addEventListener('click',()=>choosePlace(b.dataset.place)));
document.querySelectorAll('[data-scale]').forEach(b=>b.addEventListener('click',()=>setView(b.dataset.scale)));
$('#activity-options').addEventListener('click',e=>{const b=e.target.closest('[data-event]');if(b){eventMode(b.dataset.event);if(b.dataset.event==='match')enableMatchSound()}});
$('#match-start').addEventListener('click',startMatch);
$('#match-sound').addEventListener('click',()=>{if(stadiumAudio.getState().enabled)stadiumAudio.mute();else enableMatchSound();updateMatchUI()});
$('#match-volume').addEventListener('input',e=>{stadiumAudio.setVolume(Number(e.target.value)/100);$('#match-volume-label').value=`${e.target.value}%`;updateMatchUI()});
$('#match-near').addEventListener('click',()=>focusPOI('pitch',true));
$('#match-far').addEventListener('click',()=>{state.focus='main';$('#poi-select').value='main';const origin=placeOrigin();flyTo(new THREE.Vector3(1150,1000,1300).add(origin),new THREE.Vector3(0,15,0).add(origin),2200)});
for(const mode of EVENT_MODES){const option=document.createElement('option');option.value=mode.id;option.textContent=mode.title;$('#city-event-select').append(option)}
$('#city-event-toggle').addEventListener('click',()=>{const panel=$('#city-event-panel');panel.hidden=!panel.hidden;$('#city-event-toggle').setAttribute('aria-expanded',!panel.hidden);$('#appearance-panel').hidden=true;$('#appearance-toggle').setAttribute('aria-expanded','false')});
$('#city-event-close').addEventListener('click',()=>{$('#city-event-panel').hidden=true;$('#city-event-toggle').setAttribute('aria-expanded','false');$('#city-event-toggle').focus()});
$('#city-event-select').addEventListener('change',e=>setCityEvent(e.target.value));
$('#city-event-play').addEventListener('click',()=>setCityEvent(state.cityEvent&&state.cityEvent!=='none'?'none':'fireworks'));
$('#city-event-focus').addEventListener('click',()=>focusCityEvent(true));
$('#zoom-in').addEventListener('click',()=>zoom(.7));$('#zoom-out').addEventListener('click',()=>zoom(1.4));$('#home').addEventListener('click',()=>setView('district'));$('#explore').addEventListener('click',()=>setView('near'));$('#night').addEventListener('click',()=>{state.night=!state.night;applyLight()});
$('#appearance-toggle').addEventListener('click',()=>{const panel=$('#appearance-panel');panel.hidden=!panel.hidden;$('#appearance-toggle').setAttribute('aria-expanded',!panel.hidden);$('#city-event-panel').hidden=true;$('#city-event-toggle').setAttribute('aria-expanded','false')});
$('#appearance-close').addEventListener('click',()=>{$('#appearance-panel').hidden=true;$('#appearance-toggle').setAttribute('aria-expanded',false);$('#appearance-toggle').focus()});
document.querySelectorAll('[data-theme]').forEach(b=>b.addEventListener('click',()=>{if(!THEMES.includes(b.dataset.theme))return;state.theme=b.dataset.theme;applyAppearance()}));
document.querySelectorAll('[data-light]').forEach(b=>b.addEventListener('click',()=>{state.night=b.dataset.light==='night';applyLight()}));
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('#appearance-panel').hidden){$('#appearance-panel').hidden=true;$('#appearance-toggle').setAttribute('aria-expanded',false);$('#appearance-toggle').focus()}});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('#city-event-panel').hidden){$('#city-event-panel').hidden=true;$('#city-event-toggle').setAttribute('aria-expanded','false');$('#city-event-toggle').focus()}});
$('#north').addEventListener('click',()=>{const d=controls.getDistance(),angle=controls.getPolarAngle();flyTo(controls.target.clone().add(new THREE.Vector3(0,Math.cos(angle)*d,Math.sin(angle)*d)),controls.target)});
$('#distance').addEventListener('input',e=>{tween=null;const d=Math.exp(Math.log(controls.maxDistance)-e.target.value/100*(Math.log(controls.maxDistance)-Math.log(18)));camera.position.sub(controls.target).setLength(d).add(controls.target);controls.update();updateDetails()});
$('#poi-select').addEventListener('change',e=>focusPOI(e.target.value));
$('#card-toggle').addEventListener('click',()=>{state.collapsed=!state.collapsed;$('#place-card').classList.toggle('collapsed',state.collapsed);$('#card-toggle').textContent=state.collapsed?'+':'−';$('#card-toggle').setAttribute('aria-expanded',!state.collapsed);$('#card-toggle').setAttribute('aria-label',state.collapsed?'Развернуть карточку':'Свернуть карточку')});
for(const id of ['#help-button','#source-button'])$(id).addEventListener('click',()=>$('#about').showModal());$('#close-about').addEventListener('click',()=>$('#about').close());$('#about').addEventListener('click',e=>{if(e.target===$('#about'))$('#about').close()});$('#retry').addEventListener('click',()=>location.reload());
let down=null;canvas.addEventListener('pointerdown',e=>{down=[e.clientX,e.clientY]});canvas.addEventListener('pointerup',e=>{if(!down||Math.hypot(e.clientX-down[0],e.clientY-down[1])>6||!world)return;pointer.set(e.clientX/innerWidth*2-1,-e.clientY/innerHeight*2+1);raycaster.setFromCamera(pointer,camera);if(raycaster.intersectObject(activeModel().group,true).length){state.collapsed=false;$('#place-card').classList.remove('collapsed');$('#card-toggle').setAttribute('aria-expanded','true');$('#card-toggle').textContent='−';setView('near')}});
document.addEventListener('visibilitychange',()=>stadiumAudio.setHidden(document.hidden));
window.addEventListener('resize',resize);window.addEventListener('pagehide',()=>{cancelAnimationFrame(frame);stadiumAudio.setHidden(true)});window.addEventListener('pageshow',e=>{if(e.persisted){stadiumAudio.setHidden(document.hidden);frame=requestAnimationFrame(draw)}});
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();showError(new Error('3D-контекст был потерян. Перезагрузи сцену.'))});
init();
