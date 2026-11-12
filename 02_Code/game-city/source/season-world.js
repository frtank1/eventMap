import * as THREE from 'three';
import { mergeGeometries } from './vendor/BufferGeometryUtils.js';

// Original, metre-scale diorama ornaments. No DOM, textures, lights or IO.
// All resources are private to one layer; theme changes only toggle visibility.
const TAU = Math.PI * 2;
const LIMITS = Object.freeze({
  roofs: 2400, roofVertices: 120000, verticesPerRoof: 512,
  trees: 1000, paths: 2048, arches: 3, gifts: 6, bulbs: 360,
  snowParticles: 480, drawCalls: 13,
});
const clamp = THREE.MathUtils.clamp;
const finite = Number.isFinite;
const pointOK = p => Array.isArray(p) && finite(p[0]) && finite(p[1])
  && Math.abs(p[0]) <= 100000 && Math.abs(p[1]) <= 100000;
const array = value => Array.isArray(value) ? value : [];

function rng(seed) {
  let n = seed >>> 0;
  return () => ((n = (Math.imul(n, 1664525) + 1013904223) >>> 0) / 4294967296);
}

function ringPoints(input) {
  if (!Array.isArray(input) || input.length > LIMITS.verticesPerRoof + 1) return null;
  const points = [];
  for (const p of input) {
    if (!pointOK(p)) return null;
    const last = points[points.length - 1];
    if (!last || Math.hypot(p[0] - last[0], p[1] - last[1]) > 0.001) points.push([p[0], p[1]]);
  }
  if (points.length > 2 && Math.hypot(points[0][0] - points.at(-1)[0], points[0][1] - points.at(-1)[1]) < 0.001) points.pop();
  if (points.length < 3 || points.length > LIMITS.verticesPerRoof) return null;
  let area = 0;
  for (let i = 0; i < points.length; i++) {
    const a = points[i], b = points[(i + 1) % points.length];
    area += a[0] * b[1] - b[0] * a[1];
  }
  return Math.abs(area) < 0.02 ? null : points;
}

function inside(x, z, points) {
  let hit = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const a = points[i], b = points[j];
    if ((a[1] > z) !== (b[1] > z) && x < (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]) + a[0]) hit = !hit;
  }
  return hit;
}

function segmentDistance(x, z, a, b) {
  const dx = b[0] - a[0], dz = b[1] - a[1];
  const t = clamp(((x - a[0]) * dx + (z - a[1]) * dz) / (dx * dx + dz * dz || 1), 0, 1);
  return Math.hypot(x - a[0] - t * dx, z - a[1] - t * dz);
}

function tube(points, radius, segments = 32) {
  return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points.map(p => new THREE.Vector3(...p))), segments, radius, 5, false);
}

function bar(a, b, radius) {
  const start = new THREE.Vector3(...a), end = new THREE.Vector3(...b);
  const direction = end.clone().sub(start);
  return new THREE.CylinderGeometry(radius, radius, direction.length(), 6)
    .applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction.normalize()))
    .translate(...start.add(end).multiplyScalar(0.5).toArray());
}

// Clip the same faceted crown used by the host, so snow lies ON the foliage
// rather than floating as a second spherical tree. Lower faces remain green.
function crownCapGeometry() {
  const source = new THREE.IcosahedronGeometry(1, 0);
  const pos = source.getAttribute('position'), vertices = [];
  for (let i = 0; i < pos.count; i += 3) {
    const triangle = [0, 1, 2].map(j => new THREE.Vector3().fromBufferAttribute(pos, i + j));
    const clipped = [];
    for (let j = 0; j < 3; j++) {
      const a = triangle[j], b = triangle[(j + 1) % 3];
      if (a.y >= 0.08) clipped.push(a.clone());
      if ((a.y >= 0.08) !== (b.y >= 0.08)) clipped.push(a.clone().lerp(b, (0.08 - a.y) / (b.y - a.y)));
    }
    for (let j = 1; j + 1 < clipped.length; j++) {
      for (const p of [clipped[0], clipped[j], clipped[j + 1]]) vertices.push(p.x * 1.025, p.y * 1.025 + 0.025, p.z * 1.025);
    }
  }
  source.dispose();
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));
  geometry.computeVertexNormals();
  return geometry;
}

