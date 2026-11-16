import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';
import {VectorTile} from './vendor/vector-tile.js';
import Pbf from './vendor/pbf.js';
import {decorateQuarterMaterial} from '../runtime/quarter-materials.js';
import {CITY_PALETTE} from '../runtime/art-direction.js';
import {WORLD_BOUNDS} from '../runtime/world-state.js';
import {createUrbanDetails} from './urban-details.js';
import {createPedestrians} from './people.js';
import {inPolygon, onOrInPolygon, ringBounds, footprintStyle, overviewRelief, createSpatialIndex, distanceSegment, treeCandidates} from './city-overview.js';

const TILE_JSON = 'https://tiles.openfreemap.org/planet/latest';
const URBAN = WORLD_BOUNDS;
// Mobile pilot budget; original browser presentation is unchanged.
const MAX_REQUESTS = 2, MAX_CACHE = 16, MAX_DESIRED = 9, MAX_ATTEMPTS = 3;
const TIMEOUT_MS = 18000, BATCH_VERTICES = 120000;
const MAX_ARCHITECTURE = 320, MAX_OVERVIEW = 2200, MAX_OVERVIEW_VERTICES = 180000, MAX_TREES = 240;
const RAD = Math.PI / 180, METERS = 111320;
const clamp = THREE.MathUtils.clamp;
const yieldTask = () => new Promise(resolve => setTimeout(resolve, 0));
const abortError = () => new DOMException('City stream cancelled', 'AbortError');
const checkAbort = signal => { if (signal.aborted) throw abortError(); };

function tileXY(lon, lat, zoom) {
  const n = 2 ** zoom, sin = Math.sin(lat * RAD);
  return [(lon + 180) / 360 * n, (1 - Math.log((1 + sin) / (1 - sin)) / (2 * Math.PI)) / 2 * n];
}

function tileLonLat(x, y, zoom) {
  const n = 2 ** zoom;
  return [x / n * 360 - 180, Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n))) / RAD];
}

function descriptor(x, y, z) {
  const [west, north] = tileLonLat(x, y, z), [east, south] = tileLonLat(x + 1, y + 1, z);
  return {key: `${z}/${x}/${y}`, x, y, z, west, east, south, north};
}

function inUrban(lon, lat) {
  return lon >= URBAN.west && lon <= URBAN.east && lat >= URBAN.south && lat <= URBAN.north;
}

// XYZ tiles form a quadtree: intersecting tile interiors are ancestors/descendants.
function overlaps(a, b) {
  if (a.z > b.z) return overlaps(b, a);
  const scale = 2 ** (b.z - a.z);
  return Math.floor(b.x / scale) === a.x && Math.floor(b.y / scale) === a.y;
}

export function chooseZoom(distance, previous) {
  if(previous===10&&distance>31000)return 10;
  if(previous===11&&distance>17500&&distance<37000)return 11;
  if(distance>34000)return 10;
  if(distance>20000)return 11;
  // Hysteresis avoids repeated network churn at the two LOD boundaries.
  if (previous === 14 && distance < 5000) return 14;
  if (previous === 12 && distance > 10000) return 12;
  if (previous === 13 && distance >= 3900 && distance <= 12500) return 13;
  return distance < 4500 ? 14 : distance < 11500 ? 13 : 12;
}

function desiredTiles(lon, lat, distance, zoom, lonScale) {
  const [tx, ty] = tileXY(lon, lat, zoom);
  const tileWidth = 360 / 2 ** zoom * lonScale;
  const radius = clamp(distance * 0.82, tileWidth * 0.60, tileWidth * 2.15);
  const range = Math.ceil(radius / tileWidth) + 1, result = [];
  const [minX, maxY] = tileXY(URBAN.west, URBAN.south, zoom);
  const [maxX, minY] = tileXY(URBAN.east, URBAN.north, zoom);
  for (let y = Math.max(Math.floor(minY), Math.floor(ty) - range); y <= Math.min(Math.floor(maxY), Math.floor(ty) + range); y++) {
    for (let x = Math.max(Math.floor(minX), Math.floor(tx) - range); x <= Math.min(Math.floor(maxX), Math.floor(tx) + range); x++) {
      const tile = descriptor(x, y, zoom);
      const dx = Math.max(tile.west - lon, 0, lon - tile.east) * lonScale;
      const dz = Math.max(tile.south - lat, 0, lat - tile.north) * METERS;
      if (Math.hypot(dx, dz) > radius) continue;
      tile.priority = Math.hypot(((tile.west + tile.east) / 2 - lon) * lonScale, ((tile.north + tile.south) / 2 - lat) * METERS);
      result.push(tile);
    }
  }
  return result.sort((a, b) => a.priority - b.priority).slice(0, MAX_DESIRED);
}

function makeMaterials() {
  const specs = {
    cream: CITY_PALETTE.ivory, cyan: CITY_PALETTE.glass,
    sand: CITY_PALETTE.sand, mint: CITY_PALETTE.sage,
    landcover: CITY_PALETTE.land, landuse: CITY_PALETTE.grass,
    park: CITY_PALETTE.grass, water: CITY_PALETTE.water,
    roadEdge: CITY_PALETTE.kerb, road: CITY_PALETTE.road,
    path: CITY_PALETTE.pavement, minorPath: CITY_PALETTE.pavement,
    urban: CITY_PALETTE.urban, rail: CITY_PALETTE.dark, overview: CITY_PALETTE.sand,
    trunk: CITY_PALETTE.trunk, foliage: CITY_PALETTE.leaf,
    roof: CITY_PALETTE.roof, plinth: CITY_PALETTE.trim, roofUnit: CITY_PALETTE.roof,
    marking: CITY_PALETTE.trim, car: [0xffffff,0xb3b9bd], carGlass: CITY_PALETTE.dark, bench: CITY_PALETTE.wood,
  };
  const materials = {};
  for (const [name, [day, night]] of Object.entries(specs)) {
    const material = new THREE.MeshStandardMaterial({color: day, roughness: 0.88, metalness: 0});
    material.name = `city-${name}`;
    material.userData = {day, night};
    if (name === 'water') { material.roughness = 0.35; material.metalness = 0.12; }
    if (['cream','cyan','sand','mint','roof','path'].includes(name)) material.userData.facade=decorateQuarterMaterial(material,{glass:name==='cyan',paving:name==='path'});
    if(name==='minorPath'){material.transparent=true;material.depthWrite=false;material.userData.facade=decorateQuarterMaterial(material,{paving:true,minorPath:true});}
    // Separated layers and offsets keep overlapping GIS footprints stable while panning.
    if(['cream','cyan','sand','mint'].includes(name)){material.polygonOffset=true;material.polygonOffsetFactor=1;material.polygonOffsetUnits=1;}
    materials[name] = material;
  }
  return materials;
}

