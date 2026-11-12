import * as THREE from 'three';

// Local Arena metres. No scene lighting, external assets, DOM, or timers.
const GROUND = 1.62;
const TAU = Math.PI * 2;
const CYCLE = 32;
const BALL_RADIUS = 0.225;
const FORMATION = [
  [-49.5, 0], [-30, -24], [-29, -8], [-29, 8], [-30, 24],
  [-12, -15], [-10, 0], [-12, 15], [12, -23], [26, 0], [12, 23],
];
// Four seconds per possession: two seconds carrying, two seconds passing.
// The centre-back interceptions at either end make a continuous two-team drill.
const POSSESSION = [6, 8, 9, 13, 17, 19, 20, 2];
const SKIN = [0xf1c5a1, 0xc58d68, 0x8b5e43, 0xddac80, 0x694532];

/**
 * createStadiumMatch() -> { group, setActive, update, dispose }
 * Add group to the Arena without an additional height offset.
 * update accepts an absolute clock in seconds; its first finite active call
 * establishes time zero. Repeated setActive calls do not restart the clock.
 * reduceMotion=true holds one static passing tableau. A frozen input clock
 * also freezes everything. Invalid clocks are ignored. Disposal is idempotent.
 * Resources: 11 InstancedMeshes + 2 ball meshes + 1 ring; 14 draw calls,
 * 9 owned geometries, 4 owned materials, no textures or lights.
 */
