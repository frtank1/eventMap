import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {WORLD_CENTER, LON_SCALE, METERS} from '../runtime/world-state.js';

const root = fileURLToPath(new URL('../', import.meta.url));
const bundled = await build({stdin: {contents: "export * from './runtime/landmark-footprints.js';export {createBaiterekQuarter} from './runtime/baiterek-quarter.js';", resolveDir: root},
  bundle: true, format: 'esm', platform: 'node', write: false, alias: {three: root + 'source/vendor/three.module.js'}});
const {footprintCoverage, getLandmarkFootprints, createLandmarkExclusion, createBaiterekQuarter, LANDMARK_MIN_COVERAGE} = await import('data:text/javascript;base64,' + Buffer.from(bundled.outputFiles[0].text).toString('base64'));
const read = name => readFile(new URL(`../${name}`, import.meta.url), 'utf8').then(JSON.parse);
const [baiterek, arena, manifest] = await Promise.all([read('source/baiterek.json'), read('source/arena.json'), read('content/manifest.json')]);
const geo = ([x, z]) => [WORLD_CENTER[0] + x / LON_SCALE, WORLD_CENTER[1] - z / METERS];
const ring = points => points.map(geo);
const square = (x, z, w, h = w) => ring([[x,z], [x+w,z], [x+w,z+h], [x,z+h]]);
const near = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-6, `${actual} != ${expected}`);
const exclude = createLandmarkExclusion(manifest.models);

test('polygon overlap uses area, not centroid, radius, bounding box or shared edges', () => {
  const a = [square(0, 0, 10)];
  near(footprintCoverage(a, a), 1);
  near(footprintCoverage(a, [square(5, 0, 10)]), .5);
  near(footprintCoverage(a, [square(10, 0, 10)]), 0);
  near(footprintCoverage(a, [square(10, 10, 10)]), 0);
  near(footprintCoverage(a, [square(20, 0, 10)]), 0);
  near(footprintCoverage([square(-100, -100, 200)], a), .0025);
  // Same bounding box, different occupied area (concave notch).
  const l = [ring([[0,0], [10,0], [10,3], [3,3], [3,10], [0,10]])];
  near(footprintCoverage([square(5, 5, 2)], l), 0);
  near(footprintCoverage(l, a), 1);
  near(footprintCoverage(a, l), .51);
});

test('source and candidate courtyards, winding and closed/open rings are respected', () => {
  const outer = square(0, 0, 10), hole = square(3, 3, 4);
  const donut = [outer, hole];
  near(footprintCoverage([square(4, 4, 2)], donut), 0);
  near(footprintCoverage([outer], donut), .84);
  near(footprintCoverage(donut, [hole]), 0);
  near(footprintCoverage(donut, [outer]), 1);
  const reversed = donut.map(r => [...r].reverse());
  near(footprintCoverage(reversed, donut), 1);
  const closed = donut.map(r => [...r, r[0]]);
  near(footprintCoverage(closed, reversed), 1);
  // Duplicate adjacent coordinates are harmless; repeated nonadjacent ones aren't.
  near(footprintCoverage([[outer[0], ...outer, outer[0]]], [outer]), 1);
});

test('invalid, self-crossing, nested-hole and oversized rings fail open', () => {
  const good = [square(0, 0, 10)];
  const invalid = [null, [], [[]], [[geo([0, 0]), geo([1, 1])]],
    [[[NaN, 0], [1, 1], [0, 1]]], [[[181, 0], [1, 1], [0, 1]]],
    [ring([[0,0], [10,10], [0,10], [10,0]])],
    [ring([[0,0], [10,0], [5,10], [5,-2], [0,10]])],
    [square(0,0,10), square(20,20,2)],
    [square(0,0,10), square(1,1,8), square(2,2,1)],
    [square(0,0,10), square(0,0,2)],
    [Array.from({length: 513}, (_, i) => geo([Math.cos(i/513*2*Math.PI), Math.sin(i/513*2*Math.PI)]))]];
  for (const value of invalid) {
    assert.equal(footprintCoverage(value, good), 0);
    assert.equal(footprintCoverage(good, value), 0);
    assert.equal(exclude(...WORLD_CENTER, value), false);
  }
});

test('every configured source ID exists unchanged with explicit snapshot provenance', () => {
  const ids = ['baiterek', 'arena', 'adp', 'ansar', 'beijing', 'moscow', 'petersburg', 'library'];
  let total = 0;
  for (const id of ids) for (const record of getLandmarkFootprints(id)) {
    total++;
    const data = id === 'arena' ? arena : baiterek;
    const original = data.buildings.find(b => b.id === record.sourceId);
    assert.ok(original, record.sourceId);
    assert.deepEqual(record.rings, [original.points]);
    assert.equal(record.source, data.source); assert.equal(record.license, data.license);
    assert.equal(record.retrieved, data.retrieved);
    assert.equal(record.file, `source/${id === 'arena' ? 'arena' : 'baiterek'}.json`);
    assert.equal(exclude(0, 0, record.rings), true, `${id}/${record.sourceId}: rings, not centroid, decide`);
    assert.throws(() => {record.rings[0][0][0] = 0;}, TypeError);
  }
  assert.equal(total, 22);
  assert.deepEqual(getLandmarkFootprints('unknown'), []);
});

