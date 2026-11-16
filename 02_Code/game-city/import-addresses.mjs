// Manual data import, never called by the application or every build.
// One public, bounded OSM extract. No nearest-street/address guessing.
import {writeFile} from 'node:fs/promises';
import {WORLD_BOUNDS as b} from './runtime/world-state.js';
const endpoint='https://overpass-api.de/api/interpreter';
const query=`[out:json][timeout:90];nwr["addr:housenumber"](${b.south},${b.west},${b.north},${b.east});out body bb;`;
const response=await fetch(`${endpoint}?data=${encodeURIComponent(query)}`,{headers:{Accept:'application/json','User-Agent':'EventMap-local-address-extract/1.0'},signal:AbortSignal.timeout(110000)});
if(!response.ok)throw new Error(`Address import HTTP ${response.status}; retained existing extract`);
const raw=await response.text();if(raw.length>60_000_000)throw new Error('Address extract exceeds budget');
const data=JSON.parse(raw);if(data.remark||!Array.isArray(data.elements))throw new Error('Incomplete extract; retained previous file');
const clean=(v,n=160)=>typeof v==='string'?v.replace(/[\u0000-\u001f\u007f]/g,' ').trim().slice(0,n):'';
const records=[];
for(const e of data.elements){
  const t=e.tags||{},street=clean(t['addr:street:ru']||t['addr:street']||t['addr:place']),number=clean(t['addr:housenumber'],40);
  if(!street||!number||!['node','way','relation'].includes(e.type))continue;
  const box=e.type==='node'?[e.lon,e.lat,e.lon,e.lat]:e.bounds?[e.bounds.minlon,e.bounds.minlat,e.bounds.maxlon,e.bounds.maxlat]:null;
  if(!box?.every(Number.isFinite)||box[0]<b.west||box[2]>b.east||box[1]<b.south||box[3]>b.north)continue;
  // Whitelist only public map attributes. Never retain unrelated contact tags.
  records.push([`${e.type}/${e.id}`,box.map(v=>Math.round(v*1e7)/1e7),`${street}, ${number}`,clean(t['name:ru']||t.name,120)]);
}
records.sort((a,c)=>a[0].localeCompare(c[0],'en'));
if(records.length<100||records.length>100000)throw new Error('Unexpected extract size; retained previous file');
const result={schemaVersion:1,source:'OpenStreetMap',license:'ODbL-1.0',attribution:'© OpenStreetMap contributors',sourceUrl:'https://www.openstreetmap.org/copyright',timestamp:data.osm3s?.timestamp_osm_base,bounds:b,records};
const bytes=JSON.stringify(result)+'\n';if(Buffer.byteLength(bytes)>12_000_000)throw new Error('Extract byte budget exceeded');
await writeFile(new URL('./source/astana-addresses.json',import.meta.url),bytes);
console.log(JSON.stringify({addresses:records.length,bytes:Buffer.byteLength(bytes),timestamp:result.timestamp}));
