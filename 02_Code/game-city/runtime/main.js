import * as THREE from 'three';
import {OrbitControls} from '../source/vendor/OrbitControls.js';
import {createCityStream} from '../source/city-stream.js';
import {ContentStore, validateManifest, validateModel} from './content-store.js';
import {seed} from '../generated/seed.js';

const center = [71.43056, 51.12822], scale = 111320 * Math.cos(center[1] * Math.PI / 180);
const project = (lng, lat) => new THREE.Vector3((lng - center[0]) * scale, 0, -(lat - center[1]) * 111320);
const native = data => {
  const text = JSON.stringify({v: 1, ...data});
  if (window.EventMapNative?.postMessage) window.EventMapNative.postMessage(text);
  else if (window.webkit?.messageHandlers?.EventMapNative) window.webkit.messageHandlers.EventMapNative.postMessage(text);
  else { window.dispatchEvent(new CustomEvent('eventmap-message', {detail: data})); }
};
const info = document.querySelector('#status');
let renderer, controls, city, store, frame, active = true, last = 0, night = false, theme = 'default';
let current = seed, modelRoot, models = [], markers = [], lastEvents = [], nightMaterials = [];
let scene, camera, sunlight, ambient, terrain, labelRoot = document.querySelector('#labels');
const clock = performance.now();
function dispose(root) {
  const mats = new Set(); root?.traverse(o => { o.geometry?.dispose(); if (o.material) mats.add(o.material); });
  for (const m of mats) m.dispose(); root?.removeFromParent();
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
        m.userData = {color:x.color, emissive:x.emissive, intensity:x.emissiveIntensity}; materials.push(m); return m;
      });
      for (const x of data.meshes) {
        const g = new THREE.BufferGeometry();
        g.setAttribute('position', new THREE.Float32BufferAttribute(x.positions, 3));
        g.setAttribute('normal', new THREE.Float32BufferAttribute(x.normals, 3));
        g.setIndex(x.indices); g.computeBoundingSphere();
        const mesh = new THREE.Mesh(g, mats[x.material]); mesh.userData.detail = x.detail; group.add(mesh);
      }
      group.position.copy(project(entry.lng, entry.lat)); group.userData.place = entry;
      entries.push({entry, group});
    }
    return {root, entries, materials};
  } catch (e) { dispose(root); for (const m of materials) m.dispose(); throw e; }
}
function activate(pack) {
  const next = makeModels(pack); // Build first; invalid replacements never erase current geometry.
  scene.add(next.root); dispose(modelRoot); modelRoot = next.root;
  current = pack; models = next.entries; nightMaterials = next.materials;
  labelRoot.replaceChildren();
  for (const item of models) {
    const el = document.createElement('button'); el.textContent = item.entry.title; el.className = 'place-label';
    el.onclick = () => selectPlace(item.entry); labelRoot.append(el); item.label = el;
  }
  appearance(); native({type:'content', release:pack.manifest.release});
}
function selectPlace(p) { native({type:'place', id:p.id || '', title:p.title, lat:p.lat, lng:p.lng}); }
function appearance() {
  if (!scene) return;
  const winter = theme !== 'default';
  scene.background = new THREE.Color(night ? 0x101d30 : winter ? 0xcddfe9 : 0xbfd5db);
  scene.fog = new THREE.Fog(scene.background, 11000, 26000);
  ambient.intensity = night ? 1.05 : 2.5; sunlight.intensity = night ? .5 : 2.1;
  sunlight.color.setHex(night ? 0xb1d3ff : 0xffead5);
  terrain.material.color.setHex(winter ? (night ? 0x758b9e : 0xdce8ea) : night ? 0x344d45 : 0x859e7b);
  city?.setNight(night); city?.setTheme(theme);
  for (const m of nightMaterials) {
    m.color.setHex(m.userData.color); m.emissive.setHex(m.userData.emissive);
    m.emissiveIntensity = m.userData.intensity * (night ? 1.4 : .5);
    // Architectural illumination, not a fake live-event indicator.
    if (night && m.userData.emissive === 0 && m.color.getHSL({}).l > .65) {
      m.emissive.setHex(theme === 'newyear' ? 0xf4b75e : 0xbacbd9); m.emissiveIntensity = .16;
    }
  }
}
function setEvents(events) {
  for (const {mesh} of markers) dispose(mesh); markers = [];
  lastEvents = Array.isArray(events) ? events.slice(0, 150) : [];
  for (const e of lastEvents) {
    if (typeof e.id !== 'string' || e.id.length > 100 || !Number.isFinite(e.lat) || !Number.isFinite(e.lng) || e.lat < 50.9 || e.lat > 51.4 || e.lng < 71.1 || e.lng > 71.9) continue;
    const mesh = new THREE.Mesh(new THREE.OctahedronGeometry(7), new THREE.MeshBasicMaterial({color:0x9468ff}));
    mesh.position.copy(project(e.lng, e.lat)); mesh.position.y = 25;
    // Raise markers above a landmark when the event is nearby.
    for (const p of models) if (mesh.position.distanceTo(p.group.position) < p.entry.radius + 35) mesh.position.y = p.entry.height + 18;
    mesh.userData.eventId = e.id; scene.add(mesh); markers.push({mesh});
  }
}
function focus(id) {
  const item = models.find(x => x.entry.id === id); if (!item) return;
  controls.target.copy(item.group.position).add(new THREE.Vector3(0, item.entry.height * .3, 0));
  camera.position.copy(controls.target).add(new THREE.Vector3(330, 300, 430).multiplyScalar(id === 'arena' ? 1.35 : 1));
  controls.update();
}
let focusRevision = -1, updateRevision = 0, eventSignature = '';
function receive(m) {
  if (!m || m.v !== 1) return;
  if (m.type === 'sync') {
    if (!store) receive({v:1,type:'init',manifestUrl:m.manifestUrl});
    const signature=JSON.stringify(m.events);
    if(signature!==eventSignature){eventSignature=signature;setEvents(m.events);}
    if(night!==m.night || theme!==m.theme) receive({v:1,type:'appearance',night:m.night,theme:m.theme});
    if(m.focusRevision!==focusRevision){focusRevision=m.focusRevision;focus(m.focusId);}
    if(m.updateRevision!==updateRevision){updateRevision=m.updateRevision;void store?.update();}
    receive({v:1,type:'active',active:m.active});
  }
  if (m.type === 'events') setEvents(m.events);
  if (m.type === 'focus') focus(m.id);
  if (m.type === 'appearance') { night = m.night === true; theme = ['default','winter','newyear'].includes(m.theme) ? m.theme : 'default'; appearance(); }
  if (m.type === 'update') void store?.update();
  if (m.type === 'active') { active = m.active === true; if (active && !frame) frame = requestAnimationFrame(render); }
  if (m.type === 'init' && !store) {
    store = new ContentStore({seed, activate:pack => { activate(pack); setEvents(lastEvents); }, onStatus:s => {
      const labels = {'checking':'Проверяем обновления…','ready':'Город готов','current':'Актуальная версия', 'updated':'Карта обновлена', 'updated-memory-only':'Обновлено · без дискового кэша', 'cache-unavailable':'Кэш недоступен', 'kept-working':'Оставлена рабочая карта'};
      info.textContent = `${labels[s.state] || s.state} · ${s.release}`;
      native({type:'status', state:s.state, release:s.release});
    }});
    store.start(m.manifestUrl).catch(() => { info.textContent = 'Встроенная карта · сервер не настроен'; native({type:'status', state:'kept-working',release:current.manifest.release}); });
  }
}
const ray = new THREE.Raycaster(), pointer = new THREE.Vector2(); let down;
function pick(event) {
  if (!down || performance.now() - down.t > 600 || Math.hypot(event.clientX - down.x,event.clientY - down.y) > 8) return;
  const r = renderer.domElement.getBoundingClientRect();
  pointer.set((event.clientX - r.left) / r.width * 2 - 1, -(event.clientY - r.top) / r.height * 2 + 1);
  ray.setFromCamera(pointer,camera);
  const hits = ray.intersectObjects([...markers.map(x => x.mesh), modelRoot, city.group, terrain], true);
  const hit = hits.find(h => { let o = h.object; while(o) { if (!o.visible) return false; o=o.parent; } return true; });
  if (!hit) return;
  let o=hit.object; while(o) {
    if (o.userData.eventId) { native({type:'event',id:o.userData.eventId}); return; }
    if (o.userData.place) { selectPlace(o.userData.place); return; } o=o.parent;
  }
  const lat = center[1] - hit.point.z / 111320, lng = center[0] + hit.point.x / scale;
  if (lat>=50.9 && lat<=51.4 && lng>=71.1 && lng<=71.9) selectPlace({title:'Точка на карте',lat,lng});
}
let count = 0, sampleStart = 0, fps = 0;
function render(t) {
  frame = null; if (!active || document.hidden) return;
  frame = requestAnimationFrame(render); if (t-last < 1000/30-1) return; last=t;
  controls.update();
  const distance = camera.position.distanceTo(controls.target);
  city.update(controls.target,distance); city.animate(t/1000,matchMedia('(prefers-reduced-motion: reduce)').matches);
  for (const p of models) {
    const d = camera.position.distanceTo(p.group.position);
    for (const mesh of p.group.children) if(mesh.userData.detail) mesh.visible = d < 1700;
    const point = p.group.position.clone().add(new THREE.Vector3(0,p.entry.height+16,0)).project(camera);
    p.label.hidden = point.z > 1 || point.z < -1 || Math.abs(point.x)>1.2 || Math.abs(point.y)>1.2;
    p.label.style.transform=`translate(${(point.x*.5+.5)*innerWidth}px,${(-point.y*.5+.5)*innerHeight}px) translate(-50%,-100%)`;
  }
  renderer.render(scene,camera); count++;
  if(t-sampleStart>2000) { fps = Math.round(count*1000/(t-sampleStart));count=0;sampleStart=t; }
}
function resize() { if(!renderer)return; renderer.setSize(innerWidth,innerHeight); camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix(); }
try {
  renderer = new THREE.WebGLRenderer({canvas:document.querySelector('canvas'),antialias:false,alpha:false,powerPreference:'default'});
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.5)); renderer.outputColorSpace=THREE.SRGBColorSpace;
  renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;
  scene=new THREE.Scene();camera=new THREE.PerspectiveCamera(42,1,2,40000);
  ambient=new THREE.HemisphereLight(0xd8ebff,0x6d6c57,2.5);sunlight=new THREE.DirectionalLight(0xffead5,2.1);sunlight.position.set(-500,900,400);scene.add(ambient,sunlight);
  terrain=new THREE.Mesh(new THREE.PlaneGeometry(60000,60000),new THREE.MeshStandardMaterial({color:0x859e7b,roughness:1}));terrain.rotation.x=-Math.PI/2;terrain.position.y=-1;scene.add(terrain);
  controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.dampingFactor=.12;
  controls.minDistance=70;controls.maxDistance=19000;controls.maxPolarAngle=Math.PI*.46;controls.screenSpacePanning=false;
  controls.touches.ONE=THREE.TOUCH.PAN;controls.touches.TWO=THREE.TOUCH.DOLLY_ROTATE;
  controls.addEventListener('change',()=>{controls.target.x=THREE.MathUtils.clamp(controls.target.x,-14000,17000);controls.target.z=THREE.MathUtils.clamp(controls.target.z,-18000,15000);});
  activate(seed);
  city=createCityStream({center,excludeBuilding:(lng,lat)=>models.some(p=>project(lng,lat).distanceTo(p.group.position)<p.entry.radius),onStatus:s=>{document.querySelector('#tiles').textContent=s.failed?'Часть улиц недоступна':s.pending?'Загружаем улицы…':'';}});
  scene.add(city.group);focus('baiterek');resize();
  renderer.domElement.addEventListener('pointerdown',e=>{down={x:e.clientX,y:e.clientY,t:performance.now()};});
  renderer.domElement.addEventListener('pointerup',pick);
  renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();active=false;native({type:'error',state:'graphics-lost'});info.textContent='Графика остановлена. Открой карту заново.';});
  addEventListener('resize',resize);document.addEventListener('visibilitychange',()=>{if(!document.hidden&&active&&!frame)frame=requestAnimationFrame(render);});
  addEventListener('pagehide',()=>{store?.stop();active=false;cancelAnimationFrame(frame);controls.dispose();city.destroy();dispose(modelRoot);for(const x of markers)dispose(x.mesh);renderer.dispose();});
  window.EventMapMobile={receive,stats:()=>({fps,drawCalls:renderer.info.render.calls,triangles:renderer.info.render.triangles,geometries:renderer.info.memory.geometries,release:current.manifest.release,readyMs:Math.round(performance.now()-clock)})};
  frame=requestAnimationFrame(render);native({type:'ready'});
  // Browser-only local QA. Native supplies the URL through the typed bridge.
  if (['localhost','127.0.0.1'].includes(location.hostname)) receive({v:1,type:'init',manifestUrl:new URL('/world/manifest.json',location.href).href});
} catch (e) { info.textContent='3D недоступна. Можно вернуться к обычной карте.';native({type:'error',state:'graphics-unavailable'});console.error(e); }
