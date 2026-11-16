import * as THREE from 'three';

// Procedural modelling helpers; metres, +Z is forward. No downloaded meshes.
// Ring = [height, half width, half depth, forward offset, sideways offset].
// Shared side rings keep soft normals; separate end caps keep hems/cuffs crisp.
export function loft(rings,sides=16,squareness=2){
  const positions=[],indices=[];
  for(const [y,rx,rz,z=0,x=0]of rings)for(let i=0;i<sides;i++){
    const a=i/sides*Math.PI*2,c=Math.cos(a),s=Math.sin(a),power=2/squareness;
    positions.push(Math.sign(c)*Math.abs(c)**power*rx+x,y,Math.sign(s)*Math.abs(s)**power*rz+z);
  }
  for(let j=1;j<rings.length;j++)for(let i=0;i<sides;i++){
    const a=(j-1)*sides+i,b=(j-1)*sides+(i+1)%sides,c=j*sides+i,d=j*sides+(i+1)%sides;
    indices.push(a,c,b,b,c,d);
  }
  // Both ascending and descending profiles occur (torso vs limbs).
  const ascending=rings.at(-1)[0]>rings[0][0];
  for(const end of [0,rings.length-1]){
    const [y,,,z=0,x=0]=rings[end],center=positions.length/3;positions.push(x,y,z);
    const rim=positions.length/3;
    for(let i=0;i<sides;i++)positions.push(...positions.slice((end*sides+i)*3,(end*sides+i+1)*3));
    for(let i=0;i<sides;i++){
      const a=rim+i,b=rim+(i+1)%sides;
      if(end===0)indices.push(center,a,b);else indices.push(center,b,a);
    }
  }
  if(!ascending)for(let i=0;i<indices.length;i+=3)[indices[i+1],indices[i+2]]=[indices[i+2],indices[i+1]];
  const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.setIndex(indices);g.computeVertexNormals();return g;
}

// One connected swept crown with a shaped forehead/temple/nape hairline.
// This replaces separate spherical locks, not a texture or authored mesh asset.
export function sweptHair(sides=20,rows=7){
  const positions=[.018,.407,-.018],indices=[];
  for(let row=1;row<=rows;row++)for(let i=0;i<sides;i++){
    const a=i/sides*Math.PI*2,front=Math.max(0,Math.sin(a)),back=Math.max(0,-Math.sin(a));
    const theta=(row/rows)*Math.PI*(.70-.14*front+.16*back),s=Math.sin(theta),c=Math.cos(theta);
    positions.push(.151*Math.cos(a)*s+.018*c*c,.305+.102*c+.012*s*s*Math.max(0,Math.cos(a)),.132*Math.sin(a)*s-.018);
  }
  for(let i=0;i<sides;i++)indices.push(0,1+(i+1)%sides,1+i);
  for(let row=1;row<rows;row++)for(let i=0;i<sides;i++){
    const a=1+(row-1)*sides+i,b=1+(row-1)*sides+(i+1)%sides,c=a+sides,d=b+sides;
    indices.push(a,b,c,b,d,c);
  }
  const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.setIndex(indices);g.computeVertexNormals();return g;
}

export function softBox(w,h,d,r=.03){
  r=Math.min(r,w*.49,h*.49,d*.49);
  const bevel=r*.35,x=w/2-bevel,y=h/2-bevel,c=r-bevel,s=new THREE.Shape();
  s.moveTo(-x+c,-y);s.lineTo(x-c,-y);s.quadraticCurveTo(x,-y,x,-y+c);
  s.lineTo(x,y-c);s.quadraticCurveTo(x,y,x-c,y);s.lineTo(-x+c,y);
  s.quadraticCurveTo(-x,y,-x,y-c);s.lineTo(-x,-y+c);s.quadraticCurveTo(-x,-y,-x+c,-y);
  s.closePath();
  return new THREE.ExtrudeGeometry(s,{depth:d-2*bevel,bevelEnabled:true,bevelThickness:bevel,bevelSize:bevel,bevelSegments:2,steps:1,curveSegments:3}).translate(0,0,-d/2+bevel);
}

export function curve(points,radius=.008,segments=12,sides=5){
  return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p))),segments,radius,sides,false);
}
