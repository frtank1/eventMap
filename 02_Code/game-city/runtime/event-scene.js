// EventSceneRuntime: an event's own 3D scene built from a JSON config — stage with
// LED walls, lasers, lit zones, crowd, tents, gates, parking and visitor routes.
// Config is local metres around an anchor (x → east, y → north); no per-event code.
// Purely decorative: the crowd and cars are not counts of real people or vehicles.
import * as THREE from 'three';

export const EVENT_SCENE_LIMITS=Object.freeze({crowd:4000,zones:8,routes:12,lasers:16,cars:160,tents:30});
const rand=s=>()=>{s=(s*16807)%2147483647;return (s-1)/2147483646;};
const inside=(poly,x,y)=>{let c=false;for(let i=0,j=poly.length-1;i<poly.length;j=i++){const [xi,yi]=poly[i],[xj,yj]=poly[j];if(((yi>y)!==(yj>y))&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)c=!c;}return c;};
const bbox=poly=>{const xs=poly.map(p=>p[0]),ys=poly.map(p=>p[1]);return [Math.min(...xs),Math.max(...xs),Math.min(...ys),Math.max(...ys)];};

export function validateEventScene(cfg){
  if(!cfg||typeof cfg!=='object'||!Array.isArray(cfg.anchor)||cfg.anchor.length!==2)throw new Error('event scene: anchor [lng,lat] required');
  if(!Array.isArray(cfg.territory)||cfg.territory.length<3)throw new Error('event scene: territory polygon required');
  if((cfg.zones||[]).length>EVENT_SCENE_LIMITS.zones||(cfg.routes||[]).length>EVENT_SCENE_LIMITS.routes)throw new Error('event scene: too many zones or routes');
  return cfg;
}

// Flat ribbon along a polyline (glowing outlines, routes). `along` = metres from the start.
function ribbon(points,width,y){
  const pos=[],along=[],side=[];let d=0;
  for(let i=1;i<points.length;i++){
    const [ax,az]=points[i-1],[bx,bz]=points[i],len=Math.hypot(bx-ax,bz-az)||1,nx=-(bz-az)/len*width/2,nz=(bx-ax)/len*width/2;
    const q=[[ax+nx,az+nz,d,0],[ax-nx,az-nz,d,1],[bx+nx,bz+nz,d+len,0],[bx-nx,bz-nz,d+len,1]];
    for(const k of [0,1,2,2,1,3]){pos.push(q[k][0],y,q[k][1]);along.push(q[k][2]);side.push(q[k][3]);}
    d+=len;
  }
  const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(pos,3));
  g.setAttribute('along',new THREE.Float32BufferAttribute(along,1));g.setAttribute('side',new THREE.Float32BufferAttribute(side,1));g.userData.length=d;return g;
}
function label(text,color){
  const cv=document.createElement('canvas'),g=cv.getContext('2d'),font='700 30px system-ui,sans-serif';g.font=font;
  const w=Math.ceil(g.measureText(text).width)+36,h=50;cv.width=w;cv.height=h;g.font=font;
  g.fillStyle=color;g.beginPath();g.roundRect(0,0,w,h,14);g.fill();g.fillStyle='#fff';g.textBaseline='middle';g.fillText(text,18,h/2+1);
  const map=new THREE.CanvasTexture(cv);map.colorSpace=THREE.SRGBColorSpace;
  const s=new THREE.Sprite(new THREE.SpriteMaterial({map,depthTest:false,depthWrite:false,sizeAttenuation:false,toneMapped:false}));
  s.scale.set(.022*w/h,.022,1);s.renderOrder=40;return s;
}

