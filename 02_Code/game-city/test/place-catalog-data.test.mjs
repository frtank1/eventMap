import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {buildCatalog, objectGeometry, makeQuery, ringLocation, VERIFIED_PARTS} from '../import-places.mjs';
import {validatePlaceCatalog} from '../runtime/geo-catalog-store.js';

const read = path => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
const catalog = read('../source/astana-places.json');
const manifest = read('../content/manifest.json');
const node = (id, lon, lat, tags) => ({type: 'node', id, lon, lat, ...(tags ? {tags} : {})});
const square = [node(1, 71.427, 51.127), node(2, 71.428, 51.127),
  node(3, 71.428, 51.128), node(4, 71.427, 51.128)];
const extract = elements => ({osm3s: {timestamp_osm_base: '2026-11-16T06:10:36Z'}, elements});

test('all 31 manifest entries pass current runtime schema and own-boundary entrance checks', () => {
  assert.equal(validatePlaceCatalog(catalog), catalog);
  assert.deepEqual(catalog.places.map(p => p.id), manifest.models.map(m => m.id));
  assert.equal(catalog.source, 'OpenStreetMap');
  assert.equal(catalog.license, 'ODbL-1.0');
  assert.equal(catalog.places.reduce((n, p) => n + p.footprints.length, 0), 84);
  assert.equal(catalog.places.reduce((n, p) => n + p.entrances.length, 0), 27);
  assert.equal(catalog.places.filter(p => p.entrances.length).length, 8);
  assert.deepEqual(catalog.places.filter(p => p.review.status === 'needs_review').map(p => p.id),
    ['adp', 'expo', 'museum', 'kazakheli']);
});

test('original eight landmark mappings retain all 22 source polygons, plus ADP podium', () => {
  const original = {baiterek: [230401644, 230401645], arena: [166223262],
    adp: [1373723368, 1373723370, 1373723369, 1373723363, 1373723364, 1373723365, 1373723366, 1373723367],
    ansar: [230403254, 258752611], beijing: [230403277, 460047238],
    moscow: [1530050672, 258345462, 230403257], petersburg: [1530050670, 230403283, 258351821], library: [230358855]};
  for (const [id, refs] of Object.entries(original)) {
    const p = catalog.places.find(p => p.id === id);
    for (const ref of refs) assert.ok(p.footprints.some(f => f.sourceRef === `way/${ref}`), `${id}: ${ref}`);
  }
  for (const id of ['baiterek', 'adp']) {
    const p = catalog.places.find(p => p.id === id), m = manifest.models.find(m => m.id === id);
    assert.ok(p.footprints.some(f => ringLocation([m.lng, m.lat], f.rings[0]) >= 0));
  }
  // Model-source rings: the by-id landmark fetch wins over the older Baiterek snapshot (as in the importer).
  const snapshot = read('../source/baiterek.json'), fetched = read('../source/city-landmarks-osm.json');
  const source = new Map([...snapshot.buildings, ...fetched.buildings].map(b => [`way/${b.id}`, b.points]));
  for (const [id, refs] of Object.entries(VERIFIED_PARTS)) for (const ref of refs) {
    const ring = catalog.places.find(p => p.id === id).footprints.find(f => f.sourceRef === ref).rings[0];
    const raw = source.get(ref), old = raw[0].join() === raw.at(-1).join() ? raw : [...raw, raw[0]];
    const edges = r => r.slice(1).map((p, i) => [r[i].join(','), p.join(',')].sort().join('|')).sort();
    assert.deepEqual(edges(ring), edges(old), `${id}: ${ref} must retain every model-source edge`);
  }
});

test('changed part geometry is withheld even when still next to the same parent', () => {
  const p = catalog.places.find(p => p.id === 'baiterek'), elements = []; let nextId = 1000;
  for (const f of p.footprints) {
    const coordinates = f.rings[0].slice(0, -1), nodes = coordinates.map(([lon, lat]) => node(nextId++, lon, lat));
    elements.push(...nodes, {type: 'way', id: Number(f.sourceRef.split('/')[1]),
      nodes: [...nodes.map(n => n.id), nodes[0].id],
      tags: f.sourceRef === 'way/230401645' ? {building: 'yes', name: 'Baiterek'} : {'building:part': 'yes'}});
  }
  const before = buildCatalog(extract(elements), manifest).places.find(p => p.id === 'baiterek');
  assert.equal(before.footprints.length, 2);
  const part = elements.find(e => e.type === 'way' && e.id === 230401644);
  elements.find(e => e.type === 'node' && e.id === part.nodes[0]).lon += 0.000001;
  const after = buildCatalog(extract(elements), manifest).places.find(p => p.id === 'baiterek');
  assert.equal(after.review.status, 'needs_review');
  assert.deepEqual(after.sourceRefs, ['way/230401645']);
  assert.match(after.review.notes, /differs from reviewed model-source ring/);
});

