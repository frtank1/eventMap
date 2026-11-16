import {ShapeUtils, Vector2} from 'three';
import baiterekData from '../source/baiterek.json';
import arenaData from '../source/arena.json';
import {WORLD_CENTER, LON_SCALE, METERS} from './world-state.js';

// Replacement evidence, not a POI catchment area. IDs below come from the two
// bundled OSM snapshots and the source-part lists in runtime/city-landmarks.js.
// In particular, the train stations named "Байтерек" / "Астана Арена" are NOT
// the monuments. Never select a replacement by name, nearest anchor or radius.
const sourceIds = {
  baiterek: ['230401644', '230401645'],
  arena: ['166223262'],
  adp: ['1373723368', '1373723370', '1373723369', '1373723363', '1373723364', '1373723365', '1373723366', '1373723367'],
  ansar: ['230403254', '258752611'],
  beijing: ['230403277', '460047238'],
  moscow: ['1530050672', '258345462', '230403257'],
  petersburg: ['1530050670', '230403283', '258351821'],
  library: ['230358855'],
};

export const LANDMARK_MIN_COVERAGE = .9;
const MAX_VERTICES = 512, EPS = 1e-8;
const empty = Object.freeze([]), catalogue = new Map();
for (const [id, ids] of Object.entries(sourceIds)) {
  const data = id === 'arena' ? arenaData : baiterekData;
  const records = ids.flatMap(sourceId => {
    const building = data.buildings.find(b => String(b.id) === sourceId);
    if (!building) return [];
    // Own copies: freezing this module's evidence must not freeze other users'
    // JSON imports, or allow callers to mutate the shared source snapshot.
    const ring = Object.freeze(building.points.map(p => Object.freeze([...p])));
    return [Object.freeze({sourceId, rings: Object.freeze([ring]), source: data.source,
      license: data.license, retrieved: data.retrieved,
      file: `source/${id === 'arena' ? 'arena' : 'baiterek'}.json`})];
  });
  catalogue.set(id, Object.freeze(records));
}

/** Read-only, provenance-bearing [longitude, latitude] rings; unknown IDs -> []. */
export function getLandmarkFootprints(id, data) {
  // `needs_review` can flag model height/name/placement, while these individual
  // source polygons are still verified evidence. The anchor guard below remains
  // mandatory; a misplaced authored model cannot erase the real building.
  if(data){const place=data.places.find(p=>p.id===id);return place&&place.review.status!=='not_found'?place.footprints:empty;}
  return catalogue.get(id) || empty;
}

const cross = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
const same = (a, b) => a[0] === b[0] && a[1] === b[1];
const validCoordinate = p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite)
  && Math.abs(p[0]) <= 180 && Math.abs(p[1]) <= 90;
const project = p => [(p[0] - WORLD_CENTER[0]) * LON_SCALE, -(p[1] - WORLD_CENTER[1]) * METERS];
function area(ring) {
  let value = 0;
  for (let i = 1; i + 1 < ring.length; i++) value += cross(ring[0], ring[i], ring[i + 1]);
  return Math.abs(value) / 2;
}
function bounds(ring) {
  const b = {minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity};
  for (const [x, y] of ring) {
    b.minX = Math.min(b.minX, x); b.maxX = Math.max(b.maxX, x);
    b.minY = Math.min(b.minY, y); b.maxY = Math.max(b.maxY, y);
  }
  return b;
}
const overlaps = (a, b) => a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY;
function onSegment(p, a, b) {
  return Math.abs(cross(a, b, p)) <= EPS && p[0] >= Math.min(a[0], b[0]) - EPS
    && p[0] <= Math.max(a[0], b[0]) + EPS && p[1] >= Math.min(a[1], b[1]) - EPS
    && p[1] <= Math.max(a[1], b[1]) + EPS;
}
// -1 outside, 0 on boundary, 1 inside; holes are handled by the caller.
function location(p, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const a = ring[j], b = ring[i];
    if (onSegment(p, a, b)) return 0;
    if ((a[1] > p[1]) !== (b[1] > p[1]) && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
  }
  return inside ? 1 : -1;
}
function intersects(a, b, c, d) {
  const abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
  return ((abC > EPS && abD < -EPS || abC < -EPS && abD > EPS)
    && (cdA > EPS && cdB < -EPS || cdA < -EPS && cdB > EPS))
    || onSegment(a, c, d) || onSegment(b, c, d) || onSegment(c, a, b) || onSegment(d, a, b);
}
function validRings(rings) {
  // Fail open on ambiguous/self-crossing polygons rather than trusting a
  // triangulator's best-effort repair as a reason to erase map geometry.
  for (let r = 0; r < rings.length; r++) {
    const ring = rings[r];
    if (r && location(ring[0], rings[0]) !== 1) return false;
    for (let s = r; s < rings.length; s++) {
      const other = rings[s];
      if (r && s > r && (location(ring[0], other) >= 0 || location(other[0], ring) >= 0)) return false;
      for (let i = 0; i < ring.length; i++) for (let j = s === r ? i + 1 : 0; j < other.length; j++) {
        if (r === s && (j === i + 1 || i === 0 && j === ring.length - 1)) continue;
        if (intersects(ring[i], ring[(i + 1) % ring.length], other[j], other[(j + 1) % other.length])) return false;
      }
    }
  }
  return true;
}
function prepare(rings) {
  if (!Array.isArray(rings) || !rings.length || rings.length > MAX_VERTICES / 3) return null;
  const projected = []; let vertices = 0;
  for (const ring of rings) {
    if (!Array.isArray(ring) || ring.length < 3 || (vertices += ring.length) > MAX_VERTICES) return null;
    const clean = [];
    for (const p of ring) {
      if (!validCoordinate(p)) return null;
      const q = project(p);
      if (!clean.length || !same(q, clean.at(-1))) clean.push(q);
    }
    if (clean.length > 1 && same(clean[0], clean.at(-1))) clean.pop();
    if (clean.length < 3 || area(clean) <= EPS) return null;
    projected.push(clean);
  }
  if (!validRings(projected)) return null;
  const expectedArea = area(projected[0]) - projected.slice(1).reduce((n, ring) => n + area(ring), 0);
  if (expectedArea <= EPS) return null;
  const vectors = projected.map(ring => ring.map(p => new Vector2(...p)));
  const faces = ShapeUtils.triangulateShape(vectors[0], vectors.slice(1)), points = projected.flat();
  const triangles = faces.map(face => {
    const p = face.map(i => points[i]);
    if (cross(...p) < 0) p.reverse();
    return {points: p, box: bounds(p), area: area(p)};
  }).filter(t => t.area > EPS);
  const total = triangles.reduce((n, t) => n + t.area, 0);
  if (!triangles.length || Math.abs(total - expectedArea) > Math.max(EPS, expectedArea * 1e-7)) return null;
  return {rings: projected, triangles, area: total, box: bounds(projected[0])};
}
function triangleIntersection(a, b) {
  let result = a;
  for (let i = 0; i < 3 && result.length; i++) {
    const start = b[i], end = b[(i + 1) % 3], input = result;
    result = [];
    let previous = input.at(-1), previousSide = cross(start, end, previous);
    for (const point of input) {
      const side = cross(start, end, point);
      if ((side >= 0) !== (previousSide >= 0)) {
        const t = previousSide / (previousSide - side);
        result.push([previous[0] + (point[0] - previous[0]) * t, previous[1] + (point[1] - previous[1]) * t]);
      }
      if (side >= 0) result.push(point);
      previous = point; previousSide = side;
    }
  }
  return area(result);
}
function coverage(candidate, source) {
  if (!candidate || !source || !overlaps(candidate.box, source.box)) return 0;
  let intersection = 0;
  for (const a of candidate.triangles) for (const b of source.triangles) {
    if (overlaps(a.box, b.box)) intersection += triangleIntersection(a.points, b.points);
  }
  return Math.max(0, Math.min(1, intersection / candidate.area));
}

