// Manual, file-based import. The phone NEVER calls map.gov.kz. No bulk download,
// credentials, public content pack, or automatic licence acceptance.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const input=process.argv[2];
if(!input)throw Error('Usage: node import-nsdi-quarter.mjs /absolute/path/to/bounded-WFS.json');
const raw=await readFile(input);if(raw.length>2*1024*1024)throw Error('Snapshot too large');
const data=JSON.parse(raw);
if(data.type!=='FeatureCollection'||!Array.isArray(data.features)||data.totalFeatures!==data.features.length||data.features.length>600)throw Error('Truncated/non-GeoJSON snapshot');
const snapshot={schemaVersion:1,datasetId:1316,usage:'local-evaluation-only',license:'not_specified',bounds:[71.425,51.125,71.435,51.132],
  source:'https://map.gov.kz/catalogue/#/dataset/1316',retrieved:new Date().toISOString(),rawSha256:createHash('sha256').update(raw).digest('hex'),
  heightPolicy:'Source height when present; otherwise levels * 3.3 metres, an explicitly unverified visual estimate.',
  totalFeatures:data.totalFeatures,features:data.features.map(f=>({type:'Feature',id:f.id,geometry:f.geometry,properties:{levels:f.properties.levels??null,height:f.properties.height??null}}))};
// Same validation as the runtime, bundled only to resolve local Three.js imports.
const {build}=await import('esbuild');const result=await build({stdin:{contents:'export {validateNsdiSnapshot} from "./runtime/nsdi-quarter-data.js"',resolveDir:here},bundle:true,write:false,format:'esm',platform:'node',alias:{three:path.join(here,'source/vendor/three.module.js')}});
const {validateNsdiSnapshot}=await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
const good=[],rejected=[];
for(const f of snapshot.features){
  try{validateNsdiSnapshot({...snapshot,totalFeatures:1,features:[f]});good.push(f);}
  catch(e){
    if(!e.message.startsWith('Invalid NSDI polygon topology:'))throw Error(e.message);
    const p=f.geometry.coordinates.flat(2);
    rejected.push({id:f.id,reason:'Invalid source topology; keep existing geometry',bounds:[Math.min(...p.map(v=>v[0])),Math.min(...p.map(v=>v[1])),Math.max(...p.map(v=>v[0])),Math.max(...p.map(v=>v[1]))]});
  }
}
snapshot.features=good;snapshot.rejected=rejected;
try{validateNsdiSnapshot(snapshot);}catch(e){throw Error(e.message);}
const out=path.join(here,'test/nsdi-local');await mkdir(out,{recursive:true});
await writeFile(path.join(out,'quarter.json'),JSON.stringify(snapshot));
console.log(JSON.stringify({features:snapshot.features.length,rejected,bytes:JSON.stringify(snapshot).length,sha256:snapshot.rawSha256,output:path.join(out,'quarter.json'),usage:snapshot.usage}));
