// Pure cartographic helpers. Overview relief uses real footprints only; landuse
// polygons are ground, never evidence for invented buildings or picking targets.
export function inRing(p, ring) {
  let hit = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const a = ring[i], b = ring[j];
    if ((a[1] > p[1]) !== (b[1] > p[1]) && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) hit = !hit;
  }
  return hit;
}
export const inPolygon = (p, rings) => Boolean(rings[0]?.length) && inRing(p, rings[0]) && !rings.slice(1).some(r => inRing(p, r));

// Ray hits on a facade lie exactly on the footprint boundary. Floating-point
// rounding must not turn a building tap into an unrelated ground point.
export function onOrInPolygon(p,rings,tolerance=.35){
  if(inPolygon(p,rings))return true;
  return rings.some(r=>r.some((a,i)=>distanceSegment(p,a,r[(i+1)%r.length])<=tolerance));
}

export function ringBounds(ring) {
  const box = {minX: Infinity, maxX: -Infinity, minZ: Infinity, maxZ: -Infinity};
  for (const [x, z] of ring) {
    box.minX = Math.min(box.minX, x); box.maxX = Math.max(box.maxX, x);
    box.minZ = Math.min(box.minZ, z); box.maxZ = Math.max(box.maxZ, z);
  }
  return box;
}

export function stableRank(x, z) {
  let h = Math.imul(Math.round(x), 374761393) ^ Math.imul(Math.round(z), 668265263);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

// Bounds + source identity, not feature order, tile-local coordinates or zoom.
// MultiPolygon parts have their own bounds even when they share an OSM ID.
export function footprintStyle(rings, id, top, base = 0) {
  const b = ringBounds(rings[0]);
  const key = [id ?? '', ...[b.minX, b.minZ, b.maxX, b.maxZ].map(n => Math.round(n * 1e5))].join(':');
  let hash = 2166136261;
  for (let i = 0; i < key.length; i++) hash = Math.imul(hash ^ key.charCodeAt(i), 16777619);
  hash >>>= 0;
  return {
    key, hash,
    material: top > 70 ? 'cyan' : hash % 7 === 0 ? 'sand' : hash % 11 === 0 ? 'mint' : 'cream',
    plinth: base > 0 ? 0 : Math.min(3.2, (top - base) * (.13 + (hash % 3) * .025)),
    roof: Math.min(.35 + (hash % 3) * .12, (top - base) * .05),
    roofUnit: hash % 4 === 0,
  };
}

// Deliberately flattened real footprints, not guessed towers. Never inflate.
export const overviewRelief = (top, base = 0) => Math.max(0, Math.min(6, top - base));

export function distanceSegment(p, a, b) {
  const dx = b[0] - a[0], dz = b[1] - a[1];
  const t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / (dx * dx + dz * dz || 1)));
  return Math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dz);
}

// Bounded cell fanout; pathological large features use a checked fallback list.
// Normal tree/road/building queries inspect only local bounding boxes.
export function createSpatialIndex(cell = 100, maxCells = 256) {
  const grid = new Map(), large = [];
  return {
    insert(box, value) {
      if (!Object.values(box).every(Number.isFinite) || box.minX > box.maxX || box.minZ > box.maxZ) return;
      const entry = {box, value};
      const x0 = Math.floor(box.minX / cell), x1 = Math.floor(box.maxX / cell);
      const z0 = Math.floor(box.minZ / cell), z1 = Math.floor(box.maxZ / cell);
      if ((x1 - x0 + 1) * (z1 - z0 + 1) > maxCells) { large.push(entry); return; }
      for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
        const key = `${x}:${z}`;
        if (!grid.has(key)) grid.set(key, []);
        grid.get(key).push(entry);
      }
    },
    *query(p) {
      for (const entries of [grid.get(`${Math.floor(p[0] / cell)}:${Math.floor(p[1] / cell)}`) || [], large]) {
        for (const {box, value} of entries) {
          if (p[0] >= box.minX && p[0] <= box.maxX && p[1] >= box.minZ && p[1] <= box.maxZ) yield value;
        }
      }
    },
  };
}

// The builder yields/checks cancellation between bounded batches of candidates.
// World-aligned sites and ranks keep trees stable across neighbouring tile LODs.
export function* treeCandidates({areas, rect, origin = [0, 0], step = 28, maxAttempts = 6000}) {
  let attempts = 0;
  const used = new Set();
  for (const rings of areas) {
    const b = ringBounds(rings[0]);
    const x0 = Math.max(rect.minX, b.minX), x1 = Math.min(rect.maxX, b.maxX);
    const z0 = Math.max(rect.minZ, b.minZ), z1 = Math.min(rect.maxZ, b.maxZ);
    for (let x = Math.ceil((x0 + origin[0]) / step) * step - origin[0]; x < x1; x += step) {
      for (let z = Math.ceil((z0 + origin[1]) / step) * step - origin[1]; z < z1; z += step) {
        if (++attempts > maxAttempts) return;
        const wx = Math.round(x + origin[0]), wz = Math.round(z + origin[1]), key = `${wx}:${wz}`;
        const rank = stableRank(wx, wz), p = [x + (rank - .5) * 8, z + (stableRank(wz, wx) - .5) * 8];
        if (used.has(key) || p[0] < rect.minX || p[0] >= rect.maxX || p[1] < rect.minZ || p[1] >= rect.maxZ || !inPolygon(p, rings)) {
          yield null; continue;
        }
        used.add(key);
        yield {point: p, rank};
      }
    }
  }
}
