import test from 'node:test';
import assert from 'node:assert/strict';
import {registerHooks} from 'node:module';
import {defaultLook,palettes} from '../runtime/social-state.js';
import {CITY_PALETTE} from '../runtime/art-direction.js';

// Resolve the browser's existing import-map dependency without building a bundle
// or writing generated runtime files. Test runner: Node >=22.15 / 24.
const threeURL=new URL('../source/vendor/three.module.js',import.meta.url).href;
const hook=registerHooks({resolve(specifier,context,next){return next(specifier==='three'?threeURL:specifier,context);}});
const {createAvatar,createFootballDemo}=await import('../runtime/avatar.js');
const {loft,sweptHair}=await import('../runtime/avatar-geometry.js');
const THREE=await import(threeURL);
hook.deregister();

function pose(avatar){
  const values=[];avatar.root.traverse(o=>values.push(...o.position.toArray(),...o.rotation.toArray().slice(0,3),...o.scale.toArray()));
  return values.map(n=>n===0?0:n);
}

function lowestSoles(avatar){
  avatar.root.updateMatrixWorld(true);const result=[],v=new THREE.Vector3();
  avatar.root.traverse(o=>{
    if(o.name!=='Rubber outsole')return;
    let lowest=Infinity;const positions=o.geometry.attributes.position;
    for(let i=0;i<positions.count;i++){v.fromBufferAttribute(positions,i).applyMatrix4(o.matrixWorld);lowest=Math.min(lowest,v.y);}
    result.push(lowest);
  });
  return result;
}

test('avatar is finite, human-sized and bounded after per-joint batching',()=>{
  const a=createAvatar();
  a.root.traverse(o=>{if(o.isMesh){for(const key of ['position','normal'])for(const n of o.geometry.attributes[key].array)assert.ok(Number.isFinite(n),`${o.name}: ${key}`);}});
  const bounds=new THREE.Box3().setFromObject(a.root),size=bounds.getSize(new THREE.Vector3());
  assert.ok(size.y>1.8&&size.y<2.1);assert.ok(size.x<1);assert.ok(Math.abs(bounds.min.y)<.005);
  assert.ok(a.stats().draws<=42);assert.ok(a.stats().triangles<=9700);
  a.destroy();
});

test('loft offsets, outward winding and crisp end normals work in either direction',()=>{
  const rings=[[0,.2,.1,.03,-.02],[.5,.15,.12,-.01,.04],[1,.1,.1,.02,.03]],sides=12;
  for(const profile of [rings,[...rings].reverse()]){
    const g=loft(profile,sides,2.4),p=g.attributes.position,n=g.attributes.normal;
    let volume=0;const a=new THREE.Vector3(),b=new THREE.Vector3(),c=new THREE.Vector3();
    for(let i=0;i<g.index.count;i+=3){
      a.fromBufferAttribute(p,g.index.getX(i));b.fromBufferAttribute(p,g.index.getX(i+1));c.fromBufferAttribute(p,g.index.getX(i+2));
      volume+=a.dot(b.cross(c))/6;
    }
    assert.ok(volume>0,`outward signed volume: ${volume}`);
    for(let i=0;i<profile.length;i++){
      assert.ok(Math.abs(p.getX(i*sides)-(profile[i][1]+profile[i][4]))<1e-7);
      assert.ok(Math.abs(p.getZ(i*sides)-profile[i][3])<1e-7);
    }
    const ascending=profile[0][0]<profile.at(-1)[0];
    for(let end=0;end<2;end++)for(let i=0;i<=sides;i++){
      const index=profile.length*sides+end*(sides+1)+i;
      assert.ok(Math.abs(n.getY(index)-(end===0?(ascending?-1:1):(ascending?1:-1)))<1e-7);
    }
    for(const value of n.array)assert.ok(Number.isFinite(value));
    g.dispose();
  }
});

