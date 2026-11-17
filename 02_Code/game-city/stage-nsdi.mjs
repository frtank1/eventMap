import {mkdir,copyFile,access} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
export async function stageNativeNsdi(root=path.resolve(here,'../androidApp/src/debug/assets/eventmap-nsdi')){
  const source=path.join(here,'test/nsdi-local/quarter.json');
  try{await access(source);}catch{return false;}
  await mkdir(root,{recursive:true});await copyFile(source,path.join(root,'quarter.json'));return true;
}
