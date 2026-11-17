// Manual, offline-first OSM audit importer. Never imported by runtime/build.
// Usage: node import-places.mjs --input /path/to/overpass.json
//        node import-places.mjs --refresh [--endpoint https://.../api/interpreter]
// Only the explicitly reviewed public landmark objects below can be imported.
// Do not use proximity, a centroid, a bounding box or a name search as a match.
import {readFile, writeFile, rename, unlink} from 'node:fs/promises';
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';

const ROOT = new URL('./', import.meta.url);
const MAX_BYTES = 12_000_000;
const METERS = 111320;
const KINDS = new Set(['main', 'yes', 'service', 'exit', 'emergency', 'staircase', 'home']);
const ACCESS = new Set(['yes', 'private', 'customers', 'no', 'permissive', 'unknown']);
const WHEELCHAIR = new Set(['yes', 'no', 'limited']);
const refOf = e => `${e.type}/${e.id}`;
const osmUrl = ref => `https://www.openstreetmap.org/${ref}`;
const same = (a, b) => a[0] === b[0] && a[1] === b[1];
const coordinate = p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite)
  && p[0] >= 71.38 && p[0] <= 71.49 && p[1] >= 51.05 && p[1] <= 51.15;

// Additional parts from the ORIGINAL eight landmark mappings. These are not
// proximity matches: current typed way rings must equal the corresponding
// reviewed model-source rings, including every edge. Changed parts fail closed.
export const VERIFIED_PARTS = Object.freeze({
  baiterek: ['way/230401644'],
  adp: ['way/1373723368', 'way/1373723370', 'way/1373723369', 'way/1373723363',
    'way/1373723364', 'way/1373723365', 'way/1373723366', 'way/1373723367'],
  ansar: ['way/258752611'],
  beijing: ['way/460047238'],
  moscow: ['way/258345462', 'way/230403257'],
  petersburg: ['way/230403283', 'way/258351821'],
  keruen: ['way/258739309'],
  keruencity: ['way/501394752', 'way/501394749', 'way/501394746'],
  severnoe: ['way/230358852', 'way/230358853', 'way/230358851'],
  izumrud: ['way/258735490', 'way/230361899', 'way/258735487', 'way/258735488'],
  triumph: ['way/460703795', 'way/460703796', 'way/460703789', 'way/460703791', 'way/460703792', 'way/460703790', 'way/460703793', 'way/460703794'],
  highvill: ['way/1385141126', 'way/1385141134', 'way/1385141132', 'way/1385141125', 'way/1385141133', 'way/1385141124', 'way/1385141131'],
});
// Reviewed model-source rings: the Baiterek snapshot plus malls/residential towers fetched by id.
const legacyParts = new Map(['./source/baiterek.json', './source/city-landmarks-osm.json']
  .flatMap(file => JSON.parse(readFileSync(new URL(file, ROOT), 'utf8')).buildings.map(b => [`way/${b.id}`, b.points])));
export const MODEL_COUNT = 31;
function sameRingEdges(a, b) {
  const open = ring => ring.length > 1 && same(ring[0], ring.at(-1)) ? ring.slice(0, -1) : ring;
  a = open(a); b = open(b);
  if (a.length !== b.length) return false;
  const start = b.findIndex(p => same(a[0], p));
  return start >= 0 && [1, -1].some(direction => a.every((p, i) => same(p, b[(start + direction * i + b.length) % b.length])));
}

