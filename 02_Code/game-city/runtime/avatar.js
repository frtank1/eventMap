import * as THREE from 'three';
import {palettes,defaultLook,validateLook} from './social-state.js';
import {CITY_PALETTE} from './art-direction.js';
import {mergeGeometries} from '../source/vendor/BufferGeometryUtils.js';
import {loft,softBox,curve,sweptHair} from './avatar-geometry.js';

// Procedural miniature city explorer: shaped profiles + articulated rigid parts.
// Not a handmade/Blender asset, scan, licensed character or GPS representation.
export function createAvatar(input=defaultLook,{detail='full'}={}) {
  const rich=detail!=='crowd',root=new THREE.Group(),body=new THREE.Group(),torso=new THREE.Group();
  root.name='City explorer · local appearance';body.name='Pelvis';torso.name='Torso';root.add(body);body.add(torso);
  const geometries=new Set(),materials=new Set(),detailGroups=new Set(),detailByParent=new WeakMap();
  const material=(name,color,roughness=.85)=>{const m=new THREE.MeshStandardMaterial({color,roughness});m.name=name;materials.add(m);return m;};
  const coat=material('Jacket fabric',CITY_PALETTE.glass[0]),seam=material('Jacket shaded fabric',CITY_PALETTE.dark[0]),lining=material('Warm lining',CITY_PALETTE.trim[0]);
  const skin=material('Skin',0xd8a078),skinShadow=material('Skin warm recess',0xa36c51),hair=material('Hair',0x292833,.94),hairLight=material('Hair ridges',0x423b45,.94);
  const pants=material('Trousers',CITY_PALETTE.dark[0]),sole=material('Rubber sole',0x414846),bagMat=material('Canvas backpack',CITY_PALETTE.sand[0]);
  // Reuse ivory, sand and dark fabric on shirt, shoes, straps and small trims.
  const shoe=bagMat,bagEdge=pants,white=lining,metal=lining;
  const eye=material('Iris',0x28332e,.7),mouth=material('Mouth',0x87514b);
  function group(parent,name,p=[0,0,0]){const g=new THREE.Group();g.name=name;g.position.set(...p);parent.add(g);return g;}
  function fine(parent){if(!detailByParent.has(parent)){const g=group(parent,'Near detail');detailByParent.set(parent,g);detailGroups.add(g);}return detailByParent.get(parent);}
  function mesh(parent,name,g,m,p=[0,0,0],s=[1,1,1]){geometries.add(g);const o=new THREE.Mesh(g,m);o.name=name;o.position.set(...p);o.scale.set(...s);o.castShadow=false;o.receiveShadow=true;parent.add(o);return o;}
  const ellipsoid=(parent,name,m,p,s)=>mesh(parent,name,new THREE.SphereGeometry(1,rich?12:8,rich?8:6),m,p,s);
  const line=(parent,name,m,points,r=.006)=>mesh(parent,name,curve(points,r,rich?8:5,5),m);
  mesh(torso,'Tailored jacket',loft([[.862,.203,.123],[.892,.222,.14],[1.02,.215,.141],[1.19,.238,.15],[1.32,.248,.137],[1.385,.19,.106],[1.425,.095,.075]],rich?20:12,2.6),coat);
  mesh(torso,'Jacket hem',loft([[.858,.204,.125],[.87,.216,.135],[.89,.221,.139]],rich?16:10,2.6),seam);
  mesh(torso,'T-shirt',loft([[1.29,.097,.08,.055],[1.38,.084,.079,.03],[1.46,.072,.062,.008]],12,2.4),lining);
  // A cropped field jacket with a stand collar, not a bulky hood/mannequin neck.
  mesh(torso,'Stand collar',loft([[1.36,.113,.09,-.016],[1.44,.093,.074,-.016],[1.465,.084,.066,-.016]],rich?16:10,2.4),coat);
  for(const side of [-1,1]){
    const collar=mesh(torso,'Open collar',softBox(.054,.123,.026,.012),seam,[side*.075,1.399,.093]);collar.rotation.z=-side*.29;collar.rotation.y=side*.28;
  }
  const jacketDetail=fine(torso);
  if(rich){
    line(jacketDetail,'Main zip',seam,[[.012,.89,.144],[.013,1.08,.147],[.025,1.28,.157],[.043,1.375,.118]],.004);
    mesh(jacketDetail,'Zip pull',softBox(.014,.025,.008,.003),metal,[.025,1.265,.165]);
    for(const side of [-1,1]){
      line(jacketDetail,'Angled pocket welt',seam,[[side*.105,1.045,.151],[side*.151,1.012,.154],[side*.18,.987,.14]],.006);
      line(jacketDetail,'Shoulder yoke seam',seam,[[side*.11,1.335,.121],[side*.177,1.317,.13],[side*.218,1.294,.113]],.003);
    }
    mesh(jacketDetail,'Small woven chest tab',softBox(.032,.021,.009,.004),lining,[-.116,1.21,.158]);
  }
  mesh(torso,'Neck',loft([[1.405,.064,.058],[1.545,.059,.056]],12),skin);
  const head=group(torso,'Head',[0,1.49,.006]);
  // Broad cheek plane, compact chin and tapered forehead: a readable adult face
  // at miniature scale, rather than an elongated egg with stuck-on facial dots.
  mesh(head,'Shaped head',loft([[.026,.055,.064,.015],[.048,.083,.081,.018],[.085,.115,.091,.015],[.15,.143,.105,.005],[.223,.141,.116,0],[.285,.132,.109,-.008],[.336,.106,.085,-.014],[.365,.038,.033,-.015]],rich?24:14,2.3),skin);
  for(const side of [-1,1]){
    ellipsoid(head,'Ear',skin,[side*.142,.192,-.004],[.017,.031,.021]);
    if(rich)ellipsoid(fine(head),'Ear fold',skinShadow,[side*.151,.194,.011],[.005,.015,.006]);
  }
  const face=fine(head),eyes=group(face,'Blinking eyes',[0,.224,.115]);
  for(const side of [-1,1]){
    if(rich){
      ellipsoid(eyes,'Almond eye',white,[side*.057,0,0],[.022,.011,.007]);
      ellipsoid(eyes,'Iris',eye,[side*.054,-.001,.006],[.009,.010,.004]);
      line(eyes,'Upper eyelid',skinShadow,[[side*.035,.002,.005],[side*.056,.011,.005],[side*.078,.001,-.001]],.003);
      line(face,'Brow',hair,[[side*.032,.261,.116],[side*.058,.266,.112],[side*.085,.258,.1]],.0055);
    }else ellipsoid(eyes,'Small eye',eye,[side*.056,0,.003],[.008,.01,.004]);
  }
  if(rich)line(face,'Quiet smile',mouth,[[-.029,.111,.108],[-.008,.106,.116],[.014,.108,.115],[.03,.115,.105]],.003);
  mesh(head,'Shaped nose',loft([[.161,.013,.009,.121],[.174,.021,.016,.136],[.19,.016,.021,.133],[.23,.009,.012,.118],[.25,.006,.006,.114]],rich?12:8),skin);
  const hairCap=group(head,'Hair base');
  mesh(hairCap,'Continuous swept crown',sweptHair(rich?24:14,rich?7:5),hair);
  const shortHair=group(head,'Short swept hairstyle'),longHair=group(head,'Long layered hairstyle');
  const fringe=loft([[.276,.025,.018,.109,-.079],[.30,.079,.029,.11,-.037],[.334,.119,.043,.083,-.005],[.366,.093,.047,.052,.013],[.391,.039,.025,.027,.029]],rich?18:12,2.2);
  mesh(shortHair,'Single swept fringe',fringe,hair);
  mesh(longHair,'Side-part fringe',fringe,hair);
  if(rich)for(const style of [shortHair,longHair]){
    line(fine(style),'Subtle part',hairLight,[[.079,.362,.089],[.064,.386,.048],[.045,.403,-.004]],.0025);
  }
  for(const side of [-1,1]){
    mesh(longHair,'Tucked side layer',loft([[.293,.026,.064,-.018,side*.12],[.21,.03,.065,-.058,side*.139],[.085,.037,.067,-.039,side*.14],[-.035,.03,.049,-.018,side*.135],[-.075,.017,.03,.006,side*.12]],rich?14:10),hair);
  }
  mesh(longHair,'Layered back hair',loft([[.31,.124,.065,-.072],[.235,.145,.076,-.071],[.08,.149,.059,-.082],[-.045,.13,.043,-.077],[-.075,.103,.021,-.068]],rich?18:12,2.3),hair);
  const hat=group(head,'Beanie');
  mesh(hat,'Beanie crown',loft([[.285,.15,.132,-.012],[.34,.151,.13,-.014],[.402,.12,.103,-.02],[.435,.066,.059,-.018],[.445,.013,.012,-.018]],rich?18:12),coat);
  mesh(hat,'Folded beanie rim',loft([[.273,.15,.131,-.01],[.284,.158,.139,-.01],[.32,.156,.136,-.01],[.33,.149,.13,-.01]],rich?18:12),seam);
  if(rich)mesh(fine(hat),'Beanie fabric tab',softBox(.034,.035,.012,.005),lining,[.065,.301,.121]);

  const bag=group(torso,'Backpack');
  mesh(bag,'Soft canvas pack',softBox(.30,.365,.135,.042),bagMat,[0,1.18,-.204]);
  mesh(bag,'Pack bottom',softBox(.288,.065,.132,.022),bagEdge,[0,1.027,-.206]);
  const bagDetail=fine(bag);
  if(rich){
    mesh(bagDetail,'Front pocket',softBox(.224,.12,.04,.018),bagMat,[0,1.108,-.28]);
    line(bagDetail,'Pocket piping',bagEdge,[[-.093,1.144,-.302],[0,1.156,-.304],[.093,1.144,-.302]],.003);
    mesh(bagDetail,'Woven pack label',softBox(.03,.036,.007,.003),lining,[0,1.275,-.275]);
    line(bagDetail,'Carry loop',bagEdge,[[-.03,1.35,-.206],[-.026,1.398,-.2],[.026,1.398,-.2],[.03,1.35,-.206]],.009);
  }
  for(const side of [-1,1]){
    const points=[[side*.11,1.34,-.21],[side*.16,1.412,-.02],[side*.174,1.28,.153],[side*.16,1.065,.147],[side*.13,1.025,-.17]];
    // Flatten the strap cross-section, keeping its path in world proportions.
    mesh(bag,'Flat woven shoulder strap',curve(points.map(([x,y,z])=>[x/2.6,y,z]),.007,rich?12:7,5).scale(2.6,1,1),bagEdge);
  }
  const limbs=[];
  for(const side of [-1,1]){
    const shoulder=group(torso,side<0?'Left shoulder':'Right shoulder',[side*.246,1.35,0]);
    mesh(shoulder,'Upper sleeve',loft([[.051,.028,.028],[.027,.06,.059],[0,.083,.08],[-.13,.076,.073],[-.255,.064,.062],[-.285,.061,.06]],rich?14:10,2.4),coat);
    const elbow=group(shoulder,'Elbow',[0,-.285,0]);
    mesh(elbow,'Lower sleeve',loft([[.012,.062,.06],[-.075,.065,.062],[-.20,.051,.05],[-.235,.048,.047]],rich?14:10,2.4),coat);
    mesh(elbow,'Ribbed cuff',loft([[-.215,.053,.052],[-.247,.05,.047]],12,2.5),seam);
    ellipsoid(elbow,'Palm',skin,[0,-.294,.002],[.041,.054,.03]);
    ellipsoid(elbow,'Thumb',skin,[-side*.034,-.279,.018],[.016,.028,.018]);
    const hip=group(body,side<0?'Left hip':'Right hip',[side*.112,.9,0]);
    mesh(hip,'Shaped thigh',loft([[.022,.096,.098],[0,.103,.102],[-.13,.098,.093],[-.33,.075,.074],[-.39,.071,.072]],rich?14:10,2.4),pants);
    const knee=group(hip,'Knee',[0,-.39,0]);
    mesh(knee,'Tapered calf',loft([[.015,.073,.073],[-.09,.076,.07],[-.27,.059,.058],[-.36,.056,.054]],rich?14:10,2.4),pants);
    const foot=group(knee,'Ankle',[0,-.36,0]);
    mesh(foot,'Rubber outsole',loft([[-.1325,.069,.128,.054],[-.125,.0775,.1425,.054],[-.088,.0775,.1425,.054],[-.0775,.073,.137,.054]],rich?16:12,3),sole);
    mesh(foot,'Cream midsole',loft([[-.089,.079,.141,.054],[-.06,.079,.141,.054],[-.055,.074,.136,.054]],rich?16:12,3),lining);
    mesh(foot,'Suede sneaker',loft([[-.065,.074,.132,.053],[-.04,.078,.132,.057],[.015,.069,.105,.037],[.055,.057,.063,-.008],[.07,.05,.05,-.012]],rich?16:10,2.5),shoe);
    if(rich){
      mesh(fine(foot),'Heel tab',softBox(.064,.055,.015,.006),lining,[0,-.005,-.071]);
      for(let i=0;i<3;i++)line(fine(foot),'Laces',lining,[[-.033,.036-i*.012,.052+i*.021],[0,.043-i*.012,.054+i*.021],[.033,.036-i*.012,.052+i*.021]],.003);
    }
    limbs.push({side,shoulder,elbow,hip,knee,foot});
  }
  // The map caches its static shadow map: a moving character uses an owned
  // soft contact shadow instead of leaving a stale directional silhouette.
  const pixels=new Uint8Array(32*32*4);for(let y=0;y<32;y++)for(let x=0;x<32;x++){const a=Math.max(0,1-Math.hypot((x-15.5)/15.5,(y-15.5)/15.5));pixels.set([37,48,43,Math.round(a*a*95)],(y*32+x)*4);}
  const shadowTexture=new THREE.DataTexture(pixels,32,32);shadowTexture.needsUpdate=true;
  const shadowMaterial=new THREE.MeshBasicMaterial({map:shadowTexture,transparent:true,depthWrite:false});shadowMaterial.name='Contact shadow';materials.add(shadowMaterial);
  const shadow=mesh(root,'Soft contact shadow',new THREE.PlaneGeometry(.9,.65).rotateX(-Math.PI/2),shadowMaterial,[0,.006,0]);shadow.receiveShadow=false;

  // Static pieces of each articulated part share material draw calls. Never
  // merge across joints, blink groups, hairstyle toggles, or near/far detail.
  const parents=[];root.traverse(o=>{if(o.isGroup)parents.push(o);});
  for(const parent of parents){
    const buckets=new Map();for(const child of parent.children)if(child.isMesh&&child!==shadow){const values=buckets.get(child.material)||[];values.push(child);buckets.set(child.material,values);}
    for(const [mat,values]of buckets){if(values.length<2)continue;const prepared=values.map(o=>{o.updateMatrix();let g=o.geometry.clone().applyMatrix4(o.matrix);if(g.index){const old=g;g=g.toNonIndexed();old.dispose();}g.deleteAttribute('uv');return g;});
      const g=mergeGeometries(prepared);prepared.forEach(p=>p.dispose());if(!g)continue;
      values.forEach(o=>o.removeFromParent());mesh(parent,mat.name+' · batch',g,mat);
    }
  }
  const used=new Set();root.traverse(o=>{if(o.geometry)used.add(o.geometry);});for(const g of geometries)if(!used.has(g)){g.dispose();geometries.delete(g);}
  let look,walkAmount=0,lastTime=null,disposed=false,near=true,motionTime=0,gaitPhase=0;
  const tone=(m,source,factor)=>m.color.copy(source.color).multiplyScalar(factor);
  function apply(value){
    if(disposed)return;
    look=validateLook(value)||{...defaultLook};coat.color.setHex(palettes.coat[look.coat]);tone(seam,coat,.66);
    skin.color.setHex(palettes.skin[look.skin]);tone(skinShadow,skin,.66);mouth.color.copy(skin.color).multiply(new THREE.Color(.66,.44,.43));
    hair.color.setHex(palettes.hair[look.hair]);hairLight.color.copy(hair.color).lerp(new THREE.Color(CITY_PALETTE.sand[0]),.07);
    shortHair.visible=look.style===0;longHair.visible=look.style===1;hat.visible=look.style===2;hairCap.visible=look.style!==2;bag.visible=look.bag;
  }
  function setDetail(distance=0){if(disposed)return;near=rich&&Number.isFinite(distance)&&distance>=0&&distance<(near?42:32);for(const g of detailGroups)g.visible=near;}
  function update(time,moving=false,reduced=false){
    if(disposed||!Number.isFinite(time)||(lastTime!==null&&time<lastTime&&!reduced))return;
    const dt=lastTime===null?0:Math.min(.1,Math.max(0,time-lastTime));lastTime=time;
    // Integrate a bounded local clock: resuming a WebView must not jump a stride
    // or a glance. Exponential easing is independent of 30/60/120 Hz rendering.
    if(reduced){walkAmount=0;gaitPhase=0;}else{
      const target=moving?1:0,previous=walkAmount,blend=1-Math.exp(-dt*9);
      motionTime+=dt;walkAmount+=(target-walkAmount)*blend;
      // Integrate the easing curve as well as the amplitude (not an FPS-sized
      // right-endpoint approximation), so footfall timing is also consistent.
      gaitPhase=(gaitPhase+7.2*(target*dt+(previous-target)*blend/9))%(Math.PI*2);
    }
    const phase=gaitPhase,amount=walkAmount;let support=Infinity;
    for(const limb of limbs){
      const wave=Math.sin(phase+(limb.side<0?Math.PI:0));
      const hip=-wave*.38*amount,knee=Math.max(0,-wave)*.74*amount;
      const roll=(-Math.max(0,wave)*.10+Math.max(0,-wave)*.14)*amount;
      limb.hip.rotation.x=hip;limb.knee.rotation.x=knee;limb.foot.rotation.x=roll-(hip+knee);
      limb.shoulder.rotation.x=wave*.25*amount-.035;limb.shoulder.rotation.z=-limb.side*.095;
      limb.elbow.rotation.x=-.14-Math.max(0,wave)*.16*amount;
      // Exact low edge of the outsole at this restrained heel/toe roll. The
      // lowest shoe stays grounded without flattening both ankles every frame.
      support=Math.min(support,.9-.39*Math.cos(hip)-.36*Math.cos(hip+knee)-.1325*Math.cos(roll)-.054*Math.sin(roll)-.128*Math.abs(Math.sin(roll)));
    }
    body.position.y=-support;body.position.x=reduced?0:Math.sin(phase)*.008*amount;
    torso.position.y=reduced?0:Math.sin(motionTime*1.8)*.0025*(1-amount);
    torso.rotation.z=reduced?0:Math.sin(phase)*.018*amount+Math.sin(motionTime*.75)*.004*(1-amount);
    torso.rotation.y=reduced?0:Math.sin(phase)*.04*amount;
    head.rotation.y=reduced?0:Math.sin(motionTime*.55)*.035*(1-amount)-torso.rotation.y*.35;head.rotation.z=-torso.rotation.z*.6;
    const blinkPhase=(motionTime+(look.skin+look.hair)*.31)%4.6;
    eyes.scale.y=reduced||!near?1:blinkPhase<.16?Math.max(.08,1-Math.sin(blinkPhase/.16*Math.PI)):1;
    bag.rotation.x=reduced?0:Math.sin(phase+.3)*.012*amount;
  }
  function stats(){let draws=0,triangles=0;root.traverseVisible(o=>{if(o.isMesh){draws++;triangles+=(o.geometry.index?.count||o.geometry.attributes.position.count)/3;}});return {draws,triangles,detail:near?'near':'far'};}
  function destroy(){if(disposed)return;disposed=true;root.removeFromParent();for(const g of geometries)g.dispose();for(const m of materials)m.dispose();shadowTexture.dispose();}
  apply(input);setDetail(rich?0:100);update(0,false,true);
  return {root,apply,update,setDetail,stats,destroy,get look(){return {...look}}};
}

