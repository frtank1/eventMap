#!/usr/bin/env node
// Read-only checks: node --experimental-vm-modules check-export.mjs
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import * as vm from 'node:vm';
import {
  ROOT, LIMITS, FACADE_COLOR, TEST_FACADE_COLOR, buildExports,
  exportStaticModel, loadOriginals, makeSeed, jsonBytes, sha256, validateModel,
} from './export-models.mjs';

const read = relative => readFile(path.join(ROOT, relative));
const parse = async relative => JSON.parse(await read(relative));
function keys(value, expected) {
  assert(value && typeof value === 'object' && !Array.isArray(value));
  assert.deepEqual(Object.keys(value).sort(), [...expected].sort());
}
function coordinateChecks(manifest, center) {
  const expected = [
    ['baiterek', 51.12822, 71.43056, 105, 42],
    ['akorda', 51.12580, 71.44647, 86, 80],
    ['arena', center[1], center[0], 55, 155],
  ];
  assert.deepEqual(manifest.models.slice(0,3).map(m => [m.id, m.lat, m.lng, m.height, m.radius]), expected);
  assert.equal(new Set(manifest.models.map(m=>m.id)).size,19);
  for(const id of ['adp','ansar','khan','expo','mega','museum','hazret','grandmosque','palace','kazakheli','independence'])assert.ok(manifest.models.some(m=>m.id===id),`Missing ${id}`);
}

async function checkManifest(file, release, center) {
  const bytes = await read(file), manifest = JSON.parse(bytes);
  assert(bytes.equals(jsonBytes(manifest)), 'Manifest serialization is not canonical');
  keys(manifest, ['schemaVersion', 'engineVersion', 'release', 'models']);
  assert.equal(manifest.schemaVersion, 1);
  assert.equal(manifest.engineVersion, 2);
  assert.equal(manifest.release, release);
  assert(Array.isArray(manifest.models) && manifest.models.length === 19);
  coordinateChecks(manifest, center);
  const models = {}, summaries = {};
  for (const entry of manifest.models) {
    keys(entry, ['id', 'title', 'lat', 'lng', 'height', 'radius', 'file', 'sha256', 'bytes']);
    assert.equal(typeof entry.title, 'string');
    assert(entry.title.length > 0 && entry.title.length < 100 && !/https?:|[<>]/i.test(entry.title));
    assert(Number.isFinite(entry.lat) && Math.abs(entry.lat) <= 90);
    assert(Number.isFinite(entry.lng) && Math.abs(entry.lng) <= 180);
    assert(Number.isFinite(entry.height) && entry.height > 0);
    assert(Number.isFinite(entry.radius) && entry.radius > 0);
    assert(Number.isInteger(entry.bytes) && entry.bytes > 0 && entry.bytes <= LIMITS.bytes);
    assert.match(entry.sha256, /^[a-f0-9]{64}$/);
    assert.equal(entry.file, `assets/${entry.sha256}.json`);
    const assetBytes = await read(`content/${entry.file}`);
    assert.equal(assetBytes.length, entry.bytes, 'Manifest byte-size mismatch');
    assert.equal(sha256(assetBytes), entry.sha256, 'Manifest/content hash mismatch');
    const model = JSON.parse(assetBytes);
    assert(assetBytes.equals(jsonBytes(model)), 'Asset serialization is not canonical');
    assert.equal(model.id, entry.id);
    const summary = validateModel(model);
    assert(summary.min[1] >= -1 && summary.max[1] <= entry.height + 1);
    assert(summary.radialExtent <= entry.radius + 0.01);
    if(['baiterek','akorda','arena'].includes(entry.id))assert(model.meshes.some(mesh => mesh.detail), 'Expected detail geometry');
    assert(model.meshes.some(mesh => !mesh.detail), 'Expected main geometry');
    assert(summary.triangles * 3 > summary.vertices, 'Expected effective indexed deduplication');
    models[entry.id] = model;
    summaries[entry.id] = summary;
  }
  return { manifest, models, summaries };
}