function disposeTile(root) {
  if (!root) return;
  root.userData.urban?.dispose();
  const crowd=root.userData.crowd;
  if(crowd){const mats=new Set();crowd.group.traverse(o=>{if(o.material)mats.add(o.material)});for(const m of mats)m.dispose()}
  root.removeFromParent();
  root.traverse(object => { if (object.isInstancedMesh) object.dispose(); if (object.geometry) object.geometry.dispose(); });
  root.clear(); // Materials are shared and disposed only by destroy().
}

function polygons(geometry) {
  return geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.type === 'MultiPolygon' ? geometry.coordinates : [];
}

function makeShape(rings, project) {
  const paths = rings.map(ring => {
    const points = [];
    for (const coordinate of ring) {
      const [x, z] = project(coordinate);
      if (!Number.isFinite(x) || !Number.isFinite(z)) continue;
      if (!points.length || points.at(-1).x !== x || points.at(-1).y !== -z) points.push(new THREE.Vector2(x, -z));
    }
    if (points.length > 1 && points[0].equals(points.at(-1))) points.pop();
    return points;
  });
  if (!paths[0] || paths[0].length < 3) return null;
  const shape = new THREE.Shape(paths[0]);
  // toGeoJSON already groups exterior rings and their holes, including multipolygons.
  for (const hole of paths.slice(1)) if (hole.length >= 3) shape.holes.push(new THREE.Path(hole));
  return shape;
}

function positionsGeometry(positions) {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.computeVertexNormals();
  return geometry;
}

// Clip triangulated flat surfaces, not individual polygon rings. This preserves
// river islands/courtyards even when a hole crosses a tile boundary.
function clipSurface(geometry, rect) {
  const attribute = geometry.getAttribute('position'), index = geometry.index;
  const output = [], count = index ? index.count : attribute.count;
  const planes = [[0, rect.minX, 1], [0, rect.maxX, -1], [2, rect.minZ, 1], [2, rect.maxZ, -1]];
  for (let i = 0; i < count; i += 3) {
    let polygon = [0, 1, 2].map(offset => {
      const at = index ? index.getX(i + offset) : i + offset;
      return [attribute.getX(at), attribute.getY(at), attribute.getZ(at)];
    });
    for (const [axis, edge, direction] of planes) {
      const clipped = [];
      for (let j = 0; j < polygon.length; j++) {
        const a = polygon[j], b = polygon[(j + 1) % polygon.length];
        const da = (a[axis] - edge) * direction, db = (b[axis] - edge) * direction;
        if (da >= 0) clipped.push(a);
        if ((da >= 0) !== (db >= 0)) {
          const t = da / (da - db);
          clipped.push(a.map((value, k) => value + (b[k] - value) * t));
        }
      }
      polygon = clipped;
      if (!polygon.length) break;
    }
    for (let j = 1; j + 1 < polygon.length; j++) output.push(...polygon[0], ...polygon[j], ...polygon[j + 1]);
  }
  geometry.dispose();
  return positionsGeometry(output);
}

function flatPolygon(rings, project, elevation, rect) {
  const shape = makeShape(rings, project);
  if (!shape) return null;
  const geometry = new THREE.ShapeGeometry(shape);
  geometry.rotateX(-Math.PI / 2);
  geometry.translate(0, elevation, 0);
  return clipSurface(geometry, rect);
}

function roadStrip(coordinates, project, width, elevation, rect) {
  const points = coordinates.map(project), offsets = [], positions = [];
  // Shared, clamped miter joins avoid cracks at bends without extreme spikes.
  for (let i = 0; i < points.length; i++) {
    const a = points[Math.max(0, i - 1)], p = points[i], b = points[Math.min(points.length - 1, i + 1)];
    let ax = p[0] - a[0], az = p[1] - a[1], bx = b[0] - p[0], bz = b[1] - p[1];
    let al = Math.hypot(ax, az), bl = Math.hypot(bx, bz);
    if (al < 0.001) { ax = bx; az = bz; al = bl; }
    if (bl < 0.001) { bx = ax; bz = az; bl = al; }
    al = al || 1; bl = bl || 1;
    let nx = -az / al - bz / bl, nz = ax / al + bx / bl;
    const length = Math.hypot(nx, nz);
    if (length < 0.001) { nx = -bz / bl; nz = bx / bl; } else { nx /= length; nz /= length; }
    const scale = Math.min(width, width / 2 / Math.max(0.5, nx * (-bz / bl) + nz * (bx / bl)));
    offsets.push([nx * scale, nz * scale]);
  }
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i], oa = offsets[i - 1], ob = offsets[i];
    if (Math.hypot(b[0] - a[0], b[1] - a[1]) < 0.01) continue;
    const leftA = [a[0] + oa[0], elevation, a[1] + oa[1]], rightA = [a[0] - oa[0], elevation, a[1] - oa[1]];
    const leftB = [b[0] + ob[0], elevation, b[1] + ob[1]], rightB = [b[0] - ob[0], elevation, b[1] - ob[1]];
    positions.push(...leftA, ...leftB, ...rightA, ...leftB, ...rightB, ...rightA);
  }
  return clipSurface(positionsGeometry(positions), rect);
}