test('provenance and fields are whitelisted; no unrelated OSM metadata survives', () => {
  assert.deepEqual(Object.keys(catalog).sort(), ['license', 'places', 'schemaVersion', 'source', 'timestamp']);
  for (const p of catalog.places) {
    assert.deepEqual(Object.keys(p).sort(), ['entrances', 'footprints', 'id', 'review', 'sourceRefs']);
    assert.ok(p.review.notes.length <= 1000);
    for (const f of p.footprints) {
      assert.deepEqual(Object.keys(f).sort(), ['rings', 'sourceRef']);
      for (const r of f.rings) assert.deepEqual(r[0], r.at(-1));
    }
    for (const e of p.entrances) assert.deepEqual(Object.keys(e).sort(), ['access', 'id', 'kind', 'lat', 'lng', 'wheelchair']);
  }
});

test('courtyard hole and ten actual Grand Mosque doors are preserved', () => {
  const p = catalog.places.find(p => p.id === 'grandmosque');
  assert.deepEqual(p.sourceRefs, ['relation/18901938']);
  assert.equal(p.footprints[0].rings.length, 2);
  assert.equal(p.entrances.length, 10);
  assert.ok(p.entrances.every(e => e.kind === 'yes' && e.access === 'unknown'));
});

test('only boundary nodes become entrances; private/service/unknown never become main', () => {
  const nodes = structuredClone(square);
  nodes[0].tags = {entrance: 'service', access: 'private', wheelchair: 'no', phone: 'must-not-leak'};
  nodes[1].tags = {entrance: 'secondary'};
  nodes[2].tags = {entrance: 'no'};
  const data = extract([...nodes, node(99, 71.427001, 51.127001, {entrance: 'main'}),
    {type: 'way', id: 230358855, nodes: [1, 2, 3, 4, 1], tags: {building: 'yes', name: 'National Academic Library', contact: 'must-not-leak'}}]);
  const result = buildCatalog(data, manifest), p = result.places.find(p => p.id === 'library');
  assert.deepEqual(p.entrances.map(e => [e.id, e.kind, e.access, e.wheelchair]),
    [['node/1', 'service', 'private', 'no'], ['node/2', 'unknown', 'unknown', 'unknown']]);
  assert.ok(!JSON.stringify(result).includes('must-not-leak'));
  assert.equal(result.places.length, 31);
  assert.equal(result.places.find(p => p.id === 'museum').review.status, 'not_found');
});

test('multipolygon chains use exact node IDs, preserving holes', () => {
  const objects = [...square, node(5, 71.4272, 51.1272), node(6, 71.4274, 51.1272),
    node(7, 71.4274, 51.1274), node(8, 71.4272, 51.1274),
    {type: 'way', id: 10, nodes: [1, 2, 3]}, {type: 'way', id: 11, nodes: [1, 4, 3]},
    {type: 'way', id: 12, nodes: [5, 6, 7, 8, 5]}];
  const index = new Map(objects.map(e => [`${e.type}/${e.id}`, e]));
  const relation = {type: 'relation', id: 10, tags: {type: 'multipolygon'}, members: [
    {type: 'way', ref: 10, role: 'outer'}, {type: 'way', ref: 11, role: 'outer'}, {type: 'way', ref: 12, role: 'inner'}]};
  const geometry = objectGeometry(relation, index);
  assert.deepEqual(geometry.footprints[0].rings.map(r => r.length), [5, 5]);
  assert.equal(geometry.memberNodes.size, 8);
  index.get('way/11').nodes = [1, 4, 8];
  assert.throws(() => objectGeometry(relation, index), /Open\/ambiguous/);
});

test('explicit access restrictions never degrade into unknown/unrestricted doors',()=>{
  for(const access of ['no','private','employees','permit','delivery']){
    const nodes=structuredClone(square);nodes[0].tags={entrance:'yes',access};
    const data=extract([...nodes,{type:'way',id:230358855,nodes:[1,2,3,4,1],tags:{building:'yes',name:'National Academic Library'}}]);
    const door=buildCatalog(data,manifest).places.find(p=>p.id==='library').entrances[0];
    assert.ok(['no','private'].includes(door.access),access);
  }
});

test('incomplete or malformed extracts fail before output replacement', () => {
  assert.throws(() => buildCatalog({remark: 'runtime error', elements: []}, manifest), /Incomplete/);
  assert.throws(() => buildCatalog({elements: []}, manifest), /timestamp/);
  const broken = extract([{type: 'way', id: 230358855, nodes: [1, 2, 3, 1], tags: {building: 'yes', name: 'Library'}}]);
  assert.throws(() => buildCatalog(broken, manifest), /member node/);
  assert.throws(() => buildCatalog(extract([square[0], square[0]]), manifest), /Duplicate/);
});

test('changed identity is explicit needs_review, never a nearest substitute', () => {
  const result = buildCatalog(extract([...square,
    {type: 'way', id: 230358855, nodes: [1, 2, 3, 4, 1], tags: {building: 'yes', name: 'Unrelated object'}}]), manifest);
  const p = result.places.find(p => p.id === 'library');
  assert.equal(p.review.status, 'needs_review');
  assert.deepEqual(p.sourceRefs, []);
  assert.deepEqual(p.footprints, []);
  assert.deepEqual(p.entrances, []);
});

test('refresh query is a single explicit-ID query, not a citywide search', () => {
  const query = makeQuery();
  assert.match(query, /way\(1381418247\)/);
  assert.match(query, /node\(4894937300\)/);
  assert.match(query, /relation\(18901938\)/);
  assert.ok(!query.includes('around:') && !query.includes('nwr['));
});
