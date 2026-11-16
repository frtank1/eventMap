import * as THREE from 'three';
import {OrbitControls} from '../source/vendor/OrbitControls.js';
import {createCityStream} from '../source/city-stream.js';
import {ContentStore, validateManifest, validateModel} from './content-store.js';
import {seed} from '../generated/seed.js';
import {createSocialLayer} from './social-layer.js';
import {createBaiterekQuarter} from './baiterek-quarter.js';
import {createQuarterEffects} from './quarter-effects.js';
import {cameraDepth} from './quarter-state.js';
import {isMapTap} from './event-style.js';
import {CITY_PLACES} from './city-landmarks.js';
import {decorateQuarterMaterial} from './quarter-materials.js';
import {clampTarget,containsCoordinate,fallbackDistrict,referenceDistrict,CITY_AREAS} from './world-state.js';
import {labelsOverlap} from './social-state.js';
import {ART_VERSION,sceneMood,CITY_PALETTE} from './art-direction.js';
import {createLandmarkGround} from './landmark-ground.js';
import {readBundledJSON,placeMessage} from './place-addresses.js';
import {GeoCatalogStore,GEO_LIMITS} from './geo-catalog-store.js';
import {createLandmarkExclusion} from './landmark-footprints.js';
import {createFrameGovernor} from './frame-governor.js';