function checkTestRelease(active, test) {
  for (const id of active.manifest.models.map(m=>m.id).filter(id=>id!=='akorda')) {
    assert.deepEqual(test.manifest.models.find(m => m.id === id), active.manifest.models.find(m => m.id === id));
    assert.deepEqual(test.models[id], active.models[id]);
  }
  const original = active.models.akorda, changed = structuredClone(test.models.akorda);
  assert.deepEqual(changed.meshes, original.meshes, 'Test release must not change geometry');
  const differences = changed.materials.flatMap((material, i) => material.color !== original.materials[i].color ? [i] : []);
  assert.equal(differences.length, 1);
  const index = differences[0];
  assert.equal(original.materials[index].color, FACADE_COLOR);
  assert.equal(changed.materials[index].color, TEST_FACADE_COLOR);
  assert(changed.meshes.some(mesh => mesh.material === index && !mesh.detail), 'Changed material must include the facade body');
  changed.materials[index].color = FACADE_COLOR;
  assert.deepEqual(changed, original, 'Only one scalar color may change');
  const before = active.manifest.models.find(m => m.id === 'akorda');
  const after = test.manifest.models.find(m => m.id === 'akorda');
  assert.notEqual(after.sha256, before.sha256);
  const { file: file1, sha256: hash1, bytes: bytes1, ...metadata1 } = before;
  const { file: file2, sha256: hash2, bytes: bytes2, ...metadata2 } = after;
  assert.deepEqual(metadata1, metadata2, 'Test release cannot change metadata');
}

async function checkSeed(active) {
  const source = (await read('generated/seed.js')).toString('utf8');
  assert.equal(source, makeSeed(active.manifest));
  const context = vm.createContext({});
  const module = new vm.SourceTextModule(source, { context });
  const expected = [
    '../content/manifest.json',
    ...active.manifest.models.map(model => `../content/${model.file}`),
  ];
  assert.deepEqual([...module.dependencySpecifiers], expected, 'Seed must have one manifest and literal JSON model imports');
  await module.link(async specifier => {
    assert(expected.includes(specifier), 'Unexpected seed import');
    const data = await parse(specifier.slice(3));
    return new vm.SyntheticModule(['default'], function () { this.setExport('default', data); }, { context });
  });
  await module.evaluate();
  assert.deepEqual(Object.keys(module.namespace), ['seed']);
  const seed = module.namespace.seed;
  assert.deepEqual(Object.keys(seed), ['manifest', 'models']);
  assert.deepEqual(Object.keys(seed.models), active.manifest.models.map(m=>m.id));
  assert.deepEqual(seed.manifest, active.manifest);
  for (const id of Object.keys(active.models)) assert.deepEqual(seed.models[id], active.models[id]);
}

