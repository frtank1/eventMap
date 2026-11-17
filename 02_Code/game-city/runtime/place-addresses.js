import {inPolygon,onOrInPolygon,ringBounds} from '../source/city-overview.js';
import {WORLD_BOUNDS, LON_SCALE, METERS} from './world-state.js';

const cell=.002,clean=(s,n)=>typeof s==='string'&&!/[\u0000-\u001f\u007f]/.test(s)&&s.length<=n;
const key=(x,y)=>`${Math.floor(x/cell)}:${Math.floor(y/cell)}`;
const empty=addressStatus=>({address:'',addressStatus,addressSource:'',sourceRef:'',entranceStatus:addressStatus==='unavailable'?'unavailable':'unreviewed',entrances:[]});
// Explicitly identified public OSM objects, not fuzzy names/nearest POIs.
export const LANDMARK_REFS=Object.freeze({baiterek:'way/230401645',arena:'way/166223262',adp:'way/762780007',ansar:'way/230403254',beijing:'way/230403277',library:'way/230358855',khan:'way/460703779',mega:'way/499328514',moscow:'way/1530050672',petersburg:'way/1530050670',talan:'relation/8205731',hazret:'way/240860325',grandmosque:'relation/18901938'});
export function createAddressIndex(data,catalog){
  if(data?.schemaVersion!==1||!Array.isArray(data.records)||data.records.length>100000)throw new Error('Invalid address extract');
  const byRef=new Map(),grid=new Map();
  for(const row of data.records){
    if(!Array.isArray(row)||row.length!==4)throw new Error('Invalid address record');
    const [ref,b,address,name]=row;
    if(typeof ref!=='string'||! /^(node|way|relation)\/[1-9]\d{0,19}$/.test(ref)||!Array.isArray(b)||b.length!==4||!b.every(Number.isFinite)||
      b[0]>b[2]||b[1]>b[3]||b[0]<WORLD_BOUNDS.west||b[2]>WORLD_BOUNDS.east||b[1]<WORLD_BOUNDS.south||b[3]>WORLD_BOUNDS.north||
      !clean(address,202)||!address||!clean(name,120)||byRef.has(ref))throw new Error('Invalid address record');
    byRef.set(ref,row);const k=key((b[0]+b[2])/2,(b[1]+b[3])/2);if(!grid.has(k))grid.set(k,[]);grid.get(k).push(row);
  }
  const result=row=>({address:row[2],addressStatus:'source',addressSource:'OpenStreetMap',sourceRef:`osm:${row[0]}`});
  const places=new Map((catalog?.places||[]).map(p=>[p.id,p]));
  const bySource=new Map(),sourceShapes=new Map();
  for(const p of places.values())for(const r of p.sourceRefs){
    // Shared complex refs are ambiguous, never choose one by list order.
    if(!bySource.has(r))bySource.set(r,p);else bySource.set(r,null);
  }
  for(const p of places.values())for(const f of p.footprints){
    if(!sourceShapes.has(f.sourceRef))sourceShapes.set(f.sourceRef,[]);sourceShapes.get(f.sourceRef).push(f.rings);
  }
  const near=(a,b)=>Math.hypot((a[0]-b[0])*LON_SCALE,(a[1]-b[1])*METERS)<=1;
  const open=ring=>ring.length>1&&near(ring[0],ring.at(-1))?ring.slice(0,-1):ring;
  const sameRing=(a,b)=>{
    a=open(a);b=open(b);if(a.length!==b.length||a.length<3)return false;
    return b.some((p,start)=>near(a[0],p)&&[1,-1].some(direction=>a.every((q,i)=>near(q,b[(start+direction*i+b.length)%b.length]))));
  };
  const sameShape=(a,b)=>a.length===b.length&&sameRing(a[0],b[0])&&a.slice(1).every(h=>b.slice(1).some(k=>sameRing(h,k)));
  const contains=(p,place)=>Number.isFinite(place.lng)&&Number.isFinite(place.lat)&&p.footprints.some(f=>
    onOrInPolygon([place.lng*LON_SCALE,place.lat*METERS],f.rings.map(r=>r.map(([x,y])=>[x*LON_SCALE,y*METERS])),2));
  function addressLookup(place){
    // Only raw OSM snapshot references qualify. MVT feature IDs can aggregate
    // unrelated footprints and must never be treated as authoritative OSM IDs.
    const raw=/^osm:(way|node|relation)\/\d+$/.test(place.sourceRef||'')?place.sourceRef.slice(4):null;
    const refs=catalog?(places.get(place.id)?.sourceRefs||[raw]):[LANDMARK_REFS[place.id]||raw];
    const matches=[];
    for(const explicit of refs){
      const exact=byRef.get(explicit),r=exact?.[1];
      // Reject stale/wrong type references whose object is elsewhere. Small
      // padding covers source quantisation, never a nearest-address lookup.
      if(r&&place.lng>=r[0]-2/LON_SCALE&&place.lng<=r[2]+2/LON_SCALE&&place.lat>=r[1]-2/METERS&&place.lat<=r[3]+2/METERS)matches.push(exact);
    }
    if(new Set(matches.map(r=>r[2])).size>1)return empty('ambiguous');
    if(matches.length)return result(matches[0]);
    const rings=place.geoRings;if(!rings?.[0]?.length)return empty('missing');
    const b=ringBounds(rings[0]),dx=5/LON_SCALE,dy=5/METERS;
    const x0=Math.floor((b.minX-dx)/cell),x1=Math.floor((b.maxX+dx)/cell),y0=Math.floor((b.minZ-dy)/cell),y1=Math.floor((b.maxZ+dy)/cell);
    if((x1-x0+1)*(y1-y0+1)>256)return empty('missing');
    const footprints=[],nodes=[];
    for(let x=x0;x<=x1;x++)for(let y=y0;y<=y1;y++)for(const row of grid.get(`${x}:${y}`)||[]){
      const [ref,r]=row;
      if(ref.startsWith('node/')){if(inPolygon([r[0],r[1]],rings))nodes.push(row);}
      else if(Math.abs(r[0]-b.minX)<=dx&&Math.abs(r[2]-b.maxX)<=dx&&Math.abs(r[1]-b.minZ)<=dy&&Math.abs(r[3]-b.maxZ)<=dy){
        const intersection=Math.max(0,Math.min(r[2],b.maxX)-Math.max(r[0],b.minX))*Math.max(0,Math.min(r[3],b.maxZ)-Math.max(r[1],b.minZ));
        const union=(r[2]-r[0])*(r[3]-r[1])+(b.maxX-b.minX)*(b.maxZ-b.minZ)-intersection;
        // A bbox is only a search accelerator: different concave buildings can
        // share it. Without actual matching rings, do not attach this address.
        if(union>0&&intersection/union>=.85&&sourceShapes.get(ref)?.some(source=>sameShape(rings,source)))footprints.push(row);
      }
    }
    const candidates=footprints.length?footprints:nodes;
    const addresses=new Set(candidates.map(r=>r[2]));
    if(addresses.size>1)return empty('ambiguous');
    return candidates.length?result(candidates[0]):empty('missing');
  }
  function lookup(place){
    const address=addressLookup(place),raw=address.sourceRef?.replace(/^osm:/,'')||(/^osm:(node|way|relation)\/[1-9]\d{0,19}$/.test(place.sourceRef||'')?place.sourceRef.slice(4):'');
    const matched=places.get(place.id)||bySource.get(raw);
    const buildingId=place.geometryKind!=='point'&&matched?`landmark:${matched.id}`:buildingIdentity(place);
    if(!matched||matched.review.status!=='matched'||!contains(matched,place))return {...empty('missing'),...address,buildingId,entranceStatus:'unreviewed',entrances:[]};
    // An OSM door is a selectable candidate, not proof it is currently public/open.
    // Do not lead people to service, emergency, private, exit-only or blocked doors.
    const selectedShapes=place.id===matched.id?matched.footprints:matched.footprints.filter(f=>f.sourceRef===raw);
    const entrances=matched.entrances.filter(e=>!['service','exit','emergency'].includes(e.kind)&&!['private','no'].includes(e.access)&&
      selectedShapes.some(f=>onOrInPolygon([e.lng*LON_SCALE,e.lat*METERS],f.rings.map(r=>r.map(([x,y])=>[x*LON_SCALE,y*METERS])),2)))
      .sort((a,b)=>Number(b.kind==='main')-Number(a.kind==='main')||a.id.localeCompare(b.id))
      .slice(0,8).map(({id,lat,lng,kind,wheelchair})=>({id,lat,lng,kind,wheelchair}));
    return {...empty('missing'),...address,buildingId,entranceStatus:entrances.length?'mapped':'not_mapped',entrances};
  }
  return {lookup,count:byRef.size};
}