test('hair is a connected swept silhouette with a raised forehead and lower nape',()=>{
  const sides=24,rows=7,g=sweptHair(sides,rows),p=g.attributes.position,n=g.attributes.normal;
  assert.equal(g.index.count/3,sides*(2*rows-1));
  assert.equal(p.count,1+sides*rows);
  const front=1+(rows-1)*sides+sides/4,back=1+(rows-1)*sides+3*sides/4;
  assert.ok(p.getY(front)-p.getY(back)>.06);
  for(let i=0;i<p.count;i++){
    const normal=new THREE.Vector3().fromBufferAttribute(n,i);
    assert.ok(Math.abs(normal.length()-1)<1e-5);
    const outward=new THREE.Vector3(p.getX(i),p.getY(i)-.305,p.getZ(i)+.018);
    assert.ok(normal.dot(outward)>0);
  }
  g.computeBoundingBox();assert.ok(g.boundingBox.max.y<.42);assert.ok(g.boundingBox.min.x>-.17);assert.ok(g.boundingBox.max.x<.17);
  g.dispose();
});

test('all hairstyles and bag states stay within geometry, material and texture budgets',()=>{
  for(const detail of ['full','crowd'])for(let style=0;style<5;style++)for(const bag of [false,true])for(const glasses of [false,true]){
    const a=createAvatar({...defaultLook,style,bag,glasses},{detail}),materials=new Set(),textures=new Set();let residentTriangles=0;
    a.root.traverse(o=>{
      assert.ok(!o.isLight);if(!o.isMesh)return;
      residentTriangles+=(o.geometry.index?.count||o.geometry.attributes.position.count)/3;
      materials.add(o.material);if(o.material.map)textures.add(o.material.map);
      assert.equal(o.castShadow,false);assert.equal(Array.isArray(o.material),false);
      if(o.material.emissive)assert.equal(o.material.emissive.getHex(),0);
      for(const key of ['position','normal'])for(const n of o.geometry.attributes[key].array)assert.ok(Number.isFinite(n));
    });
    assert.ok(materials.size<=15);assert.equal(textures.size,1);
    for(const texture of textures){assert.ok(texture.isDataTexture);assert.equal(texture.image.width,32);assert.equal(texture.image.height,32);}
    const near=a.stats();assert.ok(near.draws<=(detail==='full'?44:30),JSON.stringify(near));
    assert.ok(near.triangles<=(detail==='full'?10400:4500),JSON.stringify(near));
    assert.ok(residentTriangles<=(detail==='full'?11500:5300),`resident triangles: ${residentTriangles}`);
    a.setDetail(100);const far=a.stats();assert.ok(far.draws<=30);assert.ok(far.triangles<=(detail==='full'?6700:4500));
    const colors=new Map([...materials].map(m=>[m.name,m.color.getHex()]));
    assert.equal(colors.get('Warm lining'),CITY_PALETTE.trim[0]);assert.equal(colors.get('Trousers'),CITY_PALETTE.dark[0]);
    assert.equal(colors.get('Canvas backpack'),CITY_PALETTE.sand[0]);
    a.destroy();
  }
});

test('existing appearance schema, palettes, hair choices and backpack survive',()=>{
  const a=createAvatar();
  for(let style=0;style<3;style++){
    const value={v:1,skin:3,hair:2,coat:4,style,bag:style!==1};a.apply(value);
    // A look saved before the new options existed loads with their defaults.
    assert.deepEqual(a.look,{...defaultLook,...value});
    assert.equal(a.root.getObjectByName('Short swept hairstyle').visible,style===0);
    assert.equal(a.root.getObjectByName('Long layered hairstyle').visible,style===1);
    assert.equal(a.root.getObjectByName('Beanie').visible,style===2);
    assert.equal(a.root.getObjectByName('Backpack').visible,value.bag);
    assert.ok(a.stats().draws<=44);assert.ok(a.stats().triangles<=10400);
    const colors=new Map();a.root.traverse(o=>{if(o.material)colors.set(o.material.name,o.material.color.getHex());});
    assert.equal(colors.get('Jacket fabric'),palettes.coat[4]);assert.equal(colors.get('Skin'),palettes.skin[3]);assert.equal(colors.get('Hair'),palettes.hair[2]);
  }
  for(const category of ['skin','hair','coat'])for(let index=0;index<palettes[category].length;index++){
    a.apply({...defaultLook,[category]:index});assert.equal(a.look[category],index);
  }
  a.apply({style:999});assert.deepEqual(a.look,defaultLook);
  const copy=a.look;copy.skin=999;assert.deepEqual(a.look,defaultLook);
  a.destroy();
});

