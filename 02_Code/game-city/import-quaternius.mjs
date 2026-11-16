// Offline, data-only import of the free Standard kit. Never loads vendor scripts.
import {createHash} from 'node:crypto';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {pipeline} from 'node:stream/promises';
import {Readable} from 'node:stream';
import {fileURLToPath, pathToFileURL} from 'node:url';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {Matrix4, Matrix3, Quaternion, Vector3} from './source/vendor/three.module.js';

export const PACK_URL='https://quaternius.com/packs/downtowncitymegakit.html';
export const DOWNLOAD_PAGE='https://quaternius.itch.io/downtown-city-megakit';
export const ARCHIVE_NAME='Downtown City MegaKit[Standard].zip';
export const ARCHIVE_SHA256='5b1a945576d54cdbb4ccc9c3d52711e6d530da74c74c586407ecc28b165335da';
export const SELECTION=Object.freeze([
  {kind:'bollard',file:'Prop_Bollard',roles:['metal']},
  {kind:'planter',file:'Prop_Planter_Single',roles:['stone','soil']},
  {kind:'planter-low',file:'Sidewalk_Planter',roles:['stone','soil']},
]);
const root=path.dirname(fileURLToPath(import.meta.url));
export const sha256=bytes=>createHash('sha256').update(bytes).digest('hex');
const round=n=>Math.round(n*1e6)/1e6||0; // Canonicalise -0 for exact JSON reproducibility.
const requireThat=(condition,message)=>{if(!condition)throw Error(message);};

/** Deliberately narrow, bounded glTF 2 reader. Textures/scripts are never loaded. */
export function readAccessor(gltf,buffers,index,expectedType){
  const a=gltf.accessors?.[index],view=gltf.bufferViews?.[a?.bufferView];
  const types={5121:[1,'getUint8'],5123:[2,'getUint16'],5125:[4,'getUint32'],5126:[4,'getFloat32']};
  requireThat(a&&view&&!a.sparse&&!a.normalized&&a.type===expectedType,'Unsupported accessor');
  const components={SCALAR:1,VEC3:3}[a.type],type=types[a.componentType];
  requireThat(type&&Number.isInteger(a.count)&&a.count>0&&a.count<=30000,'Accessor exceeds budget');
  const [bytes,method]=type,buffer=buffers[view.buffer],stride=view.byteStride??bytes*components;
  const viewOffset=view.byteOffset??0,accessorOffset=a.byteOffset??0;
  const start=viewOffset+accessorOffset,end=start+(a.count-1)*stride+bytes*components;
  requireThat(buffer&&Number.isInteger(viewOffset)&&viewOffset>=0&&Number.isInteger(accessorOffset)&&accessorOffset>=0&&
    Number.isInteger(view.byteLength)&&view.byteLength>0&&viewOffset+view.byteLength<=buffer.byteLength&&
    Number.isInteger(stride)&&stride>=bytes*components&&Number.isInteger(start)&&start>=0&&
    end<=buffer.byteLength&&end<=(view.byteOffset??0)+view.byteLength,'Accessor outside buffer');
  const data=new DataView(buffer.buffer,buffer.byteOffset,buffer.byteLength),out=[];
  for(let i=0;i<a.count;i++)for(let j=0;j<components;j++){
    const value=data[method](start+i*stride+j*bytes,true);
    requireThat(Number.isFinite(value),'Non-finite accessor');out.push(value);
  }
  return out;
}