export function createEventScene(cfg,project){
  validateEventScene(cfg);
  const group=new THREE.Group();group.name=`Event scene · ${cfg.id||'scene'} (decorative)`;
  const a=project(cfg.anchor[0],cfg.anchor[1]);group.position.set(a.x,0,a.z);
  const L=(x,y)=>[x,-y]; // local (x east, y north) → group space (x, z south)
  const r=rand(20261117),disposables=[],timed=[],add=(o)=>{group.add(o);return o;};
  const own=o=>{disposables.push(o);return o;};
  const glow=(color,opacity=1)=>own(new THREE.MeshBasicMaterial({color,transparent:true,opacity,blending:THREE.AdditiveBlending,depthWrite:false,toneMapped:false}));
  const solid=(color,extra={})=>own(new THREE.MeshStandardMaterial({color,roughness:.7,metalness:.05,...extra}));
  const shape=(poly,y)=>{const s=new THREE.Shape(poly.map(([x,yy])=>new THREE.Vector2(x,yy)));return own(new THREE.ShapeGeometry(s).rotateX(-Math.PI/2).translate(0,y,0));};
  const boxes=new Map(); // merged per colour → one draw each
  const box=(x,y,z,w,d,h,color)=>{const geo=new THREE.BoxGeometry(w,h,d).translate(x,z+h/2,-y);if(!boxes.has(color))boxes.set(color,[]);boxes.get(color).push(geo);};
  const outline=(poly,y,width,color,opacity=.9)=>{const m=add(new THREE.Mesh(own(ribbon([...poly,poly[0]].map(([x,yy])=>L(x,yy)),width,y)),glow(color,opacity)));m.renderOrder=3;return m;};

  // Territory: dark ground, low fence, violet glowing edge.
  // Draw order for the flat layers (all transparent, no depth writes): floor < zones < edges < routes < crowd.
  add(new THREE.Mesh(shape(cfg.territory,.12),own(new THREE.MeshBasicMaterial({color:0x14182b,transparent:true,opacity:.78,depthWrite:false,toneMapped:false})))).renderOrder=1;
  cfg.territory.forEach((p,i)=>{const q=cfg.territory[(i+1)%cfg.territory.length],len=Math.hypot(q[0]-p[0],q[1]-p[1]);
    const w=new THREE.Mesh(own(new THREE.BoxGeometry(len,2.2,.25)),solid(0x0b0f1c,{transparent:true,opacity:.7}));
    w.position.set((p[0]+q[0])/2,1.1,-(p[1]+q[1])/2);w.rotation.y=Math.atan2(q[1]-p[1],q[0]-p[0]);add(w);});
  outline(cfg.territory,2.3,1.1,0x7c5cff);

  // Zones: fills (the fan zone gets running light waves), glowing edges, labels.
  const waves=own(new THREE.ShaderMaterial({transparent:true,depthWrite:false,toneMapped:false,blending:THREE.AdditiveBlending,
    uniforms:{uTime:{value:0},uA:{value:new THREE.Color(0x22d3ee)},uB:{value:new THREE.Color(0xc026d3)},uStage:{value:new THREE.Vector2(...L(...(cfg.stage?.at||[0,60])))}},
    vertexShader:`varying vec2 vP;void main(){vP=position.xz;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
    fragmentShader:`uniform float uTime;uniform vec3 uA,uB;uniform vec2 uStage;varying vec2 vP;void main(){float d=distance(vP,uStage);
      float w=smoothstep(.55,1.,.5+.5*sin(d*.42-uTime*3.2));vec3 c=mix(uA,uB,.5+.5*sin(uTime*.6+d*.05));gl_FragColor=vec4(c*(.10+w*.75),1.);}`}));
  timed.push(waves);
  for(const z of cfg.zones||[]){
    const c=new THREE.Color(z.color);
    add(new THREE.Mesh(shape(z.polygon,.25),z.floor==='waves'?waves:own(new THREE.MeshBasicMaterial({color:c,transparent:true,opacity:.24,depthWrite:false,toneMapped:false})))).renderOrder=2;
    outline(z.polygon,.45,.8,c);
    const [x0,x1,y0,y1]=bbox(z.polygon),t=add(label(z.title,z.color));t.position.set((x0+x1)/2,9,-(y0+y1)/2);
  }

  // Stage, truss and animated LED walls.
  const s=cfg.stage;
  if(s){
    box(s.at[0],s.at[1],0,s.width,s.depth,s.deck,0x1f2433);
    box(s.at[0],s.at[1]+s.depth/2-1,0,s.width,2,s.wall,0x0d111c);
    for(const x of [-s.width/2,s.width/2])box(s.at[0]+x,s.at[1],0,1.2,s.depth,s.wall+2,0x3a4055);
    box(s.at[0],s.at[1],s.wall+1.5,s.width+1,s.depth,1.2,0x3a4055);
    const led=own(new THREE.ShaderMaterial({toneMapped:false,side:THREE.DoubleSide,uniforms:{uTime:{value:0}},
      vertexShader:`varying vec2 vUv;void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
      fragmentShader:`uniform float uTime;varying vec2 vUv;void main(){float col=floor(vUv.x*28.);
        float level=.18+.72*abs(sin(col*1.73+uTime*2.6))*abs(cos(col*.37-uTime*1.3));
        vec3 hue=.55+.45*cos(6.2831*(vec3(0.,.33,.67)+vUv.x*.9+uTime*.12));float bar=step(vUv.y,level)*step(.12,fract(vUv.x*28.));
        vec3 c=mix(hue*.22,hue*1.25,bar);c*=.75+.25*step(.25,fract(vUv.y*60.));gl_FragColor=vec4(c,1.);}`}));
    timed.push(led);
    for(const sc of cfg.screens||[]){
      const [ax,az]=L(...sc.from),[bx,bz]=L(...sc.to),len=Math.hypot(bx-ax,bz-az),h=sc.top-sc.bottom;
      const m=add(new THREE.Mesh(own(new THREE.PlaneGeometry(len,h)),led));m.position.set((ax+bx)/2,sc.bottom+h/2,(az+bz)/2);m.rotation.y=-Math.atan2(bz-az,bx-ax);
    }
    const t=add(label(cfg.title||'Сцена','#7c5cff'));t.position.set(s.at[0],s.wall+7,-s.at[1]);
  }

  // Lasers: thin additive beams fading along their length, swept every frame.
  const beamGeo=own(new THREE.CylinderGeometry(.16,.16,1,6,1,true).translate(0,.5,0)),haloGeo=own(new THREE.CylinderGeometry(.9,.9,1,8,1,true).translate(0,.5,0));
  const beamMat=(color,alpha)=>own(new THREE.ShaderMaterial({transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,toneMapped:false,side:THREE.DoubleSide,
    uniforms:{uColor:{value:new THREE.Color(color)},uAlpha:{value:alpha}},vertexShader:`varying float vT;void main(){vT=uv.y;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
    fragmentShader:`uniform vec3 uColor;uniform float uAlpha;varying float vT;void main(){gl_FragColor=vec4(uColor*uAlpha*pow(1.-vT,1.4),1.);}`}));
  const LASER=[0x22ff88,0x22d3ee,0xc026d3,0x7c5cff,0xfacc15],lasers=[];
  const nL=Math.min(EVENT_SCENE_LIMITS.lasers,cfg.lasers||0);
  for(let i=0;s&&i<nL;i++){
    const x0=-s.width/2+2+i*(s.width-4)/Math.max(1,nL-1),root=new THREE.Group();root.position.set(s.at[0]+x0,s.wall+1,-(s.at[1]-s.depth/2));
    const core=new THREE.Mesh(beamGeo,beamMat(LASER[i%5],1.2)),halo=new THREE.Mesh(haloGeo,beamMat(LASER[i%5],.12));core.scale.y=halo.scale.y=280;root.add(core,halo);add(root);lasers.push({root,phase:i*.7});
  }

  // Crowd: one point each, ~7% "phone lights" pulsing.
  const pts=[],cols=[],phase=[],phone=[],PEOPLE=[0xffffff,0xf0abfc,0x67e8f9,0xc4b5fd,0xfde68a].map(c=>new THREE.Color(c));let total=0;
  for(const z of cfg.zones||[]){
    const [x0,x1,y0,y1]=bbox(z.polygon);let n=0,guard=0;const want=Math.min(z.crowd||0,EVENT_SCENE_LIMITS.crowd-total);
    while(n<want&&guard++<want*20){let x=x0+r()*(x1-x0),y=y0+r()*(y1-y0);if(z.floor==='waves')y=y0+(y1-y0)*Math.pow(r(),.55);if(!inside(z.polygon,x,y))continue;
      pts.push(x,1.7,-y);const c=PEOPLE[Math.floor(r()*PEOPLE.length)];cols.push(c.r,c.g,c.b);phase.push(r()*6.28);phone.push(r()<.07?1:0);n++;}
    total+=n;
  }
  const crowdGeo=own(new THREE.BufferGeometry());crowdGeo.setAttribute('position',new THREE.Float32BufferAttribute(pts,3));crowdGeo.setAttribute('color',new THREE.Float32BufferAttribute(cols,3));
  crowdGeo.setAttribute('phase',new THREE.Float32BufferAttribute(phase,1));crowdGeo.setAttribute('phone',new THREE.Float32BufferAttribute(phone,1));
  const crowdMat=own(new THREE.ShaderMaterial({transparent:true,depthWrite:false,toneMapped:false,uniforms:{uTime:{value:0},uPixel:{value:1}},
    vertexShader:`attribute vec3 color;attribute float phase,phone;uniform float uTime,uPixel;varying vec3 vC;varying float vK;
      void main(){vec4 mv=modelViewMatrix*vec4(position,1.);float k=phone*(.5+.5*sin(uTime*3.+phase));vK=k;vC=mix(color,vec3(1.),k);
        gl_PointSize=uPixel*clamp(900./-mv.z,1.6,5.)*(1.+k*.9);gl_Position=projectionMatrix*mv;}`,
    fragmentShader:`varying vec3 vC;varying float vK;void main(){float d=length(gl_PointCoord-.5);if(d>.5)discard;gl_FragColor=vec4(vC*(.85+vK*.8),1.-d*1.2);}`}));
  timed.push(crowdMat);add(new THREE.Points(crowdGeo,crowdMat)).renderOrder=5;

  // Food court and market: tents in rows with warm string lights; food trucks.
  const TENT=[0xffffff,0xfb923c,0xf472b6,0xfde047],tentGeo=own(new THREE.ConeGeometry(3.6,3.2,4).rotateY(Math.PI/4)),tents=[];
  const lights=[];
  for(const z of (cfg.zones||[]).filter(z=>z.tents)){
    const [x0,x1,y0,y1]=bbox(z.polygon),cols3=3,n=Math.min(z.tents,EVENT_SCENE_LIMITS.tents),rows=Math.ceil(n/cols3);
    for(let i=0;i<n;i++){const cx=x0+6+(i%cols3)*(x1-x0-12)/(cols3-1),cy=y0+6+Math.floor(i/cols3)*(y1-y0-12)/Math.max(1,rows-1);
      box(cx,cy,0,5,5,2.4,0xe5e7eb);tents.push([cx,cy,TENT[i%4]]);if(i%cols3===0)lights.push([]);lights.at(-1).push(L(cx,cy));}
  }
  const tentMesh=add(new THREE.InstancedMesh(tentGeo,solid(0xffffff),Math.max(1,tents.length)));tentMesh.count=tents.length;
  const dummy=new THREE.Object3D(),tone=new THREE.Color();
  tents.forEach(([x,y,c],i)=>{dummy.position.set(x,2.4+1.6,-y);dummy.updateMatrix();tentMesh.setMatrixAt(i,dummy.matrix);tentMesh.setColorAt(i,tone.setHex(c));});
  for(const row of lights)if(row.length>1)add(new THREE.Mesh(own(ribbon(row,.5,5.6)),glow(0xffd27a,.95)));
  for(const [x,y,c] of [[-70,-8,0x14b8a6],[-56,-8,0xef4444]])box(x,y,0,6.5,2.6,3.1,c);

  // Gates.
  for(const g of cfg.gates||[]){
    const [x,y]=g.at,c=g.kind==='in'?0x22c55e:0xef4444,w=10,a2=g.across?[x,y-w/2]:[x-w/2,y],b2=g.across?[x,y+w/2]:[x+w/2,y];
    for(const [px,py] of [a2,b2])box(px,py,0,1.2,1.2,6,0xe5e7eb);
    const beam=add(new THREE.Mesh(own(new THREE.BoxGeometry(g.across?.6:w,.6,g.across?w:.6)),glow(c)));beam.position.set(x,6.4,-y);
    const t=add(label(g.title,g.kind==='in'?'#16a34a':'#dc2626'));t.position.set(x,9.5,-y);
  }

  // Parking: asphalt, bays, cars and shuttle buses.
  const pk=cfg.parking;
  if(pk){
    const [px0,px1,py0,py1]=bbox(pk.polygon);
    add(new THREE.Mesh(shape(pk.polygon,.1),own(new THREE.MeshStandardMaterial({color:0x2b303b,roughness:.95}))));
    const CAR=[0xe5e7eb,0x111827,0x9ca3af,0x1d4ed8,0xb91c1c,0xf8fafc,0x78716c],carGeo=own(new THREE.BoxGeometry(2,1.45,4.4).translate(0,.72,0)),cars=[];
    for(let i=0;i<Math.min(pk.cars||0,EVENT_SCENE_LIMITS.cars);i++){const row=Math.floor(i/21),k=i%21,x=px0+8+k*7.8,y=py0+8+row*11;if(x>px1-6||r()<.18)continue;cars.push([x,y,CAR[Math.floor(r()*CAR.length)]]);}
    const carMesh=add(new THREE.InstancedMesh(carGeo,solid(0xffffff,{metalness:.3,roughness:.4}),Math.max(1,cars.length)));carMesh.count=cars.length;
    cars.forEach(([x,y,c],i)=>{dummy.position.set(x,.1,-y);dummy.updateMatrix();carMesh.setMatrixAt(i,dummy.matrix);carMesh.setColorAt(i,tone.setHex(c));});
    for(let i=0;i<(pk.buses||0);i++){const x=px1-14-i*16,y=py1-6;box(x,y,0,3,12,3.2,0xfacc15);const w=add(new THREE.Mesh(own(new THREE.BoxGeometry(.1,.7,10)),glow(0xfff3c4)));w.position.set(x+1.56,2.2,-y);}
    const t=add(label('P · Парковка','#2563eb'));t.position.set((px0+px1)/2,10,-(py0+6));
  }

  // Visitor routes: dashes flowing in the walking direction.
  for(const route of cfg.routes||[]){
    const geo=own(ribbon(route.points.map(([x,y])=>L(x,y)),3.4,.6));
    const m=own(new THREE.ShaderMaterial({transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,toneMapped:false,uniforms:{uTime:{value:0},uColor:{value:new THREE.Color(route.color)}},
      vertexShader:`attribute float along,side;varying float vA,vS;void main(){vA=along;vS=side;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
      fragmentShader:`uniform float uTime;uniform vec3 uColor;varying float vA,vS;void main(){float dash=step(.45,fract((vA-uTime*7.)/7.));float core=1.-abs(vS-.5)*2.;gl_FragColor=vec4(uColor*(.30+dash*(.4+core)*1.4),1.);}`}));
    timed.push(m);add(new THREE.Mesh(geo,m)).renderOrder=4;
  }

  for(const [color,list] of boxes){const geo=own(mergeBoxes(list));list.forEach(g=>g.dispose());const m=add(new THREE.Mesh(geo,solid(color)));m.castShadow=true;m.receiveShadow=true;}

  let crowdCount=total;
  return {group,
    // The scene's own camera framing: three quarters from the south-west, like a venue render.
    focus:{target:new THREE.Vector3(a.x,0,a.z),offset:new THREE.Vector3(-170,190,260)},
    update(time,pixelRatio=1,reduced=false){
      const t=reduced?0:time;for(const m of timed)m.uniforms.uTime.value=t;crowdMat.uniforms.uPixel.value=pixelRatio;
      for(const l of lasers){const az=Math.sin(t*.9+l.phase)*.9,el=.18+.2*Math.sin(t*.63+l.phase*1.3);
        // Beam axis: y-up cylinder tilted toward the audience (+z) and swept sideways.
        l.root.rotation.set(Math.PI/2-el,0,0);l.root.rotateOnWorldAxis(new THREE.Vector3(0,1,0),az);}
    },
    stats:()=>({eventScene:cfg.id||'scene',eventCrowd:crowdCount,eventDraws:group.children.length}),
    destroy(){group.removeFromParent();for(const d of disposables){d.map?.dispose?.();d.dispose?.();}group.traverse(o=>{if(o.isSprite){o.material.map?.dispose();o.material.dispose();}if(o.isInstancedMesh)o.dispose();});}};
}

function mergeBoxes(list){
  const n=list.reduce((a,g)=>a+(g.index?g.index.count:g.attributes.position.count),0),pos=new Float32Array(n*3),nor=new Float32Array(n*3);let o=0;
  for(const g of list){const f=g.index?g.toNonIndexed():g;pos.set(f.attributes.position.array,o);nor.set(f.attributes.normal.array,o);o+=f.attributes.position.array.length;if(f!==g)f.dispose();}
  const m=new THREE.BufferGeometry();m.setAttribute('position',new THREE.BufferAttribute(pos,3));m.setAttribute('normal',new THREE.BufferAttribute(nor,3));return m;
}
