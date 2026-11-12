# Local static landmark assets

Generated from the existing original procedural THREE models, not downloaded
models or architectural scans. Runtime/native/backend integration is out of scope.

From `02_Code/game-city`, run with the installed Node (no dependencies needed):

```sh
node --experimental-vm-modules export-models.mjs
node --experimental-vm-modules check-export.mjs
```

`manifest.json` and `generated/seed.js` stay on **pilot-1**. The seed has four
literal JSON imports: the manifest and exactly its three model assets, for esbuild.
No source model code is imported by the seed.

**TEST RELEASE ONLY:** `releases/pilot-2.json` demonstrates a content update. Only
Akorda's ivory facade material changes, from `0xe7dfcc` to `0xe9dfce`. It is not an
architectural correction. All geometry and other properties are identical; the
Baiterek and Arena asset hashes are unchanged. It does not activate itself.

Assets are UTF-8 compact JSON followed by one LF. SHA-256 covers those exact bytes.
Hash-named files are append-only; reruns verify existing bytes and never overwrite
them. Historical assets are retained. No timestamps, random IDs, URLs, texture
references, shaders, or executable code are included in asset/manifest JSON.

Coordinates are meters in the original THREE right-handed, Y-up local space.
All visible architectural transforms are baked into positions and normalized
normals, including Akorda's source yaw (-1.823 radians); only its geographic root
translation is removed. Positions use three decimals and normals four. Vertices
are indexed/deduplicated by position plus normal within material/detail buckets,
preserving hard edges. `detail` follows `model.detail` or `facade-details` ancestry.

Hidden subtrees (including Arena's closed roof panels), activity, crowd, match,
simulation groups, and light nodes are excluded. Visible architectural lighting
fixtures/trim remain ordinary meshes with scalar materials. Civic snow shader
hooks are discarded; source daytime surface values are retained. Match and crowd
constructors are inert VM-only dependencies, so their simulations never execute.
Only Akorda is retained from the civic collection; no other civic model is saved.

Arena uses `source/arena.json` center: longitude **71.4025**, latitude **51.10806**.
Its height 55 / radius 155 use the original map POI framing in `source/app.js` (the
static roof geometry is lower). Baiterek uses height 105 / radius 42; Akorda 86 / 80.
Limits per asset are 6,000,000 bytes, 250,000 vertices, and 45 merged meshes.

The check script is read-only. It validates exact schemas, finite geometry and
dimensions, precision, normals, indices, deduplication, resource budgets, manifest
hashes/byte sizes/coordinates, seed imports, and the isolated test-release change.
It regenerates twice in independent VMs with different random seeds and compares
all bytes with the checked-in output. It also tests hierarchy transforms,
visibility, detail inheritance, materials, and reflection/back-side winding.