test('detail switches have hysteresis and crowd figures use less geometry',()=>{
  const a=createAvatar(),near=a.stats();a.setDetail(41);assert.equal(a.stats().detail,'near');
  a.setDetail(42);const far=a.stats();assert.equal(far.detail,'far');assert.ok(far.draws<near.draws);assert.ok(far.triangles<near.triangles);
  for(const distance of [41,35,32]){a.setDetail(distance);assert.equal(a.stats().detail,'far');}
  a.setDetail(31);assert.equal(a.stats().detail,'near');
  for(const distance of [NaN,Infinity,-1]){a.setDetail(0);a.setDetail(distance);assert.equal(a.stats().detail,'far');}
  a.setDetail(0);a.apply({...defaultLook,style:1,bag:false});a.setDetail(42);a.setDetail(31);
  assert.equal(a.root.getObjectByName('Backpack').visible,false);assert.equal(a.root.getObjectByName('Long layered hairstyle').visible,true);
  const crowd=createAvatar(defaultLook,{detail:'crowd'});crowd.setDetail(0);assert.equal(crowd.stats().detail,'far');
  assert.ok(crowd.stats().triangles<=4100);assert.ok(crowd.stats().draws<=28);crowd.destroy();a.destroy();
});

test('walk articulates knees, elbows and ankles and grounds actual rolled sole vertices',()=>{
  const a=createAvatar();let kneeMotion=0,elbowMotion=0,footRoll=0,footLift=0;
  for(let i=1;i<180;i++){
    a.update(i/30,true);a.root.updateMatrixWorld(true);
    a.root.traverse(o=>{
      if(o.name==='Knee')kneeMotion=Math.max(kneeMotion,Math.abs(o.rotation.x));
      if(o.name==='Elbow')elbowMotion=Math.max(elbowMotion,Math.abs(o.rotation.x+.14));
      if(o.name==='Ankle')footRoll=Math.max(footRoll,Math.abs(o.rotation.x+o.parent.rotation.x+o.parent.parent.rotation.x));
      assert.ok(Number.isFinite(o.position.y));
    });
    const soles=lowestSoles(a);assert.equal(soles.length,2);footLift=Math.max(footLift,...soles);
    assert.ok(Math.abs(Math.min(...soles))<1e-6,`soles y=${soles}`);
    assert.ok(soles.every(y=>y>=-1e-6));
  }
  assert.ok(kneeMotion>.6);assert.ok(elbowMotion>.12);assert.ok(footRoll>.1);assert.ok(footLift>.05);
  for(let i=180;i<240;i++)a.update(i/30,false);
  assert.ok(Math.abs(a.root.getObjectByName('Left hip').rotation.x)<.001);
  a.destroy();
});

test('walk start/stop and footfall phase are consistent at 30, 60 and 120 Hz',()=>{
  const poses=[];
  for(const fps of [30,60,120]){
    const a=createAvatar(),moving=[];for(let i=1;i<=fps*3;i++){a.update(i/fps,i<=fps*2);if(i===fps)moving.push(...pose(a));}
    poses.push([...moving,...pose(a)]);a.destroy();
  }
  for(const values of poses.slice(1))for(let i=0;i<values.length;i++)assert.ok(Math.abs(values[i]-poses[0][i])<1e-9);
});

test('idle breath, glances and blinking stay restrained; pauses do not jump the gait',()=>{
  const a=createAvatar(),head=a.root.getObjectByName('Head'),torso=a.root.getObjectByName('Torso'),eyes=a.root.getObjectByName('Blinking eyes');
  let blink=false,glance=false,breath=false;
  for(let i=1;i<=360;i++){
    a.update(i/60);blink||=eyes.scale.y<.2;glance||=Math.abs(head.rotation.y)>.02;breath||=Math.abs(torso.position.y)>.002;
    assert.ok(Math.abs(torso.position.y)<=.0025);assert.ok(Math.abs(torso.rotation.z)<=.004);assert.ok(Math.abs(head.rotation.y)<=.035);
    assert.ok(Math.abs(Math.min(...lowestSoles(a)))<1e-6);
  }
  assert.ok(blink);assert.ok(glance);assert.ok(breath);
  const before=pose(a);for(const time of [NaN,Infinity,-1,5])a.update(time,true);assert.deepEqual(pose(a),before);
  a.update(3600,true);const hip=a.root.getObjectByName('Left hip');assert.ok(Math.abs(hip.rotation.x)<.12);
  a.setDetail(100);a.update(3600.1);assert.equal(eyes.scale.y,1);a.destroy();
});

