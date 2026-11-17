import {WORLD_CENTER,LON_SCALE,METERS} from './world-state.js';
import {footprintCoverage} from './landmark-footprints.js';
import {onOrInPolygon} from '../source/city-overview.js';

// A bounded LOCAL experiment, not an approved production geography release.
export const NSDI_BOUNDS=Object.freeze([71.425,51.125,71.435,51.132]);
export const NSDI_LIMITS=Object.freeze({features:600,polygons:800,vertices:60000,bytes:2*1024*1024});
const box=r=>[Math.min(...r.map(p=>p[0])),Math.min(...r.map(p=>p[1])),Math.max(...r.map(p=>p[0])),Math.max(...r.map(p=>p[1]))];
const overlap=(a,b)=>a[0]<b[2]&&a[2]>b[0]&&a[1]<b[3]&&a[3]>b[1];
const inside=(p,b)=>p[0]>=b[0]&&p[0]<=b[2]&&p[1]>=b[1]&&p[1]<=b[3];
const positive=v=>v!==null&&v!==''&&Number.isFinite(Number(v))&&Number(v)>0?Number(v):null;
export function validateNsdiSnapshot(d){
  if(d?.schemaVersion!==1||d.datasetId!==1316||d.usage!=='local-evaluation-only'||d.license!=='not_specified'||
    !Array.isArray(d.bounds)||d.bounds.length!==4||d.bounds.some((v,i)=>v!==NSDI_BOUNDS[i])||
    !Array.isArray(d.features)||!d.features.length||d.features.length>NSDI_LIMITS.features||d.totalFeatures!==d.features.length+(d.rejected?.length||0))throw Error('Incomplete or invalid NSDI snapshot');
  if(d.rejected!==undefined&&(!Array.isArray(d.rejected)||d.rejected.length>20||d.rejected.some(r=>!Array.isArray(r.bounds)||r.bounds.length!==4||!r.bounds.every(Number.isFinite))))throw Error('Invalid rejected geometry');
  let vertices=0,polygons=0;const ids=new Set();
  for(const f of d.features){
    if(!/^buildings_a\.[0-9]+$/.test(f.id)||ids.has(f.id)||f.geometry?.type!=='MultiPolygon'||!Array.isArray(f.geometry.coordinates)||!f.geometry.coordinates.length)throw Error('Invalid NSDI feature');
    ids.add(f.id);const height=positive(f.properties?.height),levels=positive(f.properties?.levels);
    if(height===null&&levels===null||height!==null&&height>300||levels!==null&&(levels>90||!Number.isInteger(levels)))throw Error('Missing or invalid building dimensions');
    for(const rings of f.geometry.coordinates){
      if(++polygons>NSDI_LIMITS.polygons||!Array.isArray(rings)||!rings.length)throw Error('Invalid NSDI polygon');
      let count=0;
      for(const ring of rings){
        if(!Array.isArray(ring)||ring.length<4)throw Error('Invalid NSDI ring');
        for(const p of ring){if(++vertices>NSDI_LIMITS.vertices||++count>512||!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite)||!inside(p,[71.415,51.115,71.445,51.142]))throw Error('Invalid NSDI coordinate/budget');}
        if(ring[0].some((v,i)=>v!==ring.at(-1)[i]))throw Error('Open NSDI ring');
      }
      if(!overlap(box(rings[0]),NSDI_BOUNDS))throw Error('Out-of-area NSDI polygon');
      if(footprintCoverage(rings,rings)<.999)throw Error(`Invalid NSDI polygon topology: ${f.id}`);
    }
  }
  return d;
}
// Douglas–Peucker in metres, 8 cm maximum deviation; raw coordinates are kept
// in the source snapshot. Revalidate topology rather than silently repair it.
export function simplifyNsdiRing(ring,tolerance=.08){
  const dist=(p,a,b)=>{const dx=(b[0]-a[0])*LON_SCALE,dz=(b[1]-a[1])*METERS,x=(p[0]-a[0])*LON_SCALE,z=(p[1]-a[1])*METERS,t=Math.max(0,Math.min(1,(x*dx+z*dz)/(dx*dx+dz*dz)||0));return Math.hypot(x-t*dx,z-t*dz);};
  const recurse=points=>{let far=0,at=0;for(let i=1;i<points.length-1;i++){const d=dist(points[i],points[0],points.at(-1));if(d>far){far=d;at=i;}}return far>tolerance?[...recurse(points.slice(0,at+1)).slice(0,-1),...recurse(points.slice(at))]:[points[0],points.at(-1)];};
  const result=recurse(ring);return result.length>=4?result:ring;
}
export function createNsdiReplacement(snapshot){
  const d=validateNsdiSnapshot(snapshot),buildings=[],footprints=[];
  for(const f of d.features)f.geometry.coordinates.forEach((rings,i)=>{
    const measured=positive(f.properties.height),levels=positive(f.properties.levels);
    // 3.3 m/floor is ONLY a display estimate; raw height remains null in the snapshot.
    const height=measured??levels*3.3;
    const simplified=rings.map(r=>simplifyNsdiRing(r));
    const chosen=footprintCoverage(simplified,simplified)>.999? simplified:rings;
    if(chosen.flat().length>256)throw Error('NSDI render polygon exceeds budget');
    const copied=chosen.map(r=>r.map(p=>[...p]));
    const facadeColor=height>70?'glass':buildings.length%5===0?'sand':buildings.length%7===0?'sage':'ivory';
    buildings.push({id:`nsdi-${f.id}-${i}`,source:'nsdi',sourceRef:`nsdi:1316/${f.id}`,name:'Здание · НИПД (тест)',points:copied[0],holes:copied.slice(1),height,levels,facadeColor,heightBasis:measured?'source-height':'estimated-from-levels'});
    footprints.push({rings:copied,bounds:box(copied[0])});
  });
  const ownsGeo=rings=>{
    if(!rings?.[0]?.length)return false;
    const b=box(rings[0]),center=[(b[0]+b[2])/2,(b[1]+b[3])/2];
    // The rectangle is an explicit test replacement area, not landmark evidence.
    // Boundary-crossing source objects are retained in full, never clipped boxes.
    if((d.rejected||[]).some(r=>overlap(b,r.bounds)))return false;
    if(inside(center,NSDI_BOUNDS))return true;
    return footprints.some(p=>overlap(b,p.bounds)&&footprintCoverage(rings,p.rings)>.1);
  };
  const overview={schemaVersion:1,center:[...WORLD_CENTER],surfaces:[],roads:[],bridges:[],buildings:buildings.map((b,i)=>[
    b.height+1.15,.5,({glass:'cyan',sand:'sand',sage:'mint',ivory:'cream'})[b.facadeColor],
    [b.points,...b.holes].map(r=>r.slice(0,-1).map(([lng,lat])=>[(lng-WORLD_CENTER[0])*LON_SCALE,(WORLD_CENTER[1]-lat)*METERS]))])};
  const occupies=(lng,lat)=>footprints.some(p=>inside([lng,lat],p.bounds)&&onOrInPolygon([lng,lat],p.rings));
  return {buildings,ownsGeo,occupies,overview,stats:{features:d.features.length,rejected:d.rejected?.length||0,polygons:buildings.length,estimated:buildings.filter(b=>b.heightBasis==='estimated-from-levels').length,license:d.license}};
}
