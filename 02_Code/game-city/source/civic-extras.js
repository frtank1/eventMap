import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';

// Original procedural interpretations, not surveyed architectural models.
// Independence's placement/plan follows the verified OpenFreeMap footprint;
// architectural character is photo-informed by the references in the task.
const METERS = 111320;
const UP = new THREE.Vector3(0, 1, 0);
const PALETTE = {ivory: 0xe5ddc9, glass: 0x427b86, apex: 0x84b5b8,
  gold: 0xbca064, dark: 0x294d57, green: 0x557569, surface: 0xd3cebc};

function box(w, h, d, x = 0, y = 0, z = 0) {
  return new THREE.BoxGeometry(w, h, d).translate(x, y, z);
}
function cylinder(bottom, top, h, x, y, z, segments = 12) {
  return new THREE.CylinderGeometry(top, bottom, h, segments).translate(x, y, z);
}
function beam(a, b, r = .2, segments = 6) {
  const start = new THREE.Vector3(...a), end = new THREE.Vector3(...b);
  const direction = end.clone().sub(start);
  return new THREE.CylinderGeometry(r, r, direction.length(), segments)
    .applyQuaternion(new THREE.Quaternion().setFromUnitVectors(UP, direction.normalize()))
    .translate(...start.add(end).multiplyScalar(.5).toArray());
}
function surface(points) {
  const vertices = [];
  for (let i = 1; i < points.length - 1; i++) vertices.push(...points[0], ...points[i], ...points[i + 1]);
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));
  geometry.computeVertexNormals();
  return geometry;
}
function lerp(a, b, t) { return a.map((v, i) => v + (b[i] - v) * t); }
function silhouette(points, depth = 1) {
  const shape = new THREE.Shape();
  points.forEach(([x, y], i) => i ? shape.lineTo(x, y) : shape.moveTo(x, y));
  shape.closePath();
  return new THREE.ExtrudeGeometry(shape, {depth, bevelEnabled: false, curveSegments: 1})
    .translate(0, 0, -depth / 2);
}