const center = [71.43056, 51.12822], scale = 111320 * Math.cos(center[1] * Math.PI / 180);
let placeRequest=0,geoCatalog,geoGeometry='';
const addresses=new GeoCatalogStore({
  fallback:async()=>{const [addresses,places]=await Promise.all([readBundledJSON('addresses.json',GEO_LIMITS.addresses),readBundledJSON('places.json',GEO_LIMITS.places)]);return {addresses,places};},
  activate:activateGeography,
  onStatus:s=>{const el=document.querySelector('#geography');if(el){el.title=s.release;el.textContent=`Адреса и входы · ${s.release.slice(0,19)}${s.state==='kept-working'?' · сохранённая версия':''}`;}}
});
const project = (lng, lat) => new THREE.Vector3((lng - center[0]) * scale, 0, -(lat - center[1]) * 111320);
const native = data => {
  if(data.type==='event')placeRequest++;
  const message={v:1,...data},text = JSON.stringify(message);
  if (window.EventMapNative?.postMessage) window.EventMapNative.postMessage(text);
  else if (window.webkit?.messageHandlers?.EventMapNative) window.webkit.messageHandlers.EventMapNative.postMessage(text);
  else { window.dispatchEvent(new CustomEvent('eventmap-message', {detail: message})); }
};
const info = document.querySelector('#status');
let renderer, controls, city, store, frame, active = true, last = 0, night = false, theme = 'default';
let current = seed, modelRoot, models = [], markers = [], lastEvents = [], nightMaterials = [],landmarkGround;
let scene, camera, sunlight, ambient, terrain, social, preview=false, labelRoot = document.querySelector('#labels');
let quarter,effects,quarterMode=true,quality='balanced',blocked=false,lastNativeAppearance='';
const districts=new Map();let districtId='baiterek';
let cityStatus={};
const allPlaces=()=>models;
const styledPlaces=new Set(CITY_PLACES.map(p=>p.id));
let depthBits=0;
let sceneTime=0,lastSceneTime=null,lastCityUpdate=0,shadowDirty=true,shadowsEnabled=false;
let shadowCenter=null,contextLost=false;
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
const governor=createFrameGovernor({devicePixelRatio,mode:'auto'});
function applyQuality(next){
  quality=next.quality;social?.setAppearance(night,quality);
  renderer?.setPixelRatio(next.pixelRatio);shadowDirty=true;resize();
}
function dispose(root) {
  const mats = new Set(); root?.traverse(o => { o.geometry?.dispose(); if (o.material) mats.add(o.material); });
  for (const m of mats) {m.userData.facade?.dispose();m.dispose();} root?.removeFromParent();
}
function makeModels(pack) {
  validateManifest(pack.manifest);
  const root = new THREE.Group(), entries = [], materials = [];
  try {
    for (const entry of pack.manifest.models) {
      const data = validateModel(pack.models[entry.id], entry.id);
      const group = new THREE.Group(); root.add(group);
      const mats = data.materials.map(x => {
        const m = new THREE.MeshStandardMaterial({color:x.color, roughness:x.roughness, metalness:x.metalness,
          emissive:x.emissive, emissiveIntensity:x.emissiveIntensity, opacity:x.opacity, transparent:x.transparent, side:x.side});
        m.userData = {color:x.color, emissive:x.emissive, intensity:x.emissiveIntensity,landmark:entry.id};
        if(styledPlaces.has(entry.id)&&[0xe6cba5,0x4d9190,CITY_PALETTE.ivory[0],CITY_PALETTE.glass[0]].includes(x.color))m.userData.facade=decorateQuarterMaterial(m,{glass:[0x4d9190,CITY_PALETTE.glass[0]].includes(x.color)});
        materials.push(m); return m;
      });
      for (const x of data.meshes) {
        const g = new THREE.BufferGeometry();
        g.setAttribute('position', new THREE.Float32BufferAttribute(x.positions, 3));
        g.setAttribute('normal', new THREE.Float32BufferAttribute(x.normals, 3));
        g.setIndex(x.indices); g.computeBoundingSphere();
        const mesh = new THREE.Mesh(g, mats[x.material]); mesh.userData.detail = x.detail;
        mesh.castShadow=true;mesh.receiveShadow=true;group.add(mesh);
      }
      group.position.copy(project(entry.lng, entry.lat)); group.userData.place = {...entry,geometryKind:'stylized'};
      entries.push({entry, group});
    }
    return {root, entries, materials};
  } catch (e) { dispose(root); for (const m of materials) m.dispose(); throw e; }
}
function activate(pack) {
  const next = makeModels(pack); // Build first; invalid replacements never erase current geometry.
  scene.add(next.root); dispose(modelRoot); modelRoot = next.root;
  current = pack; models = next.entries; nightMaterials = next.materials;
  landmarkGround?.destroy();landmarkGround=createLandmarkGround(pack.manifest.models,project);scene.add(landmarkGround.group);
  labelRoot.replaceChildren();
  for (const item of allPlaces()) {
    const el = document.createElement('button'); el.textContent = item.entry.title; el.className = 'place-label';
    el.onclick = () => selectPlace({...item.entry,geometryKind:'stylized'}); labelRoot.append(el); item.label = el;
  }
  if(city){city.destroy();city=createWorldStream();scene.add(city.group);}
  populatePlaces();if(quarter)setQuarterMode();appearance(); native({type:'content', release:pack.manifest.release});
}
function createWorldStream(data=geoCatalog){
  const exact=createLandmarkExclusion(models.map(p=>p.entry),data);
  const excludeBuilding=(lng,lat,rings)=>{
    if(rings)return exact(lng,lat,rings);
    // A radius is decoration clearance only, never evidence to erase a building.
    const point=project(lng,lat);
    return models.some(p=>point.distanceTo(p.group.position)<p.entry.radius);
  };
  return createCityStream({center,excludeBuilding,onStatus:s=>{cityStatus=s;shadowDirty=true;}});
}
function activateGeography(ready){
  const signature=JSON.stringify(ready.places.places.map(p=>[p.id,p.review.status,p.footprints]));
  if(signature===geoGeometry){geoCatalog=ready.places;return;}
  // Prepare replacements before touching the working view. Addresses-only
  // updates do not rebuild geometry, move the camera, or recreate event bubbles.
  const nextCity=city?createWorldStream(ready.places):null,nextDistricts=new Map();
  try{for(const id of districts.keys())nextDistricts.set(id,createBaiterekQuarter({district:id,entries:models.map(p=>p.entry),catalog:ready.places}));}
  catch(e){nextCity?.destroy();for(const d of nextDistricts.values())d.destroy();throw e;}
  const previous={catalog:geoCatalog,signature:geoGeometry,city,quarter,districtId,quarterMode,districts:new Map(districts)};
  try{
    geoCatalog=ready.places;geoGeometry=signature;
    if(nextCity){city=nextCity;scene.add(city.group);}
    districts.clear();for(const [id,d]of nextDistricts){districts.set(id,d);scene.add(d.group);}
    if(districts.size)quarter=districts.get(districtId)||districts.values().next().value;
    setQuarterMode();appearance();
  }catch(e){
    nextCity?.destroy();for(const d of districts.values())d.destroy();districts.clear();
    for(const [id,d]of previous.districts)districts.set(id,d);
    geoCatalog=previous.catalog;geoGeometry=previous.signature;city=previous.city;quarter=previous.quarter;districtId=previous.districtId;quarterMode=previous.quarterMode;
    try{appearance();}catch{}throw e;
  }
  // Old GPU resources survive until the whole activation, including appearance,
  // has succeeded. Store/index/manifest and rendered geography stay consistent.
  if(nextCity)previous.city.destroy();for(const d of previous.districts.values())d.destroy();
}
function populatePlaces(){const select=document.querySelector('#city-place-select');if(!select)return;select.replaceChildren();const initial=document.createElement('option');initial.value='';initial.textContent='Выбрать место';select.append(initial);for(const p of [...allPlaces().map(p=>p.entry),...CITY_AREAS]){const o=document.createElement('option');o.value=p.id;o.textContent=p.title;select.append(o);}}
async function selectPlace(p) {
  const revision=++placeRequest,previous=info.textContent;
  info.textContent='Ищем адрес выбранного места…';
  const details=await addresses.lookup(p);
  if(revision!==placeRequest)return;
  if(info.textContent==='Ищем адрес выбранного места…')info.textContent=previous;
  native(placeMessage(p,details));
}
function appearance() {
  if (!scene) return;
  const winter = theme !== 'default';
  const mood=sceneMood(night,theme);
  scene.background = new THREE.Color(mood.sky);
  // Only soften the outer edge; keep the playable district crisp, including night.
  scene.fog = new THREE.Fog(scene.background,11000,45000);
  ambient.intensity=mood.ambient;ambient.color.setHex(mood.skyLight);ambient.groundColor.setHex(mood.groundLight);
  sunlight.intensity=mood.direct;sunlight.color.setHex(mood.sun);renderer.toneMappingExposure=mood.exposure;
  terrain.material.color.setHex(mood.land);
  city?.setNight(night); city?.setTheme(theme);
  quarter?.setAppearance(night,theme);effects?.setNight(night);landmarkGround?.setNight(night);shadowDirty=true;
  social?.setAppearance(night,quality);
  social?.refreshAnchors();
  document.body.classList.toggle('night',night);
  for (const m of nightMaterials) {
    m.color.setHex(m.userData.color); m.emissive.setHex(m.userData.emissive);
    m.emissiveIntensity = m.userData.intensity * (night ? 1.4 : .5);
    m.userData.facade?.setNight(night);m.userData.facade?.setTheme(theme);
    if(styledPlaces.has(m.userData.landmark)&&night){const colors={[0xe6cba5]:CITY_PALETTE.ivory[1],[0x4d9190]:CITY_PALETTE.glass[1],[0x8da89d]:CITY_PALETTE.roof[1],...Object.fromEntries(Object.values(CITY_PALETTE))};if(colors[m.userData.color])m.color.setHex(colors[m.userData.color]);}
    if(night&&m.userData.emissive===0xffcf8a)m.emissiveIntensity=.5;
    // Architectural illumination, not a fake live-event indicator.
    if (night && m.userData.emissive === 0 && m.color.getHSL({}).l > .65) {
      m.emissive.setHex(m.userData.landmark==='baiterek'?0xffdca5:theme === 'newyear' ? 0xf4b75e : 0xbacbd9); m.emissiveIntensity = .25;
    }
    if(m.userData.landmark==='baiterek'&&m.userData.emissive!==0)m.emissiveIntensity=night?.9:.12;
  }
}
let eventPresentation='bubbles';
function setEvents(events) {
  lastEvents = Array.isArray(events) ? events.slice(0, 150) : [];
  social?.setEvents(lastEvents,preview,eventPresentation);
}
function focus(id) {
  social?.closeEditor();social?.setWalking(false);
  if(id==='city'){controls.target.set(0,0,-1000);camera.position.set(4000,12000,12000);controls.update();setQuarterMode();return;}
  const area=CITY_AREAS.find(a=>a.id===id);if(area){controls.target.copy(project(area.lng,area.lat));camera.position.copy(controls.target).add(new THREE.Vector3(400,650,800));controls.update();setQuarterMode();return;}
  const item = allPlaces().find(x => x.entry.id === id); if (!item) return;
  if(['baiterek','arena'].includes(id)){overview(true,id);return;}
  controls.target.copy(item.group.position).add(new THREE.Vector3(0, item.entry.height * .3, 0));
  camera.position.copy(controls.target).add(new THREE.Vector3(330, 300, 430).multiplyScalar(item.entry.radius>150?1.5:1));
  controls.update();setQuarterMode();
}
function setQuarterMode(){
  if(!controls)return;
  const target=controls.target,distance=camera.position.distanceTo(target);
  const covered=city&&[[0,0],[-600,-600],[600,-600],[-600,600],[600,600]].every(([x,z])=>city.coverageAt(target.x+x,target.z+z));
  const reference=referenceDistrict(target,distance,quarterMode&&districtId==='baiterek');
  const fallback=reference||fallbackDistrict(target,distance,models.filter(p=>['baiterek','arena'].includes(p.entry.id)).map(p=>({id:p.entry.id,x:p.group.position.x,z:p.group.position.z})),covered);
  const changed=quarterMode!==!!fallback||fallback&&districtId!==fallback;quarterMode=!!fallback;
  if(fallback&&districts.size){districtId=fallback;if(!districts.has(districtId)){const d=createBaiterekQuarter({district:districtId,entries:models.map(p=>p.entry),catalog:geoCatalog});districts.set(districtId,d);scene.add(d.group);}quarter=districts.get(districtId);}
  for(const [id,d]of districts)d.group.visible=quarterMode&&id===districtId;
  controls.maxDistance=38000;
  if(city)city.group.visible=!quarterMode;
  // Fallback snapshots and streamed tiles are mutually exclusive: no duplicate roads.
  const lightCenter=target;
  if(!shadowCenter||shadowCenter.distanceToSquared(lightCenter)>24**2){
    shadowCenter=lightCenter.clone();shadowDirty=true;
    sunlight?.position.copy(lightCenter).add(new THREE.Vector3(-320,520,260));if(sunlight){sunlight.target.position.copy(lightCenter);sunlight.target.updateMatrixWorld();}
  }
  document.querySelector('#tiles').textContent=reference?'Нұржол · детальный квартал':quarterMode?'Встроенный квартал · подгружаем город':cityStatus.failed?'Часть карты недоступна · нужна сеть':cityStatus.pending?'Подгружаем кварталы…':city.stats().cityOverviewBlocks?'Астана · обзор застройки':`Астана · ${cityStatus.buildings||0} зданий в загруженной области`;
  document.body.classList.toggle('quarter-mode',quarterMode);if(changed)appearance();
}
function overview(close=false,id=districtId){
  social?.closeEditor();social?.setWalking(false);districtId=id;
  const arena=id==='arena',origin=models.find(p=>p.entry.id===id)?.group.position||new THREE.Vector3();
  controls.target.copy(origin).y+=arena?20:close?40:32;
  camera.position.copy(origin).add(arena?new THREE.Vector3(280,300,390):new THREE.Vector3(close?125:180,close?235:390,close?290:460));
  controls.update();setQuarterMode();lastSceneTime=null;shadowDirty=true;
}
let focusRevision = -1, updateRevision = 0, eventSignature = '';
function receive(m) {
  if (!m || m.v !== 1) return;
  if (m.type === 'sync') {
    preview=m.preview===true;
    eventPresentation='bubbles';
    blocked=m.interactionBlocked===true;social?.setPaused(blocked);
    document.documentElement.style.setProperty('--native-top','compact');
    if (!store) receive({v:1,type:'init',manifestUrl:m.manifestUrl});
    const signature=JSON.stringify([preview,m.events]);
    if(signature!==eventSignature){eventSignature=signature;setEvents(m.events);}
    social?.setCovers(m.covers);
    const lookSignature=JSON.stringify([m.night,m.theme]);
    if(lastNativeAppearance!==lookSignature){lastNativeAppearance=lookSignature;receive({v:1,type:'appearance',night:m.night,theme:m.theme});}
    if(m.focusRevision!==focusRevision){focusRevision=m.focusRevision;focus(m.focusId);}
    if(m.updateRevision!==updateRevision){updateRevision=m.updateRevision;void store?.update();void addresses.update();}
    receive({v:1,type:'active',active:m.active});
  }
  if (m.type === 'events') {preview=m.preview===true;eventPresentation=preview&&m.presentation==='bubbles'?'bubbles':'models';setEvents(m.events);}
  if (m.type === 'focus') focus(m.id);
  if (m.type === 'appearance') { night = m.night === true; theme = ['default','winter','newyear'].includes(m.theme) ? m.theme : 'default'; appearance(); }
  if (m.type === 'update') {void store?.update();void addresses.update();}
  if (m.type === 'active') { if(active!==(m.active===true)){lastSceneTime=null;if(m.active===true)void addresses.update({force:false});}active = m.active === true; if (active && !frame) frame = requestAnimationFrame(render); }
  if (m.type === 'init' && !store) {
    store = new ContentStore({seed, activate:pack => { activate(pack); setEvents(lastEvents); }, onStatus:s => {
      const labels = {'checking':'Проверяем обновления…','ready':'Город готов','current':'Актуальная версия', 'updated':'Карта обновлена', 'updated-memory-only':'Обновлено · без дискового кэша', 'cache-unavailable':'Кэш недоступен', 'kept-working':'Оставлена рабочая карта'};
      info.textContent = `${labels[s.state] || s.state} · ${s.release}`;
      native({type:'status', state:s.state, release:s.release});
    }});
    store.start(m.manifestUrl).catch(() => { info.textContent = 'Встроенная карта · сервер не настроен'; native({type:'status', state:'kept-working',release:current.manifest.release}); });
    try{void addresses.start(new URL('places-manifest.json',m.manifestUrl).href).catch(()=>addresses.ensureFallback().catch(()=>{}));}catch{void addresses.ensureFallback().catch(()=>{});}
  }
}
const ray = new THREE.Raycaster(), pointer = new THREE.Vector2(),activePointers=new Set(); let down;
function pick(event) {
  if(social?.editing)return;
  if(blocked||!isMapTap(down,{t:performance.now(),x:event.clientX,y:event.clientY}))return;
  const r = renderer.domElement.getBoundingClientRect();
  pointer.set((event.clientX - r.left) / r.width * 2 - 1, -(event.clientY - r.top) / r.height * 2 + 1);
  ray.setFromCamera(pointer,camera);
  const hits = ray.intersectObjects([social?.pickables,...markers.map(x => x.mesh), modelRoot,quarterMode?quarter?.group:city?.group, terrain].filter(Boolean), true);
  const hit = hits.find(h => { let o = h.object; while(o) { if (!o.visible) return false; o=o.parent; } return true; });
  if (!hit) return;
  if(social?.pickObject(hit.object))return;
  let o=hit.object; while(o) {
    if (o.userData.eventId) { native({type:'event',id:o.userData.eventId}); return; }
    if (o.userData.place) { selectPlace(o.userData.place); return; } o=o.parent;
  }
  const lat = center[1] - hit.point.z / 111320, lng = center[0] + hit.point.x / scale;
  const building=quarterMode?quarter?.placeAt(hit.point.x,hit.point.z):city?.placeAt(hit.point.x,hit.point.z);
  // Keep the actual tapped coordinate. A concave footprint's bounding-box
  // centre can lie in a courtyard, road, or even another building.
  if(building){selectPlace({...building,lat,lng});return;}
  if (containsCoordinate(lng,lat)) selectPlace({title:'Точка на карте',lat,lng});
}
let sampleStart = 0, fps = 0;
function render(t) {
  frame = null; if (!active || contextLost || document.hidden) return;
  frame = requestAnimationFrame(render); if (t-last < 1000/30-1) return; last=t;
  const interval=lastSceneTime===null?0:t-lastSceneTime;lastSceneTime=t;
  const dt=Math.min(.1,interval/1000);if(!blocked)sceneTime+=dt;
  controls.update();
  social?.update(sceneTime,reducedMotion.matches);
  const distance = camera.position.distanceTo(controls.target);
  scene.fog.near=Math.max(6000,distance*1.4);scene.fog.far=Math.max(18000,distance*2.8);
  const depth=cameraDepth(distance,quarterMode);
  if(Math.abs(camera.near-depth.near)>.02||camera.far!==depth.far){camera.near=depth.near;camera.far=depth.far;camera.updateProjectionMatrix();}
  if(t-lastCityUpdate>250){city?.update(controls.target,distance);setQuarterMode();social?.refreshAnchors();lastCityUpdate=t;}
  if(quarterMode)quarter?.update(distance,sceneTime,reducedMotion.matches||blocked,quality);
  else city?.animate(sceneTime,reducedMotion.matches||blocked);
  effects.update(dt,{camera,target:controls.target,distance:camera.position.length(),quality,visible:camera.position.length()<1800,reduced:reducedMotion.matches,blocked,dpr:renderer.getPixelRatio()});
  const shadows=quality==='balanced'&&distance<1200;
  if(shadows!==shadowsEnabled){shadowsEnabled=shadows;renderer.shadowMap.enabled=shadows;shadowDirty=true;}
  if(shadows&&shadowDirty){renderer.shadowMap.needsUpdate=true;shadowDirty=false;}
  // Event covers take priority over place captions; never print both on top of
  // the same stadium/building. Keep their independent label-count budgets.
  const placedLabels=social?.labelBounds()||[];let landmarkLabelCount=0;
  for (const p of allPlaces().sort((a,b)=>camera.position.distanceToSquared(a.group.position)-camera.position.distanceToSquared(b.group.position))) {
    const d = camera.position.distanceTo(p.group.position);
    // The snapshot excludes the source footprints of ALL matched landmarks.
    // Hiding everything but Baiterek would leave holes where those buildings sit.
    const inQuarter=!quarterMode||p.group.position.distanceTo(quarter.center)<(districtId==='arena'?750:1200);
    p.group.visible=inQuarter&&d<Math.max(6500,distance*1.8);
    for (const mesh of p.group.children) if(mesh.userData.detail) mesh.visible = d < 1700;
    const point = p.group.position.clone().add(new THREE.Vector3(0,p.entry.height+16,0)).project(camera);
    if(!p.label)continue;
    const rect={x:(point.x*.5+.5)*innerWidth-Math.min(230,p.entry.title.length*7)/2,y:(-point.y*.5+.5)*innerHeight-26,w:Math.min(230,p.entry.title.length*7),h:28};
    p.label.hidden = !p.group.visible || social?.editing || distance<120 || point.z > 1 || point.z < -1 || Math.abs(point.x)>.9 || Math.abs(point.y)>.7 || landmarkLabelCount>=4 || placedLabels.some(r=>labelsOverlap(r,rect));
    if(!p.label.hidden){placedLabels.push(rect);landmarkLabelCount++;}
    p.label.style.transform=`translate(${(point.x*.5+.5)*innerWidth}px,${(-point.y*.5+.5)*innerHeight}px) translate(-50%,-100%)`;
  }
  renderer.render(scene,camera);
  const measured=governor.sample(interval,{active,blocked,hidden:document.hidden,reducedMotion:reducedMotion.matches});
  window.EventMapPerformanceQA?.sampleFrame({dtMs:interval,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,quality,pixelRatio:renderer.getPixelRatio(),active,blocked,hidden:document.hidden,reducedMotion:reducedMotion.matches,warmingUp:measured.warmingUp});
  if(measured.changed)applyQuality(measured);
  if(t-sampleStart>2000) { fps=measured.frameTiming.fps===null?0:Math.round(measured.frameTiming.fps);sampleStart=t;updateMetrics(); }
}
function updateMetrics(){
  const timing=governor.snapshot().frameTiming,stats=effects.stats();
  document.querySelector('#scene-metrics').textContent=`${timing.samples?`${fps} FPS · p95 ${Math.round(timing.p95Ms)} мс`:'Прогрев · FPS ещё не измерен'}\n${renderer.info.render.calls} вызовов · ${Math.round(renderer.info.render.triangles/1000)}k треуг.\n${stats.particles} частиц · ${governor.mode==='auto'?'авто → ':''}${quality==='low'?'экономичный':'сбалансированный'}\nDPR ${renderer.getPixelRatio().toFixed(2)} · >50 мс: ${timing.slowFrames}/${timing.samples}`;
  document.querySelectorAll('[data-effect]').forEach(b=>b.setAttribute('aria-pressed',String(effects.state[b.dataset.effect])));
  document.querySelector('#quarter-walk').setAttribute('aria-pressed',String(social?.walking===true));
}
function setupSceneControls(){
  const panel=document.querySelector('#scene-settings'),button=document.querySelector('#scene-button');
  const close=()=>{panel.hidden=true;button.setAttribute('aria-expanded','false');};
  button.onclick=()=>{const show=panel.hidden;panel.hidden=!show;button.setAttribute('aria-expanded',String(show));if(show)updateMetrics();};
  document.querySelector('#scene-done').onclick=close;
  for(const id of ['#self-button','#nearby-button'])document.querySelector(id).addEventListener('click',close);
  document.querySelector('#quarter-overview').onclick=()=>{overview();close();};
  document.querySelector('#quarter-close').onclick=()=>{overview(true);close();};
  document.querySelector('#quarter-walk').onclick=()=>{quarterMode=true;districtId='baiterek';setQuarterMode();social.setWalking(!social.walking);close();};
  document.querySelector('#quarter-arena').onclick=()=>{focus('arena');close();};
  document.querySelector('#quarter-baiterek').onclick=()=>{focus('baiterek');close();};
  document.querySelector('#whole-city').onclick=()=>{focus('city');close();};
  document.querySelector('#city-place-select').onchange=e=>{if(e.target.value){focus(e.target.value);close();e.target.value='';}};
  document.querySelector('#scene-quality').onchange=e=>{const mode=['auto','low','balanced'].includes(e.target.value)?e.target.value:'auto';applyQuality(governor.reset({mode,quality:mode==='auto'?'balanced':mode}));updateMetrics();};
  document.querySelector('#show-metrics').onchange=e=>{document.querySelector('#scene-metrics').hidden=!e.target.checked;updateMetrics();};
  document.querySelectorAll('[data-effect]').forEach(b=>b.onclick=()=>{
    if(!quarterMode||districtId!=='baiterek')overview(false,'baiterek');const enabled=!effects.state[b.dataset.effect];
    if(b.dataset.effect==='fireworks'&&enabled){
      // Keep the burst above the tower in frame, even after a close-up/avatar walk.
      social.setWalking(false);controls.target.set(0,70,0);camera.position.set(200,280,360);controls.update();
    }
    effects.set(b.dataset.effect,enabled);updateMetrics();if(b.dataset.effect==='fireworks')close();
  });
  document.addEventListener('keydown',e=>{if(e.key==='Escape')close();});
}
function resize() { if(!renderer || innerWidth<1 || innerHeight<1)return; renderer.setSize(innerWidth,innerHeight,false); camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix(); }
try {
  renderer = new THREE.WebGLRenderer({canvas:document.querySelector('canvas'),antialias:true,alpha:false,powerPreference:'default'});
  const gl=renderer.getContext();depthBits=gl.getParameter(gl.DEPTH_BITS);
  renderer.setPixelRatio(governor.pixelRatio); renderer.outputColorSpace=THREE.SRGBColorSpace;
  renderer.shadowMap.type=THREE.PCFSoftShadowMap;renderer.shadowMap.autoUpdate=false;
  renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;
  scene=new THREE.Scene();camera=new THREE.PerspectiveCamera(42,1,.15,40000);
  ambient=new THREE.HemisphereLight(0xe3efff,0x69593d,1.15);sunlight=new THREE.DirectionalLight(0xffdfaa,3.3);sunlight.position.set(-300,500,250);scene.add(ambient,sunlight);
  sunlight.castShadow=true;sunlight.shadow.mapSize.set(1024,1024);Object.assign(sunlight.shadow.camera,{left:-420,right:420,top:420,bottom:-420,near:10,far:1400});sunlight.shadow.bias=-.0003;sunlight.shadow.normalBias=.5;
  terrain=new THREE.Mesh(new THREE.PlaneGeometry(80000,80000),new THREE.MeshStandardMaterial({color:0x859e7b,roughness:1}));terrain.rotation.x=-Math.PI/2;terrain.position.y=-1;scene.add(terrain);
  controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.dampingFactor=.12;
  controls.minDistance=3;controls.maxDistance=38000;controls.maxPolarAngle=Math.PI*.46;controls.screenSpacePanning=false;
  controls.touches.ONE=THREE.TOUCH.PAN;controls.touches.TWO=THREE.TOUCH.DOLLY_ROTATE;
  controls.addEventListener('change',()=>{clampTarget(controls.target);});
  activate(seed);city=createWorldStream();scene.add(city.group);
  quarter=createBaiterekQuarter({entries:models.map(p=>p.entry),catalog:geoCatalog});districts.set('baiterek',quarter);effects=createQuarterEffects();scene.add(quarter.group,effects.group);focus('baiterek');resize();
  social=createSocialLayer({scene,camera,controls,native,project,groundHeight:(x,z)=>quarterMode?quarter.groundHeight(x,z):.62,editorSpot:()=>{
    // A known clear presentation spot, never the current roof/pitch under the
    // camera: an editor opened at Arena must not bury the legs in its field.
    // The previous view is restored on close. This is explicitly not GPS.
    const x=-24,z=57,y=quarterMode&&districtId==='baiterek'?quarter.groundHeight(x,z):.62;
    return new THREE.Vector3(x,y+.008,z);
  },surfaceHeight:(x,z)=>{
    const landmark=allPlaces().find(p=>p.group.visible&&Math.hypot(p.group.position.x-x,p.group.position.z-z)<p.entry.radius);
    return landmark?landmark.entry.height+4:quarterMode?quarter.surfaceHeight(x,z):city?.placeAt(x,z)?.top||.62;
  }});
  setupSceneControls();
  renderer.domElement.addEventListener('pointerdown',e=>{activePointers.add(e.pointerId);down=activePointers.size===1?{x:e.clientX,y:e.clientY,t:performance.now()}:null;});
  renderer.domElement.addEventListener('pointerup',e=>{if(activePointers.size===1)pick(e);activePointers.delete(e.pointerId);down=null;});
  renderer.domElement.addEventListener('pointercancel',e=>{activePointers.delete(e.pointerId);down=null;});
  renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();contextLost=true;cancelAnimationFrame(frame);frame=null;native({type:'error',state:'graphics-lost'});info.textContent='Восстанавливаем графику…';});
  renderer.domElement.addEventListener('webglcontextrestored',()=>{contextLost=false;shadowDirty=true;lastSceneTime=null;governor.reset();resize();appearance();info.textContent='Графика восстановлена';if(active&&!frame)frame=requestAnimationFrame(render);native({type:'status',state:'graphics-restored',release:current.manifest.release});});
  addEventListener('resize',resize);window.visualViewport?.addEventListener('resize',resize);document.addEventListener('visibilitychange',()=>{lastSceneTime=null;if(!document.hidden){void addresses.update({force:false});if(active&&!frame)frame=requestAnimationFrame(render);}});
  addEventListener('pagehide',()=>{store?.stop();addresses.stop();active=false;cancelAnimationFrame(frame);social?.destroy();districts.forEach(d=>d.destroy());effects?.destroy();landmarkGround?.destroy();controls.dispose();city?.destroy();dispose(modelRoot);for(const x of markers)dispose(x.mesh);renderer.dispose();});
  window.EventMapMobile={receive,stats:()=>({artVersion:ART_VERSION,geographyRelease:addresses.manifest?.release||'bundled',addressCount:addresses.current?.index.count||0,fps,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,geometries:renderer.info.memory.geometries,release:current.manifest.release,quality,qualityMode:governor.mode,pixelRatio:renderer.getPixelRatio(),quarterMode,night,theme,depthBits,depthNear:camera.near,depthFar:camera.far,landmarks:allPlaces().length,...city?.stats(),frameTiming:governor.snapshot().frameTiming,...quarter?.stats(),...effects?.stats(),...social?.stats()})};
  frame=requestAnimationFrame(render);native({type:'ready'});
  // Browser-only local QA. Native supplies the URL through the typed bridge.
  if (['localhost','127.0.0.1'].includes(location.hostname)) receive({v:1,type:'init',manifestUrl:new URL('/world/manifest.json',location.href).href});
} catch (e) { info.textContent='3D недоступна. Можно вернуться к обычной карте.';native({type:'error',state:'graphics-unavailable'});console.error(e); }
