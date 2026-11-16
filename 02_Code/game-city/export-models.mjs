#!/usr/bin/env node
// Run: node --experimental-vm-modules export-models.mjs
// Local asset generation only. No renderer, network, dependencies, or source edits.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as vm from 'node:vm';
import {build} from 'esbuild';

export const ROOT = path.dirname(fileURLToPath(import.meta.url));
const SOURCE = path.join(ROOT, 'source');
export const LIMITS = Object.freeze({ bytes: 6_000_000, vertices: 250_000, meshes: 45 });
export const FACADE_COLOR = 0xe7dfcc;
export const TEST_FACADE_COLOR = 0xe9dfce; // PILOT-2 TEST ONLY: slightly warmer ivory.
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
export const jsonBytes = value => Buffer.from(`${JSON.stringify(value)}\n`);

// Allow only the vendored THREE module and the original procedural builders.
// The two simulation dependencies get inert constructors, not their executable
// implementations: createArena needs only their group/supporterCount at creation.
// They remain inside the excluded activity subtree even if made visible later.
export async function loadOriginals(randomSeed = 0x4d4150) {
  assert.equal(typeof vm.SourceTextModule, 'function',
    'Run Node with --experimental-vm-modules (no installation is needed).');
  let randomState = randomSeed >>> 0;
  const deterministicMath = Object.create(Math);
  deterministicMath.random = () => {
    randomState = (Math.imul(randomState, 1664525) + 1013904223) >>> 0;
    return randomState / 0x100000000;
  };
  const context = vm.createContext({ console, Math: deterministicMath }, {
    name: 'local-static-landmark-export',
    codeGeneration: { strings: false, wasm: false },
  });
  const allowed = new Set([
    'landmarks.js', 'civic-landmarks.js', 'vendor/three.module.js',
    'vendor/BufferGeometryUtils.js', 'stadium-match.js', 'stadium-crowd.js',
  ].map(file => path.join(SOURCE, file)));
  const modules = new Map();
  async function getModule(filename) {
    assert(allowed.has(filename), `Non-allowlisted source module: ${filename}`);
    if (!modules.has(filename)) {
      // Cache the pending read as well, so shared THREE imports have one identity.
      modules.set(filename, (async () => {
        let source;
        if (filename === path.join(SOURCE, 'stadium-match.js')) {
          source = "import {Group} from 'three'; export const createStadiumMatch = () => ({group:new Group()});";
        } else if (filename === path.join(SOURCE, 'stadium-crowd.js')) {
          source = "import {Group} from 'three'; export const createStadiumCrowd = () => ({group:new Group(),supporterCount:0});";
        } else {
          source = await readFile(filename, 'utf8');
        }
        return new vm.SourceTextModule(source, { context, identifier: filename });
      })());
    }
    return modules.get(filename);
  }
  async function linker(specifier, parent) {
    const filename = specifier === 'three'
      ? path.join(SOURCE, 'vendor/three.module.js')
      : specifier.startsWith('./')
        ? path.resolve(path.dirname(parent.identifier), specifier)
        : null;
    assert(filename, `Unsupported import: ${specifier}`);
    return getModule(filename);
  }
  async function evaluate(file) {
    const module = await getModule(path.join(SOURCE, file));
    if (module.status === 'unlinked') await module.link(linker);
    if (module.status === 'linked') await module.evaluate({ timeout: 30_000 });
    assert.equal(module.status, 'evaluated');
    return module.namespace;
  }
  const landmarks = await evaluate('landmarks.js');
  const civic = await evaluate('civic-landmarks.js');
  const THREE = await evaluate('vendor/three.module.js');
  return { THREE, landmarks, civic };
}

function round(value, scale) {
  assert(Number.isFinite(value), 'Non-finite geometry component');
  return Math.round(value * scale) / scale || 0; // Canonicalize negative zero.
}

