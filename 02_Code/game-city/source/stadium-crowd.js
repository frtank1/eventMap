import * as THREE from 'three';
import {mergeGeometries} from './vendor/BufferGeometryUtils.js';

// Original, deterministic crowd for the existing eight elliptical Arena tiers.
// 5,120 supporters, 1,280 scarves, 32 flags; seven static instanced batches.
// Only a shared time uniform changes each frame: no per-frame matrix uploads.
// No DOM, images, textures, timers, lights, or additional shadow passes.
const TAU = Math.PI * 2;
const TIERS = 8, ROWS = 2, SECTORS = 16, SEATS = 20;
const COUNT = TIERS * ROWS * SECTORS * SEATS;
const SKY = [0x65c9ed, 0x82dbf4, 0x4fb5e0, 0xc1edfa, 0x3699c8];
const AWAY = [0xf3d447, 0xffe374, 0xeac637, 0x328659, 0x276e49];
const SKIN = [0xf1c5a1, 0xc58d68, 0x8b5e43, 0xddac80, 0x694532];

function tint(geometry, hex, lift = false) {
  const position = geometry.getAttribute('position');
  const color = new THREE.Color(hex), colors = [], weights = [];
  for (let i = 0; i < position.count; i++) {
    colors.push(color.r, color.g, color.b);
    weights.push(lift ? THREE.MathUtils.clamp((position.getY(i) - 1.30) / .68, 0, 1) : 0);
  }
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  geometry.setAttribute('cheerLift', new THREE.Float32BufferAttribute(weights, 1));
  return geometry;
}

function combine(parts) {
  // Polyhedra are non-indexed; normalise before merging the small prototypes.
  const flat = parts.map(g => g.index ? g.toNonIndexed() : g);
  const result = mergeGeometries(flat);
  for (const g of flat) g.dispose();
  for (const g of parts) if (!flat.includes(g)) g.dispose();
  return result;
}

function cuboid(w, h, d, x, y, z, color = 0xffffff, lift = false) {
  return tint(new THREE.BoxGeometry(w, h, d).translate(x, y, z), color, lift);
}

// Use arc-length, not uniform angles: end-stand seats must not bunch together.
function ellipse(rx, rz) {
  const lengths = [0], steps = 1024;
  let x = rx, z = 0;
  for (let i = 1; i <= steps; i++) {
    const a = i / steps * TAU, nx = rx * Math.cos(a), nz = rz * Math.sin(a);
    lengths.push(lengths[i - 1] + Math.hypot(nx - x, nz - z));
    x = nx; z = nz;
  }
  return fraction => {
    const distance = fraction * lengths[steps];
    let lo = 0, hi = steps;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (lengths[mid] < distance) lo = mid; else hi = mid;
    }
    return (lo + (distance - lengths[lo]) / (lengths[hi] - lengths[lo])) / steps * TAU;
  };
}

