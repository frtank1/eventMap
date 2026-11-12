// Content only: no URLs, scripts, shaders, textures, or ObjectLoader input inside models.
export const ENGINE = 1;
const MAX_FILE = 6 * 1024 * 1024, MAX_PACK = 18 * 1024 * 1024;
const finite = (n, lo, hi) => typeof n === 'number' && Number.isFinite(n) && n >= lo && n <= hi;
const integer = (n, lo, hi) => Number.isInteger(n) && finite(n, lo, hi);
const demand = (ok, reason) => { if (!ok) throw new Error(reason); };
export function validateManifest(m) {
  demand(m?.schemaVersion === 1 && m.engineVersion === ENGINE, 'incompatible-version');
  demand(typeof m.release === 'string' && /^[a-zA-Z0-9._-]{1,64}$/.test(m.release), 'invalid-release');
  demand(Array.isArray(m.models) && m.models.length > 0 && m.models.length <= 12, 'model-count');
  let bytes = 0; const ids = new Set();
  for (const x of m.models) {
    demand(typeof x.id === 'string' && /^[a-z0-9-]{1,64}$/.test(x.id) && !ids.has(x.id), 'model-id');
    ids.add(x.id);
    demand(typeof x.title === 'string' && x.title.length > 0 && x.title.length <= 120, 'model-title');
    demand(finite(x.lat, 50.9, 51.4) && finite(x.lng, 71.1, 71.9), 'outside-city');
    demand(finite(x.height, 1, 400) && finite(x.radius, 1, 500), 'model-bounds');
    demand(typeof x.sha256 === 'string' && /^[a-f0-9]{64}$/.test(x.sha256) && x.file === `assets/${x.sha256}.json`, 'asset-path');
    demand(integer(x.bytes, 1, MAX_FILE), 'file-budget'); bytes += x.bytes;
  }
  demand(bytes <= MAX_PACK, 'pack-budget'); return m;
}
export function validateModel(m, id) {
  demand(m?.schemaVersion === 1 && m.id === id, 'model-schema');
  demand(Array.isArray(m.materials) && m.materials.length > 0 && m.materials.length <= 64, 'material-count');
  for (const x of m.materials) {
    demand(integer(x.color, 0, 0xffffff) && integer(x.emissive, 0, 0xffffff), 'material-color');
    demand(finite(x.roughness, 0, 1) && finite(x.metalness, 0, 1) && finite(x.opacity, 0, 1) && finite(x.emissiveIntensity, 0, 10), 'material-value');
    demand(typeof x.transparent === 'boolean' && [0, 2].includes(x.side), 'material-type');
  }
  demand(Array.isArray(m.meshes) && m.meshes.length > 0 && m.meshes.length <= 45, 'mesh-count');
  let vertices = 0, indices = 0;
  for (const x of m.meshes) {
    demand(integer(x.material, 0, m.materials.length - 1) && typeof x.detail === 'boolean', 'mesh-material');
    demand(Array.isArray(x.positions) && x.positions.length % 3 === 0 && x.positions.length > 0, 'positions');
    vertices += x.positions.length / 3;
    demand(vertices <= 250000 && x.positions.every(n => finite(n, -1000, 1000)), 'vertex-budget');
    demand(Array.isArray(x.normals) && x.normals.length === x.positions.length && x.normals.every(n => finite(n, -1.001, 1.001)), 'normals');
    demand(Array.isArray(x.indices) && x.indices.length % 3 === 0 && x.indices.length > 0, 'indices');
    indices += x.indices.length;
    demand(indices <= 1500000 && x.indices.every(n => integer(n, 0, x.positions.length / 3 - 1)), 'index-budget');
  }
  return m;
}
export async function hash(bytes) {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), x => x.toString(16).padStart(2, '0')).join('');
}
export function manifestUrl(value) {
  const u = new URL(value);
  demand(u.protocol === 'https:' || (u.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(u.hostname)), 'https-required');
  demand(!u.username && !u.password && !u.search && !u.hash, 'invalid-url'); return u;
}
async function readLimited(response, limit) {
  demand(response.ok, `http-${response.status}`);
  const declared = Number(response.headers.get('content-length'));
  demand(!declared || declared <= limit, 'response-too-large');
  const reader = response.body?.getReader();
  if (!reader) { const bytes = new Uint8Array(await response.arrayBuffer()); demand(bytes.length <= limit, 'response-too-large'); return bytes; }
  const parts = []; let length = 0;
  try {
    while (true) {
      const {value, done} = await reader.read(); if (done) break;
      length += value.length; demand(length <= limit, 'response-too-large'); parts.push(value);
    }
  } catch (e) { await reader.cancel(); throw e; }
  const out = new Uint8Array(length); let offset = 0;
  for (const part of parts) { out.set(part, offset); offset += part.length; }
  return out;
}
const decode = bytes => JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(bytes));