function plainMaterial(material) {
  assert(material?.isMeshStandardMaterial || material?.isMeshBasicMaterial,
    `Unsupported material: ${material?.type}`);
  assert(!material.isShaderMaterial && !material.vertexColors,
    'Shaders and vertex colors cannot be represented by this data schema');
  for (const value of Object.values(material)) {
    assert(!value?.isTexture, 'Textured materials are not supported');
  }
  assert([0, 1, 2].includes(material.side), 'Unsupported material side');
  // Intentionally whitelist scalar surface properties. Civic onBeforeCompile
  // snow hooks, userData, shader code, UUIDs, and URLs never enter the asset.
  return {
    color: material.color.getHex(),
    roughness: material.roughness ?? 1,
    metalness: material.metalness ?? 0,
    emissive: material.emissive?.getHex() ?? 0,
    emissiveIntensity: material.emissiveIntensity ?? 0,
    opacity: material.opacity,
    transparent: material.transparent,
    side: material.side === 2 ? 2 : 0,
  };
}

const OMIT_NAME = /(?:^|[\s/_-])(?:activity|simulation|crowd|match|architectural-lighting|searchlights)(?:$|[\s/_-])/i;

// Bake the entire root-local hierarchy, including root rotation/scale. Only the
// explicit geographic root translation is removed; never invert the full root
// matrix (that would incorrectly erase Akorda's yaw).
export function exportStaticModel(THREE, id, model, { removeRootTranslation = false } = {}) {
  const materials = [], materialIndices = new Map(), buckets = new Map();
  const excluded = new Set([model.activity, model.match?.group, model.crowd?.group, model.night].filter(Boolean));
  function bucketFor(material, detail) {
    const data = plainMaterial(material), key = JSON.stringify(data);
    if (!materialIndices.has(key)) {
      materialIndices.set(key, materials.length);
      materials.push(data);
    }
    const materialIndex = materialIndices.get(key), bucketKey = `${materialIndex}:${detail}`;
    if (!buckets.has(bucketKey)) {
      buckets.set(bucketKey, {
        positions: [], normals: [], indices: [], material: materialIndex, detail,
        vertices: new Map(),
      });
    }
    return buckets.get(bucketKey);
  }
  function appendMesh(node, matrix, detail) {
    assert(!node.isSkinnedMesh && !node.isInstancedMesh, 'Animated/instanced meshes are not static architecture');
    const geometry = node.geometry;
    assert(geometry?.isBufferGeometry, 'Expected BufferGeometry');
    assert(Object.keys(geometry.morphAttributes).length === 0, 'Morph targets are not static architecture');
    const positions = geometry.getAttribute('position'), normals = geometry.getAttribute('normal');
    assert(positions?.itemSize === 3 && normals?.itemSize === 3 && positions.count === normals.count,
      'Expected paired position/normal attributes');
    const index = geometry.getIndex(), count = index ? index.count : positions.count;
    const groups = Array.isArray(node.material)
      ? geometry.groups
      : [{ start: 0, count, materialIndex: 0 }];
    const normalMatrix = new THREE.Matrix3().getNormalMatrix(matrix);
    const determinant = matrix.determinant();
    assert(Number.isFinite(determinant) && Math.abs(determinant) > 1e-12, 'Singular model transform');
    const point = new THREE.Vector3(), normal = new THREE.Vector3();
    for (const group of groups) {
      const material = Array.isArray(node.material) ? node.material[group.materialIndex] : node.material;
      assert(material, 'Missing geometry group material');
      if (!material.visible || (material.transparent && material.opacity === 0)) continue;
      const start = Math.max(group.start, geometry.drawRange.start);
      const end = Math.min(count, group.start + group.count, geometry.drawRange.start + geometry.drawRange.count);
      if (end <= start) continue;
      assert(start % 3 === 0 && (end - start) % 3 === 0, 'Expected triangle-aligned draw range');
      const bucket = bucketFor(material, detail);
      const flip = (determinant < 0) !== (material.side === 1);
      function vertex(offset) {
        const sourceIndex = index ? index.getX(offset) : offset;
        point.fromBufferAttribute(positions, sourceIndex).applyMatrix4(matrix);
        normal.fromBufferAttribute(normals, sourceIndex).applyNormalMatrix(normalMatrix);
        if (material.side === 1) normal.negate();
        const p = [round(point.x, 1000), round(point.y, 1000), round(point.z, 1000)];
        const n = [round(normal.x, 10000), round(normal.y, 10000), round(normal.z, 10000)];
        // Normal participates in the key: hard edges must not get welded smooth.
        const key = `${p.join(',')};${n.join(',')}`;
        let result = bucket.vertices.get(key);
        if (result === undefined) {
          result = bucket.positions.length / 3;
          bucket.vertices.set(key, result);
          bucket.positions.push(...p);
          bucket.normals.push(...n);
        }
        return result;
      }
      for (let i = start; i < end; i += 3) {
        const a = vertex(i), b = vertex(i + 1), c = vertex(i + 2);
        bucket.indices.push(a, flip ? c : b, flip ? b : c);
      }
    }
  }
  function visit(node, parentMatrix, detail = false) {
    if (!node.visible || node.isLight || excluded.has(node) || OMIT_NAME.test(node.name)) return;
    if (node.matrixAutoUpdate) node.updateMatrix();
    const local = node.matrix.clone();
    if (node === model.group && removeRootTranslation) local.setPosition(0, 0, 0);
    const matrix = new THREE.Matrix4().multiplyMatrices(parentMatrix, local);
    const isDetail = detail || node === model.detail || node.name === 'facade-details';
    if (node.isMesh) appendMesh(node, matrix, isDetail);
    for (const child of node.children) visit(child, matrix, isDetail);
  }
  visit(model.group, new THREE.Matrix4());
  const meshes = [...buckets.values()].map(({ vertices, ...mesh }) => mesh);
  const data = { schemaVersion: 1, id, materials, meshes };
  validateModel(data);
  return data;
}