export function createStadiumCrowd() {
  const group = new THREE.Group();
  group.name = 'stadium-supporters';
  group.userData.supporterCount = COUNT;
  group.userData.resources = {instancedBatches: 7, supporters: COUNT, scarves: 1280, flags: 32, textures: 0, lights: 0};
  const clock = {value: 0};
  const animated = new THREE.MeshStandardMaterial({
    color: 0xffffff, vertexColors: true, roughness: .9,
    emissive: 0x192d3a, emissiveIntensity: .3,
  });
  animated.onBeforeCompile = shader => {
    shader.uniforms.uCrowdTime = clock;
    shader.vertexShader = shader.vertexShader.replace('#include <common>', `
      #include <common>
      uniform float uCrowdTime;
      attribute vec2 supporterMotion;
      attribute float cheerLift;
    `).replace('#include <begin_vertex>', `
      #include <begin_vertex>
      float wave = smoothstep(0.25, 0.95, sin(uCrowdTime * 0.8 - supporterMotion.x * 2.0));
      float cheer = 0.5 + 0.5 * sin(uCrowdTime * 1.8 + supporterMotion.y);
      float bob = wave * 0.075 + cheer * 0.018;
      transformed.y += bob * smoothstep(0.0, 0.9, position.y);
      transformed.y += cheerLift * (wave * 0.23 + cheer * 0.035);
    `);
  };
  animated.customProgramCacheKey = () => 'arena-supporter-wave-v1';
  const flagsMaterial = new THREE.MeshStandardMaterial({
    color: 0xffffff, vertexColors: true, roughness: .85, side: THREE.DoubleSide,
    emissive: 0x243c35, emissiveIntensity: .25,
  });
  flagsMaterial.onBeforeCompile = shader => {
    shader.uniforms.uCrowdTime = clock;
    shader.vertexShader = shader.vertexShader.replace('#include <common>', `
      #include <common>
      uniform float uCrowdTime;
      attribute vec2 supporterMotion;
    `).replace('#include <begin_vertex>', `
      #include <begin_vertex>
      float edge = clamp(position.x / 1.35, 0.0, 1.0);
      transformed.z += edge * (sin(position.x * 5.0 - uCrowdTime * 1.3 + supporterMotion.y) * 0.16);
    `);
  };
  flagsMaterial.customProgramCacheKey = () => 'arena-supporter-flag-v1';
  const staticMaterial = new THREE.MeshStandardMaterial({color: 0xffffff, vertexColors: true, roughness: .8});
  const materials = [animated, flagsMaterial, staticMaterial], geometries = [], batches = [];
  function batch(name, geometry, count, material = animated) {
    geometries.push(geometry);
    geometry.setAttribute('supporterMotion', new THREE.InstancedBufferAttribute(new Float32Array(count * 2), 2));
    const mesh = new THREE.InstancedMesh(geometry, material, count);
    mesh.name = name;
    mesh.castShadow = false;
    mesh.receiveShadow = false;
    // Include the shader's 27cm arm extension and small flag deformation.
    mesh.boundingSphere = new THREE.Sphere(new THREE.Vector3(0, 14, 0), 128);
    mesh.boundingBox = new THREE.Box3(new THREE.Vector3(-104, 3, -75), new THREE.Vector3(104, 29, 75));
    group.add(mesh); batches.push(mesh);
    return mesh;
  }
  const bodies = batch('supporter-shirts-and-trousers', combine([
    cuboid(.43, .60, .25, 0, 1.02, 0),
    cuboid(.28, .69, .23, 0, .375, 0, 0x34414d),
  ]), COUNT);
  const heads = batch('supporter-heads', tint(new THREE.OctahedronGeometry(.185, 0).scale(.9, 1.05, .9).translate(0, 1.57, 0), 0xffffff), COUNT);
  const armParts = [];
  for (const sign of [-1, 1]) {
    const a = new THREE.Vector3(sign * .23, 1.30, 0), b = new THREE.Vector3(sign * .43, 1.98, .02);
    const direction = b.clone().sub(a);
    const arm = new THREE.CylinderGeometry(.055, .067, direction.length(), 4, 1, true);
    arm.applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction.normalize()));
    arm.translate(...a.add(b).multiplyScalar(.5).toArray());
    armParts.push(tint(arm, 0xffffff, true), tint(new THREE.OctahedronGeometry(.075, 0).translate(...b.toArray()), 0xffffff, true));
  }
  const arms = batch('supporter-raised-arms-and-hands', combine(armParts), COUNT);
  const scarves = batch('supporter-scarves', combine([
    cuboid(.99, .12, .055, 0, 2.01, .035, 0xffffff, true),
    cuboid(.105, .12, .065, -.31, 2.01, .035, 0x284d5d, true),
    cuboid(.105, .12, .065, .31, 2.01, .035, 0x284d5d, true),
  ]), COUNT / 4);
  const seats = batch('supporter-seat-backs', cuboid(.51, .42, .07, 0, .48, -.18), COUNT, staticMaterial);
  const flagGeometry = new THREE.PlaneGeometry(1.35, .83, 6, 2).translate(.675, 2.84, 0);
  tint(flagGeometry, 0xffffff);
  const flagColors = flagGeometry.getAttribute('color'), flagPositions = flagGeometry.getAttribute('position');
  for (let i = 0; i < flagColors.count; i++) {
    // Geometric stripes only, deliberately no team emblems or official marks.
    if (flagPositions.getY(i) < 2.65) flagColors.setXYZ(i, .18, .39, .30);
  }
  const flags = batch('supporter-flags', flagGeometry, 32, flagsMaterial);
  const poles = batch('supporter-flagpoles', tint(new THREE.CylinderGeometry(.025, .025, 3.35, 4).translate(0, 1.675, 0), 0xb9c8cd), 32, staticMaterial);
  const dummy = new THREE.Object3D(), color = new THREE.Color();
  function instance(mesh, index, p, hex, scale = 1) {
    dummy.position.set(p.x, p.y, p.z);
    dummy.rotation.set(0, p.yaw, 0);
    dummy.scale.set(1, scale, 1);
    dummy.updateMatrix();
    mesh.setMatrixAt(index, dummy.matrix);
    mesh.setColorAt(index, color.setHex(hex));
    mesh.geometry.getAttribute('supporterMotion').setXY(index, p.angle, p.phase);
  }
  let index = 0, scarfIndex = 0, flagIndex = 0;
  for (let tier = 0; tier < TIERS; tier++) {
    for (let row = 0; row < ROWS; row++) {
      const outerX = 77 + tier * 3.1, outerZ = 51 + tier * 2.5;
      // Each row is strictly on the existing 3.15m x 2.55m annular slab.
      const inset = row === 0 ? .77 : 2.20;
      const rx = outerX - inset, rz = outerZ - inset * 2.55 / 3.15;
      const angleAt = ellipse(rx, rz);
      for (let sector = 0; sector < SECTORS; sector++) {
        for (let seat = 0; seat < SEATS; seat++) {
          // Two unoccupied seat-widths per sector form sixteen radial aisles.
          const angle = angleAt((sector + (seat + 1.5) / (SEATS + 2)) / SECTORS);
          const x = rx * Math.cos(angle), z = rz * Math.sin(angle);
          const p = {x, y: 4.5 + tier * 2.5 + .025, z, angle,
            yaw: Math.atan2(-x / (rx * rx), -z / (rz * rz)), phase: index * 2.39996};
          const palette = z >= 0 ? SKY : AWAY;
          const shirt = palette[(seat + tier * 3 + row) % palette.length];
          const stature = .94 + (index % 7) * .015;
          instance(bodies, index, p, shirt, stature);
          instance(heads, index, p, SKIN[index % SKIN.length], stature);
          instance(arms, index, p, SKIN[index % SKIN.length], stature);
          instance(seats, index, p, z >= 0 ? 0x357e9c : 0xa3923c);
          if (index % 4 === 0) instance(scarves, scarfIndex++, p, z >= 0 ? 0xb3edff : 0xffe359, stature);
          index++;
        }
      }
    }
    if (tier === 0 || tier === 2) {
      const rx = 75.5 + tier * 3.1, rz = 49.8 + tier * 2.5, angleAt = ellipse(rx, rz);
      for (let sector = 0; sector < SECTORS; sector++) {
        const angle = angleAt(sector / SECTORS), x = rx * Math.cos(angle), z = rz * Math.sin(angle);
        const p = {x, y: 4.525 + tier * 2.5, z, angle, yaw: Math.atan2(-x, -z), phase: sector * .91};
        instance(poles, flagIndex, p, 0xffffff);
        instance(flags, flagIndex++, p, z >= 0 ? 0x72d7fb : 0xffdf52);
      }
    }
  }
  for (const mesh of batches) {
    mesh.instanceMatrix.needsUpdate = true;
    mesh.instanceColor.needsUpdate = true;
    mesh.geometry.getAttribute('supporterMotion').needsUpdate = true;
  }
  let disposed = false, lastTime = null;
  return {
    group,
    supporterCount: COUNT,
    update(timeSeconds) {
      if (disposed || !Number.isFinite(timeSeconds) || timeSeconds === lastTime) return;
      lastTime = timeSeconds;
      // All frequencies close after this interval; safe for very long sessions.
      clock.value = ((timeSeconds % (TAU * 100)) + TAU * 100) % (TAU * 100);
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      for (const mesh of batches) mesh.dispose();
      for (const geometry of geometries) geometry.dispose();
      for (const material of materials) material.dispose();
      group.removeFromParent(); group.clear();
    },
  };
}
