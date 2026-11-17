import {build} from 'esbuild';
import {cp,mkdir,copyFile,rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {stageGeography} from './stage-geography.mjs';
import {stageNativeFestival} from './stage-festival.mjs';
import {stageNativeNsdi} from './stage-nsdi.mjs';
import {bakeCity} from './bake-city.mjs';
const here=path.dirname(fileURLToPath(import.meta.url));
const out=path.join(here,'preview-dist');await mkdir(out,{recursive:true});
await bakeCity(out);
await build({entryPoints:[path.join(here,'runtime/main.js')],bundle:true,format:'iife',target:['safari16','chrome100'],minify:true,
  outfile:path.join(out,'mobile.js'),alias:{three:path.join(here,'source/vendor/three.module.js')},legalComments:'eof'});
for(const name of ['index.html','mobile.css'])await copyFile(path.join(here,'runtime',name),path.join(out,name));
// Public OSM data stays outside executable JS: geography is lazy, the compact
// city silhouette loads asynchronously at startup without tile network requests.
for(const kind of ['addresses','places','overview'])await copyFile(path.join(here,`source/astana-${kind}.json`),path.join(out,`${kind}.json`));
// Skinned people (Quaternius CC0) — built by build-characters.mjs, committed in characters/.
await cp(path.join(here,'characters'),path.join(out,'characters'),{recursive:true,filter:p=>!p.endsWith('.md')});
for(const name of ['THREE-LICENSE.txt','VECTOR-TILE-LICENSE.txt','PBF-LICENSE.txt','POINT-GEOMETRY-LICENSE.txt','PARSING-NOTICES.txt'])await copyFile(path.join(here,'source/vendor',name),path.join(out,name));
// Design trials must not silently replace the installed/native runtime or server pack.
if(process.argv.includes('--preview-only')){console.log('Built browser preview only. Native and backend assets unchanged.');process.exit(0);}
if(!process.argv.includes('--native-only'))await stageGeography();
await stageNativeFestival();
await stageNativeNsdi();
const native=path.resolve(here,'../common/feature/map/ui/src/commonMain/composeResources/files/game-city');
await rm(path.join(native,'city-geometry.bin.gz'),{force:true});
await mkdir(native,{recursive:true});await cp(out,native,{recursive:true});
if(process.argv.includes('--native-only')){console.log('Built native engine and debug-only trials. Backend assets unchanged.');process.exit(0);}
const server=path.resolve(here,'../../04_Backend/server/src/main/resources/world');
await mkdir(server,{recursive:true});await copyFile(path.join(here,'content/manifest.json'),path.join(server,'manifest.json'));
await cp(path.join(here,'content/assets'),path.join(server,'assets'),{recursive:true});
console.log('Built mobile engine, bundled fallback, public backend content. Test release is not active.');