// Updated only after inspecting current typed OSM objects and their geometry.
// name is an identity guard, not a search. Missing/renamed objects need review.
export const REVIEWED_MATCHES = Object.freeze({
  baiterek: {refs: ['way/230401645'], name: /байтерек|бәйтерек|baiterek/i,
    notes: 'Named monument visitor building plus separately verified tower part way/230401644 (exact model-source ring). Model plinth radius 28 m is not an OSM footprint; spherical/tower yaw is not diagnostic.'},
  akorda: {refs: ['way/166198046'], name: /ақорда|акорда|ak.?orda/i,
    notes: 'Named Ak Orda office/palace building, not grounds or park. Model body 102 x 62 m, yaw -1.823 rad; facade/portico detail remains stylized.'},
  arena: {refs: ['way/166223262'], tags: {building: 'stadium', 'addr:housenumber': '48', 'addr:street': 'Тұран даңғылы'},
    notes: 'Existing typed stadium ref verified as stadium at Turan 48; building currently has no name. Not the similarly named LRT station. Model plinth 260 x 210 m, yaw 76.59927 deg; authored portals are not mapped entrance evidence.'},
  adp: {refs: ['way/762780007'], name: /abu.*dhabi|абу.*даби/i, review: true,
    urls: ['https://www.openstreetmap.org/way/1373723368'],
    notes: 'Named Abu Dhabi Plaza podium plus eight verified building:part ways 1373723363..3370, exact model-source rings. Podium alone misses tower anchor; parts are retained separately. Review concerns model height 313 m versus OSM way/1373723368 height=382, not identity of these footprints.'},
  ansar: {refs: ['way/230403254'], name: /ансар|ansar/i,
    notes: 'Named Ansar commercial podium; older way/258752611 is its building:part. Model uses geographic source polygons, not a rotated generic box; roof/facade heights are not surveyed.'},
  beijing: {refs: ['way/230403277'], name: /beijing|пекин|soluxe/i,
    notes: 'Named hotel building; older way/460047238 is building:part. Model body follows source polygons; decorative roof overhang/height is not a surveyed footprint.'},
  moscow: {refs: ['way/1530050672'], name: /москва|moscow/i,
    notes: 'Named Moscow office outline. Older ways 258345462 and 230403257 are building:parts, not independent buildings. Source-derived model body; roof embellishment is not boundary evidence.'},
  petersburg: {refs: ['way/1530050670'], name: /петербург|petersburg/i,
    notes: 'Named Saint Petersburg office outline. Older ways 230403283 and 258351821 are building:parts. Source-derived model body; roof embellishment is not boundary evidence.'},
  library: {refs: ['way/230358855'], name: /библиотек|кітапхана|library/i,
    notes: 'Named National Academic Library building. Model body follows its geographic polygon; added stairs are not entrance-node evidence.'},
  talan: {refs: ['relation/8205731'], name: /talan|талан/i,
    notes: 'Named commercial building multipolygon assembled from four open outer member ways. Model podium 100 x 64 m at yaw 0 is simplified; actual rotated outline is retained, not the complex grounds.'},
  khan: {refs: ['way/460703779'], name: /хан.*шатыр|khan.*shatyr/i,
    notes: 'Named Khan Shatyr building. Model circular plinth diameter 246 m is larger than OSM building outline; asymmetric tent shape/yaw is stylized. Two entrance=secondary nodes use kind unknown, not main.'},
  expo: {refs: ['way/454857501'], name: /alem\.ai/i, review: true,
    urls: ['https://qazexpocongress.kz/ru/nur-alem/'],
    notes: 'Current building is named Alem.ai (office), formerly Nur Alem; operator confirms reuse of this sphere. Manifest title still EXPO/Nur Alem: content review required. Model sphere diameter 80 m/plinth 90 m; current outline includes non-circular extensions, yaw unverified.'},
  mega: {refs: ['way/499328514'], name: /mega.*silk|мега/i,
    notes: 'Named Mega Silk Way building, not shopping grounds. Model roof 500 x 146 m, yaw 0.15 rad; mapped irregular outline is retained and not replaced with the model rectangle.'},
  museum: {refs: ['way/1381418247'], tags: {building: 'yes', tourism: 'museum', 'addr:housenumber': '54'},
    identity: {ref: 'node/4894937300', name: /national museum|национальный музей/i}, review: true,
    notes: 'Museum building at Tauelsizdik 54 contains the named National Museum POI node/4894937300; NOT National Museum LRT way/1484667030. Manifest anchor is west of building; asymmetric block model/yaw -0.1 rad needs placement and size review.'},
  keruen: {refs: ['way/108096984'], name: /керуен|keruen/i,
    notes: 'Named Keruen shopping centre (Dostyk 9) plus its building:part. Stylised mall: glazed ground floor, sign band, roof plant; 5 m storeys from OSM levels.'},
  asiapark: {refs: ['way/239713764'], name: /asia.?park|азия/i,
    notes: 'Named Asia Park retail building. Stylised mall from the mapped outline; 5 m storeys from OSM levels.'},
  keruencity: {refs: ['way/209448027'], name: /keruen.?city|керуен/i,
    notes: 'Named KeruenCity retail building (Korgalzhyn hwy 1) plus three building:parts. Stylised mall; heights from OSM height/levels.'},
  astanamall: {refs: ['way/240502747'], name: /astana.?mall|астана.?молл/i,
    notes: 'Named Astana Mall retail building (Tauelsizdik 34). Stylised mall from the mapped outline.'},
  eurasia: {refs: ['way/1089059381', 'way/475995069', 'way/240592139'], name: /еуразия|евразия|eurasia/i,
    notes: 'Named Eurasia, Eurasia-2 and Eurasia-3 retail buildings, kept as three separate mapped buildings.'},
  severnoe: {refs: ['way/230358850'], name: /северное|солтүстік|northern/i,
    notes: 'Named Northern Lights complex podium plus its three named tower building:parts (32/37/42 levels). Stylised glass towers with crowns.'},
  izumrud: {refs: ['way/289011352', 'way/289011351'], name: /изумруд|emerald/i,
    notes: 'Named Emerald Quarter outline and podium plus four tower building:parts with OSM heights (163-210 m). Green glass is stylised.'},
  triumph: {refs: ['way/240909255'], name: /салтанат|триумф|triumph/i,
    notes: 'Named Triumph of Astana (Astana Saltanaty) outline plus eight stepped building:parts. Spire height is stylised to the published ~142 m total.'},
  highvill: {refs: ['way/425291398', 'way/963622735', 'way/425291399', 'way/963623283', 'way/240860162'], name: /хайвил|highvill/i,
    notes: 'Named Highvill Astana blocks B, D, E1, E2 and G plus seven tower building:parts. Stylised; unnamed podium way/1385141127 is not imported.'},
  europecity: {refs: ['way/1161754596', 'way/1161754597', 'way/1161754598', 'way/1161754599', 'way/1161754600', 'way/1161754601'],
    tags: {building: 'apartments', 'addr:street': 'Ақмешіт көшесі', 'addr:housenumber': '1'},
    notes: 'Europe City (Orda Invest): six unnamed apartment towers at Akmeshit 1, identified by address. Levels 22/25 from OSM; one tower without levels uses 22 (listing: 18-25).'},
  lavie: {refs: ['way/626933554'], name: /la ?vie/i,
    notes: 'La Vie (Orda Invest), Koshkarbayev 1: OSM still maps a named construction site with seven building=construction outlines. Model uses those outlines at an estimated 15 storeys (listing: 12-18).'},
  dara: {refs: ['way/1531639893'], name: /dara/i,
    notes: 'Dara Residence (Sensata), Saraishyk 34a: OSM maps only the named residential area. Model uses two NSDI building outlines at estimated 14 and 22 storeys (listing: 2-22).'},
  hazret: {refs: ['way/240860325'], name: /хазрет|әзірет|hazrat/i,
    notes: 'Named Hazrat Sultan mosque building, not grounds. Model main body 98 x 88 m, yaw 2.25 rad; minarets/stairs extend beyond that body. Architecture remains stylized.'},
  grandmosque: {refs: ['relation/18901938'], name: /мешіт|мечеть|mosque/i,
    notes: 'Named Nursultan/Grand Mosque building multipolygon; courtyard hole retained. Model prayer-hall anchor is intentionally not whole-building center; model slab 164 x 260 m shifted local Z=62, yaw 2.27 rad. Inner-ring doors are courtyard entrances, not invented public main doors.'},
  palace: {refs: ['way/166197368'], name: /бейбітшілік|peace.*reconciliation/i,
    notes: 'Named Palace of Peace and Reconciliation building. Model pyramid base 62 x 62 m plus 78 m terrace, yaw 0; OSM description distinguishes 62 m roof from 77 m total including 15 m underfloor, not directly comparable to model height 63 m.'},
  kazakheli: {refs: ['way/506032169'], tags: {building: 'yes', historic: 'monument'},
    identity: {ref: 'node/2681623412', name: /қазақ елі|kazak eli/i}, review: true,
    notes: 'Small monument building contains named Kazak Eli memorial node/2681623412. Only this base footprint is verified, NOT plaza or semicircular colonnade. OSM height=17 differs from model 91 m monument; full-composition size/yaw remain unverified.'},
  independence: {refs: ['way/240873345'], name: /тәуелсіздік|independence|независимости/i,
    notes: 'Named Independence Palace building. Model body 123 x 149 m and yaw -0.247 rad; terrace/stairs are stylized, and only mapped outline is retained.'},
});