/** Fraction of candidate area covered by source (0..1), with holes respected.
 * Both arguments are ONE polygon's WGS84 [outerRing, ...holes], not MultiPolygon.
 * Invalid/oversized geometry returns 0. No inputs are modified.
 */
export function footprintCoverage(candidateRings, sourceRings) {
  return coverage(prepare(candidateRings), prepare(sourceRings));
}

/** Build once per activated model pack, then pass as createCityStream's callback:
 *   const excludeBuilding = createLandmarkExclusion(pack.manifest.models);
 *   excludeBuilding(lng, lat, rings); // raw WGS84 rings of EACH polygon
 * Fallback quarter: excludeBuilding(lng, lat, [building.points]).
 *
 * Deliberately conservative limitations:
 * - Only explicitly matched source landmarks have exclusion evidence.
 *   Others remain visible; authored bounds/plazas are not surveyed footprints.
 * - Active entry coordinates must lie in one of that landmark's source parts;
 *   moved/invalid anchors disable exclusion instead of erasing the old site.
 * - At least 90% of a candidate must overlap ONE source part. No union of
 *   overlapping parts (double counting), bbox, name, radius or centroid test.
 * - Missing rings (including legacy point-only decoration calls), malformed
 *   rings and >512 input vertices KEEP the candidate. Small edge differences
 *   are tolerated, but changed/split source geometry can leave duplicates.
 * - OSM snapshot provenance is retained; this does not add addresses, validate
 *   current reality, or repair authored model placement/architectural accuracy.
 */
export function createLandmarkExclusion(entries = [], data) {
  const sources = [], seen = new Set();
  for (const entry of Array.isArray(entries) ? entries : []) {
    if (!entry || !validCoordinate([entry.lng, entry.lat]) || seen.has(entry.id)) continue;
    const parts = getLandmarkFootprints(entry.id,data).map(record => prepare(record.rings)).filter(Boolean);
    const anchor = project([entry.lng, entry.lat]);
    if (!parts.some(p => location(anchor, p.rings[0]) >= 0 && !p.rings.slice(1).some(h => location(anchor, h) >= 0))) continue;
    seen.add(entry.id); sources.push(...parts);
  }
  return (_lng, _lat, rings) => {
    if (!sources.length || !rings) return false;
    // Broad phase can only KEEP candidates, never exclude them. Most city
    // buildings are far from these few landmarks and need no triangulation.
    const outer = rings[0];
    if (!Array.isArray(outer) || outer.length < 3 || outer.length > MAX_VERTICES || !outer.every(validCoordinate)) return false;
    const box = bounds(outer.map(project));
    if (!sources.some(source => overlaps(box, source.box))) return false;
    const candidate = prepare(rings);
    if (!candidate) return false;
    return sources.some(source => source.area / candidate.area >= LANDMARK_MIN_COVERAGE - 1e-7
      && coverage(candidate, source) >= LANDMARK_MIN_COVERAGE - 1e-7);
  };
}
