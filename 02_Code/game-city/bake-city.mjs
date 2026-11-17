import {build} from 'esbuild';
import {readFile,writeFile,mkdir,rm} from 'node:fs/promises';
import {gzipSync} from 'node:zlib';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
export async function bakeCity(out){
  const root=fileURLToPath(new URL('.',import.meta.url));
  const bundle=await build({stdin:{contents:"export {createCityBackdrop} from './runtime/city-backdrop.js';export {seed} from './generated/seed.js';",resolveDir:root},bundle:true,format:'esm',platform:'node',write:false,alias:{three:path.join(root,'source/vendor/three.module.js')}});
  const {createCityBackdrop,seed}=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].text).toString('base64'));
  const data=JSON.parse(await readFile(path.join(root,'source/astana-overview.json'),'utf8')),catalog=JSON.parse(await readFile(path.join(root,'source/astana-places.json'),'utf8'));
  const city=await createCityBackdrop(data,{entries:seed.manifest.models.filter(m=>m.id!=='museum'),catalog});
  try{const bytes=city.bake(),compressed=gzipSync(bytes,{level:9});await mkdir(out,{recursive:true});await rm(path.join(out,'city-geometry.bin.gz'),{force:true});await writeFile(path.join(out,'city-geometry.emc'),compressed);console.log('Baked city',JSON.stringify({...city.stats(),bytes:bytes.length,compressed:compressed.length}));}finally{city.destroy();}
}