export async function readBundledJSON(url,limit=12*1024*1024,{timeoutMs=6000}={}){
    if(!Number.isInteger(timeoutMs)||timeoutMs<1||timeoutMs>30000)throw new Error('Invalid bundled read timeout');
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeoutMs);
    try{
      // WKWebView's file-backed bundled resource is read locally, without
      // enabling universal file access or adding a remote geocoding service.
      const text=globalThis.location?.protocol==='file:'?await new Promise((resolve,reject)=>{
        const xhr=new XMLHttpRequest();xhr.open('GET',url);xhr.timeout=timeoutMs;
        xhr.onload=()=>xhr.status===0||xhr.status===200?resolve(xhr.responseText):reject(new Error('Address file unavailable'));
        xhr.onerror=xhr.ontimeout=()=>reject(new Error('Address file unavailable'));xhr.send();
      }):await (async()=>{const response=await fetch(url,{credentials:'omit',signal:controller.signal});if(!response.ok)throw new Error('Address file unavailable');return response.text();})();
      if(new TextEncoder().encode(text).length>limit)throw new Error('Bundled data budget');
      return JSON.parse(text);
    }finally{clearTimeout(timer);}
}
export function createAddressStore(url='addresses.json'){
  let pending;
  const ready=()=>pending??=readBundledJSON(url).then(data=>createAddressIndex(data));
  return {async lookup(place){try{return (await ready()).lookup(place);}catch{return empty('unavailable');}}};
}

export function placeMessage(place,details={}){
  const title=String(place.title||'Точка на карте').slice(0,120);
  return {type:'place',id:String(place.id||'').slice(0,100),title,lat:place.lat,lng:place.lng,
    ...empty('missing'),...details,geometryKind:place.geometryKind||'point',buildingId:details.buildingId||buildingIdentity(place)};
}

// Stable identity only. A local tile ID or an address node cannot identify a building.
export function buildingIdentity(place){
  if(place.geometryKind==='stylized'&&/^[a-z][a-z0-9-]{0,63}$/.test(place.id||''))return `landmark:${place.id}`;
  if(place.geometryKind==='footprint'&&/^osm:(way|relation)\/[1-9][0-9]{0,19}$/.test(place.sourceRef||''))return place.sourceRef.replace('/',':');
  return null;
}