export function createStadiumMatch() {
  const group = new THREE.Group();
  group.name = 'stadium-match';
  group.userData.demo = true;
  group.userData.teams = ['Астана', 'Тобол'];
  group.visible = false;
  const geometries = [];
  const own = geometry => { geometries.push(geometry); return geometry; };
  const box = own(new THREE.BoxGeometry(1, 1, 1));
  const sphere = own(new THREE.SphereGeometry(1, 8, 6));
  const hairGeometry = own(new THREE.SphereGeometry(1, 8, 4, 0, TAU, 0, 1.62));
  const limbGeometry = own(new THREE.CylinderGeometry(1, 1, 1, 6, 1));
  const torsoGeometry = own(new THREE.CylinderGeometry(1, 0.78, 1, 4, 1));
  torsoGeometry.rotateY(Math.PI / 4);
  const disc = own(new THREE.CircleGeometry(1, 16));
  const ringGeometry = own(new THREE.RingGeometry(0.64, 0.72, 32));
  const ballGeometry = own(new THREE.SphereGeometry(BALL_RADIUS, 20, 14));

  const matte = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.86, metalness: 0 });
  const black = new THREE.MeshStandardMaterial({ color: 0x14202b, roughness: 0.9 });
  const shadowMaterial = new THREE.MeshBasicMaterial({
    color: 0x152c27, transparent: true, opacity: 0.18, depthWrite: false,
  });
  const ringMaterial = new THREE.MeshBasicMaterial({
    color: 0xffdc70, transparent: true, opacity: 0.48, depthWrite: false,
  });
  const materials = [matte, black, shadowMaterial, ringMaterial];
  const batches = [];
  function batch(name, geometry, count, material = matte) {
    const mesh = new THREE.InstancedMesh(geometry, material, count);
    mesh.name = name;
    mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    // These small, pitch-bounded dynamic batches must not retain stale bounds.
    mesh.frustumCulled = false;
    mesh.castShadow = false;
    mesh.receiveShadow = false;
    group.add(mesh);
    batches.push(mesh);
    return mesh;
  }
  const torsos = batch('match-jerseys', torsoGeometry, 22);
  const shorts = batch('match-shorts', box, 22);
  const heads = batch('match-heads', sphere, 22);
  const hair = batch('match-hair', hairGeometry, 22);
  const upperArms = batch('match-sleeves', limbGeometry, 44);
  const forearms = batch('match-forearms', limbGeometry, 44);
  const thighs = batch('match-thighs', limbGeometry, 44);
  const socks = batch('match-socks', limbGeometry, 44);
  const boots = batch('match-boots', box, 44);
  const trim = batch('match-shirt-trim', box, 44);
  const shadows = batch('match-contact-shadows', disc, 22, shadowMaterial);
  const color = new THREE.Color();
  const paint = (mesh, index, hex) => mesh.setColorAt(index, color.setHex(hex));
  for (let i = 0; i < 22; i++) {
    const team = i < 11 ? 0 : 1;
    const keeper = i % 11 === 0;
    // Stylised DEMO kits only: Astana sky blue, Tobol yellow / green.
    const jersey = keeper ? (team ? 0xb78be9 : 0xf58c47) : (team ? 0xf4d34f : 0x67caee);
    const skin = SKIN[i % SKIN.length];
    paint(torsos, i, jersey);
    paint(shorts, i, team ? 0x286c48 : 0xe8f5f7);
    paint(heads, i, skin);
    paint(hair, i, [0x221c1a, 0x493324, 0x201c1c, 0x937347][i % 4]);
    for (let side = 0; side < 2; side++) {
      const n = i * 2 + side;
      paint(upperArms, n, jersey);
      paint(forearms, n, skin);
      paint(thighs, n, skin);
      paint(socks, n, team ? 0x318151 : 0x56bee5);
      paint(boots, n, keeper ? 0xe8ece2 : (i % 3 ? 0x1a2531 : 0xf06b44));
      paint(trim, n, team ? 0x286c48 : 0xf2fafc);
    }
  }
  for (const mesh of batches) if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;

  // Twelve curved black pentagons at the vertices of an icosahedron.
  // One combined geometry, not twelve individual patch draw calls.
  const phi = (1 + Math.sqrt(5)) / 2;
  const directions = [
    [-1, phi, 0], [1, phi, 0], [-1, -phi, 0], [1, -phi, 0],
    [0, -1, phi], [0, 1, phi], [0, -1, -phi], [0, 1, -phi],
    [phi, 0, -1], [phi, 0, 1], [-phi, 0, -1], [-phi, 0, 1],
  ];
  const patchPositions = [];
  const normal = new THREE.Vector3();
  const tangent = new THREE.Vector3();
  const bitangent = new THREE.Vector3();
  const vertex = new THREE.Vector3();
  for (const direction of directions) {
    normal.fromArray(direction).normalize();
    tangent.set(0, 1, 0).cross(normal).normalize();
    bitangent.crossVectors(normal, tangent);
    const edge = [];
    // Subdivided edges hug the ball instead of cutting through the sphere.
    for (let k = 0; k < 5; k++) {
      const a = k * TAU / 5;
      const b = (k + 1) * TAU / 5;
      for (let step = 0; step < 3; step++) {
        const u = step / 3;
        const x = ((1 - u) * Math.cos(a) + u * Math.cos(b)) * 0.39;
        const y = ((1 - u) * Math.sin(a) + u * Math.sin(b)) * 0.39;
        vertex.copy(normal).addScaledVector(tangent, x).addScaledVector(bitangent, y)
          .normalize().multiplyScalar(BALL_RADIUS + 0.002);
        edge.push(vertex.clone());
      }
    }
    for (let k = 0; k < edge.length; k++) {
      patchPositions.push(
        normal.x * (BALL_RADIUS + 0.002), normal.y * (BALL_RADIUS + 0.002), normal.z * (BALL_RADIUS + 0.002),
        ...edge[k].toArray(), ...edge[(k + 1) % edge.length].toArray(),
      );
    }
  }
  const patchGeometry = own(new THREE.BufferGeometry());
  patchGeometry.setAttribute('position', new THREE.Float32BufferAttribute(patchPositions, 3));
  patchGeometry.computeVertexNormals();
  const ball = new THREE.Group();
  ball.name = 'match-football-45cm';
  ball.add(new THREE.Mesh(ballGeometry, matte), new THREE.Mesh(patchGeometry, black));
  group.add(ball);
  const ring = new THREE.Mesh(ringGeometry, ringMaterial);
  ring.name = 'match-active-player';
  ring.rotation.x = -Math.PI / 2;
  ring.renderOrder = 1;
  group.add(ring);

  const dummy = new THREE.Object3D();
  const start = new THREE.Vector3();
  const end = new THREE.Vector3();
  const axis = new THREE.Vector3(0, 1, 0);
  const direction = new THREE.Vector3();
  const players = Array.from({ length: 22 }, () => ({ x: 0, z: 0, vx: 0, vz: 0, yaw: 0 }));
  const from = { x: 0, z: 0, vx: 0, vz: 0 };
  const to = { x: 0, z: 0, vx: 0, vz: 0 };
  let active = false;
  let disposed = false;
  let lastClock = null;
  let elapsed = 0;
  let lastRendered = -1;

  function sample(index, t, out) {
    const number = index % 11;
    const sign = index < 11 ? 1 : -1;
    const base = FORMATION[number];
    const phase = TAU * t / CYCLE;
    const lanePhase = phase + number * 0.22;
    const amplitude = number === 0 ? 0.5 : (number >= 8 ? 5 : 3.6);
    out.x = sign * (base[0] + amplitude * Math.sin(phase));
    out.z = sign * (base[1] + (number === 0 ? 1 : 1.8) * Math.sin(lanePhase));
    out.vx = sign * amplitude * Math.cos(phase) * TAU / CYCLE;
    out.vz = sign * (number === 0 ? 1 : 1.8) * Math.cos(lanePhase) * TAU / CYCLE;
  }
  function point(player, x, y, z, target) {
    const c = Math.cos(player.yaw), s = Math.sin(player.yaw);
    return target.set(player.x + c * x + s * z, GROUND + y, player.z - s * x + c * z);
  }
  function solid(mesh, index, player, x, y, z, sx, sy, sz) {
    point(player, x, y, z, dummy.position);
    dummy.rotation.set(0, player.yaw, 0);
    dummy.scale.set(sx, sy, sz);
    dummy.updateMatrix();
    mesh.setMatrixAt(index, dummy.matrix);
  }
  function bone(mesh, index, player, ax, ay, az, bx, by, bz, radius) {
    point(player, ax, ay, az, start);
    point(player, bx, by, bz, end);
    direction.subVectors(end, start);
    const length = direction.length();
    dummy.position.copy(start).add(end).multiplyScalar(0.5);
    dummy.quaternion.setFromUnitVectors(axis, direction.multiplyScalar(1 / Math.max(length, 0.0001)));
    dummy.scale.set(radius, length, radius);
    dummy.updateMatrix();
    mesh.setMatrixAt(index, dummy.matrix);
  }
  function possessionPoint(index, t, target) {
    sample(index, t, target);
    // Ball stays within a boot's reach, on the attacking side of its carrier.
    target.x += index < 11 ? 0.57 : -0.57;
    target.z += 0.11 * Math.sin(t * 7);
  }
  function render(t) {
    const beat = Math.floor(t / 4) % POSSESSION.length;
    const local = t % 4;
    const carrier = POSSESSION[beat];
    const receiver = POSSESSION[(beat + 1) % POSSESSION.length];
    const beatStart = beat * 4;
    let ballY = GROUND + BALL_RADIUS;
    if (local < 2) {
      possessionPoint(carrier, t, from);
      ball.position.set(from.x, ballY + 0.035 * Math.abs(Math.sin(t * 7)), from.z);
    } else {
      possessionPoint(carrier, beatStart + 2, from);
      possessionPoint(receiver, beatStart + 4, to);
      const u = (local - 2) / 2;
      ballY += Math.sin(Math.PI * u) * 0.72;
      ball.position.set(THREE.MathUtils.lerp(from.x, to.x, u), ballY, THREE.MathUtils.lerp(from.z, to.z, u));
    }
    ball.rotation.set(t * 5, t * 0.8, t * 2.3);
    for (let i = 0; i < 22; i++) {
      const p = players[i];
      sample(i, t, p);
      const speed = Math.hypot(p.vx, p.vz);
      p.yaw = speed > 0.16 ? Math.atan2(p.vx, p.vz) : Math.atan2(ball.position.x - p.x, ball.position.z - p.z);
      const stride = t * (TAU * 1.25) + i * 0.73;
      const run = Math.min(1, speed / 1.4);
      const bob = 0.025 * run * (1 - Math.cos(stride * 2));
      solid(torsos, i, p, 0, 1.29 + bob, 0, 0.36, 0.55, 0.24);
      solid(shorts, i, p, 0, 0.975 + bob, 0, 0.39, 0.25, 0.28);
      solid(heads, i, p, 0, 1.73 + bob, 0.015, 0.175, 0.195, 0.17);
      solid(hair, i, p, 0, 1.742 + bob, 0.006, 0.18, 0.195, 0.174);
      solid(trim, i * 2, p, 0, 1.40 + bob, 0.172, 0.38, 0.045, 0.012);
      solid(trim, i * 2 + 1, p, 0.045, 1.32 + bob, -0.172, 0.065, 0.16, 0.012);
      for (let side = 0; side < 2; side++) {
        const n = i * 2 + side;
        const sign = side ? 1 : -1;
        const phase = stride + side * Math.PI;
        const swing = Math.sin(phase) * 0.55 * run;
        const shoulderX = sign * 0.275;
        const elbowY = 1.49 + bob - 0.29 * Math.cos(-swing);
        const elbowZ = 0.29 * Math.sin(-swing);
        bone(upperArms, n, p, shoulderX, 1.49 + bob, 0, sign * 0.32, elbowY, elbowZ, 0.085);
        bone(forearms, n, p, sign * 0.32, elbowY, elbowZ,
          sign * 0.33, elbowY - 0.27 * Math.cos(0.65 - swing), elbowZ + 0.27 * Math.sin(0.65 - swing), 0.064);
        const hipY = 0.94 + bob;
        const footY = 0.12 + Math.max(0, Math.sin(phase)) * 0.18 * run;
        const footZ = Math.cos(phase) * 0.33 * run;
        const dy = footY - hipY;
        const distance = Math.hypot(dy, footZ);
        // Equal 46cm bones: a two-joint knee rather than pendulum capsule legs.
        const bend = Math.sqrt(Math.max(0, 0.46 * 0.46 - distance * distance * 0.25));
        const kneeY = (hipY + footY) * 0.5 + footZ / distance * bend;
        const kneeZ = footZ * 0.5 - dy / distance * bend;
        bone(thighs, n, p, sign * 0.115, hipY, 0, sign * 0.13, kneeY, kneeZ, 0.095);
        bone(socks, n, p, sign * 0.13, kneeY, kneeZ, sign * 0.13, footY, footZ, 0.078);
        solid(boots, n, p, sign * 0.13, footY - 0.055, footZ + 0.055, 0.17, 0.13, 0.31);
      }
      dummy.position.set(p.x, GROUND + 0.012, p.z);
      dummy.rotation.set(-Math.PI / 2, 0, 0);
      dummy.scale.set(0.43, 0.30, 1);
      dummy.updateMatrix();
      shadows.setMatrixAt(i, dummy.matrix);
    }
    const marked = players[local < 3 ? carrier : receiver];
    ring.position.set(marked.x, GROUND + 0.065, marked.z);
    ringMaterial.color.setHex((local < 3 ? carrier : receiver) < 11 ? 0x91dfff : 0xffdc70);
    for (const mesh of batches) mesh.instanceMatrix.needsUpdate = true;
    lastRendered = t;
  }

  render(0);
  return {
    group,
    setActive(value) {
      if (disposed || active === Boolean(value)) return;
      active = Boolean(value);
      group.visible = active;
      lastClock = null;
      elapsed = 0;
      if (active) render(0);
    },
    update(timeSeconds, reduceMotion = false) {
      if (disposed || !active) return;
      if (Number.isFinite(timeSeconds)) {
        if (lastClock !== null) {
          const delta = timeSeconds - lastClock;
          if (Number.isFinite(delta) && delta > 0) elapsed = (elapsed + delta % CYCLE) % CYCLE;
        }
        lastClock = timeSeconds;
      }
      const t = reduceMotion ? 1 : elapsed;
      if (t !== lastRendered) render(t);
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      active = false;
      group.visible = false;
      group.removeFromParent();
      for (const mesh of batches) mesh.dispose();
      for (const geometry of geometries) geometry.dispose();
      for (const material of materials) material.dispose();
      group.clear();
    },
  };
}
