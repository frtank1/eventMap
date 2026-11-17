import * as THREE from 'three';
import {eventStyle,eventSummary} from './event-style.js';

// Two batched, static map-sign meshes. No lights, shadows, timers, per-event
// animation or literal crowd counts. Only mounted for the explicit festival QA.
export function createEventBeacons(){
  const group=new THREE.Group();group.name='festival-demo-beacons';
  const ringGeometry=new THREE.RingGeometry(.72,1,24).rotateX(-Math.PI/2);
  const beamGeometry=new THREE.CylinderGeometry(.08,.35,1,8,1,true).translate(0,.5,0);
  const colors=[];for(let i=0;i<beamGeometry.attributes.position.count;i++){
    const y=beamGeometry.attributes.position.getY(i);colors.push(1,1,1,(1-y)*.48);
  }
  beamGeometry.setAttribute('color',new THREE.Float32BufferAttribute(colors,4));
  const ringMaterial=new THREE.MeshBasicMaterial({transparent:true,opacity:.6,depthWrite:false,side:THREE.DoubleSide,toneMapped:false});
  const beamMaterial=new THREE.MeshBasicMaterial({transparent:true,opacity:.45,depthWrite:false,vertexColors:true,side:THREE.DoubleSide,forceSinglePass:true,toneMapped:false,blending:THREE.AdditiveBlending});
  const rings=new THREE.InstancedMesh(ringGeometry,ringMaterial,150),beams=new THREE.InstancedMesh(beamGeometry,beamMaterial,150);
  rings.count=beams.count=0;rings.frustumCulled=beams.frustumCulled=false;group.add(rings,beams);
  const dummy=new THREE.Object3D(),color=new THREE.Color();let records=[],lastScale=-1,lastNight=null,disposed=false;
  function position(mesh,index,p,radius,height){dummy.position.set(p.x,p.y,p.z);dummy.scale.set(radius,height,radius);dummy.updateMatrix();mesh.setMatrixAt(index,dummy.matrix);}
  return {group,
    setEvents(groups,height){
      records=groups.slice(0,150).map(g=>({x:g.x,z:g.z,y:height(g.x,g.z)+.18,live:g.events.some(e=>eventSummary(e).animate),color:eventStyle(g.events[0]).color}));lastScale=-1;
    },
    refreshHeights(height){for(const p of records){const next=height(p.x,p.z)+.18;if(Math.abs(next-p.y)>.05){p.y=next;lastScale=-1;}}},
    update(distance,night=false,visible=true){
      group.visible=visible&&records.length>0;if(!group.visible||disposed)return;
      // Quantized scale prevents 150 matrix writes on every rendered frame.
      const scale=Math.max(3,Math.min(140,Math.round(distance/90)*2));
      if(scale!==lastScale){let n=0;rings.count=records.length;
        records.forEach((p,i)=>{color.set(p.color);position(rings,i,p,scale,1);rings.setColorAt(i,color);
          if(p.live){position(beams,n,p,scale*1.1,scale*3.5);beams.setColorAt(n++,color);}});
        beams.count=n;for(const mesh of [rings,beams]){mesh.instanceMatrix.needsUpdate=true;if(mesh.instanceColor)mesh.instanceColor.needsUpdate=true;}lastScale=scale;
      }
      if(lastNight!==night){ringMaterial.opacity=night?.68:.48;beamMaterial.opacity=night?.56:.2;lastNight=night;}
    },
    stats:()=>({beaconGroups:records.length,beaconDrawCalls:group.visible?Number(rings.count>0)+Number(beams.count>0):0}),
    destroy(){if(disposed)return;disposed=true;group.removeFromParent();rings.dispose();beams.dispose();ringGeometry.dispose();beamGeometry.dispose();ringMaterial.dispose();beamMaterial.dispose();records=[];},
  };
}
