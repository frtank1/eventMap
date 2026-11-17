import * as THREE from 'three';

// Visual profile, NOT surveyed engineering dimensions. Only source-tagged
// bridges call this helper. Layer preserves ordering of grade-separated roads.
export const BRIDGE_LIMITS=Object.freeze({parts:20000,points:4096});
export function bridgeProfile(bridge){
  const points=bridge.points,stations=[0];for(let i=1;i<points.length;i++)stations.push(stations.at(-1)+Math.hypot(points[i][0]-points[i-1][0],points[i][1]-points[i-1][1]));
  const length=stations.at(-1),deck=5+bridge.layer*3+.6,ramp=Math.min(36,length*.2);
  const height=s=>.6+(deck-.6)*Math.min(1,bridge.ramps[0]?s/ramp:1,bridge.ramps[1]?(length-s)/ramp:1);
  const sample=s=>{let i=1;while(i<stations.length-1&&stations[i]<s)i++;const a=points[i-1],b=points[i],t=(s-stations[i-1])/(stations[i]-stations[i-1]||1);return new THREE.Vector3(a[0]+(b[0]-a[0])*t,height(s),a[1]+(b[1]-a[1])*t);};
  return {length,deck,stations,ramp,height,sample};
}
export function* bridgeGeometry(bridge){
  const p=bridgeProfile(bridge);if(p.length<1)return;
  const cuts=[...new Set([...p.stations,bridge.ramps[0]?p.ramp:0,bridge.ramps[1]?p.length-p.ramp:p.length])].sort((a,b)=>a-b);
  const width=bridge.width,up=new THREE.Vector3(0,0,1),direction=new THREE.Vector3(),rotation=new THREE.Quaternion();
  const beam=(a,b,w,h,drop,side=0)=>{
    direction.subVectors(b,a);const length=direction.length(),flat=Math.hypot(direction.x,direction.z);if(length<.01)return null;
    const center=a.clone().add(b).multiplyScalar(.5);center.y+=drop;center.x+=-direction.z/(flat||1)*side;center.z+=direction.x/(flat||1)*side;
    rotation.setFromUnitVectors(up,direction.normalize());
    return new THREE.BoxGeometry(w,h,length+.025).applyQuaternion(rotation).translate(center.x,center.y,center.z);
  };
  for(let i=1;i<cuts.length;i++){
    const a=p.sample(cuts[i-1]),b=p.sample(cuts[i]);
    yield {geometry:beam(a,b,width+1,1.15,-.58),color:'cream',detail:false};
    yield {geometry:beam(a,b,width,.07,.05),color:'road',detail:false};
    for(const side of [-1,1])yield {geometry:beam(a,b,.45,1.0,.57,side*(width/2+.22)),color:'trim',detail:true};
  }
  // Piers are spaced along a joined path, not restarted per source tile.
  for(let s=28;s<p.length-20;s+=44){
    const at=p.sample(s),h=at.y-1.2;if(h<3)continue;
    yield {geometry:new THREE.BoxGeometry(Math.min(4,width*.35),h,2.4).translate(at.x,h/2,at.z),color:'sand',detail:false};
  }
}
