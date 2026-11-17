// Cesium spike data around Baiterek, from what the project already has:
//  - building outlines + floors: NSDI import (game-city/data/nsdi/astana.json, owner-permitted);
//  - our authored landmark models (game-city/content) converted to glTF 2.0 (.glb);
//  - curated public events (web/events.json).
// Test only: nothing here is shipped by the app or the web build.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {landmarkColor} from '../game-city/runtime/art-direction.js';

const here=path.dirname(fileURLToPath(import.meta.url));
const gc=path.join(here,'../game-city');
const CENTER=[71.43056,51.12822],RADIUS=Number(process.env.RADIUS)||2400,FLOOR_M=3.3;
const K=111320*Math.cos(CENTER[1]*Math.PI/180);
const dist=(lng,lat)=>Math.hypot((lng-CENTER[0])*K,(lat-CENTER[1])*111320);
// Same placement fixes the 3D city applies (runtime/main.js).
const YAW={mega:-0.328,palace:-0.255,arena:0.0692,talan:-0.2478},OFFSET={arena:[16.1,10.9],talan:[10.0,-2.0]};
const KHAN_ETFE=0xd3d8dc;
const out=path.join(here,'data');await mkdir(path.join(out,'models'),{recursive:true});

// ---- landmarks → .glb ----
const manifest=JSON.parse(await readFile(path.join(gc,'content/manifest.json'),'utf8'));
const lin=c=>{c/=255;return c<=.04045?c/12.92:((c+.055)/1.055)**2.4;};
const rgb=h=>[lin((h>>16)&255),lin((h>>8)&255),lin(h&255)];
function glb(model,id){
  const chunks=[],views=[],accessors=[];let offset=0;
  const push=(typed,target)=>{const bytes=Buffer.from(typed.buffer,typed.byteOffset,typed.byteLength);const pad=(4-bytes.length%4)%4;
    views.push({buffer:0,byteOffset:offset,byteLength:bytes.length,target});chunks.push(bytes,Buffer.alloc(pad));offset+=bytes.length+pad;return views.length-1;};
  const meshes=[],nodes=[];
  for(const m of model.meshes){
    const pos=new Float32Array(m.positions),nor=new Float32Array(m.normals),idx=new Uint32Array(m.indices);
    const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
    for(let i=0;i<pos.length;i+=3)for(let a=0;a<3;a++){min[a]=Math.min(min[a],pos[i+a]);max[a]=Math.max(max[a],pos[i+a]);}
    accessors.push({bufferView:push(pos,34962),componentType:5126,count:pos.length/3,type:'VEC3',min,max});
    accessors.push({bufferView:push(nor,34962),componentType:5126,count:nor.length/3,type:'VEC3'});
    accessors.push({bufferView:push(idx,34963),componentType:5125,count:idx.length,type:'SCALAR'});
    const a=accessors.length;meshes.push({primitives:[{attributes:{POSITION:a-3,NORMAL:a-2},indices:a-1,material:m.material}]});
    nodes.push({mesh:meshes.length-1});
  }
  const materials=model.materials.map(x=>{
    const membrane=id==='khan'&&landmarkColor(x.color)===landmarkColor(0x8da89d);
    const color=membrane?KHAN_ETFE:landmarkColor(x.color,false);
    return {pbrMetallicRoughness:{baseColorFactor:[...rgb(color),1],metallicFactor:membrane?.22:(x.metalness||0),roughnessFactor:membrane?.3:(x.roughness??.8)},doubleSided:x.side===2};
  });
  const yaw=YAW[id]||0,o=OFFSET[id]||[0,0];
  nodes.push({children:nodes.map((_,i)=>i),rotation:[0,Math.sin(yaw/2),0,Math.cos(yaw/2)],translation:[o[0],0,-o[1]]});
  const json={asset:{version:'2.0',generator:'EventMap cesium-spike'},scene:0,scenes:[{nodes:[nodes.length-1]}],nodes,meshes,materials,accessors,bufferViews:views,buffers:[{byteLength:offset}]};
  let js=Buffer.from(JSON.stringify(json));js=Buffer.concat([js,Buffer.alloc((4-js.length%4)%4,0x20)]);
  const bin=Buffer.concat(chunks),total=12+8+js.length+8+bin.length,head=Buffer.alloc(12);
  head.writeUInt32LE(0x46546c67,0);head.writeUInt32LE(2,4);head.writeUInt32LE(total,8);
  const h=(len,type)=>{const b=Buffer.alloc(8);b.writeUInt32LE(len,0);b.writeUInt32LE(type,4);return b;};
  return Buffer.concat([head,h(js.length,0x4e4f534a),js,h(bin.length,0x004e4942),bin]);
}
const landmarks=[];
for(const e of manifest.models){
  if(dist(e.lng,e.lat)>RADIUS)continue;
  const model=JSON.parse(await readFile(path.join(gc,'content',e.file),'utf8'));
  await writeFile(path.join(out,'models',`${e.id}.glb`),glb(model,e.id));
  landmarks.push({id:e.id,title:e.title,lng:e.lng,lat:e.lat,height:e.height,radius:e.radius||60});
}

// ---- NSDI buildings (outlines under our landmarks are replaced by the models) ----
const nsdi=JSON.parse(await readFile(path.join(gc,'data/nsdi/astana.json'),'utf8'));
const buildings=[];let skipped=0;
for(const [id,levels,usage,ring] of nsdi.buildings){
  let x=0,y=0;for(const p of ring){x+=p[0];y+=p[1];}x/=ring.length;y/=ring.length;
  if(dist(x,y)>RADIUS)continue;
  if(landmarks.some(l=>Math.hypot((x-l.lng)*K,(y-l.lat)*111320)<l.radius*.85)){skipped++;continue;}
  const flat=[];for(const [lng,lat] of ring.slice(0,-1))flat.push(Math.round((lng-CENTER[0])*1e6),Math.round((lat-CENTER[1])*1e6));
  // Slivers (zero-area outlines) make Cesium return no geometry; skip anything under 2 m².
  let area=0;for(let i=0,n=flat.length/2;i<n;i++){const j=(i+1)%n;area+=flat[2*i]*K*flat[2*j+1]*111320-flat[2*j]*K*flat[2*i+1]*111320;}
  if(Math.abs(area)/2/1e12<2){skipped++;continue;}
  buildings.push([levels||1,usage??0,flat]);
}
await writeFile(path.join(out,'buildings.json'),JSON.stringify({source:nsdi.source.name,permission:nsdi.source.permission,retrieved:nsdi.retrieved,center:CENTER,radius:RADIUS,floorMeters:FLOOR_M,scale:1e6,buildings}));
const events=JSON.parse(await readFile(path.join(here,'../web/events.json'),'utf8'));
await writeFile(path.join(out,'events.json'),JSON.stringify(events));
await writeFile(path.join(out,'landmarks.json'),JSON.stringify(landmarks));
console.log(`buildings ${buildings.length} (under landmarks skipped ${skipped}), landmarks ${landmarks.map(l=>l.id).join(',')}, events ${events.length}`);
