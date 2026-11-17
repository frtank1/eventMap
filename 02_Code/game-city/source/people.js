import * as THREE from 'three';

const FLOOR_Y = 0.35;
const TAU = Math.PI * 2;
const TURN_SECONDS = 0.55;
// Same tones as the player's editor, so the crowd and the player read as one cast.
const SHIRTS = [0x9b7bd5, 0x4e9f92, 0xd99d58, 0x608fbd, 0xc7758f, 0x34495e, 0xece4d4, 0x7d8f4e, 0xe07a5f];
const TROUSERS = [0x294e60, 0x2a2a30, 0x4a6fa5, 0xc9b48f, 0x5d6b3c];
const SKIN = [0xf0c9a8, 0xd8a078, 0xb87d59, 0x764d39, 0xf7dcc6, 0x5a3826];
const HAIR = [0x292833, 0x674632, 0xb98a55, 0xd7d4cb, 0x8c3f24, 0xe3c27e];
const SHOES = [0xcaa48e, 0xf2efe8, 0x2b2b2e, 0xc0503f];

function randomGenerator(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = Math.imul(state ^ (state >>> 15), state | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

// Original rounded solids. Rings are [y, width, depth, zOffset], ordered bottom
// to top; several solids can share one geometry/draw call. Each ring is a soft
// superellipse and rings share vertices, so shading is smooth like the player
// avatar instead of faceted boxes.
const SIDES = 12;
const ROUNDNESS = 2.4; // 2 = ellipse, larger = squarer
function solidGeometry(solids) {
  const positions = [];
  const indices = [];
  for (const { rings, x = 0, y = 0, z = 0 } of solids) {
    const base = positions.length / 3;
    for (const [height, width, depth, offset = 0] of rings) {
      for (let j = 0; j < SIDES; j++) {
        const a = j / SIDES * TAU - Math.PI * 0.75;
        const c = Math.cos(a);
        const s = Math.sin(a);
        positions.push(
          Math.sign(c) * Math.abs(c) ** (2 / ROUNDNESS) * width / 2 + x,
          height + y,
          Math.sign(s) * Math.abs(s) ** (2 / ROUNDNESS) * depth / 2 + z + offset,
        );
      }
    }
    for (let level = 1; level < rings.length; level++) {
      const lo = base + (level - 1) * SIDES;
      const hi = base + level * SIDES;
      for (let j = 0; j < SIDES; j++) {
        const next = (j + 1) % SIDES;
        indices.push(lo + j, hi + j, hi + next, lo + j, hi + next, lo + next);
      }
    }
    for (const end of [0, rings.length - 1]) {
      const ring = rings[end];
      const center = positions.length / 3;
      positions.push(x, y + ring[0], z + (ring[3] || 0));
      const start = base + end * SIDES;
      for (let j = 0; j < SIDES; j++) {
        const next = (j + 1) % SIDES;
        if (end === 0) indices.push(center, start + j, start + next);
        else indices.push(center, start + next, start + j);
      }
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setIndex(indices);
  geometry.computeVertexNormals();
  return geometry;
}

/**
 * Make a deterministic, eight-draw-call crowd using only supplied sidewalks.
 *
 * @param {Array<[[number, number], [number, number]]>} paths Local-meter [x,z] segments.
 * @param {object} [options]
 * @param {number} [options.count=80] Clamped to 0..100; no valid paths means zero people.
 * @param {number} [options.seed=377] Seed for appearance, placement, speed and gait.
 * @param {number[]} [options.pathIndices] Optional subset of ORIGINAL path indices.
 *   Omit to distribute across the entire array, including paths around the Arena.
 *   When there are fewer people than paths, selection spans first through last.
 * @param {number} [options.scale=1.2] Readability multiplier on a nominal 1.8m person.
 * @returns {{group: THREE.Group, update: function(number, boolean=): void, count: number}}
 * update(time, reducedMotion=false) takes elapsed SECONDS, not frame delta.
 * Reduced motion freezes the current pose; resuming does not catch up or teleport.
 * Visibility/LOD belongs to the caller; this module never hides or resizes a crowd.
 */
export function createPedestrians(paths, options = { count: 80, seed: 377 }) {
  options = options || {};
  const requestedCount = Number.isFinite(options.count)
    ? Math.min(100, Math.max(0, Math.floor(options.count))) : 80;
  const seed = Number.isFinite(options.seed) ? options.seed : 377;
  const modelScale = Number.isFinite(options.scale) && options.scale > 0 ? options.scale : 1.2;
  const random = randomGenerator(seed);
  const pick = (palette) => palette[Math.floor(random() * palette.length)];
  const group = new THREE.Group();
  group.name = 'Pedestrians';

  const sourcePaths = Array.isArray(paths) ? paths : [];
  const indices = Array.isArray(options.pathIndices)
    ? [...new Set(options.pathIndices)].filter((index) => Number.isInteger(index) && index >= 0 && index < sourcePaths.length)
    : sourcePaths.map((_, index) => index);
  // Preserve array order even if the optional subset was supplied out of order.
  indices.sort((a, b) => a - b);
  const segments = [];
  for (const index of indices) {
    const path = sourcePaths[index];
    if (!Array.isArray(path) || path.length !== 2) continue;
    const [a, b] = path;
    if (!Array.isArray(a) || !Array.isArray(b) ||
        ![a[0], a[1], b[0], b[1]].every(Number.isFinite)) continue;
    const dx = b[0] - a[0];
    const dz = b[1] - a[1];
    const length = Math.hypot(dx, dz);
    if (!Number.isFinite(length) || length < 0.5) continue;
    segments.push({ index, x: a[0], z: a[1], ux: dx / length, uz: dz / length, length, yaw: Math.atan2(dx, dz) });
  }

  const count = segments.length ? requestedCount : 0;
  group.userData.pedestrianCount = count;
  group.userData.pathIndices = [];
  if (!count) return { group, update() {}, count };

  // Hands, paired limbs and paired shoes share batches, keeping this at 8 meshes.
  const material = new THREE.MeshStandardMaterial({
    color: 0xffffff, roughness: 0.88, metalness: 0,
  });
  const meshes = [];
  const batch = (name, solids, instances = count) => {
    const mesh = new THREE.InstancedMesh(solidGeometry(solids), material, instances);
    mesh.name = `pedestrians/${name}`;
    mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    // Animated instance bounds would otherwise become stale. The owning app does LOD.
    mesh.frustumCulled = false;
    mesh.castShadow = false;
    mesh.receiveShadow = true;
    group.add(mesh);
    meshes.push(mesh);
    return mesh;
  };
  const torso = batch('torso', [{ rings: [[0, 0.29, 0.21], [0.1, 0.33, 0.24], [0.43, 0.46, 0.26], [0.5, 0.34, 0.23]] }]);
  const head = batch('head', [
    { rings: [[-0.08, 0.13, 0.13], [0.04, 0.13, 0.13]] },
    { rings: [[0, 0.17, 0.18], [0.05, 0.235, 0.235], [0.12, 0.26, 0.25], [0.2, 0.255, 0.245], [0.26, 0.215, 0.21], [0.3, 0.12, 0.12]] },
    { rings: [[0.1, 0.045, 0.04], [0.16, 0.035, 0.04]], z: 0.13 },
  ]);
  const hair = batch('hair', [
    { rings: [[0.22, 0.272, 0.262], [0.27, 0.252, 0.246], [0.305, 0.19, 0.186], [0.33, 0.08, 0.08]] },
    { rings: [[0.13, 0.235, 0.06], [0.25, 0.265, 0.08]], z: -0.105 },
  ]);
  const arms = batch('arms', [{ rings: [[-0.47, 0.10, 0.10, 0.025], [-0.25, 0.12, 0.12, -0.015], [0, 0.16, 0.16]] }], count * 2);
  const legs = batch('legs', [{ rings: [[-0.77, 0.125, 0.14], [-0.4, 0.15, 0.16], [0, 0.18, 0.19]] }], count * 2);
  const shoes = batch('shoes', [{ rings: [[0, 0.155, 0.32, 0.055], [0.08, 0.155, 0.32, 0.055], [0.14, 0.13, 0.21, 0.01]] }], count * 2);
  const hands = batch('hands', [{ rings: [[-0.56, 0.085, 0.09, 0.025], [-0.45, 0.10, 0.10, 0.025]] }], count * 2);
  const face = batch('eyes', [-0.061, 0.061].map((x) => ({
    x, z: 0.127, rings: [[0.165, 0.028, 0.015], [0.198, 0.028, 0.015]],
  })));

  const color = new THREE.Color();
  const setColor = (mesh, index, value) => mesh.setColorAt(index, color.setHex(value));
  const people = [];
  const pathCount = Math.min(count, segments.length);
  const selected = Array.from({ length: pathCount }, (_, i) => segments[
    pathCount === 1 ? Math.floor((segments.length - 1) / 2)
      : Math.round(i * (segments.length - 1) / (pathCount - 1))
  ]);
  group.userData.pathIndices = selected.map((segment) => segment.index);
  for (let i = 0; i < count; i++) {
    const segment = selected[i % pathCount];
    const speed = 0.85 + random() * 0.3;
    const travelTime = segment.length / speed;
    const cycle = 2 * (travelTime + TURN_SECONDS);
    const cohort = Math.floor(i / pathCount);
    const onPath = Math.floor((count - 1 - (i % pathCount)) / pathCount) + 1;
    people.push({
      segment, speed, travelTime, cycle,
      phase: cycle * (cohort + random()) / onPath,
      gait: random() * TAU,
      height: modelScale * (0.95 + random() * 0.09),
      width: modelScale * (0.92 + random() * 0.16),
    });
    const shirt = pick(SHIRTS);
    const skin = pick(SKIN);
    const pants = pick(TROUSERS);
    const shoe = pick(SHOES);
    setColor(torso, i, shirt);
    setColor(head, i, skin);
    setColor(hair, i, pick(HAIR));
    setColor(face, i, 0x252523);
    for (let side = 0; side < 2; side++) {
      const limb = i * 2 + side;
      setColor(arms, limb, shirt);
      setColor(hands, limb, skin);
      setColor(legs, limb, pants);
      setColor(shoes, limb, shoe);
    }
  }
  for (const mesh of meshes) mesh.instanceColor.needsUpdate = true;

  // Reused scratch objects: no per-person/per-frame matrices or vectors allocated.
  const root = new THREE.Matrix4();
  const local = new THREE.Matrix4();
  const world = new THREE.Matrix4();
  const position = new THREE.Vector3();
  const scale = new THREE.Vector3();
  const rotation = new THREE.Quaternion();
  const xAxis = new THREE.Vector3(1, 0, 0);
  const yAxis = new THREE.Vector3(0, 1, 0);
  const unit = new THREE.Vector3(1, 1, 1);
  const place = (mesh, index, x, y, z, pitch = 0) => {
    position.set(x, y, z);
    rotation.setFromAxisAngle(xAxis, pitch);
    local.compose(position, rotation, unit);
    world.multiplyMatrices(root, local);
    mesh.setMatrixAt(index, world);
  };
  const smoothstep = (value) => value * value * (3 - 2 * value);
  let previousTime = 0;
  let elapsed = 0;

  function update(time, reducedMotion = false) {
    if (!Number.isFinite(time)) return;
    const delta = Math.max(0, time - previousTime);
    previousTime = time;
    if (reducedMotion) return;
    elapsed += delta;

    for (let i = 0; i < count; i++) {
      const person = people[i];
      const { segment, speed, travelTime, cycle } = person;
      const phase = (person.phase + elapsed) % cycle;
      let distance;
      let yaw = segment.yaw;
      let walking = true;
      let walkTime;
      if (phase < travelTime) {
        distance = phase * speed;
        walkTime = phase;
      } else if (phase < travelTime + TURN_SECONDS) {
        distance = segment.length;
        yaw += Math.PI * smoothstep((phase - travelTime) / TURN_SECONDS);
        walking = false;
      } else if (phase < 2 * travelTime + TURN_SECONDS) {
        walkTime = phase - travelTime - TURN_SECONDS;
        distance = segment.length - walkTime * speed;
        yaw += Math.PI;
      } else {
        distance = 0;
        yaw += Math.PI * (1 + smoothstep((phase - 2 * travelTime - TURN_SECONDS) / TURN_SECONDS));
        walking = false;
      }

      // Settle the gait before each endpoint pivot; walk speed remains meters/sec.
      const strideWeight = walking
        ? smoothstep(Math.min(1, Math.min(walkTime, travelTime - walkTime) / 0.3)) : 0;
      const gait = person.gait + (walking ? walkTime * speed * TAU / 1.35 : 0);
      const swing = Math.sin(gait) * 0.42 * strideWeight;
      position.set(segment.x + segment.ux * distance, FLOOR_Y, segment.z + segment.uz * distance);
      rotation.setFromAxisAngle(yAxis, yaw);
      scale.set(person.width, person.height, modelScale);
      root.compose(position, rotation, scale);

      place(torso, i, 0, 0.91, 0);
      place(head, i, 0, 1.48, 0);
      place(hair, i, 0, 1.48, 0);
      place(face, i, 0, 1.48, 0);
      for (let side = 0; side < 2; side++) {
        const sign = side === 0 ? -1 : 1;
        const limb = i * 2 + side;
        const legAngle = swing * sign;
        const armAngle = -legAngle * 0.85;
        place(legs, limb, sign * 0.105, 0.91, 0, legAngle);
        // Flat shoes follow the ankle arc, never dipping beneath the sidewalk.
        place(shoes, limb, sign * 0.105, 0.77 * (1 - Math.cos(legAngle)), -0.77 * Math.sin(legAngle));
        place(arms, limb, sign * 0.285, 1.34, 0, armAngle);
        place(hands, limb, sign * 0.285, 1.34, 0, armAngle);
      }
    }
    for (const mesh of meshes) mesh.instanceMatrix.needsUpdate = true;
  }

  update(0);
  return { group, update, count };
}