export function convertGltf(gltf,buffers,spec){
  requireThat(gltf.asset?.version==='2.0'&&!gltf.extensionsRequired?.length&&!gltf.animations?.length&&!gltf.skins?.length,'Only static uncompressed glTF 2 is supported');
  const roots=gltf.scenes?.[gltf.scene??0]?.nodes;
  requireThat(Array.isArray(roots)&&gltf.nodes.length<=64,'Invalid static scene');
  const parts=[],active=new Set();
  const v=new Vector3(),normal=new Vector3();
  function visit(index,parent){
    requireThat(!active.has(index)&&active.size<32,'Cyclic or deep node hierarchy');
    const node=gltf.nodes[index];requireThat(node&&node.skin===undefined&&!node.weights,'Unsupported node');active.add(index);
    const local=node.matrix?new Matrix4().fromArray(node.matrix):new Matrix4().compose(
      new Vector3().fromArray(node.translation??[0,0,0]),new Quaternion().fromArray(node.rotation??[0,0,0,1]),new Vector3().fromArray(node.scale??[1,1,1]));
    const world=parent.clone().multiply(local),normalMatrix=new Matrix3().getNormalMatrix(world);
    requireThat(world.elements.every(Number.isFinite)&&Math.abs(world.determinant())>1e-9,'Invalid node transform');
    if(node.mesh!==undefined){
      const mesh=gltf.meshes[node.mesh];requireThat(mesh&&!mesh.weights,'Missing static mesh');
      for(const p of mesh.primitives){
        requireThat((p.mode??4)===4&&!p.targets&&!p.extensions,'Only static triangles are supported');
        const positions=readAccessor(gltf,buffers,p.attributes.POSITION,'VEC3');
        const normals=readAccessor(gltf,buffers,p.attributes.NORMAL,'VEC3');
        requireThat(positions.length===normals.length,'Mismatched normals');
        const indices=p.indices===undefined?Array.from({length:positions.length/3},(_,i)=>i):readAccessor(gltf,buffers,p.indices,'SCALAR');
        requireThat(indices.length%3===0&&indices.every(i=>Number.isInteger(i)&&i>=0&&i<positions.length/3),'Invalid triangle indices');
        const role=spec.roles[p.material];requireThat(role,'Unmapped source material');
        for(let i=0;i<positions.length;i+=3){
          v.fromArray(positions,i).applyMatrix4(world);normal.fromArray(normals,i).applyNormalMatrix(normalMatrix);
          requireThat(v.toArray().every(n=>Number.isFinite(n)&&Math.abs(n)<100)&&normal.lengthSq()>.9,'Invalid transformed vertex');
          v.toArray(positions,i);normal.toArray(normals,i);
        }
        if(world.determinant()<0)for(let i=0;i<indices.length;i+=3)[indices[i+1],indices[i+2]]=[indices[i+2],indices[i+1]];
        parts.push({role,sourceMaterial:gltf.materials[p.material].name,positions,normals:normals.map(round),indices});
      }
    }
    for(const child of node.children??[])visit(child,world);
    active.delete(index);
  }
  for(const index of roots)visit(index,new Matrix4());
  requireThat(parts.length>0&&parts.length<=4,'Primitive budget exceeded');
  const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
  for(const p of parts)for(let i=0;i<p.positions.length;i++){const axis=i%3;min[axis]=Math.min(min[axis],p.positions[i]);max[axis]=Math.max(max[axis],p.positions[i]);}
  const origin=[(min[0]+max[0])/2,min[1],(min[2]+max[2])/2];
  for(const p of parts)p.positions=p.positions.map((n,i)=>round(n-origin[i%3]));
  const bounds={min:min.map((n,i)=>round(n-origin[i])),max:max.map((n,i)=>round(n-origin[i]))};
  const triangles=parts.reduce((n,p)=>n+p.indices.length/3,0),vertices=parts.reduce((n,p)=>n+p.positions.length/3,0);
  requireThat(triangles<=1000&&vertices<=2000,'Model exceeds geometry budget');
  return {kind:spec.kind,sourceModel:spec.file,units:'metres',bounds,triangles,vertices,parts};
}

function archiveEntry(archive,entry){
  // Never extracts archive paths to disk. Only reads explicit known model/license entries.
  requireThat(!entry.includes('..')&&!entry.startsWith('/')&&!/[\[\]*?]/.test(entry),'Unsafe archive entry');
  return execFileSync('unzip',['-p',archive,entry],{maxBuffer:1024*1024,timeout:10000});
}