// Scalloped, slightly drooping bough skirts, not repeated giant cones.
function boughGeometry(radius, height, phase) {
  const count = 24, loops = [], positions = [];
  for (let level = 0; level < 4; level++) {
    const loop = [];
    for (let i = 0; i < count; i++) {
      const angle = i / count * TAU + phase;
      const scallop = i % 2 ? 0.89 : 1.05;
      const r = radius * [0.13, 0.56, scallop, 0.7][level];
      const y = height * [1, 0.6, i % 2 ? 0.22 : 0.06, 0][level];
      loop.push([Math.cos(angle) * r, y, Math.sin(angle) * r]);
    }
    loops.push(loop);
  }
  for (let level = 0; level < 3; level++) {
    for (let i = 0; i < count; i++) {
      const j = (i + 1) % count;
      positions.push(...loops[level][i], ...loops[level][j], ...loops[level + 1][i]);
      positions.push(...loops[level][j], ...loops[level + 1][j], ...loops[level + 1][i]);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.computeVertexNormals();
  return geometry;
}

const pointVertex = `
  attribute float aSize;
  attribute float aSeed;
  uniform float uScale;
  varying float vSeed;
  #include <fog_pars_vertex>
  void main() {
    vSeed = aSeed;
    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mvPosition;
    gl_PointSize = clamp(aSize * uScale / max(8.0, -mvPosition.z), 1.0, 8.0);
    #include <fog_vertex>
  }
`;

function pointMaterial(glow) {
  return new THREE.ShaderMaterial({
    uniforms: {
      ...THREE.UniformsUtils.clone(THREE.UniformsLib.fog),
      uScale: { value: glow ? 650 : 540 },
      uOpacity: { value: glow ? 0.2 : 0.56 },
      uColor: { value: new THREE.Color(glow ? 0xffdca0 : 0xf3faff) },
    },
    vertexShader: pointVertex,
    fragmentShader: `
      uniform vec3 uColor;
      uniform float uOpacity;
      varying float vSeed;
      #include <fog_pars_fragment>
      void main() {
        vec2 p = gl_PointCoord * 2.0 - 1.0;
        float r = length(p);
        ${glow ? 'float alpha = pow(max(0.0, 1.0 - r), 2.2);' : `
          float alpha = 1.0 - smoothstep(0.25, 0.98, r);
          if (vSeed < 0.13) {
            float spoke = pow(abs(cos(atan(p.y, p.x) * 3.0)), 8.0);
            alpha *= mix(0.35, 1.0, max(spoke, 1.0 - smoothstep(0.0, 0.35, r)));
          }
        `}
        if (alpha < 0.015) discard;
        gl_FragColor = vec4(uColor, alpha * uOpacity * (0.7 + vSeed * 0.3));
        #include <tonemapping_fragment>
        #include <colorspace_fragment>
        #include <fog_fragment>
      }
    `,
    transparent: true, depthWrite: false, depthTest: true,
    blending: glow ? THREE.AdditiveBlending : THREE.NormalBlending,
    fog: true, toneMapped: false,
  });
}

/**
 * @param {{kind?: 'baiterek'|'arena', roofs?: Array<{points:number[][],height:number,base?:number}>,
 *   trees?: Array<{x:number,z:number,h:number,s:number}>, paths?: number[][][]}} options
 * Roofs are simple local [x,z] rings; top Y is EXACTLY height + base + 0.75.
 * Tree h is crown-centre Y; s is crown radius (matching the host's trees).
 * @returns {{group:THREE.Group,setTheme:Function,setNight:Function,update:Function,dispose:Function}}
 * update takes absolute seconds, camera LOCAL Vector3 or [x,y,z], orbit distance
 * in metres, and reduced-motion boolean. Reduced motion hides falling snow and
 * freezes light breathing; static snow/decorations remain. No event state touched.
 * Default starts hidden. Unknown themes resolve to default. dispose is idempotent.
 * Limits and actual batch counts are available on group.userData.seasonStats.
 */
export function createSeasonLayer({ kind = 'baiterek', roofs = [], trees = [], paths = [] } = {}) {
  const arena = String(kind).toLowerCase() === 'arena';
  const random = rng(arena ? 81941 : 37749);
  const group = new THREE.Group(), winter = new THREE.Group(), holiday = new THREE.Group();
  group.name = `Season-${arena ? 'arena' : 'baiterek'}`;
  winter.name = 'Winter-snow'; holiday.name = 'Newyear-ornaments';
  group.add(winter, holiday);
  const geometries = new Set(), materials = new Set(), instanced = new Set();
  let disposed = false, theme = 'default', night = false, previousTime = null, animationTime = 0;
  let roofCount = 0, treeCount = 0, roofVertices = 0;
  const ownMaterial = m => (materials.add(m), m);
  const standard = (color, options = {}) => ownMaterial(new THREE.MeshStandardMaterial({ color, roughness: 0.8, ...options }));
  const snowMat = standard(0xf0f6f9, { roughness: 0.96, emissive: 0x9fc3e6, emissiveIntensity: 0.025 });
  const foliageMat = standard(0xffffff, { vertexColors: true, roughness: 0.9 });
  const darkMat = standard(0x405953, { roughness: 0.76 });
  const metalMat = standard(0xc8a66b, { metalness: 0.45, roughness: 0.38 });
  const giftMat = standard(0xffffff, { vertexColors: true, roughness: 0.58 });
  const ornamentMat = standard(0xffffff, { metalness: 0.3, roughness: 0.3 });
  const bulbMat = standard(0xffe3ab, { emissive: 0xffc976, emissiveIntensity: 0.35, roughness: 0.3 });
  const snowPointMat = ownMaterial(pointMaterial(false)), glowMat = ownMaterial(pointMaterial(true));
  const matrixObject = new THREE.Object3D(), color = new THREE.Color();
  const center = arena ? [154, 32] : [48, 40];
  const bounds = { minX: Infinity, maxX: -Infinity, minZ: Infinity, maxZ: -Infinity };
  const roofRecords = [];
  let maxRoofY = 0;
  const include = (x, z) => {
    bounds.minX = Math.min(bounds.minX, x); bounds.maxX = Math.max(bounds.maxX, x);
    bounds.minZ = Math.min(bounds.minZ, z); bounds.maxZ = Math.max(bounds.maxZ, z);
  };

  function addMesh(parent, geometry, material, name) {
    geometries.add(geometry);
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = name; mesh.receiveShadow = true; mesh.castShadow = false;
    parent.add(mesh);
    return mesh;
  }

  function batch(parent, list, material, name) {
    if (!list.length) return null;
    // Normalise all attributes, and dispose both originals and de-indexed copies.
    const prepared = list.map(g => {
      const normalized = g.index ? g.toNonIndexed() : g;
      if (normalized !== g) g.dispose();
      for (const key of Object.keys(normalized.attributes)) {
        if (key !== 'position' && key !== 'normal' && key !== 'color') normalized.deleteAttribute(key);
      }
      if (!normalized.getAttribute('normal')) normalized.computeVertexNormals();
      normalized.clearGroups();
      return normalized;
    });
    const merged = mergeGeometries(prepared, false);
    prepared.forEach(g => g.dispose());
    if (!merged) return null;
    merged.computeBoundingSphere();
    return addMesh(parent, merged, material, name);
  }

  function tint(geometry, hex) {
    color.set(hex);
    const colors = new Float32Array(geometry.getAttribute('position').count * 3);
    for (let i = 0; i < colors.length; i += 3) color.toArray(colors, i);
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    return geometry;
  }

  function instances(parent, geometry, material, items, name) {
    if (!items.length) { geometry.dispose(); return null; }
    geometries.add(geometry);
    const mesh = new THREE.InstancedMesh(geometry, material, items.length);
    mesh.name = name; mesh.receiveShadow = true; mesh.castShadow = false;
    items.forEach((p, i) => {
      matrixObject.position.set(p.x, p.y, p.z);
      matrixObject.rotation.set(p.rx || 0, p.ry || 0, p.rz || 0);
      matrixObject.scale.set(p.sx ?? 1, p.sy ?? 1, p.sz ?? 1);
      matrixObject.updateMatrix(); mesh.setMatrixAt(i, matrixObject.matrix);
      if (p.color !== undefined) mesh.setColorAt(i, color.set(p.color));
    });
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    mesh.computeBoundingSphere(); parent.add(mesh); instanced.add(mesh);
    return mesh;
  }

  const roofGeometries = [];
  for (const roof of array(roofs)) {
    if (roofCount >= LIMITS.roofs) break;
    if (!roof || !finite(roof.height) || roof.height < 0 || roof.height > 2000) continue;
    const base = roof.base === undefined ? 0 : roof.base;
    if (!finite(base) || Math.abs(base) > 2000) continue;
    const points = ringPoints(roof.points);
    if (!points || roofVertices + points.length > LIMITS.roofVertices) continue;
    const shape = new THREE.Shape(points.map(p => new THREE.Vector2(p[0], -p[1])));
    shape.closePath();
    const y = roof.height + base + 0.75;
    roofGeometries.push(new THREE.ExtrudeGeometry(shape, { depth: 0.16, bevelEnabled: false, steps: 1, curveSegments: 1 })
      .rotateX(-Math.PI / 2).translate(0, y - 0.16, 0));
    const record = { points, minX: Infinity, maxX: -Infinity, minZ: Infinity, maxZ: -Infinity };
    for (const [x, z] of points) {
      include(x, z);
      record.minX = Math.min(record.minX, x); record.maxX = Math.max(record.maxX, x);
      record.minZ = Math.min(record.minZ, z); record.maxZ = Math.max(record.maxZ, z);
    }
    roofRecords.push(record); maxRoofY = Math.max(maxRoofY, y);
    roofCount++; roofVertices += points.length;
  }
  batch(winter, roofGeometries, snowMat, 'Roof-snow-caps');

  const caps = [], drifts = [];
  for (const tree of array(trees)) {
    if (treeCount >= LIMITS.trees) break;
    if (!tree || !pointOK([tree.x, tree.z]) || !finite(tree.h) || !finite(tree.s) || tree.h <= 0 || tree.s <= 0 || tree.h > 100 || tree.s > 30) continue;
    const { x, z, h, s } = tree;
    include(x, z); treeCount++;
    caps.push({ x, y: h, z, sx: s, sy: s * 1.25, sz: s });
    caps.push({ x: x + 1.1, y: h + s * 0.5, z: z + 0.5, sx: s * 0.64, sy: s * 0.8, sz: s * 0.64 });
    // Existing trees already avoid paths; a small low drift stays under their crown.
    if (treeCount % 3 === 0) drifts.push({ x, y: 0.18, z, sx: s * 0.88, sy: 0.22, sz: s * 0.69, ry: random() * TAU });
  }
  instances(winter, crownCapGeometry(), snowMat, caps, 'Snow-on-existing-crowns');
  instances(winter, new THREE.IcosahedronGeometry(1, 1), snowMat, drifts, 'Small-tree-drifts');

  const segments = [];
  for (const path of array(paths).slice(0, LIMITS.paths)) {
    if (!Array.isArray(path) || !pointOK(path[0]) || !pointOK(path[1])) continue;
    const a = path[0], b = path[1], length = Math.hypot(b[0] - a[0], b[1] - a[1]);
    if (length < 12) continue;
    include(...a); include(...b);
    const t = clamp(((center[0] - a[0]) * (b[0] - a[0]) + (center[1] - a[1]) * (b[1] - a[1])) / (length * length), 0.15, 0.85);
    segments.push({ x: a[0] + (b[0] - a[0]) * t, z: a[1] + (b[1] - a[1]) * t, ux: (b[0] - a[0]) / length, uz: (b[1] - a[1]) / length });
  }
  // Empty inputs still give a finite, useful, landmark-safe presentation.
  include(center[0] - 80, center[1] - 80); include(center[0] + 80, center[1] + 80);
  const baseRadius = arena ? 4.8 : 5.5, treeHeight = arena ? 14.5 : 16.5;
  const evergreen = [], holidaySnow = [], hardware = [], brass = [], boxes = [], ornaments = [], bulbs = [];
  const [cx, cz] = center;
  const pushBulb = (x, y, z, size = 0.15) => {
    if (bulbs.length < LIMITS.bulbs) bulbs.push({ x, y, z, sx: size, sy: size, sz: size });
  };
  holidaySnow.push(new THREE.IcosahedronGeometry(1, 2).scale(baseRadius * 1.22, 0.34, baseRadius * 1.13).translate(cx, 0.25, cz));
  hardware.push(new THREE.CylinderGeometry(1.6, 1.85, 0.9, 20).translate(cx, 0.7, cz));
  hardware.push(new THREE.CylinderGeometry(0.25, 0.48, treeHeight * 0.85, 9).translate(cx, treeHeight * 0.43 + 0.6, cz));
  brass.push(new THREE.TorusGeometry(1.64, 0.065, 5, 24).rotateX(Math.PI / 2).translate(cx, 1.12, cz));
  const greens = [0x244f48, 0x32675a, 0x3a7465, 0x427d6a];
  for (let tier = 0; tier < 8; tier++) {
    const t = tier / 8, radius = baseRadius * (1 - t * 0.9);
    const y = 1.2 + t * (treeHeight - 2.1), height = (treeHeight / 8) * 1.65;
    const phase = tier * 0.41;
    evergreen.push(tint(boughGeometry(radius, height, phase).translate(cx, y, cz), greens[tier % greens.length]));
    for (let j = 0; j < 5; j++) {
      const a = j / 5 * TAU + phase, r = radius * 0.68;
      holidaySnow.push(new THREE.IcosahedronGeometry(1, 0)
        .scale(radius * 0.33, 0.11 + radius * 0.025, radius * 0.21)
        .rotateZ(-0.22).rotateY(-a)
        .translate(cx + Math.cos(a) * r, y + height * 0.48, cz + Math.sin(a) * r));
    }
    if (tier < 7) for (let j = 0; j < 4; j++) {
      const a = j / 4 * TAU + phase + 0.35;
      const r = radius * 0.94, size = 0.21 + (1 - t) * 0.1;
      ornaments.push({ x: cx + Math.cos(a) * r, y: y + 0.25, z: cz + Math.sin(a) * r,
        sx: size, sy: size * 1.12, sz: size, color: [0xd59c5a, 0xa9424e, 0xe9d6a4, 0x6da6a3][j] });
    }
  }
  const spiral = [];
  for (let i = 0; i <= 112; i++) {
    const t = i / 112, a = t * TAU * 4.6 + 0.2;
    const r = baseRadius * (1 - t * 0.93) + 0.13;
    const p = [cx + Math.cos(a) * r, 1.8 + t * (treeHeight - 1.65), cz + Math.sin(a) * r];
    spiral.push(p); pushBulb(...p, 0.13 + (1 - t) * 0.045);
  }
  hardware.push(tube(spiral, 0.035, 140));

  const starShape = new THREE.Shape();
  for (let i = 0; i < 10; i++) {
    const a = Math.PI / 2 + i * TAU / 10, r = i % 2 ? 0.42 : 0.95;
    if (i === 0) starShape.moveTo(Math.cos(a) * r, Math.sin(a) * r);
    else starShape.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  starShape.closePath();
  const star = new THREE.ExtrudeGeometry(starShape, { depth: 0.22, bevelEnabled: true, bevelSegments: 1, steps: 1, bevelSize: 0.06, bevelThickness: 0.06, curveSegments: 1 });
  star.translate(0, 0, -0.11).rotateY(Math.PI / 4).translate(cx, treeHeight + 1.15, cz);
  brass.push(bar([cx, treeHeight - 0.2, cz], [cx, treeHeight + 0.65, cz], 0.06));
  addMesh(holiday, star, bulbMat, 'Champagne-star-topper');

  const giftPositions = [[-3.5, 2.2], [-2.4, 3.2], [1.9, 3.4], [3.5, 1.9], [2.8, -2.5], [-3.6, -1.4]];
  giftPositions.forEach(([dx, dz], i) => {
    const x = cx + dx, z = cz + dz, w = 0.9 + random() * 0.65, h = 0.7 + random() * 0.75, d = 0.85 + random() * 0.4;
    const angle = (random() - 0.5) * 0.7;
    const transform = g => g.rotateY(angle).translate(x, 0.43 + h / 2, z);
    boxes.push(tint(transform(new THREE.BoxGeometry(w, h, d)), [0x9d4550, 0x3b8079, 0xd7c496, 0x5b708e, 0xb66156, 0x3b7260][i]));
    brass.push(transform(new THREE.BoxGeometry(w + 0.03, 0.09, d + 0.03).translate(0, h * 0.3, 0)));
    brass.push(transform(new THREE.BoxGeometry(0.11, h + 0.035, d + 0.035)));
    brass.push(transform(new THREE.BoxGeometry(w + 0.035, h + 0.035, 0.11)));
    for (const sign of [-1, 1]) brass.push(transform(new THREE.TorusGeometry(0.2, 0.04, 4, 10)
      .scale(1, 0.65, 1).rotateX(Math.PI / 2).translate(sign * 0.17, h / 2 + 0.08, 0)));
  });

  function clearSite(x, z, clearance) {
    if (arena ? (x / (132 + clearance)) ** 2 + (z / (108 + clearance)) ** 2 < 1 : Math.hypot(x, z) < 37 + clearance) return false;
    if (Math.hypot(x - cx, z - cz) < baseRadius + clearance + 3) return false;
    for (const roof of roofRecords) {
      if (x < roof.minX - clearance || x > roof.maxX + clearance || z < roof.minZ - clearance || z > roof.maxZ + clearance) continue;
      if (inside(x, z, roof.points)) return false;
      for (let i = 0; i < roof.points.length; i++) if (segmentDistance(x, z, roof.points[i], roof.points[(i + 1) % roof.points.length]) < clearance) return false;
    }
    return true;
  }
  segments.sort((a, b) => Math.hypot(a.x - cx, a.z - cz) - Math.hypot(b.x - cx, b.z - cz));
  const archSites = [];
  const candidates = [...segments, ...[[19, 9], [-7, 25], [27, -19]].map(([x, z]) => ({ x: cx + x, z: cz + z, ux: 0.85, uz: 0.5267826876 }))];
  for (const site of candidates) {
    if (archSites.length === LIMITS.arches) break;
    if (Math.hypot(site.x - cx, site.z - cz) > 150 || !clearSite(site.x, site.z, 6)) continue;
    if (archSites.some(a => Math.hypot(site.x - a.x, site.z - a.z) < 22)) continue;
    archSites.push(site);
  }
  for (const site of archSites) {
    const nx = -site.uz, nz = site.ux;
    const local = (x, y, z = 0) => [site.x + nx * x + site.ux * z, y, site.z + nz * x + site.uz * z];
    const arch = [local(-3.8, 0.45), local(-3.8, 3.9)];
    for (let i = 0; i <= 20; i++) {
      const a = Math.PI - i / 20 * Math.PI;
      const p = local(Math.cos(a) * 3.8, 4 + Math.sin(a) * 2.5);
      arch.push(p); pushBulb(...p, 0.14);
    }
    arch.push(local(3.8, 3.9), local(3.8, 0.45));
    hardware.push(tube(arch, 0.095, 36));
    for (const sign of [-1, 1]) {
      const p = local(sign * 3.8, 0.5);
      hardware.push(new THREE.CylinderGeometry(0.55, 0.65, 0.45, 8).translate(...p));
      holidaySnow.push(new THREE.IcosahedronGeometry(1, 1).scale(0.85, 0.19, 0.7).translate(p[0], 0.67, p[2]));
      for (let j = 0; j < 7; j++) pushBulb(...local(sign * 3.8, 0.9 + j * 0.44), 0.12);
    }
    // One delicate, suspended six-arm snowflake in each arch.
    hardware.push(bar(local(0, 6.4), local(0, 5.55), 0.025));
    for (let i = 0; i < 6; i++) {
      const a = i / 6 * TAU;
      brass.push(bar(local(0, 5.05), local(Math.cos(a) * 0.65, 5.05 + Math.sin(a) * 0.65), 0.035));
      pushBulb(...local(Math.cos(a) * 0.65, 5.05 + Math.sin(a) * 0.65), 0.09);
    }
    // A short festoon beside the walkway, never strung through the landmark.
    const stringPoints = [], sign = site.x < cx ? -1 : 1;
    let valid = true;
    for (let j = 0; j <= 16; j++) {
      const t = j / 16, p = local(sign * 5.3, 5.8 - Math.sin(t * Math.PI) * 1.15, -6 + 12 * t);
      if (!clearSite(p[0], p[2], 1)) valid = false;
      stringPoints.push(p);
    }
    if (valid) {
      hardware.push(tube(stringPoints, 0.035, 24));
      for (const end of [stringPoints[0], stringPoints.at(-1)]) {
        hardware.push(bar([end[0], 0.35, end[2]], end, 0.075));
        brass.push(new THREE.SphereGeometry(0.15, 6, 4).translate(end[0], end[1] + 0.15, end[2]));
      }
      stringPoints.forEach(p => pushBulb(p[0], p[1] - 0.1, p[2], 0.13));
    }
  }
  batch(holiday, evergreen, foliageMat, 'Sculpted-evergreen-boughs');
  batch(holiday, holidaySnow, snowMat, 'Evergreen-and-plinth-snow');
  batch(holiday, hardware, darkMat, 'Fine-frames-and-festoon-cables');
  batch(holiday, brass, metalMat, 'Brass-ribbons-and-snowflakes');
  batch(holiday, boxes, giftMat, 'Six-wrapped-gifts');
  instances(holiday, new THREE.SphereGeometry(1, 8, 6), ornamentMat, ornaments, 'Small-jewel-baubles');
  instances(holiday, new THREE.IcosahedronGeometry(1, 1), bulbMat, bulbs, 'Warm-micro-bulbs');

  function pointsObject(parent, positions, sizes, seeds, material, name) {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute('aSize', new THREE.Float32BufferAttribute(sizes, 1));
    geometry.setAttribute('aSeed', new THREE.Float32BufferAttribute(seeds, 1));
    geometries.add(geometry);
    const points = new THREE.Points(geometry, material);
    points.name = name; points.frustumCulled = false;
    parent.add(points);
    return points;
  }
  const glows = pointsObject(holiday, bulbs.flatMap(p => [p.x, p.y, p.z]), bulbs.map(() => 0.75), bulbs.map(() => random()), glowMat, 'Tiny-warm-halation');
  const particleData = Array.from({ length: LIMITS.snowParticles }, () => ({ x: random(), z: random(), y: random(), speed: 0.022 + random() * 0.021, phase: random() * TAU }));
  const snowfall = pointsObject(winter, new Float32Array(LIMITS.snowParticles * 3), particleData.map(() => 0.6 + random() * 0.7), particleData.map(() => random()), snowPointMat, 'Quiet-snowfall');
  const snowPosition = snowfall.geometry.getAttribute('position');
  snowPosition.setUsage(THREE.DynamicDrawUsage);
  let particleInitialised = false;

  let drawCalls = 0;
  group.traverse(o => { if (o.isMesh || o.isPoints) drawCalls++; });
  group.userData.seasonStats = Object.freeze({
    limits: LIMITS, drawCalls, roofs: roofCount, roofVertices, trees: treeCount,
    arches: archSites.length, gifts: LIMITS.gifts, bulbs: bulbs.length,
    snowParticles: LIMITS.snowParticles, centerpiece: Object.freeze([cx, 0, cz]),
    bounds: Object.freeze({ ...bounds }),
  });

  function setTheme(value) {
    if (disposed) return;
    const next = value === 'winter' || value === 'newyear' ? value : 'default';
    if (theme !== next) previousTime = null;
    theme = next;
    group.visible = theme !== 'default';
    winter.visible = theme !== 'default'; holiday.visible = theme === 'newyear';
    group.userData.theme = theme;
  }

  function setNight(value) {
    if (disposed) return;
    night = Boolean(value); group.userData.night = night;
    snowMat.emissiveIntensity = night ? 0.16 : 0.025;
    bulbMat.emissiveIntensity = night ? 2.2 : 0.35;
    glowMat.uniforms.uOpacity.value = night ? 0.65 : 0.14;
    snowPointMat.uniforms.uOpacity.value = night ? 0.46 : 0.56;
  }

  function update(timeSeconds = 0, cameraLocalPosition, distance = 400, reduceMotion = false) {
    if (disposed) return;
    const now = finite(timeSeconds) ? timeSeconds : (previousTime ?? 0);
    const dt = previousTime === null ? 0 : clamp(now - previousTime, 0, 0.1);
    previousTime = now;
    if (!group.visible) return;
    const d = finite(distance) ? Math.max(0, distance) : 400;
    snowfall.visible = !reduceMotion && d < 2200;
    glows.visible = d < 1800;
    if (!reduceMotion) animationTime += dt;
    const breath = reduceMotion ? 1 : 0.97 + Math.sin(animationTime * 0.7) * 0.03;
    bulbMat.emissiveIntensity = (night ? 2.2 : 0.35) * breath;
    glowMat.uniforms.uOpacity.value = (night ? 0.65 : 0.14) * breath;
    if (!snowfall.visible && particleInitialised) return;
    const px = Array.isArray(cameraLocalPosition) ? cameraLocalPosition[0] : cameraLocalPosition?.x;
    const pz = Array.isArray(cameraLocalPosition) ? cameraLocalPosition[2] : cameraLocalPosition?.z;
    const middleX = (bounds.minX + bounds.maxX) * 0.5, middleZ = (bounds.minZ + bounds.maxZ) * 0.5;
    const radius = clamp(d * 0.38, 45, 210);
    const anchorX = clamp(finite(px) ? middleX * 0.72 + px * 0.28 : middleX, bounds.minX, bounds.maxX);
    const anchorZ = clamp(finite(pz) ? middleZ * 0.72 + pz * 0.28 : middleZ, bounds.minZ, bounds.maxZ);
    const x0 = Math.max(bounds.minX - 12, anchorX - radius), x1 = Math.min(bounds.maxX + 12, anchorX + radius);
    const z0 = Math.max(bounds.minZ - 12, anchorZ - radius), z1 = Math.min(bounds.maxZ + 12, anchorZ + radius);
    const height = clamp(Math.max(65, maxRoofY + 24, d * 0.22), 65, 330);
    const wrap = v => v - Math.floor(v);
    for (let i = 0; i < particleData.length; i++) {
      const p = particleData[i];
      const x = x0 + wrap(p.x + animationTime * 0.003 + Math.sin(animationTime * 0.21 + p.phase) * 0.012) * (x1 - x0);
      const z = z0 + wrap(p.z + Math.cos(animationTime * 0.19 + p.phase) * 0.015) * (z1 - z0);
      const y = 0.6 + wrap(p.y - animationTime * p.speed * 65 / height) * height;
      snowPosition.setXYZ(i, x, y, z);
    }
    snowPosition.needsUpdate = true; particleInitialised = true;
    snowPointMat.uniforms.uOpacity.value = (night ? 0.46 : 0.56) * (1 - clamp((d - 1200) / 1000, 0, 1));
  }

  function dispose() {
    if (disposed) return;
    disposed = true; group.visible = false;
    group.removeFromParent();
    instanced.forEach(mesh => mesh.dispose());
    geometries.forEach(geometry => geometry.dispose());
    materials.forEach(material => material.dispose());
    group.clear(); winter.clear(); holiday.clear();
    instanced.clear(); geometries.clear(); materials.clear();
    particleData.length = 0; roofRecords.length = 0;
  }

  setTheme('winter'); update(0); setTheme('default'); setNight(false);
  return { group, setTheme, setNight, update, dispose };
}
