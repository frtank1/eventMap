// Rain and snow around the camera: one draw each, positions animated in the vertex
// shader (no per-frame CPU loops or buffer uploads). Decorative, not a forecast.
import * as THREE from 'three';

export const WEATHER_LIMITS=Object.freeze({rain:3200,snow:4200,box:[140,70,140]});
const rand=s=>()=>{s=(s*16807)%2147483647;return (s-1)/2147483646;};

export function createWeather(scene){
  const [W,H,D]=WEATHER_LIMITS.box,r=rand(424242);
  const shared={uTime:{value:0},uCenter:{value:new THREE.Vector3()},uBox:{value:new THREE.Vector3(W,H,D)},uScale:{value:1}};
  // uScale grows the box with the camera distance so rain/snow keep the same on-screen
  // density from the street up to the whole-city view.
  const wrap=`vec3 p=position*uScale;p.y=mod(p.y-uTime*uFall*uScale,uBox.y);
    p.xz=mod(p.xz+uDrift*uScale*uTime-uCenter.xz+uBox.xz*.5,uBox.xz)-uBox.xz*.5+uCenter.xz;p.y+=uCenter.y-uBox.y*.45;`;
  // Rain: short streaks (two vertices each, the tail lifted by aTail).
  const rainGeo=new THREE.BufferGeometry(),rp=[],tail=[];
  for(let i=0;i<WEATHER_LIMITS.rain;i++){const x=r()*W,y=r()*H,z=r()*D;rp.push(x,y,z,x,y,z);tail.push(0,1);}
  rainGeo.setAttribute('position',new THREE.Float32BufferAttribute(rp,3));rainGeo.setAttribute('aTail',new THREE.Float32BufferAttribute(tail,1));
  const rainMat=new THREE.ShaderMaterial({transparent:true,depthWrite:false,fog:false,
    uniforms:{...shared,uFall:{value:38},uDrift:{value:new THREE.Vector2(2.5,1.2)},uColor:{value:new THREE.Color(0x8fa3b8)}},
    vertexShader:`attribute float aTail;uniform float uTime,uFall,uScale;uniform vec3 uCenter,uBox;uniform vec2 uDrift;varying float vA;
      void main(){${wrap}p.y+=aTail*4.0*uScale;p.xz-=aTail*uDrift*.12*uScale;vec4 mv=modelViewMatrix*vec4(p,1.);float d=-mv.z/uScale;vA=.8*smoothstep(14.,32.,d)*(1.-smoothstep(55.,100.,d));gl_Position=projectionMatrix*mv;}`,
    fragmentShader:`uniform vec3 uColor;varying float vA;void main(){gl_FragColor=vec4(uColor,vA);}`});
  const rain=new THREE.LineSegments(rainGeo,rainMat);rain.frustumCulled=false;rain.renderOrder=5;rain.visible=false;rain.name='Weather · rain';
  // Snow: round soft points drifting sideways.
  const snowGeo=new THREE.BufferGeometry(),sp=[];
  for(let i=0;i<WEATHER_LIMITS.snow;i++)sp.push(r()*W,r()*H,r()*D);
  snowGeo.setAttribute('position',new THREE.Float32BufferAttribute(sp,3));
  const snowMat=new THREE.ShaderMaterial({transparent:true,depthWrite:false,fog:false,
    uniforms:{...shared,uFall:{value:2.2},uDrift:{value:new THREE.Vector2(.8,.3)},uColor:{value:new THREE.Color(0xffffff)},uPixel:{value:1}},
    vertexShader:`uniform float uTime,uFall,uScale,uPixel;uniform vec3 uCenter,uBox;uniform vec2 uDrift;varying float vA;
      void main(){${wrap}p.x+=sin(uTime*.7+position.z)*.6*uScale;vec4 mv=modelViewMatrix*vec4(p,1.);float d=-mv.z;
        d/=uScale;vA=.95*smoothstep(3.,9.,d)*(1.-smoothstep(50.,100.,d));gl_PointSize=clamp(uPixel*380./max(d,1.),2.,18.);gl_Position=projectionMatrix*mv;}`,
    fragmentShader:`uniform vec3 uColor;varying float vA;void main(){float r=length(gl_PointCoord-.5);if(r>.5)discard;gl_FragColor=vec4(uColor,vA*(1.-r*1.6));}`});
  const snow=new THREE.Points(snowGeo,snowMat);snow.frustumCulled=false;snow.renderOrder=5;snow.visible=false;snow.name='Weather · snow';
  scene.add(rain,snow);
  let kind='clear';
  return {
    set(next){kind=['cloudy','rain','snow'].includes(next)?next:'clear';rain.visible=kind==='rain';snow.visible=kind==='snow';},
    update(time,camera,target,reduced=false,pixelRatio=1){
      const distance=camera.position.distanceTo(target),close=distance<20000;
      rain.visible=kind==='rain'&&close;snow.visible=kind==='snow'&&close;
      if(!close||!rain.visible&&!snow.visible)return;
      const scale=Math.min(40,Math.max(1,distance/160));shared.uScale.value=scale;shared.uBox.value.set(W*scale,H*scale,D*scale);
      shared.uTime.value=reduced?0:time;shared.uCenter.value.copy(camera.position);snowMat.uniforms.uPixel.value=pixelRatio;
    },
    get kind(){return kind;},
    stats:()=>({weather:kind,weatherDraws:(rain.visible?1:0)+(snow.visible?1:0)}),
    destroy(){rain.removeFromParent();snow.removeFromParent();rainGeo.dispose();snowGeo.dispose();rainMat.dispose();snowMat.dispose();},
  };
}
