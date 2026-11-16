import * as THREE from 'three';
import {mergeGeometries} from '../source/vendor/BufferGeometryUtils.js';
import {eventStyle,eventSummary,canAnimateEvent,EVENT_STYLES} from './event-style.js';

// Original miniature signs: they symbolise a category, never literal facilities,
// attendees, a live stream or a guaranteed booking. Shared GPU geometry/materials.
export function createEventModels(){
  const geometries=new Set(),materials=new Set(),templates=new Map();
  function template(key){
    const root=new THREE.Group(),base=new THREE.Group(),motion=new THREE.Group(),lights=new THREE.Group();root.add(base,motion,lights);motion.name='Event sign motion';lights.name='Event sign lights';
    const palette=new Map();
    function mat(color){if(!palette.has(color)){const m=new THREE.MeshStandardMaterial({color,roughness:.85,metalness:0});palette.set(color,m);materials.add(m);}return palette.get(color);}
    const accent=EVENT_STYLES[key]?.color||'#b9c3d3',cream='#f4e3c5',ink='#324e59',wood='#b28c64';
    const add=(g,color,p=[0,0,0],parent=base)=>{geometries.add(g);const m=new THREE.Mesh(g,mat(color));m.position.set(...p);m.receiveShadow=true;parent.add(m);return m;};
    const box=(w,h,d,color,x=0,y=0,z=0,parent=base)=>add(new THREE.BoxGeometry(w,h,d),color,[x,y,z],parent);
    const ball=(r,color,x,y,z,parent=base)=>add(new THREE.SphereGeometry(r,12,8),color,[x,y,z],parent);
    function bar(a,b,r,color,parent=base){const p=new THREE.Vector3(...a),q=new THREE.Vector3(...b),v=q.clone().sub(p);const g=new THREE.CylinderGeometry(r,r,v.length(),6).applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),v.normalize()));return add(g,color,p.add(q).multiplyScalar(.5).toArray(),parent);}
    // A clearly synthetic round base separates the sign from real architecture.
    add(new THREE.CylinderGeometry(2.55,2.7,.34,24),cream,[0,.2,0]);
    add(new THREE.CylinderGeometry(2.4,2.52,.18,24),accent,[0,.44,0]);
    if(key==='sport'){
      box(3.9,.1,2.65,'#497e67',0,.57);box(.055,.035,2.5,cream,0,.64);
      for(const x of [-1.8,1.8]){box(.07,.035,2.5,cream,x,.64);for(const z of [-.63,.63])bar([x,.63,z],[x,1.36,z],.055,cream);bar([x,1.36,-.63],[x,1.36,.63],.055,cream);}
      for(const z of [-1.23,1.23])box(3.6,.035,.055,cream,0,.64,z);
      add(new THREE.TorusGeometry(.49,.026,5,24).rotateX(Math.PI/2),cream,[0,.65,0]);
      ball(.5,cream,0,1.14,0,motion);
      for(const normal of [[0,0,1],[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,-1]]){
        const n=new THREE.Vector3(...normal),g=new THREE.CircleGeometry(.16,5).applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,0,1),n));
        add(g,ink,n.multiplyScalar(.49).add(new THREE.Vector3(0,1.14,0)).toArray(),motion);
      }
    }else if(key==='music'){
      box(3.7,.38,2.7,ink,0,.72);box(2.8,1.55,.15,accent,0,1.7,-.9);box(3.9,.15,2.8,cream,0,2.92);
      for(const x of [-1.75,1.75])for(const z of [-1.15,1.15])bar([x,.7,z],[x,2.86,z],.075,ink);
      for(const x of [-1.2,1.2]){box(.5,1.0,.6,ink,x,1.15,.85);ball(.13,cream,x,1.45,1.17);}
      for(let i=0;i<5;i++)box(.23,.25+(i%3)*.22,.06,cream,-.65+i*.33,1.55,-.8,motion);
      const beamGeo=new THREE.ConeGeometry(.9,2.5,10,4,true).rotateX(Math.PI).translate(0,1.25,0),colors=[];
      const p=beamGeo.attributes.position;for(let i=0;i<p.count;i++)colors.push(1,1,1,Math.max(0,1-p.getY(i)/2.5)**1.5);
      beamGeo.setAttribute('color',new THREE.Float32BufferAttribute(colors,4));geometries.add(beamGeo);
      const beamMat=new THREE.MeshBasicMaterial({color:accent,transparent:true,vertexColors:true,opacity:.2,depthWrite:false,side:THREE.DoubleSide,forceSinglePass:true,toneMapped:false,blending:THREE.AdditiveBlending});materials.add(beamMat);
      for(const x of [-1.3,1.3]){const beam=new THREE.Mesh(beamGeo,beamMat);beam.position.set(x,2.95,-.65);lights.add(beam);}
    }else if(key==='market'){
      box(3.4,1.05,1.7,wood,0,1.06);box(3.55,.16,1.85,cream,0,1.64);
      for(const x of [-1.55,1.55])for(const z of [-.7,.7])bar([x,.65,z],[x,2.8,z],.075,ink);
      for(let i=0;i<6;i++){const awning=box(.64,.15,2.35,i%2?cream:accent,-1.6+i*.64,2.83,0);awning.rotation.x=.12;box(.64,.31,.1,i%2?cream:accent,-1.6+i*.64,2.56,1.13);}
      for(let i=0;i<3;i++){ball(.24,i===1?'#e4a593':'#80a96e',-.8+i*.8,1.93,0);box(.5,.15,.55,cream,-.8+i*.8,1.75,0);}
      motion.add(new THREE.Group());
    }else if(key==='discovery'){
      for(let i=0;i<3;i++){const a=i/3*Math.PI*2;bar([Math.cos(a)*.95,.6,Math.sin(a)*.95],[0,1.65,0],.065,ink);}
      bar([0,1.5,0],[0,2.2,0],.12,ink);
      bar([-1,1.9,0],[1.05,2.75,0],.32,accent,motion);bar([1.01,2.73,0],[1.28,2.84,0],.38,cream,motion);
      bar([1.27,2.84,0],[1.3,2.85,0],.28,ink,motion);
      ball(.16,cream,-1.15,1.83,0,motion);
    }else if(key==='route'){
      for(const x of [-1.65,1.65])box(.25,2.4,.25,cream,x,1.75);
      box(3.55,.6,.3,accent,0,2.96);
      for(let i=0;i<7;i++)box(.47,.23,.04,i%2?ink:cream,-1.42+i*.47,2.96,.18);
      const arrow=new THREE.Shape();arrow.moveTo(0,-.85);arrow.lineTo(.6,0);arrow.lineTo(.24,0);arrow.lineTo(.24,1);arrow.lineTo(-.24,1);arrow.lineTo(-.24,0);arrow.lineTo(-.6,0);arrow.closePath();
      add(new THREE.ShapeGeometry(arrow).rotateX(-Math.PI/2),cream,[0,.56,.3],motion);
    }else if(key==='quest'){
      bar([-.7,.55,0],[-.7,3.25,0],.08,cream);ball(.13,cream,-.7,3.35,0);
      const flag=new THREE.Shape();flag.moveTo(-.64,3.15);flag.lineTo(1.5,2.9);flag.lineTo(.9,2.3);flag.lineTo(1.5,1.9);flag.lineTo(-.64,2.12);flag.closePath();
      add(new THREE.ExtrudeGeometry(flag,{depth:.12,bevelEnabled:false}),accent,[0,0,0],motion);
      add(new THREE.OctahedronGeometry(.4,0),cream,[0,2.58,.18],motion).scale.set(1,1,.3);
    }else if(key==='community'){
      for(const [x,z,color]of [[-.72,.15,accent],[.72,-.15,'#729b91']]){
        add(new THREE.CylinderGeometry(.28,.45,1.05,10),color,[x,1.16,z]);ball(.31,cream,x,1.99,z);
      }
      add(new THREE.TorusGeometry(.5,.07,5,20),cream,[0,2.85,0]);
    }else{
      for(const x of [-.7,.7])for(const z of [-.7,.7])box(.95,.8,.95,accent,x,.98,z);
    }
    for(const parent of [base,motion]){
      const buckets=new Map();for(const o of [...parent.children])if(o.isMesh){const list=buckets.get(o.material)||[];list.push(o);buckets.set(o.material,list);}
      for(const [material,items]of buckets){const inputs=items.map(o=>{o.updateMatrix();let g=o.geometry.clone().applyMatrix4(o.matrix);if(g.index){const indexed=g;g=g.toNonIndexed();indexed.dispose();}g.deleteAttribute('uv');return g;});
        const merged=mergeGeometries(inputs);inputs.forEach(g=>g.dispose());if(!merged)continue;geometries.add(merged);items.forEach(o=>o.removeFromParent());parent.add(new THREE.Mesh(merged,material));}
    }
    return root;
  }
  for(const key of [...Object.keys(EVENT_STYLES),'cluster'])templates.set(key,template(key));
  const used=new Set();for(const root of templates.values())root.traverse(o=>{if(o.geometry)used.add(o.geometry);});for(const g of geometries)if(!used.has(g)){g.dispose();geometries.delete(g);}
  const ringGeo=new THREE.RingGeometry(2.7,2.82,40).rotateX(-Math.PI/2);geometries.add(ringGeo);
  let disposed=false;
  function create(group,y=0){
    const e=group.events[0],key=group.events.length>1?'cluster':eventStyle(e).key;
    const root=templates.get(key).clone(true),motion=root.getObjectByName('Event sign motion'),lights=root.getObjectByName('Event sign lights');root.userData.eventGroupKey=group.key;
    root.position.set(group.x,y+.07,group.z);
    const ringMat=new THREE.MeshBasicMaterial({color:EVENT_STYLES[key]?.color||'#b9c3d3',transparent:true,opacity:.38,depthWrite:false,side:THREE.DoubleSide});
    const pulse=new THREE.Mesh(ringGeo,ringMat);pulse.position.y=.03;root.add(pulse);
    let animated=false,phase=[...group.key].reduce((n,c)=>n+c.charCodeAt(0),0)%19;
    function update(t,{camera,distance,reduced,blocked,visible,index,quality,night=false}){
      root.visible=visible;const s=THREE.MathUtils.clamp(distance*.0065,1,4);root.scale.setScalar(s);
      animated=group.events.length===1&&canAnimateEvent(e,{distance,reduced,blocked,visible,index,quality});
      const state=eventSummary(e).state;
      pulse.visible=group.events.length===1&&['live','signal','gathering'].includes(state);
      pulse.scale.setScalar(animated?1+Math.sin(t*1.25+phase)*.1:1);ringMat.opacity=night?.48:.35;
      motion.position.y=animated&&key==='sport'?Math.abs(Math.sin(t*1.9+phase))*.28:0;
      motion.rotation.y=animated&&['discovery','quest'].includes(key)?Math.sin(t*.45+phase)*.13:0;
      motion.scale.y=animated&&key==='music'?1+Math.sin(t*1.8+phase)*.07:1;
      lights.visible=animated&&key==='music';lights.children.forEach((beam,i)=>{beam.rotation.z=Math.sin(t*.5+i*2)*.16;});
    }
    return {root,update,get animated(){return animated;},labelPoint(){return root.position.clone().add(new THREE.Vector3(0,4*root.scale.y,0));},destroy(){root.removeFromParent();ringMat.dispose();}};
  }
  return {create,destroy(){if(disposed)return;disposed=true;geometries.forEach(g=>g.dispose());materials.forEach(m=>m.dispose());}};
}
