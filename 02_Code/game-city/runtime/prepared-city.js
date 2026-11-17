import * as THREE from 'three';
import {getLandmarkFootprints} from './landmark-footprints.js';

// Baked GPU buffers, not ObjectLoader JSON: typed arrays are views over one
// checked allocation. Rebuilding polygons is a compatibility fallback only.
export const PREPARED_VERSION=1, MAX_PREPARED_BYTES=128*1024*1024;
const types={Float32Array,Int8Array,Uint8Array,Uint16Array,Uint32Array};
const attrs={position:[3,'Float32Array'],normal:[3,'Int8Array'],color:[3,'Uint8Array'],nightColor:[3,'Uint8Array'],snowSurface:[1,'Uint8Array'],nightLighting:[1,'Uint8Array'],minorBuilding:[1,'Uint8Array'],coverageAnchor:[2,'Float32Array']};
export function cityGeometryKey(entries=[],catalog){
  return JSON.stringify([...entries].sort((a,b)=>a.id.localeCompare(b.id)).map(e=>[e.id,e.lng,e.lat,e.radius,getLandmarkFootprints(e.id,catalog).map(f=>f.rings)]));
}
export function encodePreparedCity(meta,meshes){
  const blocks=[];let size=0;
  const add=a=>{size=Math.ceil(size/4)*4;const d={type:a.constructor.name,offset:size,length:a.length};blocks.push({offset:size,a});size+=a.byteLength;return d;};
  const items=meshes.map(m=>({name:m.name,role:m.userData.bakedRole||'building',minor:m.userData.bakedMinor||null,
    attributes:Object.fromEntries(Object.entries(m.geometry.attributes).map(([k,a])=>[k,{...add(a.array),itemSize:a.itemSize,normalized:a.normalized}])),index:add(m.geometry.index.array),sphere:[...m.geometry.boundingSphere.center.toArray(),m.geometry.boundingSphere.radius]}));
  const header=new TextEncoder().encode(JSON.stringify({...meta,version:PREPARED_VERSION,meshes:items})),start=Math.ceil((header.length+8)/4)*4;
  if(start+size>MAX_PREPARED_BYTES)throw Error('Prepared city budget');
  const out=new Uint8Array(start+size),view=new DataView(out.buffer);view.setUint32(0,0x454d4331);view.setUint32(4,header.length,true);out.set(header,8);
  for(const {offset,a}of blocks)out.set(new Uint8Array(a.buffer,a.byteOffset,a.byteLength),start+offset);
  return out;
}
export function decodePreparedCity(buffer){
  if(!(buffer instanceof ArrayBuffer)||buffer.byteLength<8||buffer.byteLength>MAX_PREPARED_BYTES)throw Error('Prepared city size');
  const view=new DataView(buffer),len=view.getUint32(4,true),start=Math.ceil((len+8)/4)*4;
  if(view.getUint32(0)!==0x454d4331||len>20*1024*1024||start>buffer.byteLength)throw Error('Prepared city header');
  const meta=JSON.parse(new TextDecoder().decode(new Uint8Array(buffer,8,len)));
  if(meta.version!==PREPARED_VERSION||typeof meta.key!=='string'||!Array.isArray(meta.meshes)||meta.meshes.length>500||!Array.isArray(meta.places)||meta.places.length>100000)throw Error('Prepared city version');
  const array=d=>{const Type=types[d?.type];if(!Type||!Number.isSafeInteger(d.offset)||d.offset<0||d.offset%4||!Number.isSafeInteger(d.length)||d.length<0||start+d.offset+d.length*Type.BYTES_PER_ELEMENT>buffer.byteLength)throw Error('Prepared city buffer');return new Type(buffer,start+d.offset,d.length);};
  for(const m of meta.meshes){
    if(!['building','surface','bridge'].includes(m.role)||!Array.isArray(m.sphere)||m.sphere.length!==4||!m.sphere.every(Number.isFinite)||m.sphere[3]<0)throw Error('Prepared city mesh');
    let count;
    for(const [k,[n,type]]of Object.entries(attrs)){const d=m.attributes?.[k];if(!d||d.type!==type||d.itemSize!==n)throw Error('Prepared city attribute');d.array=array(d);if(d.array.length%n)throw Error('Prepared city stride');const c=d.array.length/n;if(count===undefined)count=c;else if(count!==c)throw Error('Prepared city count');}
    if(!['Uint16Array','Uint32Array'].includes(m.index?.type))throw Error('Prepared city indices');m.index.array=array(m.index);
    if(m.index.array.length%3||m.index.array.some(i=>i>=count))throw Error('Prepared city index range');
    if(m.attributes.position.array.some(x=>!Number.isFinite(x)||Math.abs(x)>100000)||m.attributes.coverageAnchor.array.some(x=>!Number.isFinite(x)||Math.abs(x)>100000))throw Error('Prepared city position');
    if(m.minor&&(!Array.isArray(m.minor)||m.minor.length!==2||!m.minor.every(Number.isFinite)))throw Error('Prepared city district');
  }
  let vertices=0;
  for(const [top,rings]of meta.places){if(!Number.isFinite(top)||top<0||top>310||!Array.isArray(rings)||!rings.length)throw Error('Prepared city place');for(const r of rings){if(!Array.isArray(r)||r.length<3||r.length>256)throw Error('Prepared city ring');for(const p of r)if(++vertices>1800000||!Array.isArray(p)||p.length!==2||!p.every(x=>Number.isFinite(x)&&Math.abs(x)<90000))throw Error('Prepared city coordinate');}}
  return meta;
}
export function preparedMesh(item,material){
  const g=new THREE.BufferGeometry();for(const [name,d]of Object.entries(item.attributes))if(attrs[name])g.setAttribute(name,new THREE.BufferAttribute(d.array,d.itemSize,d.normalized));
  g.setIndex(new THREE.BufferAttribute(item.index.array,1));g.boundingSphere=new THREE.Sphere(new THREE.Vector3(...item.sphere.slice(0,3)),item.sphere[3]);
  const mesh=new THREE.Mesh(g,material);mesh.name=item.name;return mesh;
}
let bundled;
export function loadPreparedCity(){
  return bundled??=(async()=>{
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),30000);
    try{
    // Do not use .gz: Android's asset packager transparently renames/unpacks it.
    const response=await fetch(new URL('city-geometry.emc',location.href),{credentials:'omit',signal:controller.signal});
    if(!response.ok||!response.body)throw Error('Prepared city unavailable');
    const reader=response.body.pipeThrough(new DecompressionStream('gzip')).getReader(),chunks=[];let size=0;
    try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>MAX_PREPARED_BYTES)throw Error('Prepared city budget');chunks.push(value);}}catch(e){await reader.cancel().catch(()=>{});throw e;}
    const out=new Uint8Array(size);let at=0;for(const c of chunks){out.set(c,at);at+=c.length;}
    return decodePreparedCity(out.buffer);
    }finally{clearTimeout(timer);}
  })().catch(e=>{bundled=null;throw e;});
}
