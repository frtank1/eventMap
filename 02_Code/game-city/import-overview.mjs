// Manual, bounded public-data import. Never runs on a phone or at backend startup.
import {mkdir,readFile,writeFile,rename} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {VectorTile} from './source/vendor/vector-tile.js';
import Pbf from './source/vendor/pbf.js';
import {WORLD_BOUNDS as bounds,WORLD_CENTER as center,LON_SCALE,METERS} from './runtime/world-state.js';
import {footprintStyle} from './source/city-overview.js';
import {clipBridgeLine,joinBridgePaths} from './source/bridge-paths.js';

const cache=new URL('./test-results/overview-source/',import.meta.url);
await mkdir(cache,{recursive:true});
const metadata=await fetch('https://tiles.openfreemap.org/planet/latest',{signal:AbortSignal.timeout(20000)}).then(r=>{if(!r.ok)throw Error(`TileJSON ${r.status}`);return r.json();});
const template=metadata.tiles?.[0];
if(typeof template!=='string'||!template.startsWith('https://tiles.openfreemap.org/'))throw Error('Unexpected tile source');
const sourceKey=createHash('sha256').update(template).digest('hex').slice(0,12);
function xy(lon,lat,z){const n=2**z,s=Math.sin(lat*Math.PI/180);return [(lon+180)/360*n,(1-Math.log((1+s)/(1-s))/(2*Math.PI))/2*n];}
function ll(x,y,z){const n=2**z;return [x/n*360-180,Math.atan(Math.sinh(Math.PI*(1-2*y/n)))*180/Math.PI];}
function tiles(z){const [x0,y1]=xy(bounds.west,bounds.south,z),[x1,y0]=xy(bounds.east,bounds.north,z),out=[];for(let y=Math.floor(y0);y<=Math.floor(y1);y++)for(let x=Math.floor(x0);x<=Math.floor(x1);x++)out.push({x,y,z});return out;}
async function readTile(t){
  const file=new URL(`${sourceKey}-${t.z}-${t.x}-${t.y}.pbf`,cache);
  try{return await readFile(file);}catch(e){if(e.code!=='ENOENT')throw e;}
  for(let attempt=0;attempt<3;attempt++){
    try{
      const y=metadata.scheme==='tms'?2**t.z-1-t.y:t.y;
      const response=await fetch(template.replace('{z}',t.z).replace('{x}',t.x).replace('{y}',y),{signal:AbortSignal.timeout(20000)});
      if(!response.ok)throw Error(`Tile ${t.z}/${t.x}/${t.y}: ${response.status}`);
      const data=Buffer.from(await response.arrayBuffer());if(data.length>4*1024*1024)throw Error('Tile budget');
      await writeFile(file,data);return data;
    }catch(e){if(attempt===2)throw e;}
  }
}
const project=([x,y])=>[Math.round((x-center[0])*LON_SCALE*2)/2,Math.round(-(y-center[1])*METERS*2)/2];
const polygons=g=>g.type==='Polygon'?[g.coordinates]:g.type==='MultiPolygon'?g.coordinates:[];
const numeric=(...values)=>values.map(v=>v==null||v===''?NaN:Number.parseFloat(v)).find(Number.isFinite);
const buildings=[],surfaces=[],roads=[],bridgeParts=[];let missingHeights=0;
function ring(points){const out=[];for(const p of points.map(project))if(!out.length||p[0]!==out.at(-1)[0]||p[1]!==out.at(-1)[1])out.push(p);if(out.length>1&&out[0][0]===out.at(-1)[0]&&out[0][1]===out.at(-1)[1])out.pop();return out;}
function area(r){return Math.abs(r.reduce((a,p,i)=>{const q=r[(i+1)%r.length];return a+p[0]*q[1]-q[0]*p[1];},0)/2);}
function read(t,data){
  const parsed=new VectorTile(new Pbf(data)),[west,north]=ll(t.x,t.y,t.z),[east,south]=ll(t.x+1,t.y+1,t.z);
  for(const name of t.z===14?['building','transportation']:['landcover','landuse','park','water','transportation']){
    const layer=parsed.layers[name];if(!layer)continue;
    for(let i=0;i<layer.length;i++){
      const f=layer.feature(i),p=f.properties,g=f.toGeoJSON(t.x,t.y,t.z).geometry;
      if(name==='building'){
        if(f.type!==3||[true,1,'true'].includes(p.hide_3d))continue;
        const levels=numeric(p['building:levels'],p.levels),known=numeric(p.render_height,p.height,p['building:height'],levels===undefined?undefined:levels*3.1);
        // Same source-height fallback as the existing detailed renderer, not a claimed survey.
        const top=Math.min(310,Math.max(0,known??9)),base=Math.min(top,Math.max(0,numeric(p.render_min_height,p.min_height)??0));if(top<=base)continue;
        for(const rings of polygons(g)){
          const xs=rings[0].map(p=>p[0]),ys=rings[0].map(p=>p[1]),lon=(Math.min(...xs)+Math.max(...xs))/2,lat=(Math.min(...ys)+Math.max(...ys))/2;
          if(lon<west||lon>=east||lat>north||lat<=south||lon<bounds.west||lon>bounds.east||lat<bounds.south||lat>bounds.north)continue;
          const r=rings.map(ring);if(r.some(x=>x.length<3)||r.reduce((n,r)=>n+r.length,0)>256||area(r[0])<30)continue;
          if(known===undefined)missingHeights++;
          buildings.push([Math.round(top*10)/10,Math.round(base*10)/10,footprintStyle(rings,f.id,top,base).material,r]);
        }
      }else if(name==='transportation'){
        if(t.z===14){
          const widths={motorway:22,trunk:19,primary:16,secondary:13,tertiary:10,minor:7,residential:7,service:4.5,path:2.8,pedestrian:5,track:3,rail:3,transit:3};
          if(f.type!==2||p.brunnel!=='bridge'||!(p.class in widths)||Number(p.layer)<0)continue;
          const [rx0,rz0]=project([west,north]),[rx1,rz1]=project([east,south]);
          for(const line of g.type==='LineString'?[g.coordinates]:g.type==='MultiLineString'?g.coordinates:[]){
            for(const points of clipBridgeLine(line.map(project),[rx0,rz0,rx1,rz1]))bridgeParts.push({kind:p.class,layer:Math.min(5,Math.max(0,Number(p.layer)||0)),width:Math.min(36,Math.max(1.5,numeric(p.width)??widths[p.class])),points});
          }
          continue;
        }
        if(f.type!==2||p.brunnel==='tunnel'||Number(p.layer)<0||!['motorway','trunk','primary','secondary','tertiary'].includes(p.class))continue;
        if(p.brunnel==='bridge')continue;
        for(const line of g.type==='LineString'?[g.coordinates]:g.type==='MultiLineString'?g.coordinates:[])roads.push([p.class,line.map(project)]);
      }else{
        const green=name==='park'||['wood','forest','grass','park','garden','meadow','recreation_ground','village_green'].includes(p.class);
        if(name!=='water'&&!green)continue;
        for(const rings of polygons(g)){const r=rings.map(ring);if(r[0].length>=3&&r.reduce((n,r)=>n+r.length,0)<=16000)surfaces.push([name==='water'?'water':'grass',r]);}
      }
    }
  }
}
for(const z of [14,11]){
  const queue=tiles(z),results=new Array(queue.length);let next=0,done=0;
  async function worker(){while(next<queue.length){const at=next++,t=queue[at];results[at]=await readTile(t);done++;if(done%40===0)console.log(`z${z}: ${done}/${queue.length}`);}}
  await Promise.all([worker(),worker()]);
  for(let i=0;i<queue.length;i++)read(queue[i],results[i]);
}
const bridges=joinBridgePaths(bridgeParts);
const output={schemaVersion:1,source:{provider:'OpenFreeMap / OpenMapTiles / OpenStreetMap',license:'ODbL-1.0',attribution:'© OpenStreetMap contributors',url:'https://www.openstreetmap.org/copyright',template,importedAt:new Date().toISOString(),buildingZoom:14,surfaceZoom:11,bridgeZoom:14,bridgeProfile:'stylized-clearance-not-surveyed',missingHeights,minimumFootprintArea:30},center,bounds,buildings,surfaces,roads,bridges};
const json=JSON.stringify(output);if(Buffer.byteLength(json)>16*1024*1024||buildings.length>100000)throw Error('Overview snapshot budget');
const out=new URL('./source/astana-overview.json',import.meta.url),temp=new URL('./source/astana-overview.json.tmp',import.meta.url);
await writeFile(temp,json);await rename(temp,out);
console.log(JSON.stringify({buildings:buildings.length,surfaces:surfaces.length,roads:roads.length,bridges:bridges.length,bytes:Buffer.byteLength(json),sha256:createHash('sha256').update(json).digest('hex'),missingHeights}));