function exactKeys(value, keys, label) {
  assert(value && typeof value === 'object' && !Array.isArray(value), `${label} must be an object`);
  assert.deepEqual(Object.keys(value).sort(), [...keys].sort(), `${label} has unexpected keys`);
}

// Strict data validation is also called before any generated file is written.
// No source-specific THREE classes are needed by consumers or this validator.
export function validateModel(model) {
  exactKeys(model, ['schemaVersion', 'id', 'materials', 'meshes'], 'model');
  assert.equal(model.schemaVersion, 1);
  assert(typeof model.id==='string'&&/^[a-z][a-z0-9-]{0,63}$/.test(model.id), 'Invalid model id');
  assert(Array.isArray(model.materials) && model.materials.length > 0 && model.materials.length <= LIMITS.meshes);
  assert(Array.isArray(model.meshes) && model.meshes.length > 0 && model.meshes.length <= LIMITS.meshes);
  for (const material of model.materials) {
    exactKeys(material, ['color', 'roughness', 'metalness', 'emissive', 'emissiveIntensity', 'opacity', 'transparent', 'side'], 'material');
    for (const key of ['color', 'emissive']) assert(Number.isInteger(material[key]) && material[key] >= 0 && material[key] <= 0xffffff);
    for (const key of ['roughness', 'metalness', 'opacity']) assert(Number.isFinite(material[key]) && material[key] >= 0 && material[key] <= 1);
    assert(Number.isFinite(material.emissiveIntensity) && material.emissiveIntensity >= 0);
    assert.equal(typeof material.transparent, 'boolean');
    assert([0, 2].includes(material.side));
  }
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  let vertices = 0, triangles = 0, radialExtent = 0;
  const usedMaterials = new Set(), mergeKeys = new Set();
  for (const mesh of model.meshes) {
    exactKeys(mesh, ['positions', 'normals', 'indices', 'material', 'detail'], 'mesh');
    assert.equal(typeof mesh.detail, 'boolean');
    assert(Number.isInteger(mesh.material) && mesh.material >= 0 && mesh.material < model.materials.length);
    usedMaterials.add(mesh.material);
    const mergeKey = `${mesh.material}:${mesh.detail}`;
    assert(!mergeKeys.has(mergeKey), 'Meshes should be merged by material and detail');
    mergeKeys.add(mergeKey);
    assert(Array.isArray(mesh.positions) && mesh.positions.length > 0 && mesh.positions.length % 3 === 0);
    assert(Array.isArray(mesh.normals) && mesh.normals.length === mesh.positions.length);
    assert(Array.isArray(mesh.indices) && mesh.indices.length > 0 && mesh.indices.length % 3 === 0);
    const count = mesh.positions.length / 3, unique = new Set();
    for (let i = 0; i < mesh.positions.length; i += 3) {
      for (let axis = 0; axis < 3; axis++) {
        const p = mesh.positions[i + axis], n = mesh.normals[i + axis];
        assert(Number.isFinite(p) && Math.abs(p) < 10000, 'Invalid/local-coordinate position');
        assert(Number.isFinite(n) && Math.abs(n) <= 1, 'Invalid normal');
        assert(Math.abs(p * 1000 - Math.round(p * 1000)) < 1e-6, 'Positions must have <=3 decimals');
        assert(Math.abs(n * 10000 - Math.round(n * 10000)) < 1e-6, 'Normals must have <=4 decimals');
        min[axis] = Math.min(min[axis], p);
        max[axis] = Math.max(max[axis], p);
      }
      assert(Math.abs(Math.hypot(...mesh.normals.slice(i, i + 3)) - 1) < 0.0002, 'Normal must be normalized');
      radialExtent = Math.max(radialExtent, Math.hypot(mesh.positions[i], mesh.positions[i + 2]));
      const key = `${mesh.positions.slice(i, i + 3)};${mesh.normals.slice(i, i + 3)}`;
      assert(!unique.has(key), 'Indexed vertices must be deduplicated');
      unique.add(key);
    }
    const referenced = new Set();
    for (const index of mesh.indices) {
      assert(Number.isInteger(index) && index >= 0 && index < count, 'Invalid vertex index');
      referenced.add(index);
    }
    assert.equal(referenced.size, count, 'Unreferenced vertex');
    vertices += count;
    triangles += mesh.indices.length / 3;
  }
  assert.equal(usedMaterials.size, model.materials.length, 'Unused material');
  assert.equal(new Set(model.materials.map(m => JSON.stringify(m))).size, model.materials.length, 'Duplicate material');
  assert(vertices <= LIMITS.vertices, 'Vertex budget exceeded');
  const dimensions = max.map((value, axis) => value - min[axis]);
  assert(dimensions.every(value => Number.isFinite(value) && value > 0), 'Invalid dimensions');
  const bytes = jsonBytes(model).length;
  assert(bytes <= LIMITS.bytes, 'Per-model 6 MB budget exceeded');
  return { bytes, vertices, triangles, meshes: model.meshes.length, materials: model.materials.length, min, max, dimensions, radialExtent };
}

