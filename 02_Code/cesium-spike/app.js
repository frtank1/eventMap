// Cesium spike: the Baiterek district with the project's own data (NSDI floors,
// our landmark models, curated events). No Cesium ion, no tokens. Test only.
(async()=>{
  const C=Cesium,hud=document.getElementById('hud'),card=document.getElementById('card');
  const BAITEREK=[71.43056,51.12822],ASTANA_UTC=5;
  const t={start:window.__t0,cesium:performance.now()};
  const viewer=new C.Viewer('map',{baseLayer:false,baseLayerPicker:false,geocoder:false,homeButton:false,sceneModePicker:false,
    navigationHelpButton:false,animation:false,timeline:false,fullscreenButton:false,infoBox:false,selectionIndicator:false,
    shadows:true,terrainShadows:C.ShadowMode.RECEIVE_ONLY,requestRenderMode:false,creditContainer:document.getElementById('credits')});
  const scene=viewer.scene,globe=scene.globe;
  scene.debugShowFramesPerSecond=true;globe.enableLighting=true;globe.depthTestAgainstTerrain=false;
  globe.maximumScreenSpaceError=1.5;viewer.useBrowserRecommendedResolution=false;
  // Quality: Эконом = CSS pixels; Хорошо = up to 2× pixels; Макс = + ambient occlusion.
  const QUALITY=['Эконом','Хорошо','Макс'];let quality=1;
  const ao=scene.postProcessStages.ambientOcclusion,bloom=scene.postProcessStages.bloom;
  ao.uniforms.intensity=2.2;ao.uniforms.lengthCap=.12;ao.uniforms.stepSize=1.6;ao.uniforms.bias=.15;
  bloom.uniforms.brightness=-.42;bloom.uniforms.stepSize=1.4;bloom.uniforms.sigma=3.2;
  function setQuality(q){quality=q;viewer.resolutionScale=q===0?1:Math.min(devicePixelRatio,2);ao.enabled=q===2;
    const b=document.getElementById('quality');if(b)b.textContent=`Качество: ${QUALITY[q]}`;}
  setQuality(1);
  scene.highDynamicRange=false;scene.fog.enabled=true;scene.postProcessStages.fxaa.enabled=true;
  viewer.shadowMap.softShadows=true;viewer.shadowMap.darkness=.35;viewer.shadowMap.maximumDistance=1500;viewer.shadowMap.size=2048;

  // ---- base layers (test-only sources; licences must be settled before any release) ----
  const bases={
    sat:()=>new C.UrlTemplateImageryProvider({url:'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',maximumLevel:19,credit:'Спутник: Esri World Imagery (только тест)'}),
    osm:()=>new C.OpenStreetMapImageryProvider({url:'https://tile.openstreetmap.org/',maximumLevel:19,credit:'© OpenStreetMap contributors (тайлы OSM — только лёгкий тест)'}),
  };
  function setBase(kind){
    viewer.imageryLayers.removeAll();globe.baseColor=C.Color.fromCssColorString('#d9dccf');
    if(bases[kind])viewer.imageryLayers.addImageryProvider(bases[kind]());
    for(const b of document.querySelectorAll('[data-base]'))b.setAttribute('aria-pressed',String(b.dataset.base===kind));
  }
  document.querySelectorAll('[data-base]').forEach(b=>b.onclick=()=>setBase(b.dataset.base));setBase('sat');

  // ---- real sun for the chosen Astana hour, today ----
  const hour=document.getElementById('hour'),hourLabel=document.getElementById('hourLabel');
  const nowAstana=new Date(Date.now()+ASTANA_UTC*3600e3);hour.value=String(Math.min(22,Math.max(5,nowAstana.getUTCHours()+nowAstana.getUTCMinutes()/60)));
  function setHour(){const h=Number(hour.value),d=new Date(Date.UTC(nowAstana.getUTCFullYear(),nowAstana.getUTCMonth(),nowAstana.getUTCDate(),0,0)+(h-ASTANA_UTC)*3600e3);
    viewer.clock.currentTime=C.JulianDate.fromDate(d);viewer.clock.shouldAnimate=false;
    hourLabel.textContent=`${String(Math.floor(h)).padStart(2,'0')}:${String(Math.round((h%1)*60)).padStart(2,'0')}`;}
  hour.oninput=setHour;setHour();
  const shadowsBtn=document.getElementById('shadows');
  shadowsBtn.onclick=()=>{viewer.shadows=!viewer.shadows;shadowsBtn.setAttribute('aria-pressed',String(viewer.shadows));};
  document.getElementById('quality').onclick=()=>setQuality((quality+1)%3);setQuality(quality);

  // Oblique view of the boulevard, like the 3D city's opening shot.
  viewer.camera.lookAt(C.Cartesian3.fromDegrees(BAITEREK[0],BAITEREK[1],40),new C.HeadingPitchRange(C.Math.toRadians(-25),C.Math.toRadians(-20),620));
  viewer.camera.lookAtTransform(C.Matrix4.IDENTITY);

  const json=async u=>{const r=await fetch(u);if(!r.ok)throw new Error(`${u}: ${r.status}`);return r.json();};
  const [data,landmarks,events]=await Promise.all([json('data/buildings.json'),json('data/landmarks.json'),json('data/events.json')]);
  t.data=performance.now();

  // ---- NSDI buildings: one batched primitive per 1500 outlines ----
  // Wall colour by height class; alpha carries the roof palette index (the appearance is opaque).
  const WALLS=['#efe1cb','#f4eadb','#e9eef0','#d3e1ec'];
  const colorFor=(lv,k)=>{const w=C.Color.fromCssColorString(WALLS[lv>=20?3:lv>=10?2:lv>=3?1:0]);w.alpha=lv>=10?1:((k*7919)%4)/4;return w;};
  const c=data.center,s=data.scale,FLOOR=data.floorMeters,prims=[];
  const centerWC=C.Cartesian3.fromDegrees(c[0],c[1],0),enc=C.EncodedCartesian3.fromCartesian(centerWC);
  const up=C.Ellipsoid.WGS84.geodeticSurfaceNormal(centerWC,new C.Cartesian3());
  const v3=v=>`vec3(${v.x.toFixed(6)},${v.y.toFixed(6)},${v.z.toFixed(6)})`;
  const BUILDING_VS=`in vec3 position3DHigh;in vec3 position3DLow;in vec3 normal;in vec4 color;in float batchId;
out vec3 v_positionEC;out vec3 v_normalEC;out vec4 v_color;out vec3 v_local;out vec3 v_normalWC;
void main(){vec4 p=czm_computePosition();v_positionEC=(czm_modelViewRelativeToEye*p).xyz;v_normalEC=czm_normal*normal;v_normalWC=normal;v_color=color;
  // District-local metres without float32 loss: subtract the encoded centre high/low parts separately.
  v_local=(position3DHigh-${v3(enc.high)})+(position3DLow-${v3(enc.low)});
  gl_Position=czm_modelViewProjectionRelativeToEye*p;}`;
  const BUILDING_FS=`in vec3 v_positionEC;in vec3 v_normalEC;in vec4 v_color;in vec3 v_local;in vec3 v_normalWC;
const vec3 UP=${v3(up)};const float FLOOR=${FLOOR.toFixed(2)};
float hash(vec3 p){return fract(sin(dot(p,vec3(12.9898,78.233,37.719)))*43758.5453);}
void main(){
  vec3 n=normalize(v_normalWC);float upness=dot(n,UP),h=dot(v_local,UP);
  float sunUp=dot(normalize(czm_sunDirectionWC),UP),night=smoothstep(.06,-.10,sunUp),dusk=smoothstep(.35,.0,sunUp)*(1.-night);
  vec3 col=v_color.rgb,emit=vec3(0.);
  if(upness<.5){
    vec3 t=normalize(cross(UP,n));float u=dot(v_local,t),d=dot(v_local,n);
    vec2 cell=vec2(floor(u/3.1),floor(h/FLOOR));float fy=fract(h/FLOOR),fx=fract(u/3.1);
    float near=1.-smoothstep(450.,1300.,length(v_positionEC));
    float win=step(.30,fy)*step(fy,.80)*step(.22,fx)*step(fx,.78)*step(.6,h);
    float r=hash(vec3(cell,floor(d*.25)));
    vec3 glass=mix(vec3(.42,.53,.63),vec3(.62,.73,.83),r*.6);
    col=mix(col,glass,win*.75*near)*mix(1.,.93,1.-near);
    // Window lights: a share of flats lit after sunset, warm and slightly varied.
    float lit=step(.42,r)*mix(win,.45,1.-near);emit=lit*mix(vec3(1.,.70,.40),vec3(1.,.86,.62),hash(cell.yxx))*(night*1.15+dusk*.25);
    col*=mix(1.,.86,smoothstep(.0,.25,fy)*(1.-smoothstep(.25,.3,fy))); // floor slab line
  }else{
    int k=int(floor(v_color.a*4.+.5));
    vec3 roof=k==0?vec3(.78,.42,.31):k==1?vec3(.23,.58,.55):k==2?vec3(.36,.50,.67):k==3?vec3(.47,.66,.41):vec3(.72,.75,.78);
    col=v_color.a>.99?vec3(.72,.75,.78):roof;
  }
  vec3 normalEC=normalize(v_normalEC);float diff=max(dot(normalEC,czm_lightDirectionEC),0.);
  float day=1.-night;vec3 amb=mix(vec3(.10,.13,.20),vec3(.50,.52,.58),smoothstep(-.08,.30,sunUp));
  vec3 sun=czm_lightColor*mix(vec3(1.,.72,.50),vec3(1.),smoothstep(.0,.4,sunUp));
  out_FragColor=vec4(col*(amb+diff*sun*day*.85)+emit,1.);
}`;
  const buildingAppearance=new C.Appearance({vertexShaderSource:BUILDING_VS,fragmentShaderSource:BUILDING_FS,translucent:false,closed:true,
    renderState:C.Appearance.getDefaultRenderState(false,true)});
  for(let from=0;from<data.buildings.length;from+=1500){
    const instances=data.buildings.slice(from,from+1500).map(([lv,usage,flat],k)=>{
      const deg=[];for(let j=0;j<flat.length;j+=2)deg.push(c[0]+flat[j]/s,c[1]+flat[j+1]/s);
      return new C.GeometryInstance({id:{kind:'building',index:from+k,levels:lv},
        geometry:new C.PolygonGeometry({polygonHierarchy:new C.PolygonHierarchy(C.Cartesian3.fromDegreesArray(deg)),height:0,extrudedHeight:Math.max(3,lv*FLOOR),vertexFormat:C.PerInstanceColorAppearance.VERTEX_FORMAT}),
        attributes:{color:C.ColorGeometryInstanceAttribute.fromColor(colorFor(lv,from+k))}});
    });
    prims.push(scene.primitives.add(new C.Primitive({geometryInstances:instances,appearance:buildingAppearance,compressVertices:false,shadows:C.ShadowMode.CAST_ONLY,asynchronous:true})));
  }

  // ---- our authored landmarks, converted to glTF (x east, y up, z south) ----
  // Architectural floodlighting after sunset, gold (Baiterek's sphere) glows warmer.
  const floodlight=new C.CustomShader({fragmentShaderText:`void fragmentMain(FragmentInput fsInput,inout czm_modelMaterial material){
    vec3 up=normalize(fsInput.attributes.positionWC);float night=smoothstep(.06,-.10,dot(normalize(czm_sunDirectionWC),up));
    vec3 d=material.diffuse;float gold=step(d.g*1.15,d.r)*step(d.b*1.3,d.g);
    material.emissive+=night*(d*.10+gold*vec3(1.,.72,.30)*.30);}`});
  const models=[];
  for(const l of landmarks){
    try{
      const m=await C.Model.fromGltfAsync({url:`data/models/${l.id}.glb`,forwardAxis:C.Axis.X,shadows:C.ShadowMode.ENABLED,id:{kind:'landmark',...l},customShader:floodlight,
        modelMatrix:C.Transforms.eastNorthUpToFixedFrame(C.Cartesian3.fromDegrees(l.lng,l.lat,0))});
      models.push(scene.primitives.add(m));
    }catch(e){console.warn('model',l.id,e);}
  }

  // ---- events: simple pins + labels ----
  const CAT={music:'#8a5cff',sport:'#1aa36f',market:'#f08a24',community:'#2f78ff',discovery:'#e0457b',quest:'#c79a1b',route:'#22a6b3'};
  const pin=color=>{const cv=document.createElement('canvas');cv.width=48;cv.height=64;const g=cv.getContext('2d');
    g.fillStyle=color;g.beginPath();g.arc(24,22,18,0,Math.PI*2);g.fill();g.beginPath();g.moveTo(10,32);g.lineTo(24,60);g.lineTo(38,32);g.fill();
    g.fillStyle='#fff';g.beginPath();g.arc(24,22,7,0,Math.PI*2);g.fill();return cv;};
  for(const e of events){
    viewer.entities.add({id:`event:${e.id}`,position:C.Cartesian3.fromDegrees(e.lng,e.lat,4),properties:{kind:'event',...e},
      billboard:{image:pin(CAT[e.category]||'#17263a'),verticalOrigin:C.VerticalOrigin.BOTTOM,scale:.75,disableDepthTestDistance:Number.POSITIVE_INFINITY},
      label:{text:e.title.length>28?e.title.slice(0,27)+'…':e.title,font:'600 13px system-ui',fillColor:C.Color.WHITE,outlineColor:C.Color.fromCssColorString('#17263a'),outlineWidth:3,
        style:C.LabelStyle.FILL_AND_OUTLINE,verticalOrigin:C.VerticalOrigin.TOP,pixelOffset:new C.Cartesian2(0,6),disableDepthTestDistance:Number.POSITIVE_INFINITY,
        distanceDisplayCondition:new C.DistanceDisplayCondition(0,6000)}});
  }

  // ---- event scene (EventSceneRuntime prototype, config-driven) ----
  const eventScene=createEventScene(viewer,await json('scenes/demo-concert.json'));
  const sceneBtn=document.getElementById('sceneBtn');
  function openScene(on){eventScene.show(on);sceneBtn.setAttribute('aria-pressed',String(on));
    if(on){if(Number(hour.value)<17.5){hour.value='20.5';setHour();}eventScene.flyTo();
      card.innerHTML='<h3>Демо-сцена мероприятия</h3><p>Вымышленный концерт — показ возможностей, не настоящее событие.</p><p>LED-экраны, лазеры, подсветка фан-зоны, VIP, фуд-корт, палатки, входы и выход, парковка и автобусы, маршруты гостей.</p>';card.style.display='block';}
    else card.style.display='none';}
  sceneBtn.onclick=()=>openScene(!eventScene.shown);

  // ---- picking ----
  new C.ScreenSpaceEventHandler(scene.canvas).setInputAction(ev=>{
    const p=scene.pick(ev.position);let html='';
    const id=p?.id;
    if(id instanceof C.Entity&&id.properties?.kind?.getValue()==='eventScene'){openScene(true);return;}
    if(id?.kind==='building')html=`<h3>Здание</h3><p>Этажей по НИПД: ${id.levels}</p><p>Высота ≈ ${Math.round(id.levels*FLOOR)} м</p>`;
    else if(p?.primitive?.id?.kind==='landmark'){const l=p.primitive.id;html=`<h3>${l.title}</h3><p>Наша авторская модель, высота ${l.height} м</p>`;}
    else if(id instanceof C.Entity&&id.properties){const e=id.properties.getValue(viewer.clock.currentTime);
      html=`<h3>${e.title}</h3><p>${new Date(e.startAt).toLocaleString('ru-RU',{day:'numeric',month:'long',hour:'2-digit',minute:'2-digit'})}</p><p>${e.place||''}</p>`;}
    card.innerHTML=html;card.style.display=html?'block':'none';
  },C.ScreenSpaceEventType.LEFT_CLICK);

  // ---- measurements ----
  let ready=null,frames=0,last=performance.now(),fps=0;
  scene.postRender.addEventListener(()=>{
    // Change post-processing and imagery only when the state flips: toggling every frame rebuilds them.
    const sunUp=C.Cartesian3.dot(scene.context.uniformState.sunDirectionWC,up),dark=sunUp<.02;
    if(bloom.enabled!==(dark&&quality>0))bloom.enabled=dark&&quality>0;
    const bright=dark?.32:sunUp<.25?.75:1;for(let i=0;i<viewer.imageryLayers.length;i++){const l=viewer.imageryLayers.get(i);if(l.brightness!==bright){l.brightness=bright;l.saturation=dark?.6:1;}}
    frames++;const now=performance.now();if(now-last>1000){fps=Math.round(frames*1000/(now-last));frames=0;last=now;}
    if(!ready&&prims.every(p=>p.ready)&&models.every(m=>m.ready))ready=now;
  });
  setInterval(()=>{
    const res=performance.getEntriesByType('resource');const mb=res.reduce((a,r)=>a+(r.transferSize||r.encodedBodySize||0),0)/1048576;
    const cesiumMb=res.filter(r=>r.name.includes('/vendor/Cesium/')).reduce((a,r)=>a+(r.transferSize||r.encodedBodySize||0),0)/1048576;
    hud.textContent=`FPS ${fps} · ${viewer.shadows?'с тенями':'без теней'} · ${QUALITY[quality]}\n`+
      `Здания НИПД: ${data.buildings.length} · ориентиры: ${models.length} · события: ${events.length}\n`+
      `Cesium готов: ${((t.cesium-t.start)/1000).toFixed(1)} с · данные: ${((t.data-t.start)/1000).toFixed(1)} с\n`+
      `Город построен: ${ready?((ready-t.start)/1000).toFixed(1)+' с':'строится…'}\n`+
      `Скачано: ${mb.toFixed(1)} МБ (из них Cesium ${cesiumMb.toFixed(1)} МБ) · DPR ${devicePixelRatio}`;
  },500);
  window.spike={viewer,prims,models,eventScene,openScene,stats:()=>({fps,ready:ready&&(ready-t.start)/1000,buildings:data.buildings.length,models:models.length})};
})().catch(e=>{document.getElementById('hud').textContent='Ошибка: '+e.message;console.error(e);});