function checkTransformFixtures(THREE) {
  const material = new THREE.MeshStandardMaterial({ color: 0xabcdef });
  const root = new THREE.Group(), nested = new THREE.Group();
  root.position.set(1000, 12, -800);
  root.rotation.y = -1.823;
  root.scale.set(1.1, 0.9, 1.2);
  nested.position.set(1, 2, 3);
  nested.rotation.z = 0.6;
  nested.scale.set(2, 3, 4);
  root.add(nested);
  const leaf = new THREE.Mesh(new THREE.BoxGeometry(1, 2, 3), material);
  leaf.position.set(4, 5, 6);
  leaf.rotation.x = 0.35;
  leaf.scale.set(1, 2, 0.5);
  nested.add(leaf);
  // An unrelated geographic parent must not contaminate root-local export.
  const external = new THREE.Group();
  external.position.set(-23000, 100, 7000);
  external.rotation.y = 0.2;
  external.add(root);
  external.updateMatrixWorld(true);
  const model = { group: root };
  const baked = exportStaticModel(THREE, 'akorda', model, { removeRootTranslation: true });
  const mesh = baked.meshes[0];
  assert.equal(baked.meshes.length, 1);
  assert.equal(mesh.positions.length / 3, 24);
  assert.equal(mesh.indices.length, 36);
  const rotateX = (v, a) => [v[0], v[1] * Math.cos(a) - v[2] * Math.sin(a), v[1] * Math.sin(a) + v[2] * Math.cos(a)];
  const rotateY = (v, a) => [v[0] * Math.cos(a) + v[2] * Math.sin(a), v[1], -v[0] * Math.sin(a) + v[2] * Math.cos(a)];
  const rotateZ = (v, a) => [v[0] * Math.cos(a) - v[1] * Math.sin(a), v[0] * Math.sin(a) + v[1] * Math.cos(a), v[2]];
  const multiply = (v, scale) => v.map((value, i) => value * scale[i]);
  const add = (v, translation) => v.map((value, i) => value + translation[i]);
  const rounded = (values, scale) => values.map(v => Math.round(v * scale) / scale || 0);
  const expectedVertices = new Set();
  for (let i = 0; i < 24; i++) {
    let p = Array.from(leaf.geometry.attributes.position.array.slice(i * 3, i * 3 + 3));
    let n = Array.from(leaf.geometry.attributes.normal.array.slice(i * 3, i * 3 + 3));
    p = add(rotateX(multiply(p, [1, 2, 0.5]), 0.35), [4, 5, 6]);
    p = add(rotateZ(multiply(p, [2, 3, 4]), 0.6), [1, 2, 3]);
    p = rotateY(multiply(p, [1.1, 0.9, 1.2]), -1.823);
    n = rotateX(multiply(n, [1, 0.5, 2]), 0.35);
    n = rotateZ(multiply(n, [0.5, 1 / 3, 0.25]), 0.6);
    n = rotateY(multiply(n, [1 / 1.1, 1 / 0.9, 1 / 1.2]), -1.823);
    n = n.map(value => value / Math.hypot(...n));
    expectedVertices.add(`${rounded(p, 1000)};${rounded(n, 10000)}`);
  }
  const actualVertices = new Set();
  for (let i = 0; i < mesh.positions.length; i += 3) actualVertices.add(`${mesh.positions.slice(i, i + 3)};${mesh.normals.slice(i, i + 3)}`);
  assert.deepEqual(actualVertices, expectedVertices, 'Nested scale/translation/yaw/normal bake failed');
  assert.deepEqual([...root.position.toArray()], [1000, 12, -800], 'Exporter must not mutate original geographic position');
  const translated = exportStaticModel(THREE, 'akorda', model);
  for (let i = 0; i < mesh.positions.length; i++) {
    assert(Math.abs(translated.meshes[0].positions[i] - mesh.positions[i] - [1000, 12, -800][i % 3]) < 1e-9);
  }
  const duplicate = leaf.clone();
  duplicate.material = material.clone(); // Merge equivalent values, not UUIDs.
  nested.add(duplicate);
  const merged = exportStaticModel(THREE, 'akorda', model, { removeRootTranslation: true });
  assert.equal(merged.materials.length, 1);
  assert.equal(merged.meshes.length, 1);
  assert.deepEqual(merged.meshes[0].positions, mesh.positions);
  assert.deepEqual(merged.meshes[0].normals, mesh.normals);
  assert.equal(merged.meshes[0].indices.length, 72);
  nested.remove(duplicate);

  function excludedGroup(name, visible = true) {
    const group = new THREE.Group();
    group.name = name;
    group.visible = visible;
    const child = new THREE.Mesh(leaf.geometry, material);
    child.position.set(5000, 5000, 5000);
    group.add(child);
    root.add(group);
    return group;
  }
  excludedGroup('hidden-parent', false);
  for (const name of ['activity', 'simulation', 'crowd', 'match', 'architectural-lighting', 'arena-roof-searchlights']) excludedGroup(name);
  model.activity = excludedGroup('unnamed-event-root');
  model.match = { group: excludedGroup('unnamed-match-root') };
  model.crowd = { group: excludedGroup('unnamed-crowd-root') };
  model.night = excludedGroup('unnamed-night-root');
  const light = new THREE.PointLight();
  light.add(new THREE.Mesh(leaf.geometry, material));
  root.add(light);
  const invisibleMaterial = material.clone();
  invisibleMaterial.visible = false;
  root.add(new THREE.Mesh(leaf.geometry, invisibleMaterial));
  const filtered = exportStaticModel(THREE, 'akorda', model, { removeRootTranslation: true });
  assert.deepEqual(filtered, baked, 'Hidden/light/event subtrees must be omitted');

  model.detail = nested;
  let detailed = exportStaticModel(THREE, 'akorda', model, { removeRootTranslation: true });
  assert(detailed.meshes.every(m => m.detail), 'model.detail must propagate to descendants');
  delete model.detail;
  nested.name = 'facade-details';
  detailed = exportStaticModel(THREE, 'akorda', model, { removeRootTranslation: true });
  assert(detailed.meshes.every(m => m.detail), 'Named facade-details must propagate to descendants');

  const groupedGeometry = new THREE.BoxGeometry(2, 3, 4);
  groupedGeometry.setDrawRange(0, 12);
  const groupMaterials = [material, new THREE.MeshStandardMaterial({ color: 0x123456 })];
  const groupedMesh = new THREE.Mesh(groupedGeometry, [groupMaterials[0], groupMaterials[1], material, material, material, material]);
  const grouped = exportStaticModel(THREE, 'baiterek', { group: groupedMesh });
  assert.equal(grouped.meshes.length, 2);
  assert.equal(grouped.meshes.reduce((total, m) => total + m.indices.length, 0), 12, 'Draw range/material groups must be respected');

  for (const reflect of [false, true]) for (const side of [0, 1, 2]) {
    const surface = new THREE.Mesh(new THREE.BoxGeometry(2, 3, 4), new THREE.MeshStandardMaterial({ side }));
    if (reflect) surface.scale.x = -1;
    const output = exportStaticModel(THREE, 'baiterek', { group: surface });
    assert.equal(output.materials[0].side, side === 2 ? 2 : 0);
    const m = output.meshes[0];
    for (let i = 0; i < m.indices.length; i += 3) {
      const [a, b, c] = m.indices.slice(i, i + 3).map(index => new THREE.Vector3().fromArray(m.positions, index * 3));
      const n = new THREE.Vector3().fromArray(m.normals, m.indices[i] * 3);
      assert(b.sub(a).cross(c.sub(a)).dot(n) > 0, 'Reflection/back-side winding must agree with normals');
    }
  }
  const unsafe = new THREE.Mesh(new THREE.BoxGeometry(1, 2, 3), material.clone());
  unsafe.material.map = new THREE.Texture();
  assert.throws(() => exportStaticModel(THREE, 'baiterek', { group: unsafe }), /Textured materials/);
  unsafe.material = new THREE.ShaderMaterial();
  assert.throws(() => exportStaticModel(THREE, 'baiterek', { group: unsafe }), /Unsupported material/);
}

