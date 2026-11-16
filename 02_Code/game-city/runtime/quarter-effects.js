import * as THREE from 'three';
import {effectState,effectBudget} from './quarter-state.js';

// Fixed buffers + time uniforms. No per-frame allocations / per-particle CPU loop.
// Artistic previews only: none of these switches asserts a real event/weather report.
export function createQuarterEffects(){
  const group=new THREE.Group();group.name='Baiterek · three bounded artistic effects';
  let state=effectState(),elapsed=0,started=0,night=false,lastBudget=effectBudget();
  const owned=[];
  const circle=new THREE.Mesh(new THREE.RingGeometry(34,35.8,80).rotateX(-Math.PI/2),new THREE.MeshBasicMaterial({color:0xe4b982,transparent:true,opacity:.55,depthWrite:false,toneMapped:false}));
  circle.position.y=.57;group.add(circle);
  const beamGroup=new THREE.Group();group.add(beamGroup);
  const beamGeo=new THREE.CylinderGeometry(6,.45,100,12,8,true).translate(0,50,0);
  const pos=beamGeo.attributes.position,colors=[];
  for(let i=0;i<pos.count;i++){const a=1-pos.getY(i)/100;colors.push(1,1,1,Math.max(0,a)*.8);}
  beamGeo.setAttribute('color',new THREE.Float32BufferAttribute(colors,4));
  const beamMat=new THREE.MeshBasicMaterial({color:0xffcd89,vertexColors:true,transparent:true,opacity:.13,depthWrite:false,side:THREE.DoubleSide,forceSinglePass:true,blending:THREE.AdditiveBlending,toneMapped:false});
  for(let i=0;i<4;i++){const a=i*Math.PI/2+Math.PI/4,m=new THREE.Mesh(beamGeo,beamMat);m.position.set(Math.cos(a)*29,1,Math.sin(a)*29);beamGroup.add(m);}
  owned.push(beamGeo,beamMat,circle.geometry,circle.material);
  function particles(kind,count){
    const geo=new THREE.BufferGeometry(),positions=new Float32Array(count*3),seeds=new Float32Array(count),velocities=new Float32Array(count*3),colors=new Float32Array(count*3);
    let rnd=7241;const random=()=>{rnd=(Math.imul(rnd,1664525)+1013904223)>>>0;return rnd/4294967296;};
    const palette=[new THREE.Color(0xffc678),new THREE.Color(0xcbb2fa),new THREE.Color(0x9ce1e4)];
    for(let i=0;i<count;i++){
      positions.set([(random()-.5)*180,random()*110,(random()-.5)*180],i*3);seeds[i]=random();
      const theta=random()*Math.PI*2,y=random()*2-1,r=Math.sqrt(1-y*y),speed=15+random()*25;
      velocities.set([Math.cos(theta)*r*speed,y*speed,Math.sin(theta)*r*speed],i*3);
      const c=palette[Math.floor(i/100)%3];colors.set([c.r,c.g,c.b],i*3);
    }
    geo.setAttribute('position',new THREE.BufferAttribute(positions,3));geo.setAttribute('aSeed',new THREE.BufferAttribute(seeds,1));
    geo.setAttribute('aVelocity',new THREE.BufferAttribute(velocities,3));geo.setAttribute('color',new THREE.BufferAttribute(colors,3));
    const uniforms={uTime:{value:0},uDpr:{value:1},uOrigin:{value:new THREE.Vector3()},uOpacity:{value:1}};
    const vertex=kind==='snow'?`
      vec3 p=position; p.y=mod(position.y-uTime*(3.0+aSeed*4.0),110.0);
      p.x+=sin(uTime*.23+aSeed*25.0)*6.0; p+=uOrigin;
      vec4 mv=modelViewMatrix*vec4(p,1.0);gl_Position=projectionMatrix*mv;
      gl_PointSize=clamp((.9+aSeed)*uDpr*300.0/max(1.0,-mv.z),2.0*uDpr,5.0*uDpr);
      vAlpha=.85;vColor=vec3(.91,.96,1.0);`
      :`
      float burst=floor(aSeed*3.0);float age=uTime-burst*.85;
      float t=max(age,0.0);vec3 origin=vec3((burst-1.0)*32.0,135.0+burst*10.0,-15.0);
      vec3 p=origin+aVelocity*t+vec3(0.0,-5.0*t*t,0.0);
      vec4 mv=modelViewMatrix*vec4(p,1.0);gl_Position=projectionMatrix*mv;
      gl_PointSize=clamp(4.0*uDpr*500.0/max(1.0,-mv.z),2.0*uDpr,7.0*uDpr);
      vAlpha=step(0.0,age)*(1.0-smoothstep(.5,3.4,t));vColor=color;`;
    const mat=new THREE.ShaderMaterial({uniforms,transparent:true,depthWrite:false,vertexColors:true,
      blending:kind==='snow'?THREE.NormalBlending:THREE.AdditiveBlending,
      vertexShader:`attribute float aSeed;attribute vec3 aVelocity;uniform float uTime;uniform float uDpr;uniform vec3 uOrigin;varying float vAlpha;varying vec3 vColor;void main(){${vertex}}`,
      fragmentShader:'uniform float uOpacity;varying float vAlpha;varying vec3 vColor;void main(){float d=length(gl_PointCoord-.5)*2.0;float a=(1.0-smoothstep(.2,1.0,d))*vAlpha*uOpacity;if(a<.01)discard;gl_FragColor=vec4(vColor,a);}',
    });
    const points=new THREE.Points(geo,mat);points.frustumCulled=false;points.visible=false;group.add(points);owned.push(geo,mat);
    return {points,uniforms,geo};
  }
  const snow=particles('snow',700),fireworks=particles('fireworks',300);
  function set(name,value){if(!Object.hasOwn(state,name))return;state[name]=value===true;if(name==='fireworks'&&value)started=elapsed;}
  function setNight(value){night=!!value;}
  function update(dt,{camera,target,distance,quality='balanced',visible=true,reduced=false,blocked=false,dpr=1}={}){
    const budget=effectBudget({quality,distance,visible,reduced,blocked});lastBudget=budget;
    group.visible=visible;
    if(budget.active)elapsed+=Math.max(0,Math.min(.1,dt));
    if(state.fireworks&&elapsed-started>6)state.fireworks=false;
    circle.visible=beamGroup.visible=state.light&&visible&&distance<1800;
    circle.material.opacity=(night?.64:.32)+(reduced?0:Math.sin(elapsed*1.7)*.08);
    beamMat.opacity=night?.17:.06;
    for(let i=0;i<beamGroup.children.length;i++){
      const b=beamGroup.children[i];b.rotation.x=reduced?0:Math.sin(elapsed*.25+i)*.11;b.rotation.z=reduced?0:Math.cos(elapsed*.22+i)*.11;
    }
    snow.points.visible=state.snow&&budget.snow>0;fireworks.points.visible=state.fireworks&&budget.sparks>0;
    snow.geo.setDrawRange(0,budget.snow);fireworks.geo.setDrawRange(0,budget.sparks);
    snow.uniforms.uTime.value=elapsed;snow.uniforms.uDpr.value=dpr;
    // Near camera for visible flakes, bounded to this quarter, never a world-sized storm.
    snow.uniforms.uOrigin.value.copy(target).lerp(camera.position,.75);snow.uniforms.uOrigin.value.y=Math.max(0,snow.uniforms.uOrigin.value.y-40);
    fireworks.uniforms.uTime.value=elapsed-started;fireworks.uniforms.uDpr.value=dpr;
  }
  update(0,{camera:{position:new THREE.Vector3()},target:new THREE.Vector3(),distance:500});
  return {group,set,setNight,update,get state(){return {...state};},stats:()=>({effects:{...state},particles:(snow.points.visible?lastBudget.snow:0)+(fireworks.points.visible?lastBudget.sparks:0)}),
    destroy(){group.removeFromParent();owned.forEach(o=>o.dispose());}};
}
