// Close-up animated pedestrians (Quaternius CC0 modular people). Decorative only: they are not
// users, not located people and carry no data. Far away the instanced crowd stays.
import * as THREE from 'three';
import {loadPeopleKit,createPerson,OUTFITS} from './people-kit.js';
import {palettes,defaultLook} from './social-state.js';

export const KIT_CROWD=Object.freeze({low:5,balanced:9,high:14});
export const KIT_CROWD_NEAR=170; // camera distance (m) below which kit people replace the instanced crowd

export function crowdRandom(seed){let s=(seed>>>0)||1;return ()=>{s^=s<<13;s>>>=0;s^=s>>>17;s^=s<<5;s>>>=0;return s/4294967296;};}
export function randomLook(r){
  const pick=n=>Math.floor(r()*n),body=r()<.5?0:1,n=OUTFITS[body?'women':'men'].length,own=c=>r()<.55?-1:pick(c);
  // Mostly one outfit head to toe, sometimes mixed; colours mostly the outfit's own.
  const base=pick(n),part=()=>r()<.7?base:pick(n);
  return {...defaultLook,body,skin:[0,1,1,2,2,3,4,5][pick(8)],hair:pick(palettes.hair.length),coat:own(palettes.coat.length),
    pants:own(palettes.pants.length),shoes:own(palettes.shoes.length),head:part(),top:base,bottom:part(),feet:part(),
    height:pick(3),build:pick(3),bag:r()<.15};
}
// Back and forth along one path segment; returns local x, z and heading.
export function walkerPose(seg,speed,phase,time){
  const cycle=2*seg.length/speed,t=((time+phase)%cycle+cycle)%cycle,out=t<cycle/2,d=out?t*speed:(cycle-t)*speed;
  return {x:seg.a[0]+seg.ux*d,z:seg.a[1]+seg.uz*d,yaw:Math.atan2(seg.ux,seg.uz)+(out?0:Math.PI)};
}

export function createKitCrowd(paths,{count=KIT_CROWD.high,seed=4242,height=()=>0}={}){
  const group=new THREE.Group();group.name='Animated pedestrians · Quaternius CC0 · not real users';group.visible=false;
  const r=crowdRandom(seed),segments=[];
  for(const [a,b]of Array.isArray(paths)?paths:[]){const dx=b[0]-a[0],dz=b[1]-a[1],length=Math.hypot(dx,dz);if(length>8)segments.push({a,length,ux:dx/length,uz:dz/length});}
  let people=[],ready=false,destroyed=false;
  if(segments.length&&count>0&&typeof XMLHttpRequest!=='undefined')loadPeopleKit().then(kit=>{if(destroyed)return;
    people=Array.from({length:count},()=>{const seg=segments[Math.floor(r()*segments.length)],c=createPerson(kit,randomLook(r));group.add(c.root);
      return {c,seg,speed:1.05+r()*.35,phase:r()*200};});ready=true;})
    .catch(e=>console.warn('Animated pedestrians unavailable:',e.message));
  function update(time,reduced=false,quality='balanced'){
    if(!ready)return;const shown=KIT_CROWD[quality]??KIT_CROWD.balanced;
    people.forEach((p,i)=>{p.c.root.visible=i<shown;if(i>=shown)return;
      const {x,z,yaw}=walkerPose(p.seg,p.speed,p.phase,reduced?0:time);p.c.root.position.set(x,height(x,z),z);p.c.root.rotation.y=yaw;p.c.update(time,!reduced,reduced);});
  }
  function destroy(){destroyed=true;for(const p of people)p.c.destroy();people=[];group.removeFromParent();}
  return {group,update,destroy,get ready(){return ready;},get count(){return people.length;}};
}