export async function importArchive(archive,outputRoot=root){
  const bytes=await readFile(archive);
  requireThat(sha256(bytes)===ARCHIVE_SHA256,'Archive hash mismatch; audit a new vendor revision before importing');
  const art=path.join(outputRoot,'art/quaternius'),originals=path.join(art,'originals');
  const license=archiveEntry(archive,'License_Standard.txt');
  requireThat(license.toString().includes('CC0 1.0 Universal'),'Missing expected CC0 dedication');
  const sourceFiles=[],models=[],files=[];
  for(const spec of SELECTION){
    const base='Exports/glTF (Godot)/',gltfBytes=archiveEntry(archive,base+spec.file+'.gltf'),gltf=JSON.parse(gltfBytes);
    requireThat(gltf.buffers?.length===1&&gltf.buffers[0].uri===spec.file+'.bin','Unexpected buffer URI');
    const bin=archiveEntry(archive,base+spec.file+'.bin');
    requireThat(bin.length===gltf.buffers[0].byteLength,'Buffer length mismatch');
    models.push(convertGltf(gltf,[bin],spec));
    for(const [extension,content]of [['gltf',gltfBytes],['bin',bin]]){
      const filename=spec.file+'.'+extension;
      sourceFiles.push({archivePath:base+filename,localPath:'art/quaternius/originals/'+filename,bytes:content.length,sha256:sha256(content)});
      files.push([path.join(originals,filename),content]);
    }
  }
  const data={schema:'eventmap-quaternius-props-v1',provenance:{author:'Quaternius',pack:'Downtown City MegaKit',edition:'Standard FREE',
    packUrl:PACK_URL,downloadPage:DOWNLOAD_PAGE,uploadId:'17615373',archiveName:ARCHIVE_NAME,archiveBytes:bytes.length,archiveSha256:ARCHIVE_SHA256,
    license:'CC0-1.0',licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/',licenseFile:'art/quaternius/License_Standard.txt',licenseSha256:sha256(license),sourceFiles},
    adaptation:{geometry:'Original glTF indexed meshes and normals. Node transforms baked, X/Z centred and base placed at Y=0, rounded to 6 decimals. No decimation or procedural replacement.',
      materials:'Deterministic flat-colour override: bollard MI_Trim_MetalConcrete -> metal; planter MI_Trim_MetalConcrete -> stone; MI_Dirt -> soil. Runtime uses CITY_PALETTE. Original base colour/normal/ORM textures, UVs and wear channels intentionally omitted; not a texture-faithful reproduction.',
      excluded:'No buildings, paid Source assets, scripts, shaders, textures or full archive shipped. Bench/bin/streetlamp/bike rack are absent from this free archive.'},
    budgets:{models:models.length,batches:models.reduce((n,m)=>n+m.parts.length,0),triangles:models.reduce((n,m)=>n+m.triangles,0),vertices:models.reduce((n,m)=>n+m.vertices,0)},models};
  const json=JSON.stringify(data)+'\n';requireThat(Buffer.byteLength(json)<200000,'Static data exceeds 200 KB budget');
  // All validation precedes writes. These destinations are exclusively owned by this importer.
  await mkdir(originals,{recursive:true});await mkdir(path.join(outputRoot,'source'),{recursive:true});
  for(const [filename,content]of files)await writeFile(filename,content);
  await writeFile(path.join(art,'License_Standard.txt'),license);
  await writeFile(path.join(outputRoot,'source/quaternius-props.json'),json);
  return data;
}

export async function downloadStandard(destination){
  // Same anonymous, zero-payment action as "No thanks, just take me to downloads".
  // No login, email, payment endpoint, ToS acceptance, or executable vendor code.
  const cookies=new Map();
  async function request(url,options={}){
    const sameHost=new URL(url).hostname==='quaternius.itch.io';
    const response=await fetch(url,{...options,headers:{...(sameHost?{cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join('; ')}:{}),...options.headers},signal:AbortSignal.timeout(60000)});
    for(const cookie of response.headers.getSetCookie()){
      const pair=cookie.split(';')[0],i=pair.indexOf('=');cookies.set(pair.slice(0,i),pair.slice(i+1));
    }
    if(!response.ok)throw Error(`Download request failed: HTTP ${response.status}`);
    return response;
  }
  function csrf(html){const token=html.match(/name="csrf_token" value="([^"]+)"/);if(!token)throw Error('Missing anonymous CSRF token');return token[1];}
  function post(url,token){return request(url,{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},body:new URLSearchParams({csrf_token:token})});}
  const purchase=await (await request(DOWNLOAD_PAGE+'/purchase')).text();
  if(!purchase.includes('direct_download_btn'))throw Error('Free download route unavailable; refusing checkout');
  const generated=await (await post(DOWNLOAD_PAGE+'/download_url',csrf(purchase))).json();
  if(!generated.url?.startsWith(DOWNLOAD_PAGE+'/download/'))throw Error('Unexpected download page');
  const download=await (await request(generated.url)).text();
  const uploads=[...download.matchAll(/<div class="upload">([\s\S]*?)(?=<div class="upload">|<\/section>|<div class="trouble_link")/g)];
  const standard=uploads.find(([,html])=>html.includes(`title="${ARCHIVE_NAME}"`));
  const id=standard?.[1].match(/data-upload_id="(\d+)"/)?.[1];
  if(!id)throw Error('Free Standard archive not found; refusing any other version');
  const file=await (await post(`${DOWNLOAD_PAGE}/file/${id}?source=game_download`,csrf(download))).json();
  if(!file.url||file.external)throw Error('No direct free archive download returned');
  const url=new URL(file.url);
  const mirror='itchio-mirror.cb031a832f44726753d6267436f3b414.r2.cloudflarestorage.com';
  if(url.protocol!=='https:'||(!/(^|\.)(itch\.zone|hwcdn\.net)$/.test(url.hostname)&&url.hostname!==mirror))throw Error(`Unexpected archive CDN: ${url.hostname}`);
  const response=await request(url);
  const size=Number(response.headers.get('content-length'));
  if(size>300*1024*1024)throw Error('Archive exceeds expected free Standard budget');
  await pipeline(Readable.fromWeb(response.body),createWriteStream(destination,{flags:'wx'}));
  console.log(`Downloaded free Standard archive (${id}) to ${destination}`);
}

if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
  if(process.argv[2]==='--download'&&process.argv[3])await downloadStandard(path.resolve(process.argv[3]));
  else if(process.argv[2]==='--archive'&&process.argv[3]){
    const data=await importArchive(path.resolve(process.argv[3]));console.log(JSON.stringify(data.budgets));
  }else throw Error('Usage: node import-quaternius.mjs --download /tmp/standard.zip | --archive /tmp/standard.zip');
}
