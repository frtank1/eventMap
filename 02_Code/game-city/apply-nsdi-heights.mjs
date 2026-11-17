// Replaces heights of Astana overview buildings that have no height in OSM
// (OpenMapTiles default 5 m or less) with the NSDI floor count x 3.3 m.
// Tagged OSM heights are kept. The detailed street-level tiles read the same
// overview heights at runtime, so both levels of detail agree.
//
//   node import-nsdi-city.mjs astana     (first, needs internet)
//   node apply-nsdi-heights.mjs          (offline)
//   node build.mjs                       (copies into the app and preview)
import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {WORLD_CENTER,LON_SCALE,METERS} from './runtime/world-state.js';

export const FLOOR_HEIGHT=3.3;
const CELL=100;
const inside=([x,z],ring)=>{let hit=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[i],b=ring[j];if((a[1]>z)!==(b[1]>z)&&x<(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0])hit=!hit;}return hit;};
const area=r=>Math.abs(r.reduce((s,p,i)=>{const q=r[(i+1)%r.length];return s+p[0]*q[1]-q[0]*p[1];},0)/2);
const centroid=r=>{let x=0,z=0;for(const p of r){x+=p[0];z+=p[1];}return [x/r.length,z/r.length];};

export function applyHeights(overview,nsdi){
  const index=new Map(),key=(i,j)=>`${i}:${j}`;
  for(const [,levels,,ll]of nsdi.buildings){
    if(!levels)continue;
    const ring=ll.map(([lng,lat])=>[(lng-WORLD_CENTER[0])*LON_SCALE,-(lat-WORLD_CENTER[1])*METERS]);
    const xs=ring.map(p=>p[0]),zs=ring.map(p=>p[1]),item={ring,levels,area:area(ring)};
    for(let i=Math.floor(Math.min(...xs)/CELL);i<=Math.floor(Math.max(...xs)/CELL);i++)
      for(let j=Math.floor(Math.min(...zs)/CELL);j<=Math.floor(Math.max(...zs)/CELL);j++){const k=key(i,j);if(!index.has(k))index.set(k,[]);index.get(k).push(item);}
  }
  let candidates=0,matched=0,kept=0;
  const buildings=overview.buildings.map(b=>{
    const [top,base,style,rings]=b;
    if(top>5||base>0){kept++;return b;}
    candidates++;
    const c=centroid(rings[0]),cell=index.get(key(Math.floor(c[0]/CELL),Math.floor(c[1]/CELL)))||[];
    let best=null;for(const item of cell)if((!best||item.area<best.area)&&inside(c,item.ring))best=item;
    if(!best)return b;
    matched++;return [Math.min(310,Math.round(best.levels*FLOOR_HEIGHT*2)/2),base,style,rings];
  });
  const heights={source:nsdi.source?.name,retrieved:nsdi.retrieved,floorHeight:FLOOR_HEIGHT,untaggedBuildings:candidates,fromNsdi:matched,keptOsmHeights:kept};
  return {overview:{...overview,source:{...overview.source,heights},buildings},heights};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
  const here=path.dirname(fileURLToPath(import.meta.url)),file=path.join(here,'source/astana-overview.json');
  const nsdi=JSON.parse(await readFile(path.join(here,'data/nsdi/astana.json'),'utf8'));
  const {overview,heights}=applyHeights(JSON.parse(await readFile(file,'utf8')),nsdi);
  await writeFile(file,JSON.stringify(overview));
  console.log(`NSDI heights: ${heights.fromNsdi} of ${heights.untaggedBuildings} untagged buildings matched; ${heights.keptOsmHeights} OSM heights kept.`);
}
