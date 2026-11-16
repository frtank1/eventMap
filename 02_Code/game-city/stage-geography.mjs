// Stages geographic JSON only. No model export, mobile build, network or deployment.
import {createHash,randomUUID} from 'node:crypto';
import {link,mkdir,open,readFile,rename,unlink,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {GEO_LIMITS,GEO_VERSION,prepareGeoData,validateGeoManifest} from './runtime/geo-catalog-store.js';

const here=path.dirname(fileURLToPath(import.meta.url));
const kinds=['addresses','places'];
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const temporary=file=>`${file}.${process.pid}.${randomUUID()}.tmp`;

async function readSource(file,limit){
  const handle=await open(file,'r');
  try {
    const stat=await handle.stat();
    if(!stat.isFile()||stat.size<1||stat.size>limit)throw new Error(`Geography source size: ${file}`);
    // A bounded read also rejects a source that grows while staging is running.
    const buffer=Buffer.alloc(limit+1);let length=0;
    while(length<buffer.length){
      const {bytesRead}=await handle.read(buffer,length,buffer.length-length,null);
      if(!bytesRead)break;
      length+=bytesRead;
    }
    if(!length||length>limit)throw new Error(`Geography source size: ${file}`);
    return buffer.subarray(0,length);
  } finally {await handle.close();}
}

async function checkExisting(file,bytes){
  let existing;
  try {existing=await readFile(file);}
  catch(error){if(error.code==='ENOENT')return false;throw error;}
  if(!existing.equals(bytes))throw new Error(`Immutable geography asset mismatch: ${file}`);
  return true;
}

async function writeImmutable(file,bytes){
  if(await checkExisting(file,bytes))return;
  const temp=temporary(file);
  try {
    await writeFile(temp,bytes,{flag:'wx'});
    // A complete file becomes visible without ever overwriting an existing hash.
    try {await link(temp,file);}
    catch(error){if(error.code!=='EEXIST')throw error;await checkExisting(file,bytes);}
  } finally {await unlink(temp).catch(error=>{if(error.code!=='ENOENT')throw error;});}
}

export async function stageGeography({
  source=path.join(here,'source'),
  targets=[path.join(here,'content'),path.resolve(here,'../../04_Backend/server/src/main/resources/world')],
}={}){
  if(!targets.length)throw new Error('Geography staging requires a target');
  const files={},data={},descriptors={};
  for(const kind of kinds){
    files[kind]=await readSource(path.join(source,`astana-${kind}.json`),GEO_LIMITS[kind]);
    data[kind]=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(files[kind]));
    const sha256=hash(files[kind]);
    descriptors[kind]={file:`assets/${sha256}.json`,sha256,bytes:files[kind].length};
  }
  // Reuse the runtime's complete pair validation; never import/evaluate source data.
  prepareGeoData(data.addresses,data.places);
  const manifest=validateGeoManifest({schemaVersion:1,catalogVersion:GEO_VERSION,
    release:`geo-v${GEO_VERSION}-${hash(JSON.stringify(descriptors)).slice(0,56)}`,...descriptors});
  const roots=[...new Set(targets.map(target=>path.resolve(target)))];
  // Detect collisions in either tree before writing any new asset or pointer.
  for(const root of roots)for(const kind of kinds)
    await checkExisting(path.join(root,descriptors[kind].file),files[kind]);
  for(const root of roots){
    await mkdir(path.join(root,'assets'),{recursive:true});
    for(const kind of kinds)await writeImmutable(path.join(root,descriptors[kind].file),files[kind]);
  }
  const json=JSON.stringify(manifest)+'\n',pending=[];
  try {
    for(const root of roots){
      const file=path.join(root,'places-manifest.json'),temp=temporary(file);
      pending.push({file,temp});await writeFile(temp,json,{flag:'wx'});
    }
    // Each pointer replacement is atomic. Both asset trees are ready before either
    // pointer changes; cross-directory publication is not a filesystem transaction.
    for(const {file,temp} of pending)await rename(temp,file);
  } finally {
    for(const {temp} of pending)await unlink(temp).catch(error=>{if(error.code!=='ENOENT')throw error;});
  }
  return manifest;
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const manifest=await stageGeography();
  console.log(`Staged ${manifest.release}: addresses ${manifest.addresses.bytes} bytes, places ${manifest.places.bytes} bytes. Mobile and model assets unchanged. Deployment is separate.`);
}
