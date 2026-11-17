import * as THREE from 'three';

// Gradient sky dome and a handful of soft clouds. Pure decoration: it carries
// no weather, time-of-day or event data. One shader draw plus one instanced
// draw; no textures, no extra render pass.
const CLOUDS=18;
export function createSky(){
  const group=new THREE.Group();group.name='Decorative sky · not weather data';
  const uniforms={uTop:{value:new THREE.Color()},uHorizon:{value:new THREE.Color()},uSun:{value:new THREE.Vector3(0,1,0)},uGlow:{value:new THREE.Color()},uNight:{value:0}};
  const dome=new THREE.Mesh(new THREE.SphereGeometry(1,24,12),new THREE.ShaderMaterial({uniforms,side:THREE.BackSide,depthWrite:false,depthTest:false,fog:false,
    vertexShader:`varying vec3 vDir;void main(){vDir=normalize(position);gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}`,
    fragmentShader:`varying vec3 vDir;uniform vec3 uTop;uniform vec3 uHorizon;uniform vec3 uSun;uniform vec3 uGlow;uniform float uNight;
      float hash(vec2 p){vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
      void main(){
        vec3 d=normalize(vDir);float h=clamp(d.y,0.0,1.0);
        vec3 c=mix(uHorizon,uTop,pow(h,.36));
        c+=uGlow*pow(max(dot(d,normalize(uSun)),0.0),6.0)*.35;
        if(uNight>.5){vec2 g=floor(vec2(atan(d.z,d.x)*120.0,d.y*190.0));float s=step(.9965,hash(g))*smoothstep(.06,.3,d.y);c+=vec3(.9,.94,1.0)*s*(.45+.55*hash(g+7.0));}
        gl_FragColor=vec4(c,1.0);
        #include <colorspace_fragment>
      }`}));
  dome.frustumCulled=false;dome.renderOrder=-20;group.add(dome);
  const lobes=[[0,0,0,1],[1.05,-.12,.1,.78],[-1.1,-.16,-.1,.72],[.35,.34,.05,.7],[-.45,.25,.2,.6]].map(([x,y,z,r])=>new THREE.IcosahedronGeometry(r,1).scale(1.25,.62,1).translate(x*1.15,y,z));
  const puff=mergeLobes(lobes);
  const cloudMaterial=new THREE.MeshBasicMaterial({color:0xffffff,vertexColors:true,fog:false,transparent:true,opacity:.96,depthWrite:false});
  const clouds=new THREE.InstancedMesh(puff,cloudMaterial,CLOUDS);clouds.frustumCulled=false;clouds.renderOrder=-19;group.add(clouds);
  const sites=Array.from({length:CLOUDS},(_,i)=>{const a=i/CLOUDS*Math.PI*2+((i*7)%5)*.21;return {a,r:.74+((i*5)%7)*.03,y:.035+((i*3)%6)*.024,s:.022+((i*11)%5)*.008};});
  const dummy=new THREE.Object3D();
  function place(radius,time){
    sites.forEach((c,i)=>{const a=c.a+time*.0025;dummy.position.set(Math.cos(a)*c.r*radius,c.y*radius,Math.sin(a)*c.r*radius);dummy.scale.setScalar(c.s*radius);dummy.rotation.y=-a+Math.PI/2;dummy.updateMatrix();clouds.setMatrixAt(i,dummy.matrix);});
    clouds.instanceMatrix.needsUpdate=true;
  }
  let lastTime=-1e9,lastRadius=0;
  return {group,
    setMood({top,horizon,cloud,glow,night,sun}){
      uniforms.uTop.value.setHex(top);uniforms.uHorizon.value.setHex(horizon);uniforms.uGlow.value.setHex(glow);uniforms.uNight.value=night?1:0;
      if(sun)uniforms.uSun.value.copy(sun).normalize();cloudMaterial.color.setHex(cloud);cloudMaterial.opacity=night?.5:.96;
    },
    // The dome follows the camera and stays inside its far plane at every zoom.
    update(camera,time=0){
      const radius=camera.far*.92;group.position.copy(camera.position);dome.scale.setScalar(radius);
      if(Math.abs(time-lastTime)>.5||radius!==lastRadius){place(radius,time);lastTime=time;lastRadius=radius;}
    },
    stats:()=>({skyDraws:2,clouds:CLOUDS}),
    destroy(){group.removeFromParent();dome.geometry.dispose();dome.material.dispose();puff.dispose();cloudMaterial.dispose();clouds.dispose();}};
}
function mergeLobes(parts){
  const total=parts.reduce((n,g)=>n+g.attributes.position.count,0),positions=new Float32Array(total*3);let offset=0;
  for(const g of parts){const flat=g.index?g.toNonIndexed():g;positions.set(flat.attributes.position.array,offset);offset+=flat.attributes.position.array.length;if(flat!==g)flat.dispose();g.dispose();}
  // Baked top-to-bottom tone so unlit clouds still read as volumes.
  const colors=new Float32Array(total*3);let min=Infinity,max=-Infinity;
  for(let i=1;i<positions.length;i+=3){min=Math.min(min,positions[i]);max=Math.max(max,positions[i]);}
  for(let i=0;i<positions.length;i+=3){const t=(positions[i+1]-min)/(max-min),k=t*t*(3-2*t);colors[i]=.74+.26*k;colors[i+1]=.82+.18*k;colors[i+2]=.93+.07*k;}
  const merged=new THREE.BufferGeometry();merged.setAttribute('position',new THREE.BufferAttribute(positions,3));merged.setAttribute('color',new THREE.BufferAttribute(colors,3));return merged;
}

// Tiny gradient scene for image-based lighting: glass and gold pick up the sky
// instead of rendering as dull dark metal. Built once per day/night change.
export function createEnvironmentScene({top,horizon,ground,glow,sun}){
  const scene=new THREE.Scene();
  const material=new THREE.ShaderMaterial({side:THREE.BackSide,depthWrite:false,
    uniforms:{uTop:{value:new THREE.Color(top)},uHorizon:{value:new THREE.Color(horizon)},uGround:{value:new THREE.Color(ground)},uGlow:{value:new THREE.Color(glow)},uSun:{value:sun.clone().normalize()}},
    vertexShader:`varying vec3 vDir;void main(){vDir=normalize(position);gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}`,
    fragmentShader:`varying vec3 vDir;uniform vec3 uTop;uniform vec3 uHorizon;uniform vec3 uGround;uniform vec3 uGlow;uniform vec3 uSun;
      void main(){vec3 d=normalize(vDir);
        vec3 c=d.y>0.0?mix(uHorizon,uTop,pow(d.y,.45)):mix(uHorizon,uGround,smoothstep(0.0,.25,-d.y));
        c+=uGlow*pow(max(dot(d,uSun),0.0),24.0)*2.2;gl_FragColor=vec4(c,1.0);}`});
  const mesh=new THREE.Mesh(new THREE.SphereGeometry(10,24,12),material);scene.add(mesh);
  return {scene,dispose(){mesh.geometry.dispose();material.dispose();}};
}