export function makeSeed(manifest) {
  const imports = manifest.models.map((model,i) => `import model${i} from '../content/${model.file}';`);
  return [
    '// Generated by export-models.mjs. The active offline seed is pilot-1.',
    "import manifest from '../content/manifest.json';",
    ...imports,
    `export const seed = { manifest, models: { ${manifest.models.map((m,i) => `${JSON.stringify(m.id)}:model${i}`).join(', ')} } };`,
    '',
  ].join('\n');
}

// Returns all output bytes without writing, allowing checks in independent VMs.
export async function buildExports({ randomSeed } = {}) {
  const { THREE, landmarks, civic } = await loadOriginals(randomSeed);
  const arena = JSON.parse(await readFile(path.join(SOURCE, 'arena.json'), 'utf8'));
  assert(Array.isArray(arena.center) && arena.center.length === 2 && arena.center.every(Number.isFinite));
  const civicModels = civic.createCivicLandmarks([71.43056, 51.12822]);
  const akorda = civicModels.pois.find(model => model.id === 'akorda');
  assert(akorda, 'Original Akorda model is missing');
  const specs = [
    { id: 'baiterek', title: 'Байтерек', lat: 51.12822, lng: 71.43056, height: 105, radius: 42 },
    { id: 'akorda', title: 'Ак Орда', lat: 51.12580, lng: 71.44647, height: 86, radius: 80 },
    // Preserve the original map POI framing (app.js); center is authoritative JSON.
    { id: 'arena', title: 'Астана Арена', lat: arena.center[1], lng: arena.center[0], height: 55, radius: 155 },
  ];
  const models = [
    exportStaticModel(THREE, 'baiterek', landmarks.createBaiterek()),
    exportStaticModel(THREE, 'akorda', akorda, { removeRootTranslation: true }),
    exportStaticModel(THREE, 'arena', landmarks.createArena()),
  ];
  civicModels.dispose();
  // The same builders used for authoring are exported as inert geometry data.
  // No runtime model code, shader source or remote executable enters the pack.
  const bundle=await build({entryPoints:[path.join(ROOT,'runtime/city-landmarks.js')],bundle:true,format:'esm',platform:'node',write:false,alias:{three:path.join(SOURCE,'vendor/three.module.js')}});
  const module=new vm.SourceTextModule(bundle.outputFiles[0].text,{context:vm.createContext({console,Math}),identifier:'original-city-models'});
  await module.link(()=>{throw new Error('Unexpected external model dependency');});await module.evaluate({timeout:30000});
  const authored=module.namespace.createCityLandmarks();
  for(const {entry,group}of authored.entries){
    const model=exportStaticModel(THREE,entry.id,{group},{removeRootTranslation:true}),summary=validateModel(model);
    specs.push({id:entry.id,title:entry.title,lat:entry.lat,lng:entry.lng,height:Math.ceil(Math.max(entry.height,summary.max[1])),radius:Math.ceil(Math.max(entry.radius,summary.radialExtent))});models.push(model);
  }
  authored.destroy();
  const files = new Map(), stats = {};
  function assetEntry(spec, model) {
    const summary = validateModel(model), bytes = jsonBytes(model), hash = sha256(bytes);
    assert(summary.min[1] >= -1 && summary.max[1] <= spec.height + 1, `${spec.id}: height metadata does not contain geometry`);
    assert(summary.radialExtent <= spec.radius + 0.01, `${spec.id}: radius metadata does not contain geometry`);
    const file = `assets/${hash}.json`;
    files.set(`content/${file}`, bytes);
    stats[`${spec.id}:${hash}`] = summary;
    return { ...spec, file, sha256: hash, bytes: bytes.length };
  }
  const manifest = {
    schemaVersion: 1, engineVersion: 2, release: 'pilot-1',
    models: specs.map((spec, i) => assetEntry(spec, models[i])),
  };
  // Deliberate update/cache demonstrator, NOT a new architectural revision.
  // Geometry, metadata, and all other surface properties stay byte-for-byte equal.
  const testAkorda = structuredClone(models[1]);
  const facades = testAkorda.materials.filter(material => material.color === FACADE_COLOR);
  assert.equal(facades.length, 1, 'Expected exactly one original Akorda ivory material');
  facades[0].color = TEST_FACADE_COLOR;
  const pilot2 = {
    ...manifest, release: 'pilot-2',
    models: manifest.models.map((model, i) => model.id === 'akorda' ? assetEntry(specs[i], testAkorda) : { ...model }),
  };
  files.set('content/manifest.json', jsonBytes(manifest));
  files.set('content/releases/pilot-2.json', jsonBytes(pilot2));
  files.set('generated/seed.js', Buffer.from(makeSeed(manifest)));
  return { files, manifest, pilot2, stats };
}

export async function writeExports(result) {
  for (const [relative, bytes] of result.files) {
    assert(/^content\/(?:assets\/[a-f0-9]{64}\.json|manifest\.json|releases\/pilot-2\.json)$/.test(relative) || relative === 'generated/seed.js');
    const filename = path.join(ROOT, relative);
    await mkdir(path.dirname(filename), { recursive: true });
    if (relative.startsWith('content/assets/')) {
      try {
        // Hash-named assets are append-only: existing bytes are never overwritten.
        await writeFile(filename, bytes, { flag: 'wx' });
      } catch (error) {
        if (error.code !== 'EEXIST') throw error;
        assert((await readFile(filename)).equals(bytes), `Immutable asset mismatch: ${relative}`);
      }
    } else {
      let existing;
      try { existing = await readFile(filename); } catch (error) { if (error.code !== 'ENOENT') throw error; }
      if (!existing?.equals(bytes)) await writeFile(filename, bytes);
    }
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = await buildExports();
  await writeExports(result);
  for (const [file, bytes] of result.files) console.log(`${file}\t${bytes.length} bytes`);
  for (const [id, summary] of Object.entries(result.stats)) console.log(`${id}\t${JSON.stringify(summary)}`);
}