test('reduced motion stays still, invalid time is ignored and resources dispose once',()=>{
  const a=createAvatar();a.update(1,true);a.update(0,true,true);
  const still=pose(a);a.update(6,true,true);assert.deepEqual(pose(a),still);a.update(NaN,true);assert.deepEqual(pose(a),still);
  const resources=new Set();a.root.traverse(o=>{if(o.geometry)resources.add(o.geometry);if(o.material){resources.add(o.material);if(o.material.map)resources.add(o.material.map);}});
  const counts=new Map();resources.forEach(r=>r.addEventListener('dispose',()=>counts.set(r,(counts.get(r)||0)+1)));
  const parent=new THREE.Group();parent.add(a.root);a.destroy();a.destroy();resources.forEach(r=>assert.equal(counts.get(r),1));assert.equal(parent.children.length,0);
  const look=a.look;a.update(7,true);a.apply({...defaultLook,style:1});a.setDetail(100);assert.deepEqual(pose(a),still);assert.deepEqual(a.look,look);
});

test('explicit football demo can reset its clock for reduced motion after walking',()=>{
  const demo=createFootballDemo();demo.update(2);demo.update(0,true);
  const still=pose({root:demo.group});demo.update(20,true);assert.deepEqual(pose({root:demo.group}),still);
  demo.group.traverse(o=>{if(o.name==='Left hip'||o.name==='Right hip')assert.equal(Math.abs(o.rotation.x),0);});
  demo.destroy();
});

test('LOD and customization allocate no resources; independent avatars dispose safely',()=>{
  const a=createAvatar(),b=createAvatar({...defaultLook,style:1},{detail:'crowd'});
  const resources=avatar=>{const set=new Set();avatar.root.traverse(o=>{if(o.geometry)set.add(o.geometry);if(o.material){set.add(o.material);if(o.material.map)set.add(o.material.map);}});return set;};
  const before=resources(a),other=resources(b);for(const resource of before)assert.ok(!other.has(resource));
  let otherDisposed=0;other.forEach(r=>r.addEventListener('dispose',()=>otherDisposed++));
  for(let i=0;i<24;i++){a.setDetail(i%2?100:0);a.apply({...defaultLook,style:i%3,bag:i%2===0});a.update(i/30,true);}
  assert.deepEqual(resources(a),before);a.destroy();assert.equal(otherDisposed,0);b.update(1,true);assert.ok(b.stats().triangles>0);b.destroy();assert.equal(otherDisposed,other.size);
});

test('new options: trousers, shoes, buzz cut, cap, glasses, height and build',()=>{
  const a=createAvatar();
  const look={...defaultLook,style:4,pants:2,shoes:3,glasses:true,height:2,build:0};a.apply(look);
  assert.deepEqual(a.look,look);
  assert.equal(a.root.getObjectByName('Cap').visible,true);assert.equal(a.root.getObjectByName('Hair base').visible,false);
  assert.equal(a.root.getObjectByName('Glasses').visible,true);
  const colors=new Map();a.root.traverse(o=>{if(o.material)colors.set(o.material.name,o.material.color.getHex());});
  assert.equal(colors.get('Trousers'),palettes.pants[2]);assert.equal(colors.get('Sneaker suede'),palettes.shoes[3]);
  assert.ok(a.root.scale.y>1);assert.ok(a.root.getObjectByName('Pelvis').scale.x<1);
  a.apply({...look,style:3,glasses:false,height:0,build:2});
  assert.equal(a.root.getObjectByName('Hair base').visible,true);assert.equal(a.root.getObjectByName('Short swept hairstyle').visible,false);
  assert.equal(a.root.getObjectByName('Cap').visible,false);assert.equal(a.root.getObjectByName('Glasses').visible,false);
  assert.ok(a.root.scale.y<1);assert.ok(a.root.getObjectByName('Pelvis').scale.x>1);
  for(const bad of [{pants:99},{shoes:-1},{glasses:'yes'},{height:3},{build:1.5},{style:5}]){a.apply({...defaultLook,...bad});assert.deepEqual(a.look,defaultLook);}
  a.destroy();
});
