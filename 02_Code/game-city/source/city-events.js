import * as T from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';

// Original, fictional plaza vignettes. Local metres, no DOM/network/audio.
export const EVENT_MODES=[
  {id:'fireworks',title:'Салют над Байтереком',description:'Ракеты поднимаются над площадью и раскрываются цветными искрами. Приближайся и выбирай ракурс.',night:true,focus:[0,92,0],radius:100},
  {id:'concert',title:'Концерт на площади',description:'Небольшая сцена, световые лучи и зрители, которые двигаются в такт.',night:true,focus:[85,10,8],radius:30},
  {id:'marathon',title:'Городской марафон',description:'Бегуны проходят замкнутую демо-трассу. Голубая арка — старт, золотая — финиш.',night:false,focus:[95,4,5],radius:48},
  {id:'market',title:'Ярмарка выходного дня',description:'Цветные торговые павильоны, развевающиеся флажки и посетители между рядами.',night:false,focus:[88,5,8],radius:34},
  {id:'icerink',title:'Открытый каток',description:'Ледяной овал и фигуристы, скользящие по разным дорожкам.',night:false,focus:[85,3,8],radius:30},
  {id:'cinema',title:'Кино под открытым небом',description:'Кресла перед большим экраном. На экране — оригинальная движущаяся космическая миниатюра.',night:true,focus:[85,10,5],radius:32},
  {id:'drones',title:'Шоу дронов',description:'Светящиеся точки в небе плавно собираются в кольцо, звезду и сердце.',night:true,focus:[0,150,0],radius:65},
  {id:'dance',title:'Танцы в городе',description:'Цветной танцпол и небольшая группа танцующих игровых персонажей.',night:true,focus:[85,3,8],radius:22},
  {id:'basketball',title:'Уличный баскетбол',description:'Площадка с двумя кольцами, движущимися игроками и отскакивающим мячом.',night:false,focus:[85,5,8],radius:28},
  {id:'food',title:'Фуд-фестиваль',description:'Фуд-траки, столики, посетители и лёгкий пар над кухнями.',night:false,focus:[88,5,8],radius:34},
];
const TAU=Math.PI*2,PALETTE=[0x72c7e5,0xf0c66c,0xbfa0e2,0xe88f7f,0x81bf9d],SKIN=[0xf1c5a1,0xc58d68,0x8b5e43];
const box=(w,h,d,x,y,z,angle=0)=>new T.BoxGeometry(w,h,d).rotateY(angle).translate(x,y,z);
function disc(rx,rz,x,y,z){return new T.CircleGeometry(1,64).rotateX(-Math.PI/2).scale(rx,1,rz).translate(x,y,z)}
function ellipse(rx,rz,x,y,z,width=.25){const p=[];for(let i=0;i<=96;i++){const a=i/96*TAU;p.push(new T.Vector3(x+rx*Math.cos(a),y,z+rz*Math.sin(a)))}return new T.TubeGeometry(new T.CatmullRomCurve3(p),96,width,5,false)}
function makeScene(){
  const group=new T.Group(),geometries=new Set(),materials=new Set(),textures=new Set(),instances=[],buckets=new Map(),updaters=[];
  const own=g=>(geometries.add(g),g);
  const material=(color,basic=false)=>{const m=basic?new T.MeshBasicMaterial({color}):new T.MeshStandardMaterial({color,roughness:.8});materials.add(m);return m};
  function mesh(g,m){own(g);materials.add(m);const o=new T.Mesh(g,m);group.add(o);return o}
  function add(g,color,basic=false){const key=`${color}/${basic}`;if(!buckets.has(key))buckets.set(key,{geos:[],m:material(color,basic)});buckets.get(key).geos.push(g)}
  function finish(){for(const {geos,m} of buckets.values()){const flat=geos.map(g=>g.index?g.toNonIndexed():g);mesh(mergeGeometries(flat),m);for(const g of new Set([...geos,...flat]))g.dispose()}buckets.clear()}
  function particles(count,size=1.5,additive=true){
    const data=new Uint8Array(16*16*4);for(let y=0;y<16;y++)for(let x=0;x<16;x++){const n=(y*16+x)*4,r=Math.hypot((x-7.5)/7.5,(y-7.5)/7.5);data[n]=data[n+1]=data[n+2]=255;data[n+3]=Math.round(Math.pow(Math.max(0,1-r),1.8)*255)}
    const texture=new T.DataTexture(data,16,16);texture.needsUpdate=true;textures.add(texture);
    const g=own(new T.BufferGeometry());g.setAttribute('position',new T.Float32BufferAttribute(new Float32Array(count*3),3));g.setAttribute('color',new T.Float32BufferAttribute(new Float32Array(count*3),3));
    const m=new T.PointsMaterial({size,map:texture,vertexColors:true,transparent:true,depthWrite:false,blending:additive?T.AdditiveBlending:T.NormalBlending,toneMapped:false});materials.add(m);
    const points=new T.Points(g,m);points.frustumCulled=false;group.add(points);return {points,p:g.attributes.position,c:g.attributes.color};
  }
  function people(count,pose){
    const m=material(0xffffff),parts=[];
    m.onBeforeCompile=shader=>{shader.fragmentShader=shader.fragmentShader.replace('#include <emissivemap_fragment>','#include <emissivemap_fragment>\n#if defined(USE_COLOR) || defined(USE_COLOR_ALPHA)\n totalEmissiveRadiance *= vColor.rgb;\n#endif')};m.customProgramCacheKey=()=> 'event-demo-people-v1';
    function batch(g,n){own(g);const o=new T.InstancedMesh(g,m,n);o.instanceMatrix.setUsage(T.DynamicDrawUsage);o.frustumCulled=false;group.add(o);instances.push(o);parts.push(o);return o}
    const body=batch(new T.BoxGeometry(.56,.8,.33),count),head=batch(new T.SphereGeometry(.21,7,5),count),arms=batch(new T.BoxGeometry(.15,.7,.17),count*2),legs=batch(new T.BoxGeometry(.19,.75,.22),count*2),o=new T.Object3D(),color=new T.Color();
    for(let i=0;i<count;i++){const shirt=PALETTE[i%PALETTE.length];body.setColorAt(i,color.setHex(shirt));head.setColorAt(i,color.setHex(SKIN[i%SKIN.length]));for(let j=0;j<2;j++){arms.setColorAt(i*2+j,color.setHex(shirt));legs.setColorAt(i*2+j,color.setHex(0x344654))}}
    for(const p of parts)p.instanceColor.needsUpdate=true;
    function place(part,n,p,x,y,z,swing=0){const a=p.yaw||0,c=Math.cos(a),s=Math.sin(a);o.position.set(p.x+c*x+s*z,.5+(p.y||0)+y,p.z-s*x+c*z);o.rotation.set(swing,a,0);o.scale.setScalar(1.25);o.updateMatrix();part.setMatrixAt(n,o.matrix)}
    const update=t=>{for(let i=0;i<count;i++){const p=pose(i,t),g=p.gait||0,hop=p.hop||0;place(body,i,p,0,1.25+hop,0);place(head,i,p,0,1.98+hop,0);for(let j=0;j<2;j++){const sign=j?1:-1,step=Math.sin(g+j*Math.PI)*.48;place(arms,i*2+j,p,sign*.4,(p.cheer?1.9:1.25)+hop,0,p.cheer?Math.PI+step*.2:-step);place(legs,i*2+j,p,sign*.16,.48+hop,0,step)}}for(const p of parts)p.instanceMatrix.needsUpdate=true};updaters.push(update);update(0);
  }
  let disposed=false;
  return {group,add,mesh,material,particles,people,updaters,finish,setNight(value){for(const m of materials)if(m.isMeshStandardMaterial){m.emissive.copy(m.color);m.emissiveIntensity=value?.2:0}},update(t){for(const f of updaters)f(t)},dispose(){if(disposed)return;disposed=true;for(const o of instances)o.dispose();for(const g of geometries)g.dispose();for(const m of materials)m.dispose();for(const t of textures)t.dispose();group.removeFromParent();group.clear()}};
}
function fireworks(s){
  const {p,c}=s.particles(6*400,6.5),color=new T.Color();
  s.updaters.push(t=>{for(let shell=0;shell<6;shell++){const age=(t+shell*.95)%6,cx=Math.sin(shell*2.4)*46,cz=Math.cos(shell*2.4)*35,top=147+(shell%3)*14;color.setHex(PALETTE[shell%5]);
    for(let j=0;j<400;j++){const index=shell*400+j;let x=cx,y=-1000,z=cz,fade=0;
      if(j<16&&age<1.65){const a=age-j*.025;if(a>=0){x+=Math.sin(a)*4;y=5+Math.pow(a/1.65,.8)*top;fade=(1-j/16)*.9}}
      if(j>=16&&age>=1.65){const tail=(j-16)%3,a=Math.max(0,age-1.65-tail*.07),k=Math.floor((j-16)/3),uy=1-2*(k+.5)/128,angle=k*2.399963,r=Math.sqrt(1-uy*uy),speed=15+(k%5)*2.2;x+=Math.cos(angle)*r*a*speed;y=top+uy*a*speed-4*a*a;z+=Math.sin(angle)*r*a*speed;fade=Math.pow(Math.max(0,1-a/4.35),1.6)*(1-tail*.22)*1.8}
      p.setXYZ(index,x,y,z);c.setXYZ(index,color.r*fade,color.g*fade,color.b*fade);
    }}p.needsUpdate=c.needsUpdate=true});
}
function drones(s){const {p,c}=s.particles(180,3.5),color=new T.Color();function shape(k,n){const a=k/180*TAU;if(n===0)return [Math.cos(a)*34,Math.sin(a)*34];if(n===1){const r=22+13*Math.cos(a*5);return [Math.cos(a)*r,Math.sin(a)*r]}return [Math.pow(Math.sin(a),3)*34,(13*Math.cos(a)-5*Math.cos(2*a)-2*Math.cos(3*a)-Math.cos(4*a))*2.2]}
  s.updaters.push(t=>{const phase=t/7,mode=Math.floor(phase)%3,u=Math.min(1,(phase%1)*2),ease=u*u*(3-2*u);for(let i=0;i<180;i++){const a=shape(i,mode),b=shape(i,(mode+1)%3);p.setXYZ(i,T.MathUtils.lerp(a[0],b[0],ease),155+T.MathUtils.lerp(a[1],b[1],ease),Math.sin(i*.22+t*.2)*3);color.setHex(i%4?0x80d8ff:0xffd979);c.setXYZ(i,color.r,color.g,color.b)}p.needsUpdate=c.needsUpdate=true});
}
function stage(s,x=85,z=0){s.add(box(24,1.5,14,x,1.1,z),0x344354);s.add(box(24,13,.7,x,8,z-6),0x26374b);s.add(box(20,7,.2,x,8,z-5.55),0x86b4d7,true);for(const side of [-1,1]){s.add(box(3,9,3,x+side*14,5,z-3),0x273440);s.add(box(1,16,1,x+side*13,9,z-6),0x8c9ca9)}s.add(box(27,1,1,x,17,z-6),0xa7b5bb)}
function concert(s){stage(s);const beams=[];for(let i=0;i<4;i++){const m=new T.MeshBasicMaterial({color:PALETTE[i],transparent:true,opacity:.12,side:T.DoubleSide,depthWrite:false,blending:T.AdditiveBlending});const b=s.mesh(new T.ConeGeometry(7,42,12,1,true).rotateX(Math.PI).translate(0,21,0),m);b.position.set(73+i*8,16,-5);beams.push(b)}s.people(64,(i,t)=>({x:65+(i%8)*5.5,z:15+Math.floor(i/8)*3.5,yaw:Math.PI,hop:Math.max(0,Math.sin(t*3+i))*.18,cheer:true,gait:t*3+i}));s.updaters.push(t=>beams.forEach((b,i)=>{b.rotation.z=Math.sin(t*.4+i)*.35;b.rotation.x=.25+Math.cos(t*.3+i)*.2}));}
function arch(s,x,z,color){s.add(box(1,7,1,x-5,4,z),color);s.add(box(1,7,1,x+5,4,z),color);s.add(box(11,1.8,1.3,x,7,z),color);for(let i=0;i<10;i++)s.add(box(1,.08,2,x-4.5+i,.65,z),i%2?0xf7f3df:0x334453)}
function marathon(s){s.add(ellipse(31,44,95,.65,5,.65),0xd1c5a3);s.add(ellipse(31,44,95,.72,5,.12),0xeff7e8,true);arch(s,95,-39,0x78bddb);arch(s,95,49,0xe6bd66);for(let i=0;i<16;i++){const a=i/16*TAU;s.add(box(.4,.8,.4,95+34*Math.cos(a),1,5+47*Math.sin(a)),0xdd8e5c)}s.people(44,(i,t)=>{const a=i/44*TAU+t*.065;return {x:95+(30+(i%3-1)*.6)*Math.cos(a),z:5+(43+(i%3-1)*.6)*Math.sin(a),yaw:Math.atan2(-30*Math.sin(a),43*Math.cos(a)),gait:t*8+i,hop:Math.abs(Math.sin(t*8+i))*.08}});}
function stall(s,x,z,color){s.add(box(7,2.5,4,x,1.75,z),color);s.add(box(7.8,.5,5.4,x,5,z),color);s.add(box(6,.5,1,x,2.8,z+2.5),0xe2ccaa);for(const side of [-1,1])s.add(box(.2,4,.2,x+side*3.2,3,z+1.7),0xa99880)}
function market(s){const flags=[];for(let i=0;i<8;i++){const x=63+(i%4)*15,z=-15+Math.floor(i/4)*38;stall(s,x,z,PALETTE[i%5]);s.add(box(.12,8,.12,x+4,4,z),0xb3b2a2);const f=s.mesh(new T.PlaneGeometry(2,1.2),s.material(PALETTE[(i+1)%5],true));f.position.set(x+5,7.4,z);flags.push(f)}s.people(26,(i,t)=>({x:60+((i*11+t*1.1)%59),z:1+(i%3)*5,yaw:Math.PI/2,gait:t*3+i}));s.updaters.push(t=>flags.forEach((f,i)=>f.rotation.y=Math.sin(t*1.5+i)*.35));}
function icerink(s){s.add(disc(27,19,85,.6,8),0x9bd6df);s.add(ellipse(27,19,85,1.1,8,.4),0xe7e9dd);s.add(ellipse(27,19,85,.7,8,.18),0x90cde7,true);s.people(28,(i,t)=>{const a=i*.9+t*(.11+(i%3)*.02),r=.5+(i%5)*.08;return {x:85+25*r*Math.cos(a),z:8+17*r*Math.sin(a),yaw:Math.atan2(-25*Math.sin(a),17*Math.cos(a)),gait:Math.sin(t+i)*.25}});}
function cinema(s){s.add(box(27,16,1,85,9,-14),0x2d3b49);s.add(box(24,13,.2,85,9,-13.4),0x132239,true);const moon=s.mesh(new T.CircleGeometry(2.8,24),s.material(0xffdb8d,true));moon.position.set(89,11,-13.2);const star=s.mesh(new T.IcosahedronGeometry(1.2,0),s.material(0x82d6e2,true));star.position.z=-12.9;for(let i=0;i<36;i++){const x=72+(i%6)*5,z=1+Math.floor(i/6)*4;s.add(box(2,.6,2,x,1,z),0xa89376);s.add(box(2,2,.3,x,1.9,z+1),0xa89376)}s.people(36,(i)=>({x:72+(i%6)*5,z:1+Math.floor(i/6)*4,y:-.2,yaw:Math.PI}));s.updaters.push(t=>{star.position.x=85+7*Math.cos(t*.3);star.position.y=9+3*Math.sin(t*.3);star.rotation.z=t*.3;moon.position.x=89+Math.sin(t*.12)})}
function dance(s){for(let i=0;i<36;i++)s.add(box(4,.25,4,75+(i%6)*4,.65,-2+Math.floor(i/6)*4),PALETTE[(i+Math.floor(i/6))%5]);s.add(box(6,2,3,85,1.7,-9),0x3d4b5c);s.people(20,(i,t)=>({x:77+(i%5)*4+Math.sin(t*2+i)*.4,z:1+Math.floor(i/5)*5,yaw:Math.sin(t+i)*.5,gait:t*4+i,hop:Math.abs(Math.sin(t*2+i))*.35,cheer:i%3===0}));}
function basketball(s){s.add(box(20,.2,30,85,.6,8),0x548d83);for(const x of [75,95])s.add(box(.15,.08,30,x,.76,8),0xf2e7cb);for(const z of [-7,23,8])s.add(box(20,.08,.15,85,.76,z),0xf2e7cb);s.add(ellipse(3,3,85,.8,8,.1),0xf2e7cb);for(const sign of [-1,1]){const z=8+sign*13;s.add(box(.25,6,.25,85,3.5,z),0xc1bda8);s.add(box(3,2,.18,85,6,z),0xe7e7d5);s.add(new T.TorusGeometry(.65,.08,5,20).rotateX(Math.PI/2).translate(85,5.2,z-sign),0xe79151)}const ball=s.mesh(new T.SphereGeometry(.48,12,8),s.material(0xe99645));s.people(6,(i,t)=>{const a=t*.15+i/6*TAU;return {x:85+6*Math.cos(a),z:8+10*Math.sin(a),yaw:-a,gait:t*6+i}});s.updaters.push(t=>{ball.position.set(85+5*Math.cos(t*.65),.9+Math.abs(Math.sin(t*2.5))*3.2,8+8*Math.sin(t*.4));ball.rotation.z=t*3})}
function food(s){for(let i=0;i<4;i++){const x=62+i*17;s.add(box(10,4.5,5,x,3,-8),PALETTE[i]);s.add(box(3,4,5,x+6,2.75,-8),PALETTE[i]);s.add(box(7,2,.15,x,3.6,-5.4),0x3c5260);s.add(box(8,.2,2,x,2.5,-4.5),0xe7d6b1);for(const wheel of [-4,5])s.add(new T.CylinderGeometry(.8,.8,.4,10).rotateX(Math.PI/2).translate(x+wheel,1,-5.5),0x35434c);s.add(box(1,1.2,1,x,5.8,-8),0x8a9c9f)}for(let i=0;i<6;i++){const x=68+(i%3)*17,z=12+Math.floor(i/3)*13;s.add(box(7,.25,3,x,2,z),0xceba98);s.add(box(.5,2,.5,x,1,z),0x89988c)}const smoke=s.particles(64,2.4,false);s.updaters.push(t=>{for(let i=0;i<64;i++){const age=(t*.35+(i%16)/16)%1;smoke.p.setXYZ(i,62+Math.floor(i/16)*17+Math.sin(age*4+i)*age,6+age*6,-8+age*1.6);const v=(1-age)*.55;smoke.c.setXYZ(i,v,v,v)}smoke.p.needsUpdate=smoke.c.needsUpdate=true});s.people(22,(i,t)=>({x:61+(i*13+t*.7)%61,z:1+(i%3)*5,yaw:Math.PI/2,gait:t*2.4+i}));}
const BUILDERS={fireworks,concert,marathon,market,icerink,cinema,drones,dance,basketball,food};
export function createCityEvents(){
  const group=new T.Group();group.name='city-event-vignettes';group.visible=false;group.userData.mode='none';const cache=new Map();let mode='none',last=null,elapsed=0,disposed=false,night=false;
  return {group,
    setMode(id){if(disposed)return;const next=Object.hasOwn(BUILDERS,id)?id:'none';if(next===mode)return;for(const s of cache.values())s.group.visible=false;mode=next;last=null;elapsed=0;group.userData.mode=mode;group.visible=mode!=='none';if(mode==='none')return;if(!cache.has(mode)){const s=makeScene();BUILDERS[mode](s);s.finish();s.setNight(night);s.group.name=`event-${mode}`;s.update(2.4);cache.set(mode,s);group.add(s.group)}cache.get(mode).group.visible=true},
    setNight(value){night=Boolean(value);group.userData.night=night;for(const s of cache.values())s.setNight(night)},
    update(seconds,reducedMotion=false){if(disposed||mode==='none'||!Number.isFinite(seconds))return;if(last!==null&&!reducedMotion)elapsed+=Math.min(.1,Math.max(0,seconds-last));last=seconds;cache.get(mode).update(reducedMotion?2.4:elapsed)},
    dispose(){if(disposed)return;disposed=true;for(const s of cache.values())s.dispose();cache.clear();group.removeFromParent();group.clear();group.visible=false}
  };
}