/** All geometry is in meters; cameraLocalVector uses the same projected origin. */
export function createCivicExtras(center) {
  if (!center || !Number.isFinite(center[0]) || !Number.isFinite(center[1]) || Math.abs(center[1]) >= 90) {
    throw new TypeError('createCivicExtras requires a finite [longitude, latitude] center');
  }
  const longitudeScale = METERS * Math.cos(center[1] * Math.PI / 180);
  const project = ([lon, lat]) => [(lon - center[0]) * longitudeScale, -(lat - center[1]) * METERS];
  const group = new THREE.Group(); group.name = 'civic-extras';
  const pois = [], roofs = [], landmarks = [], ownedGeometry = new Set();
  const material = (color, options = {}) => new THREE.MeshStandardMaterial({color, roughness: .65, ...options});
  const materials = {
    ivory: material(PALETTE.ivory),
    glass: material(PALETTE.glass, {metalness: .32, roughness: .28, emissive: 0x538f9a, emissiveIntensity: 0}),
    apex: material(PALETTE.apex, {metalness: .28, roughness: .2, emissive: 0x91c6cd, emissiveIntensity: 0}),
    gold: material(PALETTE.gold, {metalness: .62, roughness: .35}),
    dark: material(PALETTE.dark, {metalness: .25}),
    green: material(PALETTE.green),
    // Only separate horizontal caps use this material; no facade color changes.
    surface: material(PALETTE.surface),
    glow: new THREE.MeshBasicMaterial({color: 0xffd99b, toneMapped: false}),
  };
  for (const [key, value] of Object.entries(materials)) value.name = `civic-${key}`;
  let disposed = false, night = false, theme = 'default';

  function landmark(id, title, coord, height, radius, description, yaw = 0) {
    const g = new THREE.Group(), near = new THREE.Group(), position = project(coord);
    g.name = `civic-${id}`; g.userData.poi = id; g.position.set(position[0], 0, position[1]); g.rotation.y = yaw;
    near.name = `${id}-near-detail`; near.userData.detailDistance = 1300; g.add(near); group.add(g);
    const record = {id, g, near, yaw, position, batches: new Map(), exclusions: [], glows: []};
    landmarks.push(record);
    pois.push({id, title, coord: [...coord], p: [position[0], height * .48, position[1]],
      label: [position[0], height + 12, position[1]], height, radius, description, group: g});
    return record;
  }
  function add(record, key, geometry, near = false) {
    const batchKey = `${near ? 'near' : 'main'}:${key}`;
    if (!record.batches.has(batchKey)) record.batches.set(batchKey, {key, near, pieces: []});
    record.batches.get(batchKey).pieces.push(geometry);
  }
  function roof(record, points, height) {
    const c = Math.cos(record.yaw), s = Math.sin(record.yaw);
    roofs.push({points: points.map(([x, z]) => [record.position[0] + x * c + z * s,
      record.position[1] - x * s + z * c]), height, base: 0});
  }
  function cap(record, w, d, x, y, z, key = 'surface', register = false, yaw = 0) {
    add(record, key, new THREE.PlaneGeometry(w, d).rotateX(-Math.PI / 2).rotateY(yaw).translate(x, y + .012, z));
    if (register) {
      const c = Math.cos(yaw), s = Math.sin(yaw);
      roof(record, [[-w / 2, -d / 2], [w / 2, -d / 2], [w / 2, d / 2], [-w / 2, d / 2]]
        .map(([px, pz]) => [x + px * c + pz * s, z - px * s + pz * c]), y);
    }
  }
  function slab(record, w, h, d, x, bottom, z, register = false) {
    add(record, 'ivory', box(w, h, d, x, bottom + h / 2, z));
    cap(record, w, d, x, bottom + h, z, 'surface', register);
  }
  function rectangle(record, minX, maxX, minZ, maxZ) {
    record.exclusions.push((x, z) => x >= minX && x <= maxX && z >= minZ && z <= maxZ);
  }

  // Palace of Peace and Reconciliation: 62 m square glass pyramid, 62 m high.
  const palace = landmark('palace', 'Дворец мира и согласия', [71.46361, 51.12306], 62, 68,
    'Стеклянная пирамида с треугольной решёткой, прозрачной по цвету вершиной и озеленёнными террасами. Авторская стилизация; высота около 62 м.');
  palace.g.userData.baseDimensions = [62, 62];
  for (let i = 0; i < 3; i++) slab(palace, 78 - i * 6, 1, 78 - i * 6, 0, i, 0);
  rectangle(palace, -39, 39, -39, 39);
  const corners = [[-31, 3, 31], [31, 3, 31], [31, 3, -31], [-31, 3, -31]], tip = [0, 62, 0];
  for (let side = 0; side < 4; side++) {
    const a = corners[side], b = corners[(side + 1) % 4], upperA = lerp(a, tip, .72), upperB = lerp(b, tip, .72);
    add(palace, 'glass', surface([a, b, upperB, upperA]));
    add(palace, 'apex', surface([upperA, upperB, tip]));
    add(palace, 'ivory', beam(a, tip, .26));
    add(palace, 'ivory', beam(a, b, .22));
    for (let n = 1; n < 10; n++) {
      const t = n / 10;
      add(palace, 'ivory', beam(lerp(a, tip, t), lerp(b, tip, t), .13));
      add(palace, 'ivory', beam(lerp(a, b, t), lerp(a, tip, t), .13));
      add(palace, 'ivory', beam(lerp(b, a, t), lerp(b, tip, t), .13));
    }
    add(palace, 'glow', beam(upperA, upperB, .075));
  }
  palace.g.userData.latticeFaces = 4;
  for (const x of [-35, 35]) for (const z of [-25, 0, 25]) {
    add(palace, 'green', box(2.3, .65, 13, x, 1.325, z));
  }
  for (const z of [-35, 35]) for (const x of [-24, 24]) {
    add(palace, 'green', box(17, .65, 2.3, x, 1.325, z));
  }
  // A recessed-looking portal projects just through the sloping south face.
  add(palace, 'dark', box(10, 5.6, 2, 0, 5.8, 30.7));
  add(palace, 'apex', box(8.3, 4.8, .12, 0, 5.55, 31.76));
  for (const x of [-5.2, 5.2]) add(palace, 'ivory', box(.7, 5.9, 2.7, x, 5.95, 31.0));
  slab(palace, 12, .4, 5, 0, 8.8, 31.5);
  for (let i = 0; i < 6; i++) slab(palace, 13, .5, 6 - i * .7, 0, i * .5, 36 - i * .35);
  for (const x of [-2.75, 0, 2.75]) add(palace, 'ivory', box(.12, 4.8, .14, x, 5.55, 31.85), true);
  add(palace, 'gold', box(4, .45, .15, 0, 8.2, 31.85), true);
  add(palace, 'glow', box(9, .1, .15, 0, 8.75, 32.9));

  // Kazakh Eli: a slender 91 m composition with abstract, non-figurative reliefs.
  const eli = landmark('kazakheli', 'Монумент «Қазақ Елі»', [71.46988392, 51.12211177], 91, 100,
    'Белая колонна с золотым силуэтом Самрука и полукруглой колоннадой из 28 колонн. Авторская модель без детализированных человеческих скульптур; около 91 м.');
  for (const [w, h, y] of [[32, .8, 0], [28, .8, .8], [24, 1, 1.6], [20, 1.2, 2.6]]) slab(eli, w, h, w, 0, y, 0);
  add(eli, 'ivory', cylinder(11, 8, 5.2, 0, 6.4, 0, 8));
  slab(eli, 17.2, 1.1, 17.2, 0, 9, 0);
  add(eli, 'ivory', cylinder(6.2, 5.4, 2.5, 0, 11.35, 0));
  add(eli, 'ivory', cylinder(4.8, 4.8, 1.2, 0, 13.2, 0));
  add(eli, 'ivory', cylinder(3.5, 2.15, 64.2, 0, 45.9, 0, 20));
  for (const [r, h, y] of [[3.1, 1, 78.5], [3.8, .7, 79.35], [3.45, .8, 80.1], [2.9, .9, 80.95]]) {
    add(eli, 'ivory', cylinder(r, r, h, 0, y, 0, 20));
  }
  add(eli, 'gold', cylinder(2.3, 1.9, .8, 0, 81.8, 0, 16));
  add(eli, 'gold', new THREE.SphereGeometry(1.65, 12, 8).translate(0, 83.5, 0));
  const wing = [[-.4, 86], [-2.6, 87.3], [-5.5, 88.5], [-9.8, 91], [-8.6, 87.6],
    [-7, 86.1], [-7.5, 88], [-5.4, 85.6], [-4.2, 85], [-4.9, 86.6], [-2.9, 84.8], [-.6, 84.5]];
  add(eli, 'gold', silhouette(wing, 1.05));
  add(eli, 'gold', silhouette(wing.map(([x, y]) => [-x, y]).reverse(), 1.05));
  add(eli, 'gold', silhouette([[-.8, 83.3], [-1.25, 85.6], [-.7, 88.3], [.3, 88.8],
    [1.6, 88.1], [.55, 87.8], [1.25, 85.6], [.8, 83.3], [0, 84]], 1.5));
  // Podium panels and geometric motifs, deliberately not human figures.
  for (let face = 0; face < 4; face++) {
    const yaw = face * Math.PI / 2;
    add(eli, 'dark', box(6.3, 3.6, .12, 0, 6.35, 9.4).rotateY(yaw), true);
    const diamond = [[0, 7.7, 9.49], [2.4, 6.35, 9.49], [0, 5, 9.49], [-2.4, 6.35, 9.49], [0, 7.7, 9.49]];
    for (let i = 1; i < diamond.length; i++) add(eli, 'gold', beam(diamond[i - 1], diamond[i], .13).rotateY(yaw), true);
  }
  rectangle(eli, -16.5, 16.5, -16.5, 16.5);
  // OSM's low colonnade component (2408733540) is offset east from the
  // column: replace that footprint too, without clearing the whole square.
  rectangle(eli, 18, 43, -38, 48);
  const arcRadius = 60, arcZ = -6;
  eli.g.userData.colonnadeColumns = 28; eli.g.userData.colonnadeSpan = 120;
  for (let i = 0; i < 28; i++) {
    const angle = i / 27 * Math.PI, x = arcRadius * Math.cos(angle), z = arcZ - arcRadius * Math.sin(angle);
    add(eli, 'ivory', cylinder(1.6, 1.6, .65, x, 1.225, z));
    add(eli, 'ivory', cylinder(1.3, 1.15, .75, x, 1.925, z));
    add(eli, 'ivory', cylinder(.96, .77, 9.2, x, 6.9, z));
    add(eli, 'ivory', cylinder(1.15, 1.35, .6, x, 11.8, z));
    add(eli, 'ivory', box(2.8, .4, 2.8, x, 12.3, z));
    add(eli, 'gold', cylinder(1.04, 1.04, .14, x, 2.5, z), true);
    add(eli, 'glow', box(.35, .35, .35, x, 12.55, z + .7));
  }
  for (let i = 0; i < 54; i++) {
    const angle = (i + .5) / 54 * Math.PI, length = 2 * arcRadius * Math.sin(Math.PI / 108) + .06;
    const x = arcRadius * Math.cos(angle), z = arcZ - arcRadius * Math.sin(angle), yaw = angle + Math.PI / 2;
    add(eli, 'ivory', box(length, .9, 8).rotateY(yaw).translate(x, .45, z));
    cap(eli, length, 8, x, .9, z, 'surface', false, yaw);
    add(eli, 'ivory', box(length, 1.5, 5.8).rotateY(yaw).translate(x, 13.25, z));
    cap(eli, length, 5.8, x, 14, z, 'surface', true, yaw);
    add(eli, 'gold', box(length, .16, 5.95).rotateY(yaw).translate(x, 13.6, z));
  }
  eli.exclusions.push((x, z) => z <= arcZ + .2 && Math.abs(Math.hypot(x, z - arcZ) - arcRadius) <= 4.25);

  // Independence Palace: the verified tile footprint is ~123 x 149 m, yaw -.247.
  const independence = landmark('independence', 'Дворец Независимости', [71.47204846, 51.12062014], 30, 115,
    'Горизонтальный объём из сине-зелёного стекла с белой ромбовидной решёткой на четырёх фасадах. План и поворот согласованы с открытым контуром; архитектурные детали стилизованы.', -.247);
  independence.g.userData.baseDimensions = [123, 149]; independence.g.userData.exoskeletonFaces = 4;
  slab(independence, 127, 1.2, 153, 0, 0, 0);
  slab(independence, 124, 1.3, 150, 0, 1.2, 0);
  const y0 = 2.5, y1 = 29, width = 123, depth = 149;
  const base = [[-59.5, y0, 72.5], [59.5, y0, 72.5], [59.5, y0, -72.5], [-59.5, y0, -72.5]];
  const top = [[-61.5, y1, 74.5], [61.5, y1, 74.5], [61.5, y1, -74.5], [-61.5, y1, -74.5]];
  for (let side = 0; side < 4; side++) {
    const next = (side + 1) % 4, span = side % 2 ? depth : width, h = y1 - y0;
    add(independence, 'glass', surface([base[side], base[next], top[next], top[side]]));
    // Bilinear placement follows the lightly tapered glass volume on each side.
    function point(u, v, offset = .5) {
      const p = lerp(lerp(base[side], base[next], u / span), lerp(top[side], top[next], u / span), v / h);
      const normal = [[0, 0, 1], [1, 0, 0], [0, 0, -1], [-1, 0, 0]][side];
      return p.map((value, i) => value + normal[i] * offset);
    }
    const pitch = span / (side % 2 ? 6 : 5);
    for (const direction of [-1, 1]) {
      for (let i = -1; i <= Math.ceil(span / pitch) + 1; i++) {
        const start = i * pitch, delta = direction * pitch;
        const t0 = Math.max(0, Math.min((0 - start) / delta, (span - start) / delta));
        const t1 = Math.min(1, Math.max((0 - start) / delta, (span - start) / delta));
        if (t1 - t0 > .001) add(independence, 'ivory', beam(point(start + delta * t0, h * t0), point(start + delta * t1, h * t1), .48));
      }
    }
    add(independence, 'ivory', beam(point(0, 0), point(0, h), .5));
    add(independence, 'ivory', beam(point(0, 0), point(span, 0), .3));
    for (let u = 4.8; u < span; u += 4.8) add(independence, 'dark', beam(point(u, 0, .06), point(u, h, .06), .07, 4), true);
    for (const v of [7.5, 15.5, 23.5]) add(independence, 'dark', beam(point(0, v, .08), point(span, v, .08), .09, 4), true);
    add(independence, 'glow', beam(point(0, h, .65), point(span, h, .65), .075));
  }
  slab(independence, 127, 1, 153, 0, 29, 0, true);
  add(independence, 'gold', box(127.15, .12, 153.15, 0, 29.14, 0));
  // Entry steps and glazed bays sit on the south side in building-local axes.
  for (let i = 0; i < 7; i++) {
    const d = 15 - i * 1.6;
    slab(independence, 50, .36, d, 0, i * .36, 73 + d / 2);
  }
  slab(independence, 43, .5, 8, 0, 9.2, 75.5, true);
  for (const x of [-20, 20]) add(independence, 'ivory', cylinder(.38, .38, 6.7, x, 5.85, 78));
  for (let i = -3; i <= 3; i++) {
    const x = i * 5.4;
    add(independence, 'apex', box(4.8, 5.5, .2, x, 5.3, 73.2));
    add(independence, 'ivory', box(.14, 5.5, .28, x, 5.3, 73.35), true);
    add(independence, 'gold', box(.1, 1, .18, x + .24, 5, 73.55), true);
  }
  add(independence, 'glow', box(40, .1, .1, 0, 9.12, 79.5));
  rectangle(independence, -63.75, 63.75, -76.75, 76.75);
  rectangle(independence, -25.25, 25.25, 73, 88.25);

  // One mesh per material per landmark/LOD tier. All temporary geometry is owned
  // and disposed here, including non-indexed conversion buffers.
  for (const record of landmarks) {
    for (const {key, near, pieces} of record.batches.values()) {
      const normalized = pieces.map(piece => {
        const geometry = piece.index ? piece.toNonIndexed() : piece;
        if (geometry !== piece) piece.dispose();
        geometry.deleteAttribute('uv'); geometry.clearGroups();
        return geometry;
      });
      const geometry = mergeGeometries(normalized, false);
      normalized.forEach(piece => piece.dispose());
      if (!geometry) throw new Error(`Could not merge ${record.id}:${key}`);
      geometry.computeBoundingBox(); geometry.computeBoundingSphere(); ownedGeometry.add(geometry);
      const mesh = new THREE.Mesh(geometry, materials[key]);
      mesh.name = `${record.id}-${near ? 'detail-' : ''}${key}`;
      mesh.castShadow = key !== 'glow' && key !== 'surface'; mesh.receiveShadow = key !== 'glow';
      (near ? record.near : record.g).add(mesh);
      if (key === 'surface') mesh.userData.snowSurface = true;
      if (key === 'glow') {mesh.visible = false; record.glows.push(mesh);}
    }
    record.batches.clear();
  }
  group.updateMatrixWorld(true);

  function refreshPalette() {
    materials.surface.color.setHex(theme === 'default' ? PALETTE.surface : 0xe8f1ed);
    materials.surface.roughness = theme === 'default' ? .65 : .95;
    materials.glass.emissiveIntensity = night ? .20 : 0;
    materials.apex.emissiveIntensity = night ? .36 : 0;
    materials.glow.color.setHex(theme === 'newyear' ? 0xffc66e : 0xffd99b);
    for (const record of landmarks) for (const mesh of record.glows) mesh.visible = night;
  }
  return {
    group, pois, roofs,
    excludeBuilding(lon, lat) {
      if (disposed || !Number.isFinite(lon) || !Number.isFinite(lat)) return false;
      const [x, z] = project([lon, lat]);
      return landmarks.some(record => {
        const dx = x - record.position[0], dz = z - record.position[1];
        const c = Math.cos(record.yaw), s = Math.sin(record.yaw);
        const localX = dx * c - dz * s, localZ = dx * s + dz * c;
        return record.exclusions.some(inside => inside(localX, localZ));
      });
    },
    setNight(value) { if (!disposed) {night = Boolean(value); refreshPalette();} },
    setTheme(value) {
      if (!disposed) {theme = value === 'winter' || value === 'newyear' ? value : 'default'; refreshPalette();}
    },
    updateDetail(cameraLocalVector) {
      if (disposed || !cameraLocalVector || ![cameraLocalVector.x, cameraLocalVector.y, cameraLocalVector.z].every(Number.isFinite)) return;
      for (const record of landmarks) {
        record.near.visible = Math.hypot(cameraLocalVector.x - record.position[0], cameraLocalVector.y,
          cameraLocalVector.z - record.position[1]) < record.near.userData.detailDistance;
      }
    },
    dispose() {
      if (disposed) return;
      disposed = true; group.removeFromParent();
      ownedGeometry.forEach(geometry => geometry.dispose()); ownedGeometry.clear();
      Object.values(materials).forEach(value => value.dispose());
      for (const record of landmarks) {record.near.clear(); record.g.clear(); record.glows.length = 0; record.exclusions.length = 0;}
      group.clear(); roofs.length = 0;
    },
  };
}