// Explicitly opt-in test scenery. Never placed for real events or in the normal map.
export function createFootballDemo(){
  const group=new THREE.Group(),mats=[],geos=[];group.name='DEMO football, not a real pitch';
  const mat=color=>{const m=new THREE.MeshStandardMaterial({color,roughness:1});mats.push(m);return m;};
  const turf=mat(0x527d68),line=mat(0xe3e8d7),edge=mat(0xc9c7b4);
  function box(w,h,d,m,x=0,y=0,z=0){const g=new THREE.BoxGeometry(w,h,d);geos.push(g);const o=new THREE.Mesh(g,m);o.position.set(x,y,z);group.add(o);return o;}
  box(23,.16,35,edge,0,.02,0);box(21,.12,33,turf,0,.16,0);
  for(const x of [-10,10])box(.1,.02,32,line,x,.235,0);
  for(const z of [-16,0,16])box(20,.02,.1,line,0,.235,z);
  const ringGeo=new THREE.RingGeometry(3,3.09,32);geos.push(ringGeo);const ring=new THREE.Mesh(ringGeo,line);ring.rotation.x=-Math.PI/2;ring.position.y=.24;group.add(ring);
  for(const z of [-16,16]){box(5,.1,.1,line,0,2.2,z);for(const x of [-2.5,2.5])box(.1,2.2,.1,line,x,1.1,z);}
  const players=Array.from({length:4},(_,i)=>{const a=createAvatar({...defaultLook,coat:i<2?0:1,bag:false},{detail:'crowd'});a.root.scale.setScalar(.95);group.add(a.root);return a;});
  const ballGeo=new THREE.IcosahedronGeometry(.23,1);geos.push(ballGeo);const ball=new THREE.Mesh(ballGeo,line);group.add(ball);
  function update(t,reduced=false){if(reduced)t=0;players.forEach((p,i)=>{const phase=t*.3+i*Math.PI*.5;p.root.position.set(Math.sin(phase)*6,.25,Math.cos(phase)*10);p.root.rotation.y=Math.atan2(Math.cos(phase)*6,-Math.sin(phase)*10);p.update(t,true,reduced);});ball.position.set(Math.sin(t*.3)*5,.49,Math.cos(t*.3)*8);}
  update(0);return {group,update,destroy(){group.removeFromParent();players.forEach(p=>p.destroy());geos.forEach(g=>g.dispose());mats.forEach(m=>m.dispose());}};
}