function fail(message) { throw new Error(message); }
function nodeCoordinate(node) {
  const p = [node?.lon, node?.lat];
  if (node?.type !== 'node' || !coordinate(p)) fail('Missing/out-of-bounds member node');
  return p;
}

// -1 outside, 0 boundary, 1 inside, including closed rings.
export function ringLocation(p, ring) {
  let inside = false;
  for (let i = 1; i < ring.length; i++) {
    const a = ring[i - 1], b = ring[i];
    const cross = (p[0] - a[0]) * (b[1] - a[1]) - (p[1] - a[1]) * (b[0] - a[0]);
    if (Math.abs(cross) < 1e-13 && p[0] >= Math.min(a[0], b[0]) - 1e-12
      && p[0] <= Math.max(a[0], b[0]) + 1e-12 && p[1] >= Math.min(a[1], b[1]) - 1e-12
      && p[1] <= Math.max(a[1], b[1]) + 1e-12) return 0;
    if ((a[1] > p[1]) !== (b[1] > p[1])
      && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
  }
  return inside ? 1 : -1;
}

const inPolygon = (p, rings) => ringLocation(p, rings[0]) >= 0
  && !rings.slice(1).some(r => ringLocation(p, r) >= 0);

const cross = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
function segmentsMeet(a, b, c, d) {
  if (Math.max(a[0], b[0]) < Math.min(c[0], d[0]) || Math.max(c[0], d[0]) < Math.min(a[0], b[0])
    || Math.max(a[1], b[1]) < Math.min(c[1], d[1]) || Math.max(c[1], d[1]) < Math.min(a[1], b[1])) return false;
  const p = cross(a, b, c), q = cross(a, b, d), r = cross(c, d, a), s = cross(c, d, b);
  return p * q <= 0 && r * s <= 0;
}

function ringsMeet(a, b) {
  for (let i = 1; i < a.length; i++) for (let j = 1; j < b.length; j++) {
    if (segmentsMeet(a[i - 1], a[i], b[j - 1], b[j])) return true;
  }
  return false;
}

function checkedRing(ids, index) {
  if (ids.length < 4 || ids.length > 20000 || ids[0] !== ids.at(-1)) fail('Unclosed/oversized OSM ring');
  if (new Set(ids.slice(0, -1)).size !== ids.length - 1) fail('Repeated node in OSM ring');
  const ring = ids.map(id => nodeCoordinate(index.get(`node/${id}`)));
  for (let i = 1; i < ring.length; i++) {
    if (same(ring[i - 1], ring[i])) fail('Zero-length OSM edge');
    for (let j = i + 2; j < ring.length; j++) {
      if (i === 1 && j === ring.length - 1) continue;
      if (segmentsMeet(ring[i - 1], ring[i], ring[j - 1], ring[j])) fail('Self-intersecting OSM ring');
    }
  }
  let twiceArea = 0;
  for (let i = 1; i + 1 < ring.length; i++) twiceArea += (ring[i][0] - ring[0][0]) * (ring[i + 1][1] - ring[0][1])
    - (ring[i][1] - ring[0][1]) * (ring[i + 1][0] - ring[0][0]);
  if (Math.abs(twiceArea) < 1e-12) fail('Zero-area OSM ring');
  return ring;
}

// Join only exact OSM endpoint IDs. Ambiguous branches/open chains fail closed.
function joinMembers(members, index) {
  const remaining = members.map(member => {
    const way = index.get(`way/${member.ref}`);
    if (!Array.isArray(way?.nodes) || way.nodes.length < 2) fail('Missing relation member way');
    return [...way.nodes];
  });
  const rings = [];
  while (remaining.length) {
    let chain = remaining.shift();
    while (chain[0] !== chain.at(-1)) {
      const candidates = remaining.map((nodes, i) => ({nodes, i}))
        .filter(({nodes}) => nodes[0] === chain.at(-1) || nodes.at(-1) === chain.at(-1));
      if (candidates.length !== 1) fail('Open/ambiguous multipolygon chain');
      const {nodes, i} = candidates[0];
      remaining.splice(i, 1);
      if (nodes.at(-1) === chain.at(-1)) nodes.reverse();
      chain.push(...nodes.slice(1));
    }
    rings.push({ids: chain, ring: checkedRing(chain, index)});
  }
  return rings;
}

export function objectGeometry(object, index) {
  const sourceRef = refOf(object), memberNodes = new Set();
  if (object.type === 'way') {
    const ring = checkedRing(object.nodes || [], index);
    object.nodes.forEach(id => memberNodes.add(id));
    return {footprints: [{sourceRef, rings: [ring]}], memberNodes};
  }
  if (object.type !== 'relation' || object.tags?.type !== 'multipolygon') fail('Unsupported building relation type');
  const members = object.members || [];
  if (members.some(m => m.type === 'relation' || m.type === 'way' && !['outer', 'inner'].includes(m.role)))
    fail('Unsupported multipolygon member role');
  const outers = joinMembers(members.filter(m => m.type === 'way' && m.role === 'outer'), index);
  const inners = joinMembers(members.filter(m => m.type === 'way' && m.role === 'inner'), index);
  if (!outers.length) fail('Multipolygon lacks outer ring');
  const footprints = outers.map(o => ({sourceRef, rings: [o.ring]}));
  for (let i = 0; i < outers.length; i++) for (let j = i + 1; j < outers.length; j++) {
    if (ringsMeet(outers[i].ring, outers[j].ring) || ringLocation(outers[i].ring[0], outers[j].ring) >= 0
      || ringLocation(outers[j].ring[0], outers[i].ring) >= 0) fail('Intersecting/nested outer rings');
  }
  for (const inner of inners) {
    const parents = footprints.filter(p => inner.ring.slice(0, -1).every(v => ringLocation(v, p.rings[0]) === 1)
      && !ringsMeet(inner.ring, p.rings[0]));
    if (parents.length !== 1) fail('Unassigned/ambiguous multipolygon hole');
    if (parents[0].rings.slice(1).some(r => ringsMeet(r, inner.ring) || ringLocation(r[0], inner.ring) >= 0
      || ringLocation(inner.ring[0], r) >= 0)) fail('Intersecting/nested holes');
    parents[0].rings.push(inner.ring);
  }
  for (const ring of [...outers, ...inners]) ring.ids.forEach(id => memberNodes.add(id));
  // Explicit tagged entrance members are admissible, but unrelated POIs are not.
  for (const member of members.filter(m => m.type === 'node')) {
    if (!index.has(`node/${member.ref}`)) fail('Missing direct relation member node');
    memberNodes.add(member.ref);
  }
  return {footprints, memberNodes};
}

function entranceFromNode(node) {
  const tags = node.tags || {};
  if ((!tags.entrance && tags.building !== 'entrance') || tags.entrance === 'no') return null;
  const [lng, lat] = nodeCoordinate(node);
  // Unknown tags are explicit unknown, never assumed public or main.
  return {id: refOf(node), lng, lat, kind: KINDS.has(tags.entrance) ? tags.entrance : 'unknown',
    // Unknown explicit restrictions (permit/employees/delivery/...) must not
    // become an apparently unrestricted candidate. Only absent data is unknown.
    access: ACCESS.has(tags.access) ? tags.access : tags.access ? 'private' : 'unknown',
    wheelchair: WHEELCHAIR.has(tags.wheelchair) ? tags.wheelchair : 'unknown'};
}

function anchorAudit(model, footprints) {
  if (!footprints.length) return {notes: 'No verified area; anchor, size and yaw are unverified.', near: false};
  const anchor = [model.lng, model.lat], scale = METERS * Math.cos(model.lat * Math.PI / 180);
  let distance = Infinity;
  for (const p of footprints) for (const r of p.rings) for (let i = 1; i < r.length; i++) {
    const a = [(r[i - 1][0] - anchor[0]) * scale, (r[i - 1][1] - anchor[1]) * METERS];
    const b = [(r[i][0] - anchor[0]) * scale, (r[i][1] - anchor[1]) * METERS];
    const dx = b[0] - a[0], dy = b[1] - a[1], denominator = dx * dx + dy * dy;
    const t = denominator ? Math.max(0, Math.min(1, -(a[0] * dx + a[1] * dy) / denominator)) : 0;
    distance = Math.min(distance, Math.hypot(a[0] + t * dx, a[1] + t * dy));
  }
  const inside = footprints.some(p => inPolygon(anchor, p.rings));
  const points = footprints.flatMap(p => p.rings[0]), xs = points.map(p => p[0]), ys = points.map(p => p[1]);
  const b = [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)];
  const dx = ((b[0] + b[2]) / 2 - anchor[0]) * scale, dy = ((b[1] + b[3]) / 2 - anchor[1]) * METERS;
  return {near: inside || distance <= 30, notes: `Anchor ${inside ? 'inside/on' : `${distance.toFixed(1)} m outside`} OSM footprint; bbox-center offset E ${dx.toFixed(1)} m, N ${dy.toFixed(1)} m (not a relocation target). OSM E-W/N-S extent ${((b[2] - b[0]) * scale).toFixed(1)} x ${((b[3] - b[1]) * METERS).toFixed(1)} m.`};
}