// One transaction, one last-known-good pack. No partial manifest activation.
export class BrowserCache {
  async db() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open('eventmap-city-v1', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('packs');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error('cache-blocked'));
    });
  }
  async read(key) {
    const db = await this.db();
    try { return await new Promise((resolve, reject) => {
      const t = db.transaction('packs'); const r = t.objectStore('packs').get(key);
      r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error);
    }); } finally { db.close(); }
  }
  async write(key, pack) {
    const db = await this.db();
    try { await new Promise((resolve, reject) => {
      const t = db.transaction('packs', 'readwrite');
      // Bound disk use even after changing the configured content host.
      t.objectStore('packs').clear(); t.objectStore('packs').put(pack, key);
      t.oncomplete = resolve; t.onerror = () => reject(t.error); t.onabort = () => reject(t.error);
    }); } finally { db.close(); }
  }
}

export class ContentStore {
  constructor({seed, cache = new BrowserCache(), fetcher = (...args) => fetch(...args), onStatus = () => {}, activate = () => {}, timeout = 20000}) {
    this.seed = seed; this.cache = cache; this.fetcher = fetcher; this.onStatus = onStatus;
    this.activate = activate; this.timeout = timeout; this.current = seed; this.busy = false; this.stopped = false;
  }
  status(state, extra = {}) { this.onStatus({state, release: this.current.manifest.release, ...extra}); }
  async checkPack(pack) {
    validateManifest(pack.manifest); const models = {};
    for (const entry of pack.manifest.models) {
      const bytes = pack.files[entry.sha256];
      demand(bytes instanceof Uint8Array && bytes.length === entry.bytes, 'asset-size');
      demand(await hash(bytes) === entry.sha256, 'asset-hash');
      models[entry.id] = validateModel(decode(bytes), entry.id);
    }
    return {manifest: pack.manifest, models};
  }
  async start(url) {
    this.url = manifestUrl(url).href;
    try {
      const cached = await this.cache.read(this.url);
      if (cached) { const ready = await this.checkPack(cached); if (!this.stopped) { await this.activate(ready); this.current = ready; } }
    } catch { this.status('cache-unavailable'); }
    if (this.stopped) return;
    this.status('ready'); await this.update();
  }
  async update() {
    if (this.busy || !this.url || this.stopped) return;
    this.busy = true; this.status('checking');
    const controller = new AbortController(); this.controller = controller;
    const timer = setTimeout(() => controller.abort(), this.timeout);
    const get = async (url, limit) => readLimited(await this.fetcher(url, {
      signal: controller.signal, credentials: 'omit', redirect: 'error', cache: 'no-store',
    }), limit);
    try {
      const manifest = validateManifest(decode(await get(this.url, 64 * 1024)));
      // Release label is not an identity; publishers can roll back to an earlier pack.
      if (JSON.stringify(manifest) === JSON.stringify(this.current.manifest)) { this.status('current'); return; }
      const pack = {manifest, files: {}};
      let cached; try { cached = await this.cache.read(this.url); } catch { /* optional disk cache */ }
      for (const entry of manifest.models) {
        const saved = cached?.files?.[entry.sha256];
        pack.files[entry.sha256] = saved instanceof Uint8Array && saved.length === entry.bytes && await hash(saved) === entry.sha256
          ? saved : await get(new URL(entry.file, this.url).href, entry.bytes);
      }
      const ready = await this.checkPack(pack);
      if (this.stopped) return;
      // Rendering builds the replacement before removing the current scene.
      await this.activate(ready); this.current = ready;
      try { await this.cache.write(this.url, pack); this.status('updated'); }
      catch { this.status('updated-memory-only'); }
    } catch (e) { if (!this.stopped) this.status('kept-working', {reason: String(e.message || 'network').slice(0, 100)}); }
    finally { clearTimeout(timer); this.busy = false; }
  }
  stop() { this.stopped = true; this.controller?.abort(); }
}
