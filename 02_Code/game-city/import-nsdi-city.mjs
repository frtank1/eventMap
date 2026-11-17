// Downloads building outlines with floor counts from the National Spatial Data
// Infrastructure of Kazakhstan (map.gov.kz, layer openmap:buildings_a) for one
// city. Run it on a computer with internet access; the phone never calls the
// portal. Reuse permission: see 05_Docs/licenses/NSDI.md.
//
//   node import-nsdi-city.mjs astana        -> data/nsdi/astana.json
//   node import-nsdi-city.mjs almaty|shymkent
//
// Output is compact: [id, levels|null, usage|null, [[lng,lat],...]] per outer
// ring, coordinates rounded to 1e-6 degrees (about 0.1 m).
import {mkdir,writeFile,rename} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';

export const CITIES=Object.freeze({
  // south, west, north, east (degrees). Product viewports, not administrative borders.
  astana:{title:'Астана',bbox:[50.95,71.10,51.40,71.85]},
  almaty:{title:'Алматы',bbox:[43.10,76.70,43.45,77.20]},
  shymkent:{title:'Шымкент',bbox:[42.20,69.45,42.45,69.80]},
});
export const ENDPOINT='https://map.gov.kz/geoserver/openmap/ows';
const PAGE=1000;

export function pageUrl(city,startIndex,count=PAGE){
  const [s,w,n,e]=CITIES[city].bbox;
  const q=new URLSearchParams({service:'WFS',version:'2.0.0',request:'GetFeature',typeNames:'openmap:buildings_a',
    outputFormat:'application/json',propertyName:'levels,usage,geom',sortBy:'id',count:String(count),startIndex:String(startIndex),
    bbox:`${s},${w},${n},${e},urn:ogc:def:crs:EPSG::4326`});
  return `${ENDPOINT}?${q}`;
}
const round=v=>Math.round(v*1e6)/1e6;
const whole=v=>{const n=Number(v);return Number.isInteger(n)&&n>0&&n<200?n:null;};
// One record per outer ring of each polygon; holes are not needed for heights.
export function compact(feature){
  const g=feature?.geometry,polys=g?.type==='MultiPolygon'?g.coordinates:g?.type==='Polygon'?[g.coordinates]:[];
  const out=[];
  for(const poly of polys){
    const ring=poly?.[0];if(!Array.isArray(ring)||ring.length<4)continue;
    const pts=ring.map(([x,y])=>[round(x),round(y)]);
    if(!pts.every(([x,y])=>Number.isFinite(x)&&Number.isFinite(y)))continue;
    out.push([String(feature.id||''),whole(feature.properties?.levels),whole(feature.properties?.usage),pts]);
  }
  return out;
}
export async function download(city,{fetchImpl=fetch,log=console.log,pause=150}={}){
  if(!CITIES[city])throw Error(`Unknown city "${city}". Use: ${Object.keys(CITIES).join(', ')}`);
  const buildings=[];let start=0,matched=null;
  for(;;){
    let page,attempt=0;
    for(;;){
      try{
        const r=await fetchImpl(pageUrl(city,start),{signal:AbortSignal.timeout(120000)});
        if(!r.ok)throw Error(`HTTP ${r.status}`);page=await r.json();break;
      }catch(e){if(++attempt>=5)throw Error(`Page at ${start} failed 5 times: ${e.message}`);log(`  retry ${attempt} at ${start}: ${e.message}`);await new Promise(r=>setTimeout(r,2000*attempt));}
    }
    const features=Array.isArray(page.features)?page.features:[];
    if(matched===null&&Number.isFinite(Number(page.numberMatched)))matched=Number(page.numberMatched);
    for(const f of features)buildings.push(...compact(f));
    start+=features.length;
    log(`  ${city}: ${start}${matched?` / ${matched}`:''} buildings`);
    if(features.length<PAGE)break;
    if(pause)await new Promise(r=>setTimeout(r,pause));
  }
  if(matched!==null&&start!==matched)throw Error(`Incomplete: received ${start} of ${matched}`);
  return {schemaVersion:1,source:{name:'НИПД РК (map.gov.kz), слой openmap:buildings_a',endpoint:ENDPOINT,permission:'05_Docs/licenses/NSDI.md'},
    city,title:CITIES[city].title,bbox:CITIES[city].bbox,retrieved:new Date().toISOString(),features:start,buildings};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
  const city=process.argv[2];const here=path.dirname(fileURLToPath(import.meta.url)),dir=path.join(here,'data/nsdi');
  const data=await download(city);await mkdir(dir,{recursive:true});
  const file=path.join(dir,`${city}.json`),tmp=file+'.part';
  await writeFile(tmp,JSON.stringify(data));await rename(tmp,file);
  const withLevels=data.buildings.filter(b=>b[1]).length;
  console.log(`Saved ${data.buildings.length} outlines (${withLevels} with floors) to ${path.relative(process.cwd(),file)}`);
}
