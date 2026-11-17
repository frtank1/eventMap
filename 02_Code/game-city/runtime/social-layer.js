import * as THREE from 'three';
import {createAvatar,createFootballDemo} from './avatar.js';
import {defaultLook,palettes,validateLook,visibleEvents,groupEvents,labelsOverlap} from './social-state.js';
import {eventStyle,eventSummary,EVENT_STYLES} from './event-style.js';
import {eventIcon} from './event-icons.js';
import {createEventModels} from './event-models.js';
import {createBubble,bubbleRect,safeBubbleCover,updateBubbleCover,nativeBubbleCovers,eventTimeLabel,setLabelVisible,positionLabel,setLabelClass,setLabelLevel} from './event-bubbles.js';
import {eventDisplayLevel} from './art-direction.js';
import {eventLabelBudget,eventGroupBucket,groupDistanceForBucket} from './event-density.js';
import {createEventBeacons} from './event-beacons.js';

const storageKey='eventmap.avatar-look.v1';
const node=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text)e.textContent=text;return e;};
const icon=(name)=>({person:'<circle cx="12" cy="7" r="3"/><path d="M5 21v-3a7 7 0 0 1 14 0v3"/>',near:'<circle cx="12" cy="12" r="8"/><path d="m15 9-2 4-4 2 2-4z"/>',plus:'<path d="M12 5v14M5 12h14"/>',minus:'<path d="M5 12h14"/>'}[name]);
function iconButton(id,name,label){const b=document.querySelector(id);b.innerHTML=`<svg viewBox="0 0 24 24" aria-hidden="true">${icon(name)}</svg><span>${label}</span>`;return b;}

