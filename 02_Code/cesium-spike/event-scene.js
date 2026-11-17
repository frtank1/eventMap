// EventSceneRuntime prototype: builds an event's 3D scene from a JSON config (no
// per-concert code). Local metres around an anchor: x → east, y → north.
window.createEventScene=function(viewer,cfg){
  const C=Cesium,scene=viewer.scene;
  const enu=C.Transforms.eastNorthUpToFixedFrame(C.Cartesian3.fromDegrees(cfg.anchor[0],cfg.anchor[1],0));
  const P=(x,y,z=0)=>C.Matrix4.multiplyByPoint(enu,new C.Cartesian3(x,y,z),new C.Cartesian3());
  const ring=(poly,z=0)=>[...poly,poly[0]].map(([x,y])=>P(x,y,z));
  const col=(hex,a=1)=>C.Color.fromCssColorString(hex).withAlpha(a);
  const ds=new C.CustomDataSource(cfg.id);viewer.dataSources.add(ds);
  const prims=scene.primitives.add(new C.PrimitiveCollection());
  const E=o=>ds.entities.add(o),box=(x,y,z,w,d,h,color,extra={})=>E({position:P(x,y,z+h/2),box:{dimensions:new C.Cartesian3(w,d,h),material:color,...extra}});
  let time=0;const timeUniformed=[];
  const rand=(s=>()=>{s=(s*16807)%2147483647;return (s-1)/2147483646;})(20261117);

  // Territory: dark ground, fence and a glowing outline.
  E({polygon:{hierarchy:new C.PolygonHierarchy(cfg.territory.map(([x,y])=>P(x,y))),height:.15,material:col('#14182b',.72)}});
  E({wall:{positions:ring(cfg.territory),maximumHeights:ring(cfg.territory).map(()=>2.2),minimumHeights:ring(cfg.territory).map(()=>0),material:col('#0b0f1c',.55)}});
  E({polyline:{positions:ring(cfg.territory,2.3),width:6,material:new C.PolylineGlowMaterialProperty({glowPower:.25,color:col('#7c5cff')})}});

  // Animated floor light (ripples running from the stage) and zone fills.
  const waves=new C.Material({fabric:{type:'EventFloorWaves',uniforms:{time:0,tint:col('#22d3ee'),tint2:col('#c026d3')},source:`
    czm_material czm_getMaterial(czm_materialInput m){czm_material mat=czm_getDefaultMaterial(m);
      float d=distance(m.st,vec2(.5,1.05));float r=.5+.5*sin(d*38.-time*3.2);float g=smoothstep(.55,1.,r);
      vec3 c=mix(tint.rgb,tint2.rgb,.5+.5*sin(time*.6+d*6.));mat.diffuse=c*.2;mat.emission=c*g*.9;mat.alpha=.18+g*.42;return mat;}`},translucent:true});
  timeUniformed.push(waves);
  for(const z of cfg.zones){
    const hier=new C.PolygonHierarchy(z.polygon.map(([x,y])=>P(x,y)));
    if(z.floor==='waves')prims.add(new C.Primitive({geometryInstances:new C.GeometryInstance({geometry:new C.PolygonGeometry({polygonHierarchy:hier,height:.35,vertexFormat:C.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat})}),
      appearance:new C.MaterialAppearance({material:waves,translucent:true,flat:true}),asynchronous:false}));
    else E({polygon:{hierarchy:hier,height:.3,material:col(z.color,.22)}});
    E({polyline:{positions:ring(z.polygon,.5),width:4,material:new C.PolylineGlowMaterialProperty({glowPower:.3,color:col(z.color)})}});
    const cx=z.polygon.reduce((a,p)=>a+p[0],0)/z.polygon.length,cy=z.polygon.reduce((a,p)=>a+p[1],0)/z.polygon.length;
    E({position:P(cx,cy,9),label:{text:z.title,font:'700 14px system-ui',fillColor:C.Color.WHITE,showBackground:true,backgroundColor:col(z.color,.85),
      pixelOffset:new C.Cartesian2(0,-4),disableDepthTestDistance:Number.POSITIVE_INFINITY,distanceDisplayCondition:new C.DistanceDisplayCondition(0,1600)}});
  }

  // Stage, truss and LED walls with animated content.
  const s=cfg.stage;
  box(s.at[0],s.at[1],0,s.width,s.depth,s.deck,col('#1f2433'));
  box(s.at[0],s.at[1]+s.depth/2-1,0,s.width,2,s.wall,col('#0d111c'));
  for(const x of [-s.width/2,s.width/2])box(s.at[0]+x,s.at[1],0,1.2,s.depth,s.wall+2,col('#3a4055'));
  box(s.at[0],s.at[1],s.wall+1.5,s.width+1,s.depth,1.2,col('#3a4055'));
  const led=new C.Material({fabric:{type:'EventLedWall',uniforms:{time:0},source:`
    czm_material czm_getMaterial(czm_materialInput m){czm_material mat=czm_getDefaultMaterial(m);vec2 st=m.st;
      float col=floor(st.x*28.);float level=.18+.72*abs(sin(col*1.73+time*2.6))*abs(cos(col*.37-time*1.3));
      vec3 hue=.55+.45*cos(6.2831*(vec3(.0,.33,.67)+st.x*.9+time*.12));
      float bar=step(st.y,level)*step(.12,fract(st.x*28.));vec3 c=mix(hue*.22,hue*1.25,bar);
      c+=vec3(1.)*.5*smoothstep(.985,1.,sin(time*1.7))*(1.-bar);
      c*=.75+.25*step(.25,fract(st.y*60.));mat.diffuse=vec3(0.);mat.emission=c;return mat;}`},translucent:false});
  timeUniformed.push(led);
  for(const sc of cfg.screens){
    const pos=[P(...sc.from),P(...sc.to)];
    prims.add(new C.Primitive({geometryInstances:new C.GeometryInstance({geometry:new C.WallGeometry({positions:pos,maximumHeights:[sc.top,sc.top],minimumHeights:[sc.bottom,sc.bottom],vertexFormat:C.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat})}),
      appearance:new C.MaterialAppearance({material:led,faceForward:true,flat:true,closed:false}),asynchronous:false}));
  }
  E({position:P(s.at[0],s.at[1],s.wall+6),label:{text:cfg.title,font:'800 15px system-ui',fillColor:C.Color.WHITE,showBackground:true,backgroundColor:col('#7c5cff',.9),
    disableDepthTestDistance:Number.POSITIVE_INFINITY,distanceDisplayCondition:new C.DistanceDisplayCondition(0,2500)}});

  // Lasers sweeping from the stage roof: a primitive collection updated in place
  // (entity polylines with callbacks rebuild geometry every frame).
  const LASER=['#22ff88','#22d3ee','#c026d3','#7c5cff','#facc15'],lasers=prims.add(new C.PolylineCollection()),laserList=[];
  for(let i=0;i<cfg.lasers;i++){
    const x0=-s.width/2+2+i*(s.width-4)/(cfg.lasers-1),from=P(s.at[0]+x0,s.at[1]-s.depth/2,s.wall+1);
    laserList.push({x0,from,phase:i*.7,line:lasers.add({positions:[from,from],width:5,
      material:C.Material.fromType('PolylineGlow',{color:col(LASER[i%LASER.length],.9),glowPower:.22,taperPower:.9})})});
  }
  function updateLasers(){for(const l of laserList){const a=Math.sin(time*.9+l.phase)*.9,e=.18+.2*Math.sin(time*.63+l.phase*1.3),L=280;
    l.line.positions=[l.from,P(s.at[0]+l.x0+Math.sin(a)*Math.cos(e)*L,s.at[1]-s.depth/2-Math.cos(a)*Math.cos(e)*L,s.wall+1+Math.sin(e)*L)];}}

  // Crowd: one point per visitor, a share of phone lights twinkling.
  const crowd=prims.add(new C.PointPrimitiveCollection());const crowdPts=[];
  const inside=(poly,x,y)=>{let c=false;for(let i=0,j=poly.length-1;i<poly.length;j=i++){const [xi,yi]=poly[i],[xj,yj]=poly[j];if(((yi>y)!==(yj>y))&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)c=!c;}return c;};
  const PEOPLE=['#ffffff','#f0abfc','#67e8f9','#c4b5fd','#fde68a'];
  for(const z of cfg.zones){
    const xs=z.polygon.map(p=>p[0]),ys=z.polygon.map(p=>p[1]),x0=Math.min(...xs),x1=Math.max(...xs),y0=Math.min(...ys),y1=Math.max(...ys);
    let n=0,guard=0;while(n<z.crowd&&guard++<z.crowd*20){let x=x0+rand()*(x1-x0),y=y0+rand()*(y1-y0);if(z.id==='fan')y=y0+(y1-y0)*Math.pow(rand(),.55);if(!inside(z.polygon,x,y))continue;
      const base=col(PEOPLE[Math.floor(rand()*PEOPLE.length)],.95);crowdPts.push({p:crowd.add({position:P(x,y,1.7),pixelSize:3.2,color:base}),base,phone:rand()<.07,ph:rand()*6.28});n++;}
  }

  // Food court and market: tents in rows, string lights; food trucks.
  const TENT=['#ffffff','#fb923c','#f472b6','#fde047'];
  for(const z of cfg.zones.filter(z=>z.tents)){
    const xs=z.polygon.map(p=>p[0]),ys=z.polygon.map(p=>p[1]),x0=Math.min(...xs)+6,x1=Math.max(...xs)-6,y0=Math.min(...ys)+6,y1=Math.max(...ys)-6;
    const cols=3,rows=Math.ceil(z.tents/cols),tops=[];
    for(let i=0;i<z.tents;i++){const cx=x0+(i%cols)*(x1-x0)/(cols-1),cy=y0+Math.floor(i/cols)*(y1-y0)/Math.max(1,rows-1);
      box(cx,cy,0,5,5,2.4,col('#e5e7eb'));
      E({position:P(cx,cy,2.4+1.6),cylinder:{length:3.2,topRadius:.05,bottomRadius:3.6,slices:4,material:col(TENT[i%TENT.length])}});
      tops.push(P(cx,cy,5.6));
    }
    for(let r=0;r<rows;r++){const row=tops.slice(r*cols,r*cols+cols);if(row.length>1)E({polyline:{positions:row,width:5,material:new C.PolylineGlowMaterialProperty({glowPower:.35,color:col('#ffd27a')})}});}
  }
  [[-70,-8],[-56,-8]].forEach(([x,y],i)=>box(x,y,0,6.5,2.6,3.1,col(i?'#ef4444':'#14b8a6')));

  // Gates: pillars and a glowing beam.
  for(const g of cfg.gates){
    const [x,y]=g.at,c=g.kind==='in'?'#22c55e':'#ef4444',w=10;
    const a=g.across?[x,y-w/2]:[x-w/2,y],b=g.across?[x,y+w/2]:[x+w/2,y];
    for(const [px,py] of [a,b])box(px,py,0,1.2,1.2,6,col('#e5e7eb'));
    E({polyline:{positions:[P(...a,6.3),P(...b,6.3)],width:8,material:new C.PolylineGlowMaterialProperty({glowPower:.3,color:col(c)})}});
    E({position:P(x,y,9),label:{text:g.title,font:'700 13px system-ui',fillColor:C.Color.WHITE,showBackground:true,backgroundColor:col(c,.9),disableDepthTestDistance:Number.POSITIVE_INFINITY,distanceDisplayCondition:new C.DistanceDisplayCondition(0,1600)}});
  }

  // Parking: asphalt, bays, parked cars and shuttle buses.
  const pk=cfg.parking,pxs=pk.polygon.map(p=>p[0]),pys=pk.polygon.map(p=>p[1]),[px0,px1,py0,py1]=[Math.min(...pxs),Math.max(...pxs),Math.min(...pys),Math.max(...pys)];
  E({polygon:{hierarchy:new C.PolygonHierarchy(pk.polygon.map(([x,y])=>P(x,y))),height:.12,material:col('#2b303b',.92)}});
  E({position:P((px0+px1)/2,py0+6,10),label:{text:'P · Парковка',font:'800 14px system-ui',fillColor:C.Color.WHITE,showBackground:true,backgroundColor:col('#2563eb',.9),disableDepthTestDistance:Number.POSITIVE_INFINITY,distanceDisplayCondition:new C.DistanceDisplayCondition(0,1600)}});
  const CAR=['#e5e7eb','#111827','#9ca3af','#1d4ed8','#b91c1c','#f8fafc','#78716c'];
  for(let i=0;i<pk.cars;i++){const r=Math.floor(i/21),k=i%21,x=px0+8+k*7.8,y=py0+8+r*11;if(x>px1-6)continue;
    if(rand()<.18)continue;box(x,y,0,2,4.4,1.45,col(CAR[Math.floor(rand()*CAR.length)]));}
  for(let i=0;i<pk.buses;i++){const x=px1-14-i*16,y=py1-6;box(x,y,0,3,12,3.2,col('#facc15'));
    E({polyline:{positions:[P(x+1.55,y-5,2.2),P(x+1.55,y+5,2.2)],width:4,material:new C.PolylineGlowMaterialProperty({glowPower:.4,color:col('#fff3c4')})}});}

  // Visitor routes: dashes flowing along the walking direction.
  for(const r of cfg.routes){
    const pts=r.points.map(([x,y])=>P(x,y,.8));let len=0;for(let i=1;i<pts.length;i++)len+=C.Cartesian3.distance(pts[i-1],pts[i]);
    const m=new C.Material({fabric:{type:`EventRoute${prims.length}`,uniforms:{time:0,color:col(r.color),len},source:`
      czm_material czm_getMaterial(czm_materialInput m){czm_material mat=czm_getDefaultMaterial(m);
        float d=m.st.s*len-time*7.;float dash=step(.45,fract(d/7.));float core=1.-abs(m.st.t-.5)*2.;
        mat.diffuse=color.rgb*.3;mat.emission=color.rgb*(.25+dash*core*1.2);mat.alpha=.25+dash*.7*core;return mat;}`},translucent:true});
    timeUniformed.push(m);
    prims.add(new C.Primitive({geometryInstances:new C.GeometryInstance({geometry:new C.PolylineGeometry({positions:pts,width:9,arcType:C.ArcType.NONE,vertexFormat:C.PolylineMaterialAppearance.VERTEX_FORMAT})}),
      appearance:new C.PolylineMaterialAppearance({material:m,translucent:true}),asynchronous:false}));
  }

  // Map pin shown instead of the scene from far away.
  const pin=viewer.entities.add({position:P(0,0,30),properties:{kind:'eventScene',id:cfg.id},
    billboard:{image:(()=>{const cv=document.createElement('canvas');cv.width=72;cv.height=88;const g=cv.getContext('2d');g.fillStyle='#7c5cff';g.beginPath();g.arc(36,32,28,0,Math.PI*2);g.fill();g.beginPath();g.moveTo(14,46);g.lineTo(36,84);g.lineTo(58,46);g.fill();g.fillStyle='#fff';g.font='800 22px system-ui';g.textAlign='center';g.fillText('3D',36,40);return cv;})(),
      verticalOrigin:C.VerticalOrigin.BOTTOM,scale:.8,disableDepthTestDistance:Number.POSITIVE_INFINITY},
    label:{text:cfg.title,font:'700 13px system-ui',fillColor:C.Color.WHITE,outlineColor:col('#17263a'),outlineWidth:3,style:C.LabelStyle.FILL_AND_OUTLINE,verticalOrigin:C.VerticalOrigin.TOP,pixelOffset:new C.Cartesian2(0,6),disableDepthTestDistance:Number.POSITIVE_INFINITY}});

  let shown=false;
  function show(on){shown=on;ds.show=on;prims.show=on;pin.show=!on;}
  scene.preRender.addEventListener(()=>{
    if(!shown)return;time=performance.now()/1000;
    for(const m of timeUniformed)m.uniforms.time=time;updateLasers();
    for(const c of crowdPts)if(c.phone){const k=.5+.5*Math.sin(time*3+c.ph);c.p.color=C.Color.lerp(c.base,C.Color.WHITE,k,new C.Color());c.p.pixelSize=3.2+k*2.4;}
  });
  show(false);
  return {show,get shown(){return shown;},pin,
    flyTo(){viewer.camera.flyTo({destination:P(-170,-260,190),orientation:{heading:C.Math.toRadians(32),pitch:C.Math.toRadians(-30),roll:0},duration:2.2});},
    stats:()=>({entities:ds.entities.values.length,primitives:prims.length,crowd:crowdPts.length})};
};
