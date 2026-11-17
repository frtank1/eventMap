import * as THREE from 'three';
import {flatPolygon,roadStrip} from '../source/city-stream.js';
import {CITY_PALETTE} from './art-direction.js';
import {WORLD_CENTER,LON_SCALE,METERS} from './world-state.js';
import {createLandmarkExclusion} from './landmark-footprints.js';
import {onOrInPolygon} from '../source/city-overview.js';
import {bridgeGeometry,BRIDGE_LIMITS} from './bridge-geometry.js';
import {LOD_COVERAGE_GLSL} from './city-lod.js';

// Persistent, geographic silhouettes, not a second schematic map. Small houses
// are a separate distance-culled batch; windows are filtered shader detail, not
// extra geometry. No trees or animated people in this whole-city layer.
export const BACKDROP_LIMITS=Object.freeze({buildings:100000,vertices:1800000,cell:3000,minorDistance:8500,rectangles:9});
const pause=()=>new Promise(resolve=>setTimeout(resolve,0));
const palette={cream:CITY_PALETTE.ivory,cyan:CITY_PALETTE.glass,sand:CITY_PALETTE.sand,mint:CITY_PALETTE.sage,roof:CITY_PALETTE.rooftop,...CITY_PALETTE};
const colors=Object.fromEntries(Object.entries(palette).map(([k,p])=>[k,p.map(c=>new THREE.Color(c).toArray().map(x=>Math.round(x*255)))]));
export const polygonArea=r=>Math.abs(r.reduce((a,p,i)=>{const q=r[(i+1)%r.length];return a+p[0]*q[1]-q[0]*p[1];},0)/2);
export function validateOverview(d){
  if(d?.schemaVersion!==1||d.center?.some((v,i)=>v!==WORLD_CENTER[i])||d.center?.length!==2||!Array.isArray(d.buildings)||d.buildings.length>BACKDROP_LIMITS.buildings||!Array.isArray(d.surfaces)||!Array.isArray(d.roads)||!Array.isArray(d.bridges)||d.bridges.length>3000)throw Error('Invalid city overview');
  let vertices=0;
  const rings=(r,max=16000)=>{if(!Array.isArray(r)||!r.length)throw Error('Invalid rings');let n=0;for(const ring of r){if(!Array.isArray(ring)||ring.length<3)throw Error('Invalid ring');for(const p of ring){if(++n>max||!Array.isArray(p)||p.length!==2||!p.every(v=>Number.isFinite(v)&&Math.abs(v)<90000))throw Error('Invalid coordinate');}}vertices+=n;};
  for(const [top,base,style,r]of d.buildings){if(!Number.isFinite(top)||!Number.isFinite(base)||base<0||top<=base||top>310||!['cream','cyan','sand','mint'].includes(style))throw Error('Invalid building');rings(r,256);}
  for(const [kind,r]of d.surfaces){if(!['water','grass'].includes(kind))throw Error('Invalid surface');rings(r);}
  if(d.roads.length>20000||d.surfaces.length>10000)throw Error('Surface budget');
  for(const [kind,line]of d.roads){if(!['motorway','trunk','primary','secondary','tertiary'].includes(kind)||!Array.isArray(line)||line.length<2||line.length>16000)throw Error('Invalid road');for(const p of line){if(!Array.isArray(p)||p.length!==2||!p.every(v=>Number.isFinite(v)&&Math.abs(v)<90000))throw Error('Invalid road coordinate');}vertices+=line.length;}
  for(const b of d.bridges){
    if(!['motorway','trunk','primary','secondary','tertiary','minor','residential','service','path','pedestrian','track','rail','transit'].includes(b.kind)||!Number.isFinite(b.layer)||b.layer<0||b.layer>5||!Number.isFinite(b.width)||b.width<1.5||b.width>36||!Array.isArray(b.ramps)||b.ramps.length!==2||!b.ramps.every(x=>typeof x==='boolean')||!Array.isArray(b.points)||b.points.length<2||b.points.length>BRIDGE_LIMITS.points)throw Error('Invalid bridge');
    for(const p of b.points)if(!Array.isArray(p)||p.length!==2||!p.every(x=>Number.isFinite(x)&&Math.abs(x)<90000))throw Error('Invalid bridge coordinate');vertices+=b.points.length;
  }
  if(vertices>BACKDROP_LIMITS.vertices)throw Error('City vertex budget');return d;
}
function batch(){return {p:[],n:[],c:[],night:[],snow:[],light:[],minor:[],anchor:[],i:[]};}
function vertex(b,p,n,color,anchor,lighting=0,minor=0){const index=b.p.length/3;b.p.push(...p);b.n.push(...n);b.c.push(...colors[color][0]);b.night.push(...colors[color][1]);b.snow.push(color==='roof'||color==='grass'?1:0);b.light.push(lighting);b.minor.push(minor);b.anchor.push(...anchor);return index;}
// Indexed roof + four vertices per wall. No hidden bottom faces or per-building
// objects/materials; holes stay holes, and source heights are never flattened.
export function appendBuilding(b,[top,base,style,rings]){
  const outer=rings[0],xs=outer.map(p=>p[0]),zs=outer.map(p=>p[1]),anchor=[(Math.min(...xs)+Math.max(...xs))/2,(Math.min(...zs)+Math.max(...zs))/2];
  const minor=Number(top-base<15&&polygonArea(outer)<250);
  const paths=rings.map(r=>r.map(([x,z])=>new THREE.Vector2(x,-z)));
  const triangles=THREE.ShapeUtils.triangulateShape(paths[0],paths.slice(1)),points=rings.flat(),offset=b.p.length/3;
  for(const [x,z]of points)vertex(b,[x,top,z],[0,1,0],'roof',anchor,0,minor);
  for(const tri of triangles)b.i.push(...tri.map(i=>offset+i));
  for(let ri=0;ri<rings.length;ri++){
    const ring=rings[ri],signed=ring.reduce((a,p,i)=>{const q=ring[(i+1)%ring.length];return a+p[0]*q[1]-q[0]*p[1];},0);
    for(let i=0;i<ring.length;i++){
      const [x,z]=ring[i],[xx,zz]=ring[(i+1)%ring.length],dx=xx-x,dz=zz-z,len=Math.hypot(dx,dz);if(len<.01)continue;
      const sign=(signed>0?1:-1)*(ri? -1:1),normal=[dz/len*sign,0,-dx/len*sign],at=b.p.length/3;
      for(const p of [[x,base,z],[xx,base,zz],[x,top,z],[xx,top,zz]])vertex(b,p,normal,style,anchor,1,minor);
      if(sign>0)b.i.push(at,at+2,at+1,at+1,at+2,at+3);else b.i.push(at,at+1,at+2,at+1,at+3,at+2);
    }
  }
  return anchor;
}
function mesh(b,material){
  const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(b.p,3));g.setAttribute('normal',new THREE.Int8BufferAttribute(b.n.map(n=>Math.round(n*127)),3,true));
  g.setAttribute('color',new THREE.Uint8BufferAttribute(b.c,3,true));g.setAttribute('nightColor',new THREE.Uint8BufferAttribute(b.night,3,true));g.setAttribute('snowSurface',new THREE.Uint8BufferAttribute(b.snow,1));g.setAttribute('nightLighting',new THREE.Uint8BufferAttribute(b.light,1));g.setAttribute('minorBuilding',new THREE.Uint8BufferAttribute(b.minor,1));g.setAttribute('coverageAnchor',new THREE.Float32BufferAttribute(b.anchor,2));g.setIndex(b.i);g.computeBoundingSphere();return new THREE.Mesh(g,material);
}
export async function createCityBackdrop(data,{entries=[],catalog,signal,onProgress,excludeBuilding=null}={}){
  validateOverview(data);const group=new THREE.Group();group.name='persistent-astana';
  const masks=Array.from({length:9},()=>new THREE.Vector4());
  const uniforms={coverage:{value:masks},coverageCount:{value:0},coverageWeight:{value:1},quarterMask:{value:new THREE.Vector3(0,0,0)},cityNight:{value:0},cityWinter:{value:0}};
  const material=new THREE.MeshStandardMaterial({vertexColors:true,roughness:.9});
  material.onBeforeCompile=s=>{
    Object.assign(s.uniforms,uniforms);
    s.vertexShader='attribute vec3 nightColor; attribute float snowSurface; attribute float nightLighting; attribute float minorBuilding; attribute vec2 coverageAnchor; varying vec2 cityAnchor; varying vec2 cityFacade; varying float cityLighting; varying float cityMinor; uniform float cityNight; uniform float cityWinter;\n'+s.vertexShader;
    s.vertexShader=s.vertexShader.replace('#include <color_vertex>','#include <color_vertex>\n vColor=mix(color,nightColor,cityNight); vColor=mix(vColor,mix(vec3(.77,.84,.85),vec3(.28,.40,.52),cityNight),cityWinter*snowSurface*.86); cityAnchor=coverageAnchor; cityLighting=nightLighting; cityMinor=minorBuilding; cityFacade=vec2(abs(normal.x)>.5?position.z:position.x,position.y);');
    s.fragmentShader='varying vec2 cityAnchor; varying vec2 cityFacade; varying float cityLighting; varying float cityMinor; uniform float cityNight; uniform vec4 coverage[9]; uniform int coverageCount; uniform float coverageWeight; uniform vec3 quarterMask;\n'+LOD_COVERAGE_GLSL+s.fragmentShader;
    s.fragmentShader=s.fragmentShader.replace('#include <clipping_planes_fragment>',`#include <clipping_planes_fragment>
      float cityKeep=cityCoverageSample();
      for(int i=0;i<9;i++){if(i>=coverageCount)break;vec4 r=coverage[i];if(cityAnchor.x>=r.x&&cityAnchor.y>=r.y&&cityAnchor.x<r.z&&cityAnchor.y<r.w&&cityKeep<coverageWeight)discard;}
      if(quarterMask.z>0.0&&distance(cityAnchor,quarterMask.xy)<quarterMask.z)discard;
      if(cityMinor>.5&&cityKeep>=1.0-smoothstep(6500.0,9000.0,distance(cameraPosition,vec3(cityAnchor.x,0.0,cityAnchor.y))))discard;
    `);
    // Screen-space averaging prevents distant windows becoming sparkling noise.
    // These are emissive materials, not thousands of per-window PointLights.
    s.fragmentShader=s.fragmentShader.replace('#include <emissivemap_fragment>',`#include <emissivemap_fragment>
      // Cheap base occlusion gives daytime city silhouettes depth without a
      // city-wide shadow map or per-building geometry. Night lighting stays intact.
      if(cityNight<.5 && cityLighting>.5 && cityLighting<1.5){
        diffuseColor.rgb*=mix(.68,1.0,smoothstep(0.0,12.0,cityFacade.y));
        // Mid-distance facades carry the same broad window rhythm as the
        // streamed streets. It filters out before it becomes a pixel grid.
        vec2 bays=vec2(cityFacade.x/8.6,(cityFacade.y-5.0)/5.4);
        vec2 aa=max(fwidth(bays),vec2(.001));
        float detail=(1.0-smoothstep(.17,.48,max(aa.x,aa.y)))*(1.0-smoothstep(3500.0,7000.0,distance(cameraPosition,vec3(cityAnchor.x,cityFacade.y,cityAnchor.y))));
        vec2 pane=1.0-smoothstep(vec2(.34,.33)-aa,vec2(.34,.33)+aa,abs(fract(bays)-.5));
        diffuseColor.rgb=mix(diffuseColor.rgb,vec3(.07,.25,.31),pane.x*pane.y*detail*smoothstep(5.0,5.6,cityFacade.y)*.75);
      }
      if(cityNight>.5){
        if(cityLighting>.5 && cityLighting<1.5){
          vec2 uv=cityFacade/vec2(4.5,3.8);
          float detail=1.0-smoothstep(.15,.65,max(fwidth(uv.x),fwidth(uv.y)));
          float windows=.17;
          if(detail>.01){
            vec2 edge=smoothstep(vec2(.22),vec2(.32),abs(fract(uv)-.5));
            float lit=step(.3,fract(sin(dot(floor(uv),vec2(12.9898,78.233)))*43758.5453));
            windows=mix(.17,(1.0-edge.x)*(1.0-edge.y)*lit,detail);
          }
          totalEmissiveRadiance+=vec3(1.0,.55,.19)*windows*.95;
        }else if(cityLighting>1.5){
          float farLight=smoothstep(1200.0,10000.0,distance(cameraPosition,vec3(cityAnchor.x,0.0,cityAnchor.y)));
          totalEmissiveRadiance+=vec3(1.0,.48,.12)*mix(.035,.48,farLight);
        }
      }
    `);
  };
  // Geographic surfaces sit less than a metre above the base plane. Keep a
  // small depth bias at city-scale distances, without moving their world height.
  const surfaceMaterial=material.clone();surfaceMaterial.onBeforeCompile=material.onBeforeCompile;
  surfaceMaterial.polygonOffset=true;surfaceMaterial.polygonOffsetFactor=-1;surfaceMaterial.polygonOffsetUnits=-4;
  // Bridges have one persistent owner even over detailed tiles.
  const bridgeMaterial=material.clone();bridgeMaterial.onBeforeCompile=s=>{
    material.onBeforeCompile(s);s.uniforms.coverageCount={value:0};s.uniforms.quarterMask={value:new THREE.Vector3()};
    s.fragmentShader=s.fragmentShader.replace('#include <emissivemap_fragment>','#include <emissivemap_fragment>\n totalEmissiveRadiance += vec3(.8,.42,.13)*cityNight*step(.4,vColor.r)*.32;');
  };
  const chunks=new Map(),minor=[],bridgeDetails=[],index=new Map(),large=[];let count=0,triangles=0,bridgeParts=0,bridgeTriangles=0;
  const cleanup=()=>{group.traverse(o=>o.geometry?.dispose());material.dispose();surfaceMaterial.dispose();bridgeMaterial.dispose();group.clear();group.removeFromParent();};
  const check=()=>{if(signal?.aborted)throw new DOMException('City overview cancelled','AbortError');};
  try{
    const excluded=createLandmarkExclusion(entries,catalog);
    let slice=performance.now();
    for(const building of data.buildings){
      check();const [top,base,style,rings]=building,outer=rings[0];
      const xs=outer.map(p=>p[0]),zs=outer.map(p=>p[1]),x=(Math.min(...xs)+Math.max(...xs))/2,z=(Math.min(...zs)+Math.max(...zs))/2;
      const geo=rings.map(r=>r.map(([x,z])=>[WORLD_CENTER[0]+x/LON_SCALE,WORLD_CENTER[1]-z/METERS]));
      if(excludeBuilding?.(geo)||excluded(WORLD_CENTER[0]+x/LON_SCALE,WORLD_CENTER[1]-z/METERS,geo))continue;
      const small=top-base<15&&polygonArea(outer)<250,key=`${Math.floor(x/3000)}:${Math.floor(z/3000)}:${Number(small)}`;
      if(!chunks.has(key))chunks.set(key,{data:batch(),small,x:Math.floor(x/3000)*3000+1500,z:Math.floor(z/3000)*3000+1500});
      appendBuilding(chunks.get(key).data,building);count++;
      const record={top,rings},x0=Math.floor(Math.min(...xs)/250),x1=Math.floor(Math.max(...xs)/250),z0=Math.floor(Math.min(...zs)/250),z1=Math.floor(Math.max(...zs)/250);
      if((x1-x0+1)*(z1-z0+1)>64)large.push(record);
      else for(let gx=x0;gx<=x1;gx++)for(let gz=z0;gz<=z1;gz++){const ik=`${gx}:${gz}`;if(!index.has(ik))index.set(ik,[]);index.get(ik).push(record);}
      if(performance.now()-slice>7){onProgress?.(count);await pause();check();slice=performance.now();}
    }
    for(const c of chunks.values()){check();const m=mesh(c.data,material);triangles+=c.data.i.length/3;c.data=null;group.add(m);if(c.small)minor.push({mesh:m,x:c.x,z:c.z,visible:false});await pause();}
    chunks.clear();
    const surfaces=batch(),rect={minX:-90000,minZ:-90000,maxX:90000,maxZ:90000};
    const addSurface=(g,kind)=>{if(!g)return;const p=g.attributes.position,n=g.attributes.normal,offset=surfaces.p.length/3;for(let i=0;i<p.count;i++)vertex(surfaces,[p.getX(i),p.getY(i),p.getZ(i)],[n.getX(i),n.getY(i),n.getZ(i)],kind,[p.getX(i),p.getZ(i)],kind==='road'?2:0);if(g.index)for(const i of g.index.array)surfaces.i.push(offset+i);else for(let i=0;i<p.count;i++)surfaces.i.push(offset+i);g.dispose();};
    for(const [kind,rings]of data.surfaces){check();addSurface(flatPolygon(rings,p=>p,kind==='water'?-.4:-.8,rect),kind);if(performance.now()-slice>7){await pause();slice=performance.now();}}
    const widths={motorway:24,trunk:22,primary:18,secondary:14,tertiary:10};
    for(const [kind,line]of data.roads){check();addSurface(roadStrip(line,p=>p,widths[kind],-.25,rect),'road');if(performance.now()-slice>7){await pause();slice=performance.now();}}
    group.add(mesh(surfaces,surfaceMaterial));triangles+=surfaces.i.length/3;
    const bridgeBatches=new Map();
    for(const bridge of data.bridges){
      check();const at=bridge.points[Math.floor(bridge.points.length/2)],cell=`${Math.floor(at[0]/6000)}:${Math.floor(at[1]/6000)}`;
      for(const part of bridgeGeometry(bridge)){
        const g=part.geometry;if(!g)continue;if(++bridgeParts>BRIDGE_LIMITS.parts){g.dispose();throw Error('Bridge geometry budget');}
        const key=`${cell}:${Number(part.detail)}`;if(!bridgeBatches.has(key))bridgeBatches.set(key,{b:batch(),detail:part.detail});
        const b=bridgeBatches.get(key).b,p=g.attributes.position,n=g.attributes.normal,offset=b.p.length/3;
        for(let i=0;i<p.count;i++)vertex(b,[p.getX(i),p.getY(i),p.getZ(i)],[n.getX(i),n.getY(i),n.getZ(i)],part.color,[p.getX(i),p.getZ(i)]);
        for(const i of g.index.array)b.i.push(offset+i);g.dispose();
        if(performance.now()-slice>7){await pause();check();slice=performance.now();}
      }
    }
    for(const {b,detail}of bridgeBatches.values()){const m=mesh(b,bridgeMaterial);m.name=detail?'bridge-railings':'bridge-structure';group.add(m);bridgeTriangles+=b.i.length/3;if(detail)bridgeDetails.push(m);}
    triangles+=bridgeTriangles;
    return {group,setAppearance(night,theme){uniforms.cityNight.value=night?1:0;uniforms.cityWinter.value=theme!=='default'?1:0;},
      update(camera,rectangles=[],quarter=null,coverageWeight=1){
        uniforms.coverageWeight.value=Math.max(0,Math.min(1,Number.isFinite(coverageWeight)?coverageWeight:1));
        uniforms.coverageCount.value=Math.min(9,rectangles.length);rectangles.slice(0,9).forEach((r,i)=>masks[i].set(...r));uniforms.quarterMask.value.set(quarter?.x||0,quarter?.z||0,quarter?.radius||0);
        // Shader fades per building. Cull a chunk only beyond its last possible
        // visible member, not abruptly at the chunk centre's former threshold.
        for(const c of minor){const distance=Math.hypot(camera.position.x-c.x,camera.position.y,camera.position.z-c.z);c.visible=distance<11250;c.mesh.visible=c.visible;}
        for(const m of bridgeDetails)m.visible=camera.position.distanceTo(m.geometry.boundingSphere.center)<12000;
      },placeAt(x,z){
        let found=null;const candidates=new Set(large);
        for(let gx=Math.floor((x-.35)/250);gx<=Math.floor((x+.35)/250);gx++)for(let gz=Math.floor((z-.35)/250);gz<=Math.floor((z+.35)/250);gz++)for(const b of index.get(`${gx}:${gz}`)||[])candidates.add(b);
        for(const b of candidates)if((!found||b.top>found.top)&&onOrInPolygon([x,z],b.rings))found=b;
        return found?{title:'Здание',top:found.top,geometryKind:'footprint',geoRings:found.rings.map(r=>r.map(([x,z])=>[WORLD_CENTER[0]+x/LON_SCALE,WORLD_CENTER[1]-z/METERS])),lng:WORLD_CENTER[0]+x/LON_SCALE,lat:WORLD_CENTER[1]-z/METERS}:null;
      },
      stats:()=>({backdropBuildings:count,backdropTriangles:triangles,backdropReady:true,bridges:data.bridges.length,bridgeTriangles}),destroy:cleanup};
  }catch(e){cleanup();chunks.clear();throw e;}
}