function numberProperty(value) {
  if (value === null || value === undefined || value === '') return NaN;
  const number = Number.parseFloat(value);
  return typeof value === 'string' && /ft|feet|'/i.test(value) ? number * 0.3048 : number;
}

function firstNumber(...values) {
  return values.map(numberProperty).find(Number.isFinite);
}

function buildingHeight(props) {
  const levels = firstNumber(props['building:levels'], props.levels);
  const top = clamp(firstNumber(props.render_height, props.height, props['building:height'], levels === undefined ? undefined : levels * 3.1) ?? 9, 0, 310);
  const base = clamp(firstNumber(props.render_min_height, props.min_height, props['building:min_height']) ?? 0, 0, top);
  return {top, base};
}

const hiddenBuilding = props => [true, 1, 'true'].includes(props.hide_3d);

function extrude(shape, depth, y) {
  const geometry = new THREE.ExtrudeGeometry(shape, {depth, bevelEnabled: false, steps: 1, curveSegments: 1});
  geometry.rotateX(-Math.PI / 2); geometry.translate(0, y, 0);
  return geometry;
}

async function buildTile(bytes, tile, context, signal, overviewTiles=[]) {
  checkAbort(signal);
  const parsed = new VectorTile(new Pbf(new Uint8Array(bytes)));
  const root = new THREE.Group();
  root.name = `city-tile-${tile.key}`;
  const originX = (tile.west - context.center[0]) * context.lonScale;
  const originZ = -(tile.north - context.center[1]) * METERS;
  root.position.set(originX, 0, originZ);
  root.userData = {key: tile.key, zoom: tile.z, buildings: 0, skipped: 0,places:[],placeGrid:new Map()};
  const project = p => [(p[0] - tile.west) * context.lonScale, -(p[1] - tile.north) * METERS];
  const rect = {
    minX: (Math.max(tile.west, URBAN.west) - tile.west) * context.lonScale,
    maxX: (Math.min(tile.east, URBAN.east) - tile.west) * context.lonScale,
    minZ: (tile.north - Math.min(tile.north, URBAN.north)) * METERS,
    maxZ: (tile.north - Math.max(tile.south, URBAN.south)) * METERS,
  };
  const batches = new Map(), roofs=[],roadSegments=[],walks=[],roofUnits=[],cars=[],benches=[];
  const treeAreas = [], obstacles = createSpatialIndex(), buildingIndex = createSpatialIndex();
  let architectureCount = 0;
  const owns=p=>p[0]>=rect.minX&&p[0]<rect.maxX&&p[1]>=rect.minZ&&p[1]<rect.maxZ;
  const canDecorate=p=>owns(p)&&!context.excludeBuilding?.(tile.west+p[0]/context.lonScale,tile.north-p[1]/METERS);
  const inBuilding = p => { for (const rings of buildingIndex.query(p)) if (inPolygon(p, rings)) return true; return false; };
  function propsMesh(name,geometry,items){if(!items.length){geometry.dispose();return}const m=new THREE.InstancedMesh(geometry,context.materials[name],items.length),o=new THREE.Object3D();items.forEach((p,i)=>{o.position.set(p.x,p.y,p.z);o.scale.set(p.sx??1,p.sy??1,p.sz??1);o.rotation.y=p.angle||0;o.updateMatrix();m.setMatrixAt(i,o.matrix);if(p.color!==undefined)m.setColorAt(i,new THREE.Color(p.color))});m.instanceMatrix.needsUpdate=true;m.name=`city-${name}`;m.userData.cityDetail=true;m.computeBoundingSphere();root.add(m);return m}
  function flush(name) {
    const batch = batches.get(name);
    if (!batch?.geometries.length) return;
    const merged = mergeGeometries(batch.geometries, false);
    for (const geometry of batch.geometries) geometry.dispose();
    batch.geometries = []; batch.vertices = 0;
    if (!merged) throw new Error(`Cannot merge city ${name} geometry`);
    merged.computeBoundingSphere();
    const mesh = new THREE.Mesh(merged, context.materials[name]);
    mesh.name = `city-${name}`;
    mesh.userData.cityBuilding = ['cream', 'cyan', 'sand', 'mint', 'roof', 'plinth'].includes(name);
    if (name === 'overview') mesh.raycast = () => {}; // Never advertise cartographic relief as a place.
    root.add(mesh);
  }
  function add(name, geometry) {
    if (!geometry) return;
    if (!geometry.getAttribute('position').count) { geometry.dispose(); return; }
    if (geometry.index) { const old = geometry; geometry = old.toNonIndexed(); old.dispose(); }
    geometry.deleteAttribute('uv');
    geometry.clearGroups();
    if (!batches.has(name)) batches.set(name, {geometries: [], vertices: 0});
    const batch = batches.get(name);
    batch.geometries.push(geometry);
    batch.vertices += geometry.getAttribute('position').count;
    if (batch.vertices >= BATCH_VERTICES) flush(name);
  }
  let sliceStart = performance.now(), processed = 0;
  async function checkpoint() {
    checkAbort(signal);
    if (performance.now() - sliceStart >= 8) {
      await yieldTask(); checkAbort(signal); sliceStart = performance.now();
    }
  }
  try {
    for (const layerName of ['landcover', 'landuse', 'park', 'water', 'transportation', 'building']) {
      const layer = parsed.layers[layerName];
      if (!layer) continue;
      for (let i = 0; i < layer.length; i++) {
        checkAbort(signal);
        if (++processed % 48 === 0 && performance.now() - sliceStart >= 8) {
          await yieldTask(); checkAbort(signal); sliceStart = performance.now();
        }
        const feature = layer.feature(i), props = feature.properties;
        try {
          if (layerName === 'building') {
            if(tile.z<13)continue; // One LOD owner: never overlap silhouettes and real footprints.
            if (feature.type !== 3 || hiddenBuilding(props)) continue;
            const {top, base} = buildingHeight(props);
            if(top<=base)continue;
            let added = 0;
            for (const rings of polygons(feature.toGeoJSON(tile.x, tile.y, tile.z).geometry)) {
              if (++processed % 32 === 0) await checkpoint();
              // OpenMapTiles groups many buildings into one MultiPolygon. Apply
              // ownership/exclusions to EACH building, not the feature's bbox.
              let west=Infinity,east=-Infinity,north=-Infinity,south=Infinity;
              for(const p of rings[0]){west=Math.min(west,p[0]);east=Math.max(east,p[0]);south=Math.min(south,p[1]);north=Math.max(north,p[1])}
              const lon=(west+east)/2,lat=(south+north)/2;
              if(lon<tile.west||lon>=tile.east||lat>tile.north||lat<=tile.south||!inUrban(lon,lat)||context.excludeBuilding?.(lon,lat,rings))continue;
              const style = footprintStyle(rings, feature.id, top, base);
              const shape = makeShape(rings, project);
              if (!shape) continue;
              const detailed = architectureCount < MAX_ARCHITECTURE && rings.reduce((n,r)=>n+r.length,0) <= 64;
              const plinth = detailed ? style.plinth : 0;
              add(style.material, extrude(shape, top - base - plinth, base + plinth + .55)); added++;
              if (detailed) {
                architectureCount++;
                if (plinth) add('plinth', extrude(shape, plinth, base + .55));
                add('roof', extrude(shape, style.roof, top + .55));
              }
              // Vector-tile IDs can represent a whole MultiPolygon, not one OSM
              // building. This view-local ID must not be used as a backend place ID.
              const place={id:`footprint-${style.hash.toString(16)}`,geometryKind:'footprint',geoRings:rings,title:String(props['name:ru']||props.name||'Здание').slice(0,120),lng:lon,lat,top:top+1.2,rings:rings.map(r=>r.map(project))};
              root.userData.places.push(place);
              const bounds = ringBounds(place.rings[0]);
              buildingIndex.insert(bounds, place.rings);
              const minX=Math.floor(bounds.minX/100),maxX=Math.floor(bounds.maxX/100),minZ=Math.floor(bounds.minZ/100),maxZ=Math.floor(bounds.maxZ/100);
              // Local 100 m cells bound marker-height/picking queries; very large
              // footprints use the slow list rather than an unbounded cell fanout.
              if((maxX-minX+1)*(maxZ-minZ+1)<=256)for(let x=minX;x<=maxX;x++)for(let z=minZ;z<=maxZ;z++){const key=`${x}:${z}`,list=root.userData.placeGrid.get(key)||[];list.push(place);root.userData.placeGrid.set(key,list);}
              else {const list=root.userData.placeGrid.get('large')||[];list.push(place);root.userData.placeGrid.set('large',list);}
              if(tile.z===14&&detailed){
                let points=rings[0].map(project);const area=points.reduce((s,p,i)=>{const q=points[(i+1)%points.length];return s+p[0]*q[1]-q[0]*p[1]},0);if(area<0)points=points.reverse();
                roofs.push({points,height:top-base,base:base+.55,hash:style.hash});
                const p=project([lon,lat]);
                const sx=Math.min(5,(bounds.maxX-bounds.minX)*.18),sz=Math.min(4,(bounds.maxZ-bounds.minZ)*.18);
                const fits=[[0,0],[-sx/2,-sz/2],[sx/2,-sz/2],[sx/2,sz/2],[-sx/2,sz/2]].every(([x,z])=>inPolygon([p[0]+x,p[1]+z],place.rings));
                if(style.roofUnit&&top>8&&sx>1&&sz>1&&fits)roofUnits.push({x:p[0],y:top+.55+style.roof+.65,z:p[1],sx,sy:1.3,sz});
              }
            }
            root.userData.buildings+=added;
            continue;
          }
          if (layerName === 'transportation') {
            if (feature.type !== 2 || props.brunnel === 'tunnel' || Number(props.layer) < 0) continue;
            const kind = props.class, sub = props.subclass;
            const pedestrian = ['path', 'pedestrian', 'track'].includes(kind) || ['footway', 'cycleway', 'steps'].includes(sub);
            const rail = kind === 'rail' || kind === 'transit';
            const widths = {motorway: 22, trunk: 19, primary: 16, secondary: 13, tertiary: 10, minor: 7, residential: 7, service: 4.5, path: 2.8, pedestrian: 5, track: 3, rail: 3, transit: 3};
            if (!(kind in widths)) continue;
            if (tile.z <= 12 && (pedestrian || kind === 'service')) continue;
            const width = Math.max(clamp(firstNumber(props.width) ?? widths[kind], 1.5, 36),tile.z<14&&!pedestrian&&!rail?(tile.z<=11?40:tile.z===12?26:16):0);
            const bridge = props.brunnel === 'bridge';
            const y = (bridge ? 5 + Math.max(0, Number(props.layer) || 0) * 3 : 0) + (pedestrian ? 0.36 : rail ? 0.49 : 0.60);
            const geometry = feature.toGeoJSON(tile.x, tile.y, tile.z).geometry;
            const lines = geometry.type === 'LineString' ? [geometry.coordinates] : geometry.type === 'MultiLineString' ? geometry.coordinates : [];
            for (const line of lines) {
              if (line.length < 2) continue;
              if(tile.z>=13&&!bridge){
                const points=line.map(project),pad=width/2+5;
                for(let j=1;j<points.length;j++){
                  const a=points[j-1],b=points[j],box=ringBounds([a,b]);
                  obstacles.insert({minX:box.minX-pad,maxX:box.maxX+pad,minZ:box.minZ-pad,maxZ:box.maxZ+pad},{a,b,pad});
                }
              }
              if (!pedestrian && !rail) add('roadEdge', roadStrip(line, project, width + 1.0, y - 0.20, rect));
              const minor=pedestrian&&width<4&&!props.name;
              add(pedestrian ? minor?'minorPath':'path' : rail ? 'rail' : 'road', roadStrip(line, project, width, y, rect));
              if(tile.z===14&&!bridge&&!rail){
                const pts=line.map(project);
                for(let j=1;j<pts.length;j++){
                  const a=pts[j-1],b=pts[j],length=Math.hypot(b[0]-a[0],b[1]-a[1]);if(length<8)continue;
                  const ux=(b[0]-a[0])/length,uz=(b[1]-a[1])/length,angle=Math.atan2(ux,uz);
                  roadSegments.push({points:[a,b],width,pedestrian});
                  if(pedestrian&&owns(a)&&owns(b)){walks.push([a,b]);if(benches.length<60&&length>25){const p=[a[0]-uz*(width/2+1.4),a[1]+ux*(width/2+1.4)];if(canDecorate(p))benches.push({x:p[0],y:.85,z:p[1],sx:3,sy:.45,sz:1,angle})}}
                  if(!pedestrian&&width>=7){
                    for(let d=6;d<length-4;d+=15){const p=[a[0]+ux*d,a[1]+uz*d];if(owns(p))add('marking',new THREE.BoxGeometry(.45,.05,5).rotateY(angle).translate(p[0],y+.035,p[1]))}
                    if(cars.length<65&&length>28){const t=.25+(cars.length%5)*.1,p=[a[0]+ux*length*t-uz*width*.23,a[1]+uz*length*t+ux*width*.23];if(owns(p))cars.push({x:p[0],y:1.15,z:p[1],sx:1.9,sy:1.2,sz:4.3,angle,color:[CITY_PALETTE.ivory[0],CITY_PALETTE.sage[0],CITY_PALETTE.dark[0],CITY_PALETTE.sand[0]][cars.length%4]})}
                  }
                }
              }
            }
            continue;
          }
          if (feature.type !== 3) continue;
          if (layerName === 'water' && props.brunnel === 'tunnel') continue;
          if (layerName === 'landcover' && !['wood', 'grass', 'farmland', 'wetland'].includes(props.class)) continue;
          const urban=layerName==='landuse'&&['residential','commercial','industrial','retail','construction','railway'].includes(props.class);
          if (layerName === 'landuse' && !urban&&!['park', 'forest', 'grass', 'meadow', 'recreation_ground', 'village_green', 'garden', 'allotments', 'cemetery', 'pitch', 'wood'].includes(props.class)) continue;
          const elevation = {landcover: -0.12, landuse: -0.08, park: -0.04, water: 0.04}[layerName];
          for (const rings of polygons(feature.toGeoJSON(tile.x, tile.y, tile.z).geometry)){
            add(urban?'urban':layerName, flatPolygon(rings, project, elevation, rect));
            if(tile.z>=13){
              const projected=rings.map(r=>r.map(project));
              if(layerName==='water')obstacles.insert(ringBounds(projected[0]),{rings:projected});
              const wooded=['wood','forest','park','garden','village_green'].includes(props.class)||layerName==='park';
              if(!urban&&wooded&&['park','landuse','landcover'].includes(layerName))treeAreas.push(projected);
            }
          }
        } catch (error) {
          if (signal.aborted || error.name === 'AbortError') throw error;
          root.userData.skipped++; // One malformed feature must not discard a whole city tile.
        }
      }
    }
    if(tile.z<13){
      await yieldTask();checkAbort(signal);
      let count=0,vertices=0;
      // z12 uses its four real z13 children. Preserve orientation, concavities
      // and courtyards in low relief. z10/11 show landuse/roads only, no invented
      // buildings. This cartographic layer has no place IDs or picking targets.
      for(const source of overviewTiles){
        const layer=new VectorTile(new Pbf(new Uint8Array(source.bytes))).layers.building;
        if(!layer)continue;
        for(let i=0;i<layer.length&&count<MAX_OVERVIEW&&vertices<MAX_OVERVIEW_VERTICES;i++){
          await checkpoint();
          try {
            const f=layer.feature(i);if(f.type!==3||hiddenBuilding(f.properties))continue;
            const {top,base}=buildingHeight(f.properties);
            if(top<=base)continue;
            for(const rings of polygons(f.toGeoJSON(source.x,source.y,source.z).geometry)){
              if(++processed%32===0)await checkpoint();
              if(count>=MAX_OVERVIEW||vertices>=MAX_OVERVIEW_VERTICES)break;
              const box=ringBounds(rings[0]),lon=(box.minX+box.maxX)/2,lat=(box.minZ+box.maxZ)/2,p=project([lon,lat]);
              if(lon<source.west||lon>=source.east||lat>source.north||lat<=source.south||!canDecorate(p))continue;
              // A conservative preflight bounds triangulation work on malformed
              // or extremely complex features without replacing them by boxes.
              const points=rings.reduce((n,r)=>n+r.length,0);
              if(points>512||vertices+points*18>MAX_OVERVIEW_VERTICES)continue;
              const shape=makeShape(rings,project);if(!shape)continue;
              const geometry=extrude(shape,overviewRelief(top,base),.55);
              vertices+=geometry.getAttribute('position').count;
              add('overview',geometry);count++;
            }
          } catch(error) {
            if(signal.aborted||error.name==='AbortError')throw error;
            root.userData.skipped++;
          }
        }
      }
      root.userData.overviewBlocks=count;
    }
    for (const name of batches.keys()) flush(name);
    const trees=[];
    for(const candidate of treeCandidates({areas:treeAreas,rect,origin:[originX,originZ]})){
      if(++processed%128===0)await checkpoint();
      if(!candidate)continue;
      const p=candidate.point;
      if(!canDecorate(p)||inBuilding(p))continue;
      let blocked=false;
      for(const obstacle of obstacles.query(p)){
        if(obstacle.rings?inPolygon(p,obstacle.rings):distanceSegment(p,obstacle.a,obstacle.b)<obstacle.pad){blocked=true;break;}
      }
      if(!blocked)trees.push(candidate);
    }
    const safeTrees=trees.sort((a,b)=>a.rank-b.rank).slice(0,MAX_TREES);
    root.userData.trees=safeTrees.length;root.userData.architectureDetails=architectureCount;
    propsMesh('trunk',new THREE.CylinderGeometry(.32,.42,4.8,5),safeTrees.map(({point:p})=>({x:p[0],y:2.65,z:p[1]})));
    const foliage=propsMesh('foliage',new THREE.SphereGeometry(1,7,5),safeTrees.map(({point:p,rank})=>({x:p[0],y:6.8+rank,z:p[1],sx:4.3+rank,sy:4.0+rank,sz:4.0+rank})));
    if(foliage){foliage.userData.cityDetail=false;foliage.userData.cityCanopy=true;}
    propsMesh('roofUnit',new THREE.BoxGeometry(1,1,1),roofUnits);
    propsMesh('car',new THREE.BoxGeometry(1,1,1),cars);
    propsMesh('carGlass',new THREE.BoxGeometry(1,1,1),cars.map(p=>({...p,y:1.85,sx:1.65,sy:.55,sz:2.2,color:undefined})));
    propsMesh('bench',new THREE.BoxGeometry(1,1,1),benches);
    if(tile.z===14){
      const urban=createUrbanDetails({buildings:roofs.filter(r=>r.hash%7===0).slice(0,12),roads:roadSegments,canPlace:owns,maxLamps:64});
      urban.setNight(context.night);urban.group.userData.cityDetail=true;root.add(urban.group);root.userData.urban=urban;
      const paths=walks.filter(([a,b])=>!inBuilding(a)&&!inBuilding(b));
      const crowd=createPedestrians(paths,{count:Math.min(12,paths.length),seed:tile.x*31+tile.y});crowd.group.userData.cityPeople=true;root.add(crowd.group);root.userData.crowd=crowd;
    }
    checkAbort(signal);
    return root;
  } catch (error) {
    for (const batch of batches.values()) for (const geometry of batch.geometries) geometry.dispose();
    disposeTile(root);
    throw error;
  }
}

