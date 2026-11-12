import {build} from 'esbuild';
import {cp,mkdir,copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const here=path.dirname(fileURLToPath(import.meta.url));
const out=path.join(here,'preview-dist');await mkdir(out,{recursive:true});
await build({entryPoints:[path.join(here,'runtime/main.js')],bundle:true,format:'iife',target:['safari16','chrome100'],minify:true,
  outfile:path.join(out,'mobile.js'),alias:{three:path.join(here,'source/vendor/three.module.js')},legalComments:'eof'});
for(const name of ['index.html','mobile.css'])await copyFile(path.join(here,'runtime',name),path.join(out,name));
for(const name of ['THREE-LICENSE.txt','VECTOR-TILE-LICENSE.txt','PBF-LICENSE.txt','POINT-GEOMETRY-LICENSE.txt','PARSING-NOTICES.txt'])await copyFile(path.join(here,'source/vendor',name),path.join(out,name));
const native=path.resolve(here,'../common/feature/map/ui/src/commonMain/composeResources/files/game-city');
await mkdir(native,{recursive:true});await cp(out,native,{recursive:true});
const server=path.resolve(here,'../../04_Backend/server/src/main/resources/world');
await mkdir(server,{recursive:true});await copyFile(path.join(here,'content/manifest.json'),path.join(server,'manifest.json'));
await cp(path.join(here,'content/assets'),path.join(server,'assets'),{recursive:true});
console.log('Built mobile engine, bundled fallback, public backend content. Test release is not active.');