export function createSocialLayer({scene,camera,controls,native,project,groundHeight=()=>.12,surfaceHeight=groundHeight,editorSpot=()=>null}){
  let look={...defaultLook};try{look=validateLook(JSON.parse(localStorage.getItem(storageKey)))||look;}catch{}
  const avatar=createAvatar(look);avatar.root.position.set(-24,groundHeight(-24,57)+.008,57);avatar.root.visible=false;scene.add(avatar.root);
  const markerRoot=document.querySelector('#event-labels'),editor=document.querySelector('#avatar-editor'),nearby=document.querySelector('#nearby-list');
  let eventModels=null,presentation='bubbles',covers=new Map(),nativeCovers=[],coverRequest='',lastCoverRequest=0;
  const pointGroup=new THREE.Group();scene.add(pointGroup);
  let events=[],rawEvents=[],preview=false,court=null,editing=false,savedCamera=null,transition=null,groups=[],lastGroup='',lastPrune=0,paused=false;
  let walking=false,walkPhase=1.97,lastWalkTime=null,follow=false,lastBucket=-1,previewStep=false,night=false,quality='balanced',savedAvatarPosition=null;
  let displayLevel='district',selectedEvent=null,occupiedLabels=[],festival=false,beacons=null;
  // A portrait fill follows only the editor; no moving shadow map or city lights.
  const portraitLight=new THREE.DirectionalLight(0xffeddb,1.7);portraitLight.visible=false;
  scene.add(portraitLight,portraitLight.target);
  const followOffset=new THREE.Vector3(10,7,14),followTarget=new THREE.Vector3();
  const selfButton=iconButton('#self-button','person','Мой образ');
  const nearButton=iconButton('#nearby-button','near','События');
  iconButton('#zoom-in','plus','Приблизить').onclick=()=>zoom(.7);
  iconButton('#zoom-out','minus','Отдалить').onclick=()=>zoom(1/.7);
  function zoom(factor){cancelTransition();const offset=camera.position.clone().sub(controls.target);offset.multiplyScalar(factor);offset.setLength(THREE.MathUtils.clamp(offset.length(),controls.minDistance,controls.maxDistance));camera.position.copy(controls.target).add(offset);controls.update();}
  function go(target,distance=110){transition={target:target.clone(),position:target.clone().add(new THREE.Vector3(.45,.65,1).normalize().multiplyScalar(distance))};}
  controls.addEventListener('start',cancelTransition);
  function cancelTransition(){transition=null;follow=false;}
  function setPaused(value){paused=value;controls.enabled=!value;}
  function closeList(){nearby.hidden=true;nearButton.setAttribute('aria-expanded','false');}
  function choose(e){closeList();selectedEvent=e.id;const p=project(e.lng,e.lat);p.y=surfaceHeight(p.x,p.z)+5;go(p,95);native({type:'event',id:e.id});}
  function listing(items){
    nearby.replaceChildren();const header=node('div','sheet-heading');header.append(node('strong','', `События на карте · ${items.length}`));
    const close=node('button','close-sheet','Закрыть');close.onclick=closeList;header.append(close);nearby.append(header);
    if(!items.length)nearby.append(node('p','muted','Здесь пока нет доступных событий. Можно выбрать место на карте и создать своё.'));
    const list=node('div','nearby-items');nearby.append(list);let shown=0;
    const more=node('button','nearby-more');
    function appendPage(){for(const e of items.slice(shown,shown+20)){
      const style=eventStyle(e),summary=eventSummary(e),b=node('button','nearby-event'),icon=node('span','nearby-icon');icon.innerHTML=eventIcon(style.key);icon.style.color=style.color;
      const cover=safeBubbleCover(covers.get(e.id));if(cover){const img=node('img','nearby-cover');img.src=cover;img.alt='';img.loading='lazy';img.onerror=()=>img.remove();icon.append(img);}
      const copy=node('span','nearby-copy');copy.append(node('strong','',e.title||'Событие'),node('span','muted',`${summary.state==='planned'?eventTimeLabel(e):summary.label}${preview?' · демо':''}`));b.append(icon,copy);b.onclick=()=>choose(e);list.append(b);
    }shown=Math.min(items.length,shown+20);more.hidden=shown>=items.length;more.textContent=`Ещё события · ${items.length-shown}`;}
    more.onclick=appendPage;appendPage();nearby.append(more);
    const guide=node('details','event-guide');guide.append(node('summary','',presentation==='bubbles'?'Что означают шарики':'Что означают модели и движение'));
    guide.append(node('p','muted',presentation==='bubbles'?'При приближении в шарике видна обложка. На общем плане — компактный значок; число означает события, не посетителей. У текущих событий рядом появляется условная миниатюра категории: она не подтверждает наличие сцены, поля или людей.'+(preview?' Это тестовое превью.':''):'Миниатюры — условные значки событий, не настоящие сооружения или люди. Движение включено только у текущих событий; подтверждения не гарантируют достоверность.'));
    for(const [key,s]of Object.entries(EVENT_STYLES)){const row=node('div','event-guide-row'),icon=node('span');icon.innerHTML=eventIcon(key);icon.style.color=s.color;row.append(icon,node('span','',presentation==='bubbles'?s.label:`${s.label} · ${s.hint}`));guide.append(row);}nearby.append(guide);
    nearby.hidden=false;nearButton.setAttribute('aria-expanded','true');
  }
  nearButton.onclick=()=>{if(!nearby.hidden){closeList();return;}const target=controls.target;listing([...events].sort((a,b)=>project(a.lng,a.lat).distanceToSquared(target)-project(b.lng,b.lat).distanceToSquared(target)));};
  function save(){avatar.apply(look);let persisted=true;try{localStorage.setItem(storageKey,JSON.stringify(look));}catch{persisted=false;}document.querySelector('#avatar-note').textContent=persisted?'Сохранено на этом устройстве. Геопозиция не используется.':'Образ изменён. Хранилище недоступно: после закрытия он может сброситься.';}
  function updatePalette(){editor.querySelectorAll('[data-palette]').forEach(b=>b.setAttribute('aria-pressed',String(look[b.dataset.palette]===Number(b.dataset.value))));}
  for(const [key,label]of [['skin','Кожа'],['hair','Волосы'],['coat','Одежда']]){
    const section=node('div','palette-section');section.append(node('span','',label));const row=node('div','swatches');
    palettes[key].forEach((hex,i)=>{const b=node('button','swatch');b.style.setProperty('--swatch',`#${hex.toString(16).padStart(6,'0')}`);b.dataset.palette=key;b.dataset.value=i;b.setAttribute('aria-label',`${label}, вариант ${i+1}`);b.onclick=()=>{look[key]=i;save();updatePalette();};row.append(b);});section.append(row);document.querySelector('#avatar-options').append(section);
  }
  const styleButton=document.querySelector('#hair-style'),bagButton=document.querySelector('#backpack');
  function updateChoices(){styleButton.textContent=['Короткие волосы','Длинные волосы','Шапка'][look.style];bagButton.textContent=look.bag?'Рюкзак: да':'Рюкзак: нет';}
  styleButton.onclick=()=>{look.style=(look.style+1)%3;save();updateChoices();};bagButton.onclick=()=>{look.bag=!look.bag;save();updateChoices();};
  const viewButtons=[...editor.querySelectorAll('[data-avatar-view]')],stepButton=document.querySelector('#avatar-step');
  function setView(view='three-quarter'){
    const angles={front:.537,'three-quarter':-.12,back:Math.PI+.537};
    avatar.root.rotation.y=angles[view]??angles.front;
    viewButtons.forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.avatarView===view)));
  }
  viewButtons.forEach(b=>b.onclick=()=>setView(b.dataset.avatarView));
  stepButton.onclick=()=>{previewStep=!previewStep;stepButton.setAttribute('aria-pressed',String(previewStep));};
  function closeEditor(){
    if(!editing)return;editing=false;previewStep=false;portraitLight.visible=false;editor.hidden=true;document.body.classList.remove('editing-avatar');avatar.root.visible=preview;
    if(savedAvatarPosition){avatar.root.position.copy(savedAvatarPosition);savedAvatarPosition=null;}
    if(savedCamera){go(savedCamera.target,1);transition.position.copy(savedCamera.position);}
    savedCamera=null;document.querySelector('#scene-note').textContent=festival?`Фестиваль · ${events.length} вымышленных событий`:preview?'Демо · события и движение тестовые':'Город · события от участников';selfButton.focus();
  }
  document.querySelector('#avatar-done').onclick=closeEditor;
  function frameAvatar(){
    if(!editing)return;
    const top=(document.querySelector('#qa')?.getBoundingClientRect().bottom||4)+12;
    const bottom=innerWidth<700?innerHeight-editor.getBoundingClientRect().height-28:innerHeight;
    const available=Math.max(180,bottom-top);
    const size=Math.min(available*.82,540),tan=Math.tan(THREE.MathUtils.degToRad(camera.fov/2));
    const distance=THREE.MathUtils.clamp(1.95*innerHeight/(2*tan*size),3.8,11);
    const offset=new THREE.Vector3(2.5,1.8,4.2).normalize().multiplyScalar(distance);
    const shift=(innerHeight/2-(top+available/2+8))/innerHeight*2*distance*tan;
    const target=avatar.root.position.clone();target.y+=.95-shift/(Math.hypot(offset.x,offset.z)/distance);
    transition={target,position:target.clone().add(offset)};
  }
  addEventListener('resize',frameAvatar);
  function openEditor(){
    if(editing)return;walking=false;follow=false;closeList();editing=true;previewStep=false;stepButton.setAttribute('aria-pressed','false');savedCamera={position:camera.position.clone(),target:controls.target.clone()};avatar.root.visible=true;setView();portraitLight.visible=true;
    const spot=editorSpot();if(spot){savedAvatarPosition=avatar.root.position.clone();avatar.root.position.copy(spot);}
    document.body.classList.add('editing-avatar');editor.hidden=false;updatePalette();updateChoices();document.querySelector('#avatar-done').focus();document.querySelector('#scene-note').textContent='Пример образа · это не твоя геопозиция';
    frameAvatar();
  }
  selfButton.onclick=openEditor;
  function keydown(e){if(e.key==='Escape'){closeEditor();closeList();}if(editing&&e.key==='Tab'){const focusable=[...editor.querySelectorAll('button')];const first=focusable[0],last=focusable.at(-1);if(e.shiftKey&&document.activeElement===first){last.focus();e.preventDefault();}else if(!e.shiftKey&&document.activeElement===last){first.focus();e.preventDefault();}}}
  document.addEventListener('keydown',keydown);
  function rebuild(distance){
    const bucket=eventGroupBucket(distance,lastBucket);
    if(lastGroup&&bucket===lastBucket)return;lastBucket=bucket;
    const next=groupEvents(events,groupDistanceForBucket(bucket),festival),signature=JSON.stringify(next.map(g=>g.events.map(e=>e.id)));
    if(signature===lastGroup)return;lastGroup=signature;
    // Most events do not change membership at a grouping boundary. Retain their
    // decoded covers, DOM and nearby meshes instead of rebuilding all 150.
    const reusable=new Map(groups.map(g=>[g.reuseKey,g]));groups=[];
    if(festival){if(!beacons){beacons=createEventBeacons();scene.add(beacons.group);}beacons.setEvents(next,surfaceHeight);}
    for(const g of next){
      const reuseKey=`${presentation}:${JSON.stringify(g.events)}`;
      const old=reusable.get(reuseKey);
      if(old){reusable.delete(reuseKey);groups.push(old);continue;}
      if(presentation==='bubbles'){
        const b=createBubble(g,covers);b.onclick=()=>g.events.length===1?choose(g.events[0]):listing(g.events);
        markerRoot.append(b);groups.push({...g,reuseKey,point:new THREE.Vector3(g.x,surfaceHeight(g.x,g.z)+.07,g.z),label:b,visual:null});continue;
      }
      eventModels ||= createEventModels();const visual=eventModels.create(g,surfaceHeight(g.x,g.z));pointGroup.add(visual.root);
      const point=visual.labelPoint();
      const b=node('button','event-pin');const single=g.events.length===1;
      const style=single?eventStyle(g.events[0]):{key:'cluster',color:'#b9c3d3'},summary=single?eventSummary(g.events[0]):null;
      const symbol=node('span','event-symbol');symbol.innerHTML=eventIcon(style.key);const copy=node('span','event-copy');
      copy.append(node('strong','',single?g.events[0].title:`Событий рядом: ${g.events.length}`),node('small','',single?summary.label:'Открыть список'));
      b.style.setProperty('--event-color',style.color);b.dataset.state=summary?.state||'cluster';b.append(symbol,copy);
      b.onclick=()=>single?choose(g.events[0]):listing(g.events);markerRoot.append(b);groups.push({...g,reuseKey,point,visual,label:b});
    }
    for(const g of reusable.values()){g.label.remove();g.visual?.destroy();g.activity?.destroy();}
  }
  function setCovers(values){
    nativeCovers=Array.isArray(values)?values.slice(0,8):[];
    if(preview)return;
    covers=nativeBubbleCovers(nativeCovers,events);
    for(const g of groups)if(!g.visual)updateBubbleCover(g.label,g,covers);
  }
  function setEvents(values,isPreview=false,requestedPresentation='bubbles',dense=false){
    rawEvents=Array.isArray(values)?values.slice(0,150):[];preview=isPreview===true;events=visibleEvents(rawEvents);lastGroup='';
    festival=preview&&dense===true&&requestedPresentation==='bubbles';
    markerRoot.classList.toggle('festival-density',festival);
    if(!festival&&beacons){beacons.destroy();beacons=null;}
    presentation=preview&&requestedPresentation==='models'?'models':'bubbles';
    covers=new Map(preview&&presentation==='bubbles'?rawEvents.filter(e=>e&&safeBubbleCover(e.coverImage)).map(e=>[e.id,e.coverImage]):[]);
    if(!preview)setCovers(nativeCovers);
    coverRequest='';
    markerRoot.classList.toggle('bubble-layer',presentation==='bubbles');
    const demo=preview&&presentation!=='bubbles'&&events.find(e=>e.id==='preview-football');
    if(demo&&!court){
      court=createFootballDemo();court.group.position.copy(project(demo.lng,demo.lat));court.group.position.y=.55;scene.add(court.group);
    }
    if(!demo&&court){court.destroy();court=null;}
    avatar.root.visible=editing||preview||walking;
    document.querySelector('#scene-note').textContent=festival?`Фестиваль · ${events.length} вымышленных событий`:walking?'Прогулка персонажа · демо, не GPS':preview?'Демо · события и движение тестовые':'Город · события от участников';
    closeList();rebuild(camera.position.distanceTo(controls.target));
  }
  let chromeBounds=null,chromeMeasuredAt=-Infinity;
  function update(t,reduced=false){
    if(t-lastPrune>5){lastPrune=t;const next=visibleEvents(rawEvents);if(JSON.stringify(next)!==JSON.stringify(events)){events=next;lastGroup='';}}
    if(transition&&!paused){camera.position.lerp(transition.position,.12);controls.target.lerp(transition.target,.12);if(camera.position.distanceTo(transition.position)<.03&&controls.target.distanceTo(transition.target)<.03)transition=null;}
    const distance=camera.position.distanceTo(controls.target);rebuild(distance);displayLevel=eventDisplayLevel(distance,displayLevel);
    beacons?.update(distance,night,!editing);
    const boxes=[],wantedCovers=[];let count=0,modelCount=0;
    const labelBudget=eventLabelBudget({festival,small:innerWidth<700,quality});
    const nativeTop=document.documentElement.style.getPropertyValue('--native-top'),chromeKey=`${innerWidth}:${innerHeight}:${nativeTop}`;
    // Avoid forcing browser layout after balloon style writes on every frame.
    // Resize/native chrome changes invalidate immediately; QA-only text is
    // remeasured at 4 Hz. Geometry/animation still updates every rendered frame.
    if(!chromeBounds||chromeBounds.key!==chromeKey||t-chromeMeasuredAt>.25){chromeBounds={key:chromeKey,zoom:document.querySelector('.zoom-controls').getBoundingClientRect(),top:Math.max(nativeTop==='compact'?70:12,(document.querySelector('#qa')?.getBoundingClientRect().bottom||0)+8)};chromeMeasuredAt=t;}
    const zoomRect=chromeBounds.zoom,top=chromeBounds.top;
    for(const g of [...groups].sort((a,b)=>a.point.distanceToSquared(controls.target)-b.point.distanceToSquared(controls.target))){
      if(presentation==='bubbles'){
        const p=g.point.clone().project(camera),x=(p.x*.5+.5)*innerWidth,y=(-p.y*.5+.5)*innerHeight;
        setLabelLevel(g.label,displayLevel);
        setLabelClass(g.label,'is-selected',g.events.some(e=>e.id===selectedEvent));
        const rect=bubbleRect(x,y,{width:innerWidth,height:innerHeight,top,small:innerWidth<700,level:displayLevel,festival});
        const show=!editing&&count<labelBudget&&p.z>-1&&p.z<1&&rect&&!labelsOverlap(rect,{x:zoomRect.x,y:zoomRect.y,w:zoomRect.width,h:zoomRect.height},8)&&!boxes.some(b=>labelsOverlap(b,rect,festival?1:4));
        setLabelVisible(g.label,show);setLabelClass(g.label,'balloon-moving',!!show&&displayLevel==='street'&&!paused&&!reduced&&g.events.length===1&&eventSummary(g.events[0]).animate&&count<3);
        const activityVisible=!!show&&displayLevel==='street'&&g.events.length===1&&eventSummary(g.events[0]).animate&&modelCount<(quality==='low'?2:4);
        if(activityVisible&&!g.activity){eventModels||=createEventModels();g.activity=eventModels.create(g,surfaceHeight(g.x,g.z));pointGroup.add(g.activity.root);}
        if(g.activity)g.activity.update(t,{camera,distance:camera.position.distanceTo(g.point),reduced,blocked:paused,visible:activityVisible,index:modelCount,quality,night});
        if(activityVisible)modelCount++;
        if(show){positionLabel(g.label,x,y);boxes.push(rect);count++;const e=g.events.find(e=>e.coverRef);if(e&&displayLevel!=='city')wantedCovers.push(e.id);}continue;
      }
      const view=g.visual.root.position.clone().project(camera),inFrame=view.z>-1&&view.z<1&&Math.abs(view.x)<1.1&&Math.abs(view.y)<1.1;
      const visible=!editing&&inFrame&&modelCount<(quality==='low'?8:12);
      g.visual.update(t,{camera,distance:camera.position.distanceTo(g.visual.root.position),reduced,blocked:paused,visible,index:modelCount,quality,night});if(visible)modelCount++;
      g.point.copy(g.visual.labelPoint());
      const p=g.point.clone().project(camera),x=(p.x*.5+.5)*innerWidth,y=(-p.y*.5+.5)*innerHeight;
      const labelX=THREE.MathUtils.clamp(x,119,Math.max(119,innerWidth-119));
      const rect={x:labelX-110,y:y-48,w:220,h:52};
      const show=visible&&count<5&&p.z>-1&&p.z<1&&x>8&&x<innerWidth-8&&rect.y>top&&rect.y+rect.h<innerHeight-150&&!boxes.some(b=>labelsOverlap(b,rect));
      setLabelVisible(g.label,show);
      if(show){positionLabel(g.label,labelX,y);boxes.push(rect);count++;}
    }
    occupiedLabels=boxes;
    if(!preview&&!paused&&t-lastCoverRequest>.5){const ids=wantedCovers.slice(0,8).sort(),key=JSON.stringify(ids);if(key!==coverRequest){coverRequest=key;native({type:'covers',ids});}lastCoverRequest=t;}
    const dt=lastWalkTime===null?0:Math.min(.1,Math.max(0,t-lastWalkTime));lastWalkTime=t;
    if(!paused){
      if(walking&&!editing&&!reduced){walkPhase+=dt*1.5/62;const x=Math.cos(walkPhase)*62,z=Math.sin(walkPhase)*62;avatar.root.position.set(x,groundHeight(x,z)+.008,z);avatar.root.rotation.y=Math.atan2(-Math.sin(walkPhase),Math.cos(walkPhase));}
      if(follow&&walking&&!editing){followTarget.copy(avatar.root.position);followTarget.y+=.8;controls.target.copy(followTarget);camera.position.copy(followTarget).add(followOffset);}
      avatar.setDetail(editing?0:camera.position.distanceTo(avatar.root.position));
      avatar.update(t,editing?previewStep:walking,reduced);if(court)court.update(t,reduced);
      if(editing){portraitLight.position.copy(camera.position).add(new THREE.Vector3(-1,2,1));portraitLight.target.position.copy(avatar.root.position).y+=1;}
    }
  }
  function setWalking(value){
    closeEditor();closeList();walking=value===true;follow=walking;transition=null;lastWalkTime=null;
    avatar.root.visible=walking||preview;
    document.querySelector('#scene-note').textContent=walking?'Прогулка персонажа · демо, не GPS':festival?`Фестиваль · ${events.length} вымышленных событий`:preview?'Демо · события и движение тестовые':'Город · события от участников';
  }
  function pickObject(object){let o=object;while(o){if(o.userData.eventGroupKey){const g=groups.find(g=>g.key===o.userData.eventGroupKey);if(!g)return false;g.events.length===1?choose(g.events[0]):listing(g.events);return true;}o=o.parent;}return false;}
  function refreshAnchors(){beacons?.refreshHeights(surfaceHeight);for(const g of groups){if(g.visual){g.visual.root.position.y=surfaceHeight(g.x,g.z)+.07;g.point.copy(g.visual.labelPoint());}else g.point.y=surfaceHeight(g.x,g.z)+.07;if(g.activity)g.activity.root.position.y=surfaceHeight(g.x,g.z)+.07;}}
  return {setEvents,setCovers,update,setPaused,closeEditor,setWalking,pickObject,refreshAnchors,labelBounds:()=>occupiedLabels.map(r=>({...r})),pickables:pointGroup,setAppearance:(value,q)=>{night=!!value;quality=q||'balanced';},get walking(){return walking;},get editing(){return editing;},get look(){return {...look};},
    stats:()=>({events:events.length,labels:groups.filter(g=>!g.label.hidden).length,eventModels:groups.filter(g=>g.visual?.root.visible||g.activity?.root.visible).length,animatedEvents:groups.filter(g=>g.visual?.animated||g.activity?.animated||g.label.classList.contains('balloon-moving')).length,eventPresentation:presentation,eventDisplayLevel:displayLevel,festivalDensity:festival,...beacons?.stats(),preview,avatarEditor:editing,walking,avatar:avatar.stats()}),
    destroy(){controls.removeEventListener('start',cancelTransition);document.removeEventListener('keydown',keydown);removeEventListener('resize',frameAvatar);avatar.destroy();court?.destroy();beacons?.destroy();portraitLight.removeFromParent();portraitLight.target.removeFromParent();groups.forEach(g=>{g.visual?.destroy();g.activity?.destroy();});eventModels?.destroy();pointGroup.removeFromParent();markerRoot.replaceChildren();}};
}