function checkValidationFailures(model) {
  const mutations = [
    draft => { draft.url = 'https://invalid.example/model'; },
    draft => { draft.meshes[0].positions[0] = Infinity; },
    draft => { draft.meshes[0].normals[0] = NaN; },
    draft => { draft.meshes[0].indices[0] = -1; },
    draft => { draft.materials[0].side = 1; },
    draft => { draft.materials[0].map = 'texture.png'; },
    draft => { draft.meshes[0].detail = 'true'; },
    draft => { draft.meshes[0].positions[0] = 0.0001; },
  ];
  for (const mutate of mutations) {
    const draft = structuredClone(model);
    mutate(draft);
    assert.throws(() => validateModel(draft), 'Unsafe/malformed data should be rejected');
  }
}

const center = (await parse('source/arena.json')).center;
const active = await checkManifest('content/manifest.json', 'pilot-1', center);
const test = await checkManifest('content/releases/pilot-2.json', 'pilot-2', center);
console.log('PASS strict schemas, finite dimensions, precision, unit normals, indices, deduplication, budgets, coordinates, hashes and sizes');
checkTestRelease(active, test);
await checkSeed(active);
console.log('PASS pilot-2 changes only Akorda facade color; pilot-1 remains active; exact seed JSON imports and exported shape');
const { THREE, civic, landmarks } = await loadOriginals();
checkTransformFixtures(THREE);
checkValidationFailures(active.models.akorda);
// Real-model guards, in addition to synthetic hierarchy fixtures.
const city = civic.createCivicLandmarks([70, 50]);
const palace = city.pois.find(model => model.id === 'akorda');
assert.equal(palace.group.rotation.y, -1.823);
assert(Math.abs(palace.group.position.x) > 1000);
const localized = exportStaticModel(THREE, 'akorda', palace, { removeRootTranslation: true });
assert.deepEqual(localized, active.models.akorda, 'Akorda must be independent of geographic origin');
palace.group.rotation.y = 0;
assert.notEqual(sha256(jsonBytes(exportStaticModel(THREE, 'akorda', palace, { removeRootTranslation: true }))),
  sha256(jsonBytes(localized)), 'Source yaw must affect baked output');
city.dispose();
for (const [id, build] of [['baiterek', landmarks.createBaiterek], ['arena', landmarks.createArena]]) {
  const model = build();
  model.activity.visible = true;
  assert.deepEqual(exportStaticModel(THREE, id, model), active.models[id], 'Activity must be excluded even when visible');
}
console.log('PASS nested transforms, yaw, geographic isolation, visibility, activity/crowd/match/light exclusions, material merging, detail ancestry, draw ranges, winding and unsafe-data rejection');

const first = await buildExports({ randomSeed: 1 });
const second = await buildExports({ randomSeed: 0xfeed1234 });
assert.deepEqual([...first.files.keys()], [...second.files.keys()]);
for (const [file, bytes] of first.files) {
  assert(bytes.equals(second.files.get(file)), `Nondeterministic generation: ${file}`);
  assert(bytes.equals(await read(file)), `Stale generated output: ${file}`);
}
console.log(`PASS independent-VM deterministic regeneration of ${first.files.size} outputs with different random seeds, all equal to disk`);
for (const [id, summary] of Object.entries(active.summaries)) {
  console.log(`${id}: ${summary.bytes} bytes; ${summary.vertices} vertices; ${summary.meshes} meshes; bounds ${summary.dimensions.join(' x ')} m`);
}
console.log('ALL EXPORT CHECKS PASSED (read-only; no files written)');
