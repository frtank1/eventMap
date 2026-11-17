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
import {CITY_LOD_VERSION,cityLodBand,cityLodWeights} from './city-lod.js';
import {labelsOverlap} from './social-state.js';
import {setLabelVisible,positionLabel} from './event-bubbles.js';
import {ART_VERSION,sceneMood,CITY_PALETTE,landmarkColor,landmarkSwatch} from './art-direction.js';
import {createLandmarkGround} from './landmark-ground.js';
import {readBundledJSON,placeMessage} from './place-addresses.js';
import {GeoCatalogStore,GEO_LIMITS} from './geo-catalog-store.js';
import {createLandmarkExclusion} from './landmark-footprints.js';
import {createFrameGovernor} from './frame-governor.js';
import {createSky,createEnvironmentScene} from './sky.js';
import {createCityBackdrop} from './city-backdrop.js';
import {createFramePacer} from './frame-pacer.js';
import {festivalAllowed,debugSceneAllowed} from './event-density.js';
import {createZoomBenchmark} from './zoom-benchmark.js';
import {createNsdiReplacement,NSDI_LIMITS} from './nsdi-quarter-data.js';

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
let renderer, controls, city, store, frame, active = true, night = false, theme = 'default';
let current = seed, modelRoot, models = [], markers = [], lastEvents = [], nightMaterials = [],landmarkGround;
let scene, camera, sunlight, ambient, terrain, social, preview=false, labelRoot = document.querySelector('#labels');
let quarter,effects,sky,quarterMode=true,quality='balanced',blocked=false,lastNativeAppearance='';
const districts=new Map();let districtId='baiterek';
let cityStatus={};
let backdrop,overviewData,backdropBuild,backdropStopped=false;
let quarterReplacement=null,trialBackdrop=null,trialSnapshot=null,trialRevision=0,trialBusy=false,trialRequested='current',geometryRevision=0,trialBuild=null;
const trialVariant=()=>quarterReplacement?'nsdi':'current';
function makeQuarter(id='baiterek',catalog=geoCatalog,replacement=quarterReplacement){return createBaiterekQuarter({district:id,entries:models.map(p=>p.entry),catalog,replacement});}
const pacer=createFramePacer(30);
async function refreshBackdrop(){
  if(!overviewData||backdropStopped)return;
  backdropBuild?.abort();const job=new AbortController();backdropBuild=job;
  let next,nextOverlay;
  try{
    const options={entries:models.filter(p=>!withheldModels.has(p.entry.id)).map(p=>p.entry),catalog:geoCatalog,signal:job.signal};
    next=await createCityBackdrop(overviewData,{...options,excludeBuilding:quarterReplacement?.ownsGeo});
    if(quarterReplacement)nextOverlay=await createCityBackdrop(quarterReplacement.overview,options);
    if(job.signal.aborted){next.destroy();nextOverlay?.destroy();return;}
    const previous=backdrop,previousOverlay=trialBackdrop;backdrop=next;trialBackdrop=nextOverlay||null;scene.add(next.group);if(trialBackdrop)scene.add(trialBackdrop.group);next=nextOverlay=null;previous?.destroy();previousOverlay?.destroy();city?.setBackdropReady(true);appearance();setQuarterMode();
    native({type:'status',state:'overview-ready'});
  }catch(e){next?.destroy();nextOverlay?.destroy();if(e.name!=='AbortError'){console.warn('City overview unavailable; keeping streamed map.',e.message);if(!backdrop)native({type:'status',state:'overview-unavailable'});}}
}
const allPlaces=()=>models;
const styledPlaces=new Set(CITY_PLACES.map(p=>p.id));
let depthBits=0;
let sceneTime=0,lastSceneTime=null,lastCityUpdate=0,lastAnchorLayout='',shadowDirty=true,shadowsEnabled=false;
let shadowCenter=null,shadowSpan=0,contextLost=false;
// Late-afternoon key light: about 37 degrees above the horizon.
const SUN_OFFSET=new THREE.Vector3(-380,360,300);
let pmrem,environment,environmentKey='';
// Placement under review: the authored museum sits about 45 m off its real
// outline, so drawing it next to the real footprint shows two overlapping
// buildings. Until it is re-placed the real outline alone is shown.
const withheldModels=new Set(['museum']);
// Yaw corrections (radians) for authored models whose long axis does not match
// the real outline in the place catalogue (minimum-area rectangle of both).
// MEGA was 18.8 degrees off, the Palace of Peace 14.6. Fold these into the
// source models at the next export and delete the entries here.
const placementYaw={mega:-0.328,palace:-0.255};
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
        if(styledPlaces.has(entry.id)&&['ivory','glass'].includes(landmarkSwatch(x.color)))m.userData.facade=decorateQuarterMaterial(m,{glass:landmarkSwatch(x.color)==='glass'});
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
      group.position.copy(project(entry.lng, entry.lat)); group.rotation.y=placementYaw[entry.id]||0; group.userData.place = {...entry,geometryKind:'stylized'};
      entries.push({entry, group});
    }
    return {root, entries, materials};
  } catch (e) { dispose(root); for (const m of materials) m.dispose(); throw e; }
}
function activate(pack) {
  geometryRevision++;
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
  void refreshBackdrop();
  populatePlaces();if(quarter)setQuarterMode();appearance(); native({type:'content', release:pack.manifest.release});
}
function createWorldStream(data=geoCatalog,replacement=quarterReplacement){
  const exact=createLandmarkExclusion(models.map(p=>p.entry),data);
  const excludeBuilding=(lng,lat,rings)=>{
    if(rings)return replacement?.ownsGeo(rings)||exact(lng,lat,rings);
    // A radius is decoration clearance only, never evidence to erase a building.
    const point=project(lng,lat);
    return replacement?.occupies(lng,lat)||models.some(p=>point.distanceTo(p.group.position)<p.entry.radius);
  };
  const stream=createCityStream({center,excludeBuilding,onStatus:s=>{cityStatus=s;shadowDirty=true;}});
  stream.setBackdropReady(!!backdrop);return stream;
}
function activateGeography(ready){
  const signature=JSON.stringify(ready.places.places.map(p=>[p.id,p.review.status,p.footprints]));
  if(signature===geoGeometry){geoCatalog=ready.places;return;}
  geometryRevision++;
  // Prepare replacements before touching the working view. Addresses-only
  // updates do not rebuild geometry, move the camera, or recreate event bubbles.
  const nextCity=city?createWorldStream(ready.places):null,nextDistricts=new Map();
  try{for(const id of districts.keys())nextDistricts.set(id,makeQuarter(id,ready.places));}
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
  void refreshBackdrop();
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
  sky?.setMood({top:mood.zenith,horizon:mood.sky,cloud:mood.cloud,glow:night?0x5f7fb5:0xfff0c8,night,sun:SUN_OFFSET});
  const key=`${night}:${theme}`;
  if(key!==environmentKey){
    // One small prefiltered sky per day/night change; no per-frame cost.
    environmentKey=key;pmrem||=new THREE.PMREMGenerator(renderer);
    const source=createEnvironmentScene({top:mood.zenith,horizon:mood.sky,ground:mood.groundLight,glow:night?0x6f8fc5:0xffe2b0,sun:SUN_OFFSET});
    const next=pmrem.fromScene(source.scene,0,1,50);source.dispose();environment?.dispose();environment=next;
    scene.environment=next.texture;scene.environmentIntensity=night?.22:.2;
  }
  ambient.intensity=mood.ambient;ambient.color.setHex(mood.skyLight);ambient.groundColor.setHex(mood.groundLight);
  sunlight.intensity=mood.direct;sunlight.color.setHex(mood.sun);renderer.toneMappingExposure=mood.exposure;
  terrain.material.color.setHex(mood.land);
  city?.setNight(night); city?.setTheme(theme);
  backdrop?.setAppearance(night,theme);
  trialBackdrop?.setAppearance(night,theme);
  quarter?.setAppearance(night,theme);effects?.setNight(night);landmarkGround?.setNight(night);shadowDirty=true;
  social?.setAppearance(night,quality);
  social?.refreshAnchors();
  document.body.classList.toggle('night',night);
  for (const m of nightMaterials) {
    m.color.setHex(m.userData.color); m.emissive.setHex(m.userData.emissive);
    if(styledPlaces.has(m.userData.landmark))m.color.setHex(landmarkColor(m.userData.color,night));
    m.emissiveIntensity = m.userData.intensity * (night ? 1.4 : .5);
    m.userData.facade?.setNight(night);m.userData.facade?.setTheme(theme);
    if(night&&m.userData.emissive===0xffcf8a)m.emissiveIntensity=.5;
    // Architectural illumination, not a fake live-event indicator.
    if (night && m.userData.emissive === 0 && m.color.getHSL({}).l > .65) {
      m.emissive.setHex(m.userData.landmark==='baiterek'?0xffdca5:theme === 'newyear' ? 0xf4b75e : 0xbacbd9); m.emissiveIntensity = .25;
    }
    if(m.userData.landmark==='baiterek'&&m.userData.emissive!==0)m.emissiveIntensity=night?.9:.3;
  }
}
let eventPresentation='bubbles',festivalDensity=false;
let zoomBenchmark=null,benchmarkRevision=0,benchmarkLods=[],benchmarkOrigin={x:0,z:0,id:'center'},benchmarkCoverage={};
const debugContext=()=>({hostname:location.hostname,native:!!(window.EventMapNative||window.webkit?.messageHandlers?.EventMapNative),debugPreview:window.EventMapDebugPreview===true});
function trialUi(){
  const button=document.querySelector('#quarter-source');if(!button)return;
  button.hidden=!debugSceneAllowed({preview:true},debugContext());button.disabled=trialBusy||!backdrop;
  button.textContent=trialBusy?'Готовим вариант…':quarterReplacement?'НИПД · тест → текущая':'Текущая → НИПД · тест';
  const note=document.querySelector('#quarter-source-note');note.hidden=button.hidden;
  note.textContent=quarterReplacement?`НИПД: ${quarterReplacement.stats.features} объектов; высоты ≈ этажи × 3,3 м. Исключений: ${quarterReplacement.stats.rejected}. Только локальный тест.`:'Сравнение квартала без смены камеры, событий и персонажа.';
}
async function switchQuarterVariant(variant){
  if(!['current','nsdi'].includes(variant)||!debugSceneAllowed({preview:true},debugContext())||variant===trialRequested&&trialBusy)return;
  trialRequested=variant;
  if(variant===trialVariant()&&!trialBusy){trialUi();return;}
  if(!overviewData||!backdrop){native({type:'status',state:'quarter-trial-failed',title:'Дождись загрузки объёмной карты',variant:trialVariant()});return;}
  const revision=++trialRevision,geoRevision=geometryRevision;trialBusy=true;finishZoomBenchmark('quarter-variant');trialUi();
  native({type:'status',state:'quarter-trial-loading',title:'Готовим тестовый квартал; текущая карта остаётся на экране'});
  trialBuild?.abort();const controller=new AbortController();trialBuild=controller;
  const deadline=setTimeout(()=>controller.abort(),30000);
  let nextBackdrop,nextOverlay,nextQuarter,nextCity,previous;
  try{
    let replacement=null;
    if(variant==='nsdi'){
      if(!trialSnapshot)trialSnapshot=await readBundledJSON(debugContext().native?'/assets/eventmap-nsdi/quarter.json':'/test/nsdi-quarter.json',NSDI_LIMITS.bytes);
      replacement=createNsdiReplacement(trialSnapshot);
    }
    if(controller.signal.aborted)throw Error('Подготовка превысила 30 секунд — попробуй ещё раз');
    const options={entries:models.filter(p=>!withheldModels.has(p.entry.id)).map(p=>p.entry),catalog:geoCatalog,signal:controller.signal};
    nextQuarter=makeQuarter('baiterek',geoCatalog,replacement);nextCity=createWorldStream(geoCatalog,replacement);
    nextBackdrop=await createCityBackdrop(overviewData,{...options,excludeBuilding:replacement?.ownsGeo});
    if(replacement)nextOverlay=await createCityBackdrop(replacement.overview,options);
    if(controller.signal.aborted)throw Error('Подготовка превысила 30 секунд — попробуй ещё раз');
    if(revision!==trialRevision||backdropStopped||geoRevision!==geometryRevision)throw Error('Карта обновилась во время подготовки — повтори сравнение');
    // Commit only fully constructed geometry. Camera, social layer and event
    // identities are untouched; no requests to the government portal at runtime.
    backdropBuild?.abort();
    previous={backdrop,overlay:trialBackdrop,quarter:districts.get('baiterek'),activeQuarter:quarter,city,replacement:quarterReplacement};
    backdrop=nextBackdrop;trialBackdrop=nextOverlay||null;city=nextCity;quarterReplacement=replacement;
    districts.set('baiterek',nextQuarter);if(districtId==='baiterek')quarter=nextQuarter;
    scene.add(backdrop.group,city.group,nextQuarter.group);if(trialBackdrop)scene.add(trialBackdrop.group);
    city.setBackdropReady(true);setQuarterMode();appearance();lastAnchorLayout='';social?.refreshAnchors();shadowDirty=true;
    previous.backdrop?.destroy();previous.overlay?.destroy();previous.quarter?.destroy();previous.city?.destroy();previous=null;
    nextBackdrop=nextOverlay=nextQuarter=nextCity=null;
    native({type:'status',state:'quarter-trial-ready',title:replacement?'НИПД · тестовый квартал · высоты расчётные':'Текущая карта восстановлена',variant:trialVariant()});
  }catch(e){
    if(previous){
      backdrop=previous.backdrop;trialBackdrop=previous.overlay;quarter=previous.activeQuarter;city=previous.city;quarterReplacement=previous.replacement;districts.set('baiterek',previous.quarter);
      try{setQuarterMode();appearance();social?.refreshAnchors();}catch{}
    }
    nextBackdrop?.destroy();nextOverlay?.destroy();nextQuarter?.destroy();nextCity?.destroy();
    if(revision===trialRevision){trialRequested=trialVariant();native({type:'status',state:'quarter-trial-failed',title:`Оставлена текущая карта: ${e.message}`,variant:trialVariant()});}
  }finally{clearTimeout(deadline);if(trialBuild===controller)trialBuild=null;if(revision===trialRevision){trialBusy=false;trialUi();}}
}
function finishZoomBenchmark(reason){
  if(!zoomBenchmark)return;
  if(reason)zoomBenchmark.cancel(reason);
  const benchmark=zoomBenchmark.report();zoomBenchmark=null;
  native({type:'status',state:'benchmark-result',benchmark:{...benchmark,lodVersion:CITY_LOD_VERSION,area:benchmarkOrigin.id,coverage:benchmarkCoverage,quarterVariant:trialVariant(),lodTransitions:benchmarkLods,events:social?.stats().events,viewport:[innerWidth,innerHeight],night}});
}
function receiveBenchmark(m){
  if(!debugSceneAllowed(m,debugContext())||!Number.isSafeInteger(m.benchmarkRevision)||m.benchmarkRevision<=benchmarkRevision)return;
  benchmarkRevision=m.benchmarkRevision;
  if(m.benchmarkCommand==='stop'){finishZoomBenchmark('user');return;}
  if(m.benchmarkCommand!=='start'||blocked||!active)return;
  if(!backdrop||trialBusy){native({type:'status',state:'benchmark-result',benchmark:{status:'unavailable',reason:trialBusy?'quarter-preparing':'overview-not-ready'}});return;}
  finishZoomBenchmark('restarted');social?.closeEditor();social?.setWalking(false);focus('city');
  const area=CITY_AREAS.find(a=>a.id===m.benchmarkArea),origin=area?project(area.lng,area.lat):{x:0,z:0};
  benchmarkOrigin={x:origin.x,z:origin.z,id:area?.id||'center'};
  zoomBenchmark=createZoomBenchmark();benchmarkLods=[];benchmarkCoverage={maxTiles:0,maxBuildings:0,streamFrames:0,quarterFrames:0};
  native({type:'status',state:'benchmark-progress',title:'Прогрев · затем город → квартал → здание → город'});
}
function setEvents(events) {
  lastEvents = Array.isArray(events) ? events.slice(0, 150) : [];
  social?.setEvents(lastEvents,preview,eventPresentation,festivalDensity);
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
  if(fallback&&districts.size){districtId=fallback;if(!districts.has(districtId)){const d=makeQuarter(districtId);districts.set(districtId,d);scene.add(d.group);}quarter=districts.get(districtId);}
  for(const [id,d]of districts)d.group.visible=quarterMode&&id===districtId;
  controls.maxDistance=38000;
  if(city)city.group.visible=!quarterMode;
  // Fallback snapshots and streamed tiles are mutually exclusive: no duplicate roads.
  const lightCenter=target;
  // Fit the single shadow map to what the camera can actually see: a close
  // view gets crisp shadows instead of one texel per metre.
  const span=Math.round(THREE.MathUtils.clamp(distance*1.15,150,420)/30)*30;
  if(!shadowCenter||shadowCenter.distanceToSquared(lightCenter)>24**2||span!==shadowSpan){
    shadowCenter=lightCenter.clone();shadowSpan=span;shadowDirty=true;
    if(sunlight){
      sunlight.position.copy(lightCenter).add(SUN_OFFSET);sunlight.target.position.copy(lightCenter);sunlight.target.updateMatrixWorld();
      Object.assign(sunlight.shadow.camera,{left:-span,right:span,top:span,bottom:-span});sunlight.shadow.camera.updateProjectionMatrix();
    }
  }
  document.querySelector('#tiles').textContent=reference?'Нұржол · детальный квартал':quarterMode?'Встроенный квартал':backdrop&&distance>6200?'Астана · объёмный город':cityStatus.failed?'Объёмный город · детали недоступны':cityStatus.pending?'Подгружаем детали кварталов…':`Астана · ${cityStatus.buildings||0} подробных зданий`;
  document.body.classList.toggle('quarter-mode',quarterMode);if(changed)appearance();trialUi();
}
function overview(close=false,id=districtId){
  social?.closeEditor();social?.setWalking(false);districtId=id;
  const arena=id==='arena',origin=models.find(p=>p.entry.id===id)?.group.position||new THREE.Vector3();
  controls.target.copy(origin).y+=arena?20:close?46:32;
  camera.position.copy(origin).add(arena?new THREE.Vector3(280,300,390):new THREE.Vector3(close?150:180,close?118:390,close?300:460));
  controls.update();setQuarterMode();lastSceneTime=null;shadowDirty=true;
}
let focusRevision = -1, updateRevision = 0, eventSignature = '';
function receive(m) {
  if (!m || m.v !== 1) return;
  if (m.type === 'sync') {
    preview=m.preview===true;
    festivalDensity=festivalAllowed(m,debugContext());
    eventPresentation='bubbles';
    blocked=m.interactionBlocked===true;social?.setPaused(blocked);
    document.documentElement.style.setProperty('--native-top','compact');
    if (!store) receive({v:1,type:'init',manifestUrl:m.manifestUrl});
    const signature=JSON.stringify([preview,festivalDensity,m.events]);
    if(signature!==eventSignature){eventSignature=signature;setEvents(m.events);}
    social?.setCovers(m.covers);
    const lookSignature=JSON.stringify([m.night,m.theme]);
    if(lastNativeAppearance!==lookSignature){lastNativeAppearance=lookSignature;receive({v:1,type:'appearance',night:m.night,theme:m.theme});}
    if(m.focusRevision!==focusRevision){focusRevision=m.focusRevision;focus(m.focusId);}
    if(m.updateRevision!==updateRevision){updateRevision=m.updateRevision;void store?.update();void addresses.update();}
    receive({v:1,type:'active',active:m.active});
    if(blocked)finishZoomBenchmark('interaction-blocked');
    receiveBenchmark(m);
    if(debugSceneAllowed(m,debugContext())&&['current','nsdi'].includes(m.quarterVariant)&&m.quarterVariant!==trialRequested)void switchQuarterVariant(m.quarterVariant);
    trialUi();
  }
  if (m.type === 'events') {preview=m.preview===true;festivalDensity=festivalAllowed(m,debugContext());eventPresentation=preview&&m.presentation==='bubbles'?'bubbles':'models';setEvents(m.events);}
  if (m.type === 'benchmark') receiveBenchmark(m);
  if (m.type === 'quarter-variant'&&debugSceneAllowed(m,debugContext()))void switchQuarterVariant(m.variant);
  if (m.type === 'focus') focus(m.id);
  if (m.type === 'appearance') { night = m.night === true; theme = ['default','winter','newyear'].includes(m.theme) ? m.theme : 'default'; appearance(); }
  if (m.type === 'update') {void store?.update();void addresses.update();}
  if (m.type === 'active') { if(active!==(m.active===true)){lastSceneTime=null;if(m.active===true)void addresses.update({force:false});}active = m.active === true; if(!active)finishZoomBenchmark('inactive');if (active && !frame) frame = requestAnimationFrame(render); }
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
  const hits = ray.intersectObjects([social?.pickables,...markers.map(x => x.mesh), modelRoot,quarterMode?quarter?.group:city?.group,trialBackdrop?.group,backdrop?.group, terrain].filter(Boolean), true);
  const hit = hits.find(h => { let o = h.object; while(o) { if (!o.visible) return false; o=o.parent; } return true; });
  if (!hit) return;
  if(social?.pickObject(hit.object))return;
  let o=hit.object; while(o) {
    if (o.userData.eventId) { native({type:'event',id:o.userData.eventId}); return; }
    if (o.userData.place) { selectPlace(o.userData.place); return; } o=o.parent;
  }
  const lat = center[1] - hit.point.z / 111320, lng = center[0] + hit.point.x / scale;
  const building=(quarterMode?quarter?.placeAt(hit.point.x,hit.point.z):trialBackdrop?.placeAt(hit.point.x,hit.point.z)||city?.placeAt(hit.point.x,hit.point.z))||backdrop?.placeAt(hit.point.x,hit.point.z);
  // Keep the actual tapped coordinate. A concave footprint's bounding-box
  // centre can lie in a courtyard, road, or even another building.
  if(building){selectPlace({...building,lat,lng});return;}
  if (containsCoordinate(lng,lat)) selectPlace({title:'Точка на карте',lat,lng});
}
let sampleStart = 0, fps = 0;
function render(t) {
  frame = null; if (!active || contextLost || document.hidden) return;
  frame = requestAnimationFrame(render); if (!pacer.due(t)) return;
  const interval=lastSceneTime===null?0:t-lastSceneTime;lastSceneTime=t;
  const dt=Math.min(.1,interval/1000);if(!blocked)sceneTime+=dt;
  if(zoomBenchmark){const p=zoomBenchmark.pose();controls.target.set(benchmarkOrigin.x,p.targetY,benchmarkOrigin.z+p.targetZ);camera.position.copy(controls.target).add(new THREE.Vector3(Math.sin(p.azimuth)*Math.cos(p.elevation),Math.sin(p.elevation),Math.cos(p.azimuth)*Math.cos(p.elevation)).multiplyScalar(p.distance));}
  controls.update();
  social?.update(sceneTime,reducedMotion.matches);
  const distance = camera.position.distanceTo(controls.target);
  // Near the reference quarter a soft horizon haze gives depth; other zooms keep
  // the previous far-only fade so whole-city views stay crisp.
  // Fade into the horizon exactly where content ends: the authored quarter
  // reaches about 1.2 km, the streamed city as far as its current tile ring.
  // Unloaded ground must never read as an empty field behind the city.
  const reach=quarterMode?1450:(city?.reach()||6000);
  scene.fog.near=backdrop?distance+24000:distance+reach*(quarterMode?.18:.38);scene.fog.far=backdrop?Math.min(72000,distance+49000):distance+reach*1.03;
  sky.update(camera,sceneTime);
  const depth=cameraDepth(distance,quarterMode,!!backdrop);
  if(Math.abs(camera.near-depth.near)>.02||camera.far!==depth.far){camera.near=depth.near;camera.far=depth.far;camera.updateProjectionMatrix();}
  if(t-lastCityUpdate>250){
    city?.update(controls.target,distance);setQuarterMode();
    const layout=JSON.stringify([quarterMode,districtId,!!backdrop,trialVariant(),city?.coverageBounds()]);
    if(layout!==lastAnchorLayout){lastAnchorLayout=layout;social?.refreshAnchors();}
    lastCityUpdate=t;
  }
  city?.setLod(distance,quality);
  backdrop?.update(camera,quarterMode?[]:city?.coverageBounds(),quarterMode?{x:quarter.center.x,z:quarter.center.z,radius:districtId==='arena'?750:1200}:null,city?.detailWeight()??1);
  // The same replacement survives EVERY city zoom; only the authored close
  // quarter owns its near LOD. Stream tiles must never cover this overlay.
  if(trialBackdrop){trialBackdrop.group.visible=!(quarterMode&&districtId==='baiterek');trialBackdrop.update(camera);}
  if(zoomBenchmark){
    const coverage=city?.stats();benchmarkCoverage.maxTiles=Math.max(benchmarkCoverage.maxTiles,coverage?.cityTiles||0);benchmarkCoverage.maxBuildings=Math.max(benchmarkCoverage.maxBuildings,coverage?.cityBuildings||0);
    if(quarterMode)benchmarkCoverage.quarterFrames++;else if((coverage?.cityBuildings||0)>0&&distance<3100)benchmarkCoverage.streamFrames++;
    const band=cityLodBand(distance,benchmarkLods.at(-1)?.band).id;
    if(benchmarkLods.at(-1)?.band!==band&&benchmarkLods.length<32)benchmarkLods.push({band,distance:Math.round(distance),nearVisible:quarterMode&&districtId==='baiterek',replacementVisible:trialBackdrop?.group.visible||false,detailWeight:city?.detailWeight()??1});
  }
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
    p.group.visible=(backdrop||inQuarter)&&d<Math.max(backdrop?80000:6500,distance*1.8)&&!withheldModels.has(p.entry.id);
    for (const mesh of p.group.children) if(mesh.userData.detail) mesh.visible = d < 1700;
    const point = p.group.position.clone().add(new THREE.Vector3(0,p.entry.height+16,0)).project(camera);
    if(!p.label)continue;
    const rect={x:(point.x*.5+.5)*innerWidth-Math.min(230,p.entry.title.length*7)/2,y:(-point.y*.5+.5)*innerHeight-26,w:Math.min(230,p.entry.title.length*7),h:28};
    const hidden=!p.group.visible || social?.editing || distance<120 || point.z > 1 || point.z < -1 || Math.abs(point.x)>.9 || Math.abs(point.y)>.7 || landmarkLabelCount>=4 || placedLabels.some(r=>labelsOverlap(r,rect));
    setLabelVisible(p.label,!hidden);
    if(!hidden){placedLabels.push(rect);landmarkLabelCount++;positionLabel(p.label,(point.x*.5+.5)*innerWidth,(-point.y*.5+.5)*innerHeight);}
  }
  renderer.render(scene,camera);
  const measured=governor.sample(interval,{active,blocked,hidden:document.hidden,reducedMotion:reducedMotion.matches});
  window.EventMapPerformanceQA?.sampleFrame({dtMs:interval,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,quality,pixelRatio:renderer.getPixelRatio(),active,blocked,hidden:document.hidden,reducedMotion:reducedMotion.matches,warmingUp:measured.warmingUp});
  if(zoomBenchmark){zoomBenchmark.sample({dtMs:interval,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,quality,pixelRatio:renderer.getPixelRatio(),active,blocked,hidden:document.hidden,reducedMotion:reducedMotion.matches});if(!zoomBenchmark.running)finishZoomBenchmark();}
  if(measured.changed)applyQuality(measured);
  if(t-sampleStart>2000) { fps=measured.frameTiming.fps===null?0:Math.round(measured.frameTiming.fps);sampleStart=t;updateMetrics();if(zoomBenchmark)native({type:'status',state:'benchmark-progress',title:`Прогон ${Math.round(zoomBenchmark.progress*100)}% · ${zoomBenchmark.pose().phase}`}); }
}
function updateMetrics(){
  const timing=governor.snapshot().frameTiming,stats=effects.stats();
  document.querySelector('#scene-metrics').textContent=`${timing.samples?`${fps} FPS · p95 ${Math.round(timing.p95Ms)} мс`:'Прогрев · FPS ещё не измерен'}\nСлой: ${cityLodBand(camera.position.distanceTo(controls.target)).title} · 6 уровней\n${renderer.info.render.calls} вызовов · ${Math.round(renderer.info.render.triangles/1000)}k треуг.\n${stats.particles} частиц · ${governor.mode==='auto'?'авто → ':''}${quality==='low'?'экономичный':'сбалансированный'}\nDPR ${renderer.getPixelRatio().toFixed(2)} · >50 мс: ${timing.slowFrames}/${timing.samples}`;
  document.querySelectorAll('[data-effect]').forEach(b=>b.setAttribute('aria-pressed',String(effects.state[b.dataset.effect])));
  document.querySelector('#quarter-walk').setAttribute('aria-pressed',String(social?.walking===true));
}
function setupSceneControls(){
  document.querySelector('#quarter-source').onclick=()=>void switchQuarterVariant(quarterReplacement?'current':'nsdi');
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
  renderer.toneMapping=THREE.NeutralToneMapping;renderer.toneMappingExposure=1.05;
  scene=new THREE.Scene();camera=new THREE.PerspectiveCamera(42,1,.15,40000);
  ambient=new THREE.HemisphereLight(0xe3efff,0x69593d,1.15);sunlight=new THREE.DirectionalLight(0xffdfaa,3.3);sunlight.position.set(-300,500,250);scene.add(ambient,sunlight);
  sunlight.castShadow=true;sunlight.shadow.mapSize.set(2048,2048);Object.assign(sunlight.shadow.camera,{left:-420,right:420,top:420,bottom:-420,near:10,far:1400});sunlight.shadow.bias=-.0003;sunlight.shadow.normalBias=.5;sunlight.shadow.radius=2.2;
  terrain=new THREE.Mesh(new THREE.PlaneGeometry(160000,160000),new THREE.MeshStandardMaterial({color:0x859e7b,roughness:1}));terrain.rotation.x=-Math.PI/2;terrain.position.y=-1;terrain.receiveShadow=true;scene.add(terrain);
  controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.dampingFactor=.12;
  controls.minDistance=3;controls.maxDistance=38000;controls.maxPolarAngle=Math.PI*.46;controls.screenSpacePanning=false;
  controls.touches.ONE=THREE.TOUCH.PAN;controls.touches.TWO=THREE.TOUCH.DOLLY_ROTATE;
  controls.addEventListener('change',()=>{clampTarget(controls.target);});
  controls.addEventListener('start',()=>finishZoomBenchmark('gesture'));
  sky=createSky();scene.add(sky.group);
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
    return landmark?landmark.entry.height+4:quarterMode?quarter.surfaceHeight(x,z):trialBackdrop?.placeAt(x,z)?.top||city?.placeAt(x,z)?.top||backdrop?.placeAt(x,z)?.top||.62;
  }});
  setupSceneControls();
  renderer.domElement.addEventListener('pointerdown',e=>{activePointers.add(e.pointerId);down=activePointers.size===1?{x:e.clientX,y:e.clientY,t:performance.now()}:null;});
  renderer.domElement.addEventListener('pointerup',e=>{if(activePointers.size===1)pick(e);activePointers.delete(e.pointerId);down=null;});
  renderer.domElement.addEventListener('pointercancel',e=>{activePointers.delete(e.pointerId);down=null;});
  renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();finishZoomBenchmark('graphics-lost');contextLost=true;cancelAnimationFrame(frame);frame=null;native({type:'error',state:'graphics-lost'});info.textContent='Восстанавливаем графику…';});
  renderer.domElement.addEventListener('webglcontextrestored',()=>{contextLost=false;shadowDirty=true;lastSceneTime=null;environmentKey='';governor.reset();resize();appearance();info.textContent='Графика восстановлена';if(active&&!frame)frame=requestAnimationFrame(render);native({type:'status',state:'graphics-restored',release:current.manifest.release});});
  addEventListener('pagehide',()=>{backdropStopped=true;trialRevision++;trialBuild?.abort();trialBackdrop?.destroy();});
  addEventListener('resize',resize);window.visualViewport?.addEventListener('resize',resize);document.addEventListener('visibilitychange',()=>{lastSceneTime=null;if(document.hidden)finishZoomBenchmark('hidden');if(!document.hidden){void addresses.update({force:false});if(active&&!frame)frame=requestAnimationFrame(render);}});
  addEventListener('pagehide',()=>{backdropBuild?.abort();backdrop?.destroy();store?.stop();addresses.stop();active=false;cancelAnimationFrame(frame);social?.destroy();districts.forEach(d=>d.destroy());effects?.destroy();sky?.destroy();environment?.dispose();pmrem?.dispose();landmarkGround?.destroy();controls.dispose();city?.destroy();dispose(modelRoot);for(const x of markers)dispose(x.mesh);renderer.dispose();});
  window.EventMapMobile={receive,stats:()=>({artVersion:ART_VERSION,lodVersion:CITY_LOD_VERSION,layer:cityLodBand(camera.position.distanceTo(controls.target)).id,layerWeights:cityLodWeights(camera.position.distanceTo(controls.target),quality),quarterVariant:trialVariant(),trialBusy,trialData:quarterReplacement?.stats||null,trialLod:trialBackdrop?(quarterMode&&districtId==='baiterek'?'near-quarter':'city-silhouette'):null,trialSilhouette:trialBackdrop?.stats()||null,cameraDistance:camera.position.distanceTo(controls.target),geographyRelease:addresses.manifest?.release||'bundled',addressCount:addresses.current?.index.count||0,fps,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,geometries:renderer.info.memory.geometries,release:current.manifest.release,quality,qualityMode:governor.mode,pixelRatio:renderer.getPixelRatio(),quarterMode,night,theme,depthBits,depthNear:camera.near,depthFar:camera.far,landmarks:allPlaces().length,...city?.stats(),...backdrop?.stats(),frameTiming:governor.snapshot().frameTiming,...quarter?.stats(),...effects?.stats(),...social?.stats()})};
  frame=requestAnimationFrame(render);native({type:'ready'});
  // The 7 MB bundled overview can exceed the ordinary 6 s read deadline on
  // Android cold start. Keep a bounded local-only allowance; API timeouts stay unchanged.
  void readBundledJSON('overview.json',16*1024*1024,{timeoutMs:30000}).then(data=>{overviewData=data;return refreshBackdrop();}).catch(e=>{console.warn('City overview unavailable:',e.message);native({type:'status',state:'overview-unavailable'});});
  // Browser-only local QA. Native supplies the URL through the typed bridge.
  if (['localhost','127.0.0.1'].includes(location.hostname)) receive({v:1,type:'init',manifestUrl:new URL('/world/manifest.json',location.href).href});
} catch (e) { info.textContent='3D недоступна. Можно вернуться к обычной карте.';native({type:'error',state:'graphics-unavailable'});console.error(e); }