export function buildCatalog(data, manifest) {
  if (data?.remark || !Array.isArray(data?.elements) || data.elements.length > 100000)
    fail('Incomplete/oversized Overpass response; previous catalog retained');
  // Overpass replication time, or server Date of the last OSM API batch read.
  const timestamp = data.osm3s?.timestamp_osm_base || (data.source === 'OpenStreetMap' ? data.timestamp : null);
  if (typeof timestamp !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{3})?Z$/.test(timestamp) || !Number.isFinite(Date.parse(timestamp)))
    fail('Missing OSM source timestamp');
  if (!Array.isArray(manifest?.models) || manifest.models.length !== MODEL_COUNT
    || new Set(manifest.models.map(m => m.id)).size !== MODEL_COUNT) fail(`Expected the ${MODEL_COUNT}-model manifest`);
  const index = new Map();
  for (const e of data.elements) {
    if (!['node', 'way', 'relation'].includes(e.type) || !Number.isSafeInteger(e.id) || e.id <= 0) fail('Invalid OSM object ID/type');
    const ref = refOf(e);
    if (index.has(ref)) fail(`Duplicate OSM object ${ref}`);
    index.set(ref, e);
  }
  const places = manifest.models.map(model => {
    if (!coordinate([model.lng, model.lat])) fail('Invalid manifest anchor');
    const spec = REVIEWED_MATCHES[model.id];
    const sourceRefs = [], footprints = [], entrances = new Map(), issues = [];
    if (spec) for (const ref of spec.refs) {
      const object = index.get(ref);
      if (!object) { issues.push(`Reviewed ${ref} absent from extract.`); continue; }
      const tags = object.tags || {};
      const names = ['name', 'name:en', 'name:ru', 'name:kk', 'official_name'].map(k => tags[k] || '').join(' ');
      const identityOK = spec.name ? spec.name.test(names) : Object.entries(spec.tags || {}).every(([k, v]) => tags[k] === v);
      if (!identityOK || !tags.building || tags.building === 'no') {
        issues.push(`${ref} identity/building tags changed; excluded.`); continue;
      }
      const geometry = objectGeometry(object, index);
      if (geometry.footprints.some(f => f.rings.length > 32 || f.rings.reduce((n, r) => n + r.length, 0) > 512))
        fail(`${ref} exceeds runtime polygon budget; geometry is not simplified`);
      if (spec.identity) {
        const poi = index.get(spec.identity.ref);
        const poiNames = ['name', 'name:en', 'name:ru', 'name:kk'].map(k => poi?.tags?.[k] || '').join(' ');
        if (!poi || !spec.identity.name.test(poiNames)
          || !geometry.footprints.some(p => inPolygon(nodeCoordinate(poi), p.rings))) {
          issues.push(`${ref} contained identity POI changed; excluded.`); continue;
        }
      }
      sourceRefs.push(ref); footprints.push(...geometry.footprints);
      for (const id of geometry.memberNodes) {
        const node = index.get(`node/${id}`);
        if (!node) fail(`Incomplete extract: missing node/${id}`);
        const entry = entranceFromNode(node);
        if (entry) entrances.set(entry.id, entry);
      }
    }
    // A named/tag-guarded parent must already be matched. Never import a part
    // solely because it lies near an anchor or has an old untyped numeric ID.
    if (footprints.length) for (const ref of VERIFIED_PARTS[model.id] || []) {
      const part = index.get(ref), baseline = legacyParts.get(ref);
      if (part?.type !== 'way' || part.tags?.['building:part'] !== 'yes' || !baseline) {
        issues.push(`Verified part ${ref} missing/changed; excluded.`); continue;
      }
      const geometry = objectGeometry(part, index);
      if (!sameRingEdges(geometry.footprints[0].rings[0], baseline)) {
        issues.push(`Part ${ref} differs from reviewed model-source ring; excluded.`); continue;
      }
      sourceRefs.push(ref); footprints.push(...geometry.footprints);
      for (const id of geometry.memberNodes) {
        const entry = entranceFromNode(index.get(`node/${id}`));
        if (entry) entrances.set(entry.id, entry);
      }
    }
    const audit = anchorAudit(model, footprints);
    const status = !footprints.length ? (spec && issues.some(s => s.includes('changed')) ? 'needs_review' : 'not_found')
      : issues.length || !audit.near || spec?.review ? 'needs_review' : 'matched';
    const entries = [...entrances.values()].sort((a, b) => a.id.localeCompare(b.id, 'en', {numeric: true}));
    if (entries.length > 64 || footprints.length > 40 || sourceRefs.length > 40) fail('Runtime place budget exceeded');
    const notes = [spec?.notes || 'No hand-reviewed typed OSM match.', ...issues, audit.notes,
      `${entries.length} OSM-tagged entrance node(s), boundary/direct members only; mapped, not surveyed.`,
      'No synthetic entrances; model placement unchanged.'].join(' ');
    if (notes.length > 1000) fail(`Review note too long: ${model.id}`);
    return {id: model.id, sourceRefs, footprints, entrances: entries,
      // Node IDs themselves are canonical OSM links. Keep sourceUrls within
      // the runtime's 12-link budget, prioritizing the matched area objects.
      review: {status, notes, sourceUrls: [...new Set([...sourceRefs.map(osmUrl), ...(spec?.refs || []).map(osmUrl),
        ...(spec?.identity ? [osmUrl(spec.identity.ref)] : []), ...(spec?.urls || []), ...entries.map(e => osmUrl(e.id))])].slice(0, 12)}};
  });
  if (places.reduce((n, p) => n + p.footprints.reduce((m, f) => m + f.rings.reduce((k, r) => k + r.length, 0), 0), 0) > 24000)
    fail('Runtime total vertex budget exceeded');
  return {schemaVersion: 1, timestamp, source: 'OpenStreetMap', license: 'ODbL-1.0', places};
}

