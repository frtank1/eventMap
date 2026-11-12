// Stages an asset-only backend release. Does NOT rebuild or modify mobile resources.
import {readFile,writeFile,mkdir,rename} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {validateManifest,validateModel,hash} from './runtime/content-store.js';
const root=path.dirname(fileURLToPath(import.meta.url));
const input=path.resolve(root,process.argv[2]||'content/manifest.json');
const manifest=JSON.parse(await readFile(input,'utf8'));
const release=process.argv.find(x=>x.startsWith('--release='))?.slice(10);
if(release)manifest.release=release;
validateManifest(manifest);
const files=[];
for(const e of manifest.models) {
  const bytes=await readFile(path.join(root,'content',e.file));
  if(bytes.length!==e.bytes || await hash(bytes)!==e.sha256)throw Error(`Invalid asset: ${e.id}`);
  validateModel(JSON.parse(bytes),e.id);files.push({e,bytes});
}
const target=path.resolve(root,'../../04_Backend/server/src/main/resources/world');
await mkdir(path.join(target,'assets'),{recursive:true});
for(const {e,bytes} of files) {
  const name=path.join(target,e.file);
  try { await writeFile(name,bytes,{flag:'wx'}); }
  catch(error) {if(error.code!=='EEXIST')throw error;if(await hash(await readFile(name))!==e.sha256)throw Error(`Immutable asset mismatch: ${e.id}`);}
}
// New pointer is replaced only after every referenced immutable asset is present.
const temporary=path.join(target,`manifest-${process.pid}.tmp`);
await writeFile(temporary,JSON.stringify(manifest)+'\n');await rename(temporary,path.join(target,'manifest.json'));
console.log(`Staged ${manifest.release}: ${manifest.models.length} models. Mobile engine unchanged. Backend deployment is a separate step.`);
