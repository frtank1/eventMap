import * as THREE from 'three';

// Decorative vehicles on the bundled quarter's own road centrelines. They are
// street dressing like the pedestrians: not live traffic, not GPS positions of
// users and never a routing source. One instanced draw, no lights or shadows.
export const TRAFFIC_LIMITS=Object.freeze({low:12,balanced:28,minRoute:120,maxRadius:760});
const PAINT=[0xfaf6ea,0xf2bd3c,0x38b0c0,0xe0604a,0x5b8fd6,0xfaf6ea,0xf08a3c,0x7cc36a];
function carGeometry(){
  const parts=[
    {size:[2.05,.78,4.5],at:[0,.78,0],tone:1},      // body
    {size:[1.82,.66,2.3],at:[0,1.48,-.25],tone:.34}, // glazed cabin
    {size:[1.9,.1,2.1],at:[0,1.85,-.25],tone:.92},   // roof
    {size:[2.16,.42,.5],at:[0,.34,1.42],tone:.16},{size:[2.16,.42,.5],at:[0,.34,-1.42],tone:.16}, // wheel pairs
  ];
  const positions=[],normals=[],colors=[];
  for(const part of parts){
    const g=new THREE.BoxGeometry(...part.size).translate(...part.at).toNonIndexed();
    positions.push(...g.attributes.position.array);normals.push(...g.attributes.normal.array);
    for(let i=0;i<g.attributes.position.count;i++)colors.push(part.tone,part.tone,part.tone);g.dispose();
  }
  const geometry=new THREE.BufferGeometry();
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));
  geometry.setAttribute('color',new THREE.Float32BufferAttribute(colors,3));
  geometry.computeBoundingSphere();return geometry;
}
// Routes: [{points:[[x,z],...],width}] in quarter-local metres.
export function planTraffic(routes,{count=TRAFFIC_LIMITS.balanced,seed=4242}={}){
  let state=seed>>>0;const random=()=>{state=(state+0x6d2b79f5)>>>0;let v=Math.imul(state^(state>>>15),state|1);v^=v+Math.imul(v^(v>>>7),v|61);return ((v^(v>>>14))>>>0)/4294967296;};
  const usable=[];
  for(const route of routes){
    const points=route.points.filter((p,i,a)=>i===0||Math.hypot(p[0]-a[i-1][0],p[1]-a[i-1][1])>.5);if(points.length<2)continue;
    const lengths=[0];for(let i=1;i<points.length;i++)lengths.push(lengths[i-1]+Math.hypot(points[i][0]-points[i-1][0],points[i][1]-points[i-1][1]));
    const length=lengths[lengths.length-1];
    if(length<TRAFFIC_LIMITS.minRoute||!points.every(p=>Math.hypot(p[0],p[1])<TRAFFIC_LIMITS.maxRadius))continue;
    usable.push({points,lengths,length,width:route.width});
  }
  usable.sort((a,b)=>b.length-a.length);
  const cars=[],limit=Math.min(count,TRAFFIC_LIMITS.balanced);
  for(let i=0;usable.length&&cars.length<limit;i++){
    const route=usable[i%usable.length],direction=i%2?1:-1;
    cars.push({route,direction,offset:random()*route.length,speed:7+random()*5,lane:Math.min(3.2,route.width*.24),paint:PAINT[Math.floor(random()*PAINT.length)]});
  }
  return cars;
}
// Position and heading at time t. Cars run the route and reappear at its start.
export function carPose(car,time,out={x:0,z:0,yaw:0}){
  const {route}=car,span=route.length;let s=(car.offset+time*car.speed)%span;if(s<0)s+=span;if(car.direction<0)s=span-s;
  let low=0,high=route.lengths.length-1;while(high-low>1){const mid=(low+high)>>1;if(route.lengths[mid]<=s)low=mid;else high=mid;}
  const a=route.points[low],b=route.points[high],len=route.lengths[high]-route.lengths[low]||1,t=(s-route.lengths[low])/len;
  const dx=(b[0]-a[0])/len,dz=(b[1]-a[1])/len,side=car.direction*car.lane;
  out.x=a[0]+(b[0]-a[0])*t-dz*side;out.z=a[1]+(b[1]-a[1])*t+dx*side;out.yaw=Math.atan2(dx*car.direction,dz*car.direction);return out;
}
export function createTraffic(routes,{height=.62,seed}={}){
  const group=new THREE.Group();group.name='Decorative traffic · not real vehicles';
  const cars=planTraffic(routes,{seed});
  const geometry=carGeometry(),material=new THREE.MeshStandardMaterial({color:0xffffff,roughness:.55,metalness:.1,vertexColors:true});
  const mesh=new THREE.InstancedMesh(geometry,material,Math.max(1,cars.length));mesh.count=cars.length;mesh.frustumCulled=false;group.add(mesh);
  const tone=new THREE.Color();cars.forEach((car,i)=>mesh.setColorAt(i,tone.setHex(car.paint)));if(mesh.instanceColor)mesh.instanceColor.needsUpdate=true;
  const dummy=new THREE.Object3D(),pose={x:0,z:0,yaw:0};let shown=cars.length,placed=false;
  function update(time,reduced=false,quality='balanced'){
    shown=Math.min(cars.length,TRAFFIC_LIMITS[quality]||TRAFFIC_LIMITS.balanced);mesh.count=shown;
    if(reduced&&placed)return;placed=true;
    for(let i=0;i<shown;i++){
      carPose(cars[i],reduced?0:time,pose);dummy.position.set(pose.x,height,pose.z);dummy.rotation.y=pose.yaw;dummy.scale.setScalar(1.85);dummy.updateMatrix();mesh.setMatrixAt(i,dummy.matrix);
    }
    mesh.instanceMatrix.needsUpdate=true;
  }
  update(0);
  return {group,update,
    setNight(night){material.emissive.setHex(night?0x31456b:0);material.emissiveIntensity=night?.55:0;},
    stats:()=>({trafficCars:shown,trafficDraws:1}),
    destroy(){group.removeFromParent();geometry.dispose();material.dispose();mesh.dispose();}};
}