/**
 * Stream OpenFreeMap data as Three.js meshes in local meters (x east, z south).
 * No renderer, DOM, base plane, lights, MapLibre, or runtime CDN dependency.
 *
 * update(target, distance): target is in the same local-meter coordinate system.
 * ready resolves once TileJSON is available, without waiting for tile meshes;
 * it also resolves after terminal TileJSON failure or destroy. See onStatus.
 * onStatus counts loaded/failed/pending for the current desired set; buildings
 * counts displayed features, including any previous-LOD fallback still visible.
 * Display group.userData.attribution in the caller's map UI.
 * @param {{center: [number, number], onStatus?: Function, excludeBuilding?: Function}} options
 */
export function createCityStream({center, onStatus, excludeBuilding} = {}) {
  if (!Array.isArray(center) || center.length !== 2 || !center.every(Number.isFinite) || Math.abs(center[1]) > 85 || Math.abs(center[0]) > 180) {
    throw new TypeError('createCityStream requires center: [longitude, latitude]');
  }
  if (excludeBuilding !== undefined && typeof excludeBuilding !== 'function') throw new TypeError('excludeBuilding must be a function');
  const origin = [...center], lonScale = METERS * Math.cos(origin[1] * RAD);
  const group = new THREE.Group(), materials = makeMaterials();
  group.name = 'astana-city-stream';
  group.userData.attribution = 'OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors';
  group.userData.bounds = {...URBAN};
  const context = {center: origin, lonScale, materials, excludeBuilding,night:false};
  const cache = new Map(), visible = new Set();
  let desired = new Map(), zoom = 14, distance = 1500, destroyed = false;
  let template = null, scheme = 'xyz', active = 0, touch = 0, signature = '';
  let retryTimer = null, initTimer = null, metadataController = null, initAttempts = 0, metadataFailed = false;
  let buildTail = Promise.resolve(), statusSignature = '', readyDone = false;
  let resolveReady;
  const ready = new Promise(resolve => { resolveReady = resolve; });
  function settleReady() { if (!readyDone) { readyDone = true; resolveReady(); } }
  function status(message) {
    if (destroyed || typeof onStatus !== 'function') return;
    let loaded = 0, failed = 0, buildings = 0;
    for (const key of desired.keys()) {
      const record = cache.get(key);
      if (record?.state === 'ready') loaded++;
      else if (record?.state === 'failed') failed++;
    }
    const pending = metadataFailed ? 0 : desired.size - loaded - failed;
    for (const key of visible) buildings += cache.get(key)?.root?.userData.buildings || 0;
    const result = {
      loaded, pending, failed: metadataFailed ? Math.max(1, desired.size) : failed, zoom, buildings,
      message: message || (metadataFailed ? 'Map source unavailable; reload to retry.' : !template ? 'Loading OpenFreeMap TileJSON…' : pending ? `Streaming Astana · z${zoom} · ${loaded}/${desired.size} tiles` : failed ? 'Some map tiles are unavailable; keeping available coverage.' : `Astana ready · z${zoom} · ${buildings} buildings`),
    };
    const nextSignature = JSON.stringify(result);
    if (nextSignature === statusSignature) return;
    statusSignature = nextSignature;
    try { onStatus(result); } catch (error) { console.warn('City stream onStatus callback:', error); }
  }
  function removeRecord(record) {
    if (cache.get(record.key) !== record) return;
    record.controller?.abort();
    visible.delete(record.key);
    disposeTile(record.root);
    cache.delete(record.key);
  }
  function makeRoom() {
    if (cache.size < MAX_CACHE) return true;
    const candidates = [...cache.values()].filter(r => !desired.has(r.key) && !visible.has(r.key) && r.state !== 'loading').sort((a, b) => a.used - b.used);
    if (candidates.length) removeRecord(candidates[0]);
    return cache.size < MAX_CACHE;
  }
  function detailVisibility(root) {
    const near=distance<3000;
    root.traverse(object => {
      if(object.userData.cityDetail)object.visible=near;
      if(object.userData.cityPeople)object.visible=distance<1000;
      if(object.userData.cityCanopy)object.visible=distance<12500;
      if(object.userData.cityBuilding){object.castShadow=near&&root.userData.zoom===14;object.receiveShadow=near;}
      else if(object.isMesh)object.receiveShadow=near;
    });
  }
  function show(record) {
    visible.add(record.key); record.used = ++touch;
    if (record.root.parent !== group) group.add(record.root);
    detailVisibility(record.root);
  }
  function hide(key) { visible.delete(key);const record=cache.get(key);record?.root?.removeFromParent();if(record?.z===14&&!desired.has(key))removeRecord(record); }
  function reconcile() {
    // Retain old coverage only where the requested footprint still intersects it.
    for (const key of [...visible]) {
      const record = cache.get(key);
      if (!record || ![...desired.values()].some(tile => overlaps(tile, record))) hide(key);
    }
    for (const tile of desired.values()) {
      const record = cache.get(tile.key);
      if (record?.state !== 'ready') continue;
      const conflicts = [...visible].map(key => cache.get(key)).filter(r => overlaps(r, tile) && r.key !== tile.key);
      const parent = conflicts.find(r => r.z < tile.z);
      if (parent) {
        const replacements = [...desired.values()].filter(t => overlaps(t, parent));
        if (!replacements.every(t => cache.get(t.key)?.state === 'ready')) continue;
        hide(parent.key);
        for (const replacement of replacements) show(cache.get(replacement.key));
      } else {
        for (const conflict of conflicts) hide(conflict.key);
        show(record);
      }
    }
  }
  function tileURL(tile) {
    const y = scheme === 'tms' ? 2 ** tile.z - 1 - tile.y : tile.y;
    return template.replaceAll('{z}', String(tile.z)).replaceAll('{x}', String(tile.x)).replaceAll('{y}', String(y));
  }
  async function loadTile(record) {
    active++;
    record.state = 'loading'; record.attempts++; record.controller = new AbortController();
    const controller = record.controller, signal = controller.signal;
    let timedOut = false;
    const timeout = setTimeout(() => { timedOut = true; controller.abort(); }, TIMEOUT_MS);
    try {
      const response = await fetch(tileURL(record), {signal, mode: 'cors', credentials: 'omit'});
      if (!response.ok) {
        const error = new Error(`Tile HTTP ${response.status}`);
        error.permanent = [400, 401, 403, 404, 410].includes(response.status);
        throw error;
      }
      const bytes = await response.arrayBuffer();
      const overviewTiles=[];
      if(record.z===12){
        // Still at most MAX_REQUESTS concurrent HTTP calls: children are serial
        // within each record, cancelled with it, and share its timeout.
        for(let dx=0;dx<2;dx++)for(let dy=0;dy<2;dy++){
          const child=descriptor(record.x*2+dx,record.y*2+dy,13);
          const r=await fetch(tileURL(child),{signal,mode:'cors',credentials:'omit'});
          if(!r.ok)throw new Error(`Overview tile HTTP ${r.status}`);
          const data=await r.arrayBuffer();if(data.byteLength>4*1024*1024)throw new Error('Overview tile too large');
          overviewTiles.push({...child,bytes:data});
        }
      }
      clearTimeout(timeout);
      checkAbort(signal);
      if (!desired.has(record.key) || destroyed) throw abortError();
      // Serialize CPU mesh builds; at most four fetched buffers can wait here.
      const build = buildTail.then(() => buildTile(bytes, record, context, signal,overviewTiles));
      buildTail = build.catch(() => {});
      const root = await build;
      if (destroyed || signal.aborted || !desired.has(record.key) || cache.get(record.key) !== record) { disposeTile(root); throw abortError(); }
      record.root = root; record.state = 'ready'; record.used = ++touch;
    } catch (error) {
      if (destroyed || !desired.has(record.key) || (signal.aborted && !timedOut)) {
        removeRecord(record);
      } else {
        record.error = error.message;
        record.state = error.permanent || record.attempts >= MAX_ATTEMPTS ? 'failed' : 'retry';
        record.retryAt = Date.now() + 700 * 2 ** (record.attempts - 1);
      }
    } finally {
      clearTimeout(timeout);
      record.controller = null; active--;
      if (!destroyed) { reconcile(); status(); pump(); }
    }
  }
  function pump() {
    if (destroyed || !template) return;
    clearTimeout(retryTimer); retryTimer = null;
    let nextRetry = Infinity;
    for (const tile of desired.values()) {
      let record = cache.get(tile.key);
      if (record?.state === 'retry' && record.retryAt > Date.now()) {
        nextRetry = Math.min(nextRetry, record.retryAt); continue;
      }
      if (record && record.state !== 'retry') continue;
      if (active >= MAX_REQUESTS) break;
      if (!record) {
        if (!makeRoom()) break;
        record = {...tile, attempts: 0, used: ++touch, state: 'queued', root: null, controller: null};
        cache.set(tile.key, record);
      }
      void loadTile(record);
    }
    if (Number.isFinite(nextRetry)) retryTimer = setTimeout(pump, Math.max(1, nextRetry - Date.now()));
  }
  function update(target, cameraDistance) {
    if (destroyed) return;
    if (!target || !Number.isFinite(target.x) || !Number.isFinite(target.z)) return;
    distance = Number.isFinite(cameraDistance) ? Math.max(1, cameraDistance) : distance;
    const lon = clamp(origin[0] + target.x / lonScale, URBAN.west, URBAN.east);
    const lat = clamp(origin[1] - target.z / METERS, URBAN.south, URBAN.north);
    const nextZoom = chooseZoom(distance, zoom);
    const tiles = desiredTiles(lon, lat, distance, nextZoom, lonScale);
    const nextSignature = tiles.map(tile => tile.key).join('|');
    for (const key of visible) detailVisibility(cache.get(key).root);
    zoom = nextZoom;
    if (signature === nextSignature) return;
    signature = nextSignature;
    desired = new Map(tiles.map(tile => [tile.key, tile]));
    for (const record of cache.values()) {
      if (desired.has(record.key)) record.used = ++touch;
      else if (record.state === 'loading') record.controller?.abort();
    }
    reconcile(); pump(); status();
  }
  let isNight=false,seasonTheme='default';
  function setTheme(value){seasonTheme=['winter','newyear'].includes(value)?value:'default';setNight(isNight)}
  function setNight(value) {
    if (destroyed) return;
    const night = Boolean(value);isNight=night;context.night=night;
    for (const [name, material] of Object.entries(materials)) {
      material.color.setHex(night ? material.userData.night : material.userData.day);
      if(seasonTheme!=='default'&&['landcover','landuse','park'].includes(name))material.color.setHex(night?0x93a9bf:0xe2ebee);
      if(seasonTheme!=='default'&&name==='path')material.color.setHex(night?0x8298ac:0xc8d7df);
      if(seasonTheme!=='default'&&name==='foliage')material.color.setHex(night?0x9fb6c2:0xe2ecec);
      material.emissive.setHex(night && name==='water' ? 0x092b3b : 0x000000);
      material.emissiveIntensity = 0.15;
      material.userData.facade?.setNight(night);material.userData.facade?.setTheme(seasonTheme);
    }
    for(const record of cache.values())record.root?.userData.urban?.setNight(night);
  }
  function animate(time,reducedMotion=false){if(destroyed)return;for(const key of visible){const crowd=cache.get(key)?.root?.userData.crowd;if(crowd?.group.visible)crowd.update(time,reducedMotion)}}
  function coverageAt(x,z){const lng=origin[0]+x/lonScale,lat=origin[1]-z/METERS;return [...visible].some(key=>{const r=cache.get(key);return r&&lng>=r.west&&lng<r.east&&lat<=r.north&&lat>r.south;});}
  function placeAt(x,z){
    let found=null;
    for(const key of visible){
      const root=cache.get(key)?.root;if(!root)continue;
      const p=[x-root.position.x,z-root.position.z],grid=root.userData.placeGrid,candidates=new Set(grid.get('large')||[]);
      for(let gx=Math.floor((p[0]-.35)/100);gx<=Math.floor((p[0]+.35)/100);gx++)for(let gz=Math.floor((p[1]-.35)/100);gz<=Math.floor((p[1]+.35)/100);gz++)for(const b of grid.get(`${gx}:${gz}`)||[])candidates.add(b);
      for(const b of candidates)if((!found||b.top>found.top)&&onOrInPolygon(p,b.rings))found=b;
    }
    return found;
  }
  function stats(){return {cityTiles:visible.size,cityCache:cache.size,cityRequests:active,cityBuildings:[...visible].reduce((n,key)=>n+(cache.get(key)?.root?.userData.buildings||0),0),cityOverviewBlocks:[...visible].reduce((n,key)=>n+(cache.get(key)?.root?.userData.overviewBlocks||0),0),cityZoom:zoom};}
  async function initialize() {
    if (destroyed) return;
    initAttempts++;
    metadataController = new AbortController();
    const controller = metadataController;
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const response = await fetch(TILE_JSON, {signal: controller.signal, mode: 'cors', credentials: 'omit'});
      if (!response.ok) throw new Error(`TileJSON HTTP ${response.status}`);
      const json = await response.json();
      if (destroyed) return;
      const actualTemplate = json.tiles?.[0];
      if (typeof actualTemplate !== 'string' || !['{z}', '{x}', '{y}'].every(token => actualTemplate.includes(token))) throw new Error('TileJSON has no supported XYZ template');
      const url = new URL(actualTemplate, TILE_JSON);
      if (url.protocol !== 'https:') throw new Error('TileJSON requires an HTTPS tile source');
      // Use the advertised version, never invent /latest/{z}/{x}/{y}.pbf URLs.
      template = actualTemplate.startsWith('https://') ? actualTemplate : url.href.replaceAll('%7B', '{').replaceAll('%7D', '}');
      scheme = json.scheme === 'tms' ? 'tms' : 'xyz';
      group.userData.attribution = json.attribution || group.userData.attribution;
      group.userData.tileTemplate = template;
      group.userData.source = TILE_JSON;
      settleReady();
      pump(); status();
    } catch (error) {
      if (destroyed) return;
      if (initAttempts < MAX_ATTEMPTS) {
        status(`TileJSON unavailable; retrying (${initAttempts}/${MAX_ATTEMPTS})…`);
        initTimer = setTimeout(initialize, 700 * 2 ** (initAttempts - 1));
      } else {
        metadataFailed = true;
        status(`Map source unavailable: ${error.message}`); settleReady();
      }
    } finally { clearTimeout(timeout); }
  }
  function destroy() {
    if (destroyed) return;
    destroyed = true;
    clearTimeout(retryTimer); clearTimeout(initTimer);
    metadataController?.abort();
    for (const record of [...cache.values()]) removeRecord(record);
    for (const material of Object.values(materials)){material.userData.facade?.dispose?.();material.dispose()}
    desired.clear(); visible.clear(); group.clear(); group.removeFromParent();
    settleReady();
  }
  update(new THREE.Vector3(), distance);
  void initialize();
  return {group, update, animate, setNight, setTheme, destroy, ready,coverageAt,placeAt,stats};
}