test('only active, source-bound landmarks can suppress footprints; radii are ignored', () => {
  const entry = manifest.models.find(e => e.id === 'arena');
  const rings = getLandmarkFootprints('arena')[0].rings;
  assert.equal(createLandmarkExclusion()(...arena.center, rings), false);
  assert.equal(createLandmarkExclusion([{...entry, id: 'unknown', radius: 1e9}])(...arena.center, rings), false);
  assert.equal(createLandmarkExclusion([{...entry, lng: 0, lat: 0}])(...arena.center, rings), false);
  assert.equal(createLandmarkExclusion([{...entry, lng: NaN}])(...arena.center, rings), false);
  assert.equal(createLandmarkExclusion([{...entry, radius: 0}])(...arena.center, rings), true);
  assert.equal(createLandmarkExclusion([{...entry, radius: 1e9}])(...WORLD_CENTER, [square(500,500,10)]), false);
  assert.equal(exclude(...arena.center), false, 'legacy point-only callback must not erase buildings');
  assert.equal(exclude(...arena.center, []), false);
});

test('90% coverage tolerates small source changes but not incidental partial overlap', () => {
  assert.equal(LANDMARK_MIN_COVERAGE, .9);
  const entry = manifest.models.find(e => e.id === 'adp');
  const callback = createLandmarkExclusion([entry]);
  const original = getLandmarkFootprints('adp')[0].rings[0];
  const shift = t => [original.map(p => [p[0] + (original[1][0]-original[0][0])*t, p[1] + (original[1][1]-original[0][1])*t])];
  // The real OSM quadrilateral is slightly skewed, not an exact parallelogram.
  assert.ok(Math.abs(footprintCoverage(shift(.09), [original]) - .91) < .0001);
  assert.ok(Math.abs(footprintCoverage(shift(.11), [original]) - .89) < .0001);
  assert.equal(callback(entry.lng, entry.lat, shift(.09)), true);
  assert.equal(callback(entry.lng, entry.lat, shift(.11)), false);
});

test('real nearby buildings and same-name railway stations survive old radius/name exclusions', () => {
  const station = baiterek.buildings.find(b => b.id === '1547354202');
  const arenaStation = arena.buildings.find(b => b.id === '1484250141');
  assert.equal(station.name, 'Байтерек'); assert.equal(arenaStation.name, 'Астана Арена');
  assert.equal(exclude(0, 0, [station.points]), false);
  assert.equal(exclude(0, 0, [arenaStation.points]), false);
  let preserved = 0;
  const known = new Set(manifest.models.flatMap(e => getLandmarkFootprints(e.id).map(r => r.sourceId)));
  for (const b of [...baiterek.buildings, ...arena.buildings]) {
    if (known.has(b.id)) continue;
    const [lng, lat] = b.points.reduce((p, q) => [p[0]+q[0]/b.points.length, p[1]+q[1]/b.points.length], [0, 0]);
    if (manifest.models.some(e => Math.hypot((lng-e.lng)*LON_SCALE, (lat-e.lat)*METERS) < e.radius)
      && !exclude(lng, lat, [b.points])) preserved++;
  }
  assert.ok(preserved >= 10, `preserved real neighbors: ${preserved}`);
});

test('coverage and callback do not mutate caller rings or manifest entries', () => {
  const entries = structuredClone(manifest.models), before = structuredClone(entries);
  const rings = structuredClone(getLandmarkFootprints('arena')[0].rings), original = structuredClone(rings);
  const callback = createLandmarkExclusion(entries);
  assert.equal(callback(...arena.center, rings), true);
  near(footprintCoverage(rings, rings), 1);
  assert.deepEqual(entries, before); assert.deepEqual(rings, original);
});

test('fallback picking carries raw source rings and address lookup metadata in both districts', () => {
  for (const [district, data] of [['baiterek', baiterek], ['arena', arena]]) {
    const q = createBaiterekQuarter({district}); let checked = 0;
    try {
      for (const building of data.buildings) {
        const p = building.points.reduce((a, b) => [a[0]+b[0]/building.points.length, a[1]+b[1]/building.points.length], [0,0]);
        const place = q.placeAt((p[0]-WORLD_CENTER[0])*LON_SCALE, -(p[1]-WORLD_CENTER[1])*METERS);
        if (!place) continue;
        const id = place.id.slice(4), source = data.buildings.find(b => b.id === id);
        assert.ok(source);
        assert.equal(place.sourceRef, `osm:way/${id}`);
        assert.equal(place.geometryKind, 'footprint');
        assert.deepEqual(place.geoRings, [source.points]);
        checked++;
      }
      assert.ok(checked > 20, `${district}: ${checked} pickable source footprints`);
    } finally { q.destroy(); }
  }
});