export function makeQuery() {
  const refs = [...new Set([...Object.values(REVIEWED_MATCHES).flatMap(s => [...s.refs, ...(s.identity ? [s.identity.ref] : [])]),
    ...Object.values(VERIFIED_PARTS).flat()])];
  return `[out:json][timeout:180];(${refs.map(ref => `${ref.replace('/', '(')});`).join('')});(._;>>;);out body;`;
}

export async function fetchExtract(endpoint = 'https://overpass-api.de/api/interpreter') {
  const url = new URL(endpoint);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) fail('Expected plain HTTPS Overpass endpoint');
  url.searchParams.set('data', makeQuery());
  // One manual request, no retries/service calls from the application.
  const response = await fetch(url, {method: 'GET', credentials: 'omit',
    headers: {Accept: 'application/json', 'User-Agent': 'EventMap-public-landmark-audit/1.0'},
    signal: AbortSignal.timeout(240000)});
  if (!response.ok) fail(`Overpass HTTP ${response.status}; previous catalog retained`);
  const chunks = []; let size = 0;
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > MAX_BYTES) fail('Overpass byte budget exceeded');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function main() {
  const args = process.argv.slice(2), options = {};
  for (let i = 0; i < args.length; i++) {
    const key = args[i];
    if (key === '--refresh') options.refresh = true;
    else if (['--input', '--output', '--endpoint'].includes(key) && args[i + 1] && !args[i + 1].startsWith('--')) options[key.slice(2)] = args[++i];
    else fail(`Unknown/incomplete argument ${key}`);
  }
  if (Boolean(options.input) === Boolean(options.refresh)) fail('Choose --input FILE or --refresh; never runs at build/runtime');
  if (options.endpoint && !options.refresh) fail('--endpoint requires --refresh');
  const raw = options.input ? await readFile(options.input) : null;
  if (raw?.length > MAX_BYTES) fail('Input byte budget exceeded');
  const data = raw ? JSON.parse(raw.toString('utf8')) : await fetchExtract(options.endpoint);
  const manifest = JSON.parse(await readFile(new URL('./content/manifest.json', ROOT), 'utf8'));
  const catalog = buildCatalog(data, manifest), text = JSON.stringify(catalog, null, 2) + '\n';
  if (Buffer.byteLength(text) > 1024 * 1024) fail('Catalog byte budget exceeded');
  const output = options.output ? pathToFileURL(resolve(options.output)) : new URL('./source/astana-places.json', ROOT);
  const temporary = new URL(`${output.href}.tmp-${process.pid}`);
  try { await writeFile(temporary, text, {flag: 'wx'}); await rename(temporary, output); }
  finally { await unlink(temporary).catch(() => {}); }
  console.log(JSON.stringify({timestamp: catalog.timestamp, places: catalog.places.length,
    footprints: catalog.places.reduce((sum, p) => sum + p.footprints.length, 0),
    entrances: catalog.places.reduce((sum, p) => sum + p.entrances.length, 0),
    status: Object.fromEntries(['matched', 'needs_review', 'not_found'].map(s => [s, catalog.places.filter(p => p.review.status === s).length])),
    bytes: Buffer.byteLength(text)}));
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
