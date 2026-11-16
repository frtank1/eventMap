import * as THREE from 'three';

// Bounded architectural ground illumination, not event/activity evidence.
// A shared radial texture replaces a PointLight for every landmark.
export function createLandmarkGround(entries,project){
  const pixels=new Uint8Array(32*32*4);
  for(let y=0;y<32;y++)for(let x=0;x<32;x++){
    const r=Math.hypot((x-15.5)/15.5,(y-15.5)/15.5),a=Math.max(0,1-r);
    pixels.set([255,255,255,Math.round(a*a*140)],(y*32+x)*4);
  }
  const texture=new THREE.DataTexture(pixels,32,32);texture.needsUpdate=true;
  const geometry=new THREE.PlaneGeometry(1,1).rotateX(-Math.PI/2);
  const material=new THREE.MeshBasicMaterial({map:texture,color:0xffd29b,transparent:true,opacity:.42,depthWrite:false,toneMapped:false,blending:THREE.AdditiveBlending});
  const group=new THREE.Group();group.name='Architectural ground light · not event status';
  const dummy=new THREE.Object3D(),mesh=new THREE.InstancedMesh(geometry,material,entries.length);group.add(mesh);
  for(const [i,e]of entries.entries()){
    const p=project(e.lng,e.lat);dummy.position.set(p.x,.74,p.z);const size=Math.min(360,Math.max(70,e.radius*2.5));dummy.scale.set(size,1,size);dummy.updateMatrix();mesh.setMatrixAt(i,dummy.matrix);
  }
  mesh.computeBoundingSphere();mesh.instanceMatrix.needsUpdate=true;group.visible=false;
  let disposed=false;
  return {group,setNight(night){group.visible=!!night;},destroy(){if(disposed)return;disposed=true;group.removeFromParent();mesh.dispose();geometry.dispose();material.dispose();texture.dispose();}};
}
