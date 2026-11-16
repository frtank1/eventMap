# Imported Quaternius street props

Actual vendor geometry from the **free Standard** edition of [Downtown City
MegaKit](https://quaternius.com/packs/downtowncitymegakit.html), downloaded from
the [official Quaternius itch.io page](https://quaternius.itch.io/downtown-city-megakit)
on 2026-11-16 via “No thanks, just take me to the downloads”. No account, payment,
checkout, or explicit terms acceptance was used.

Archive: `Downtown City MegaKit[Standard].zip`, itch.io upload `17615373`.
SHA-256: `5b1a945576d54cdbb4ccc9c3d52711e6d530da74c74c586407ecc28b165335da`.
The original archive license is copied byte-for-byte as `License_Standard.txt`:
**CC0 1.0 Universal**. Original selected `.gltf`/`.bin` files are retained for
auditing and offline reproduction; these are data, not executable code.
The full 223 MB archive is **not** part of the repository or runtime bundle.

| Runtime kind | Original glTF mesh | Triangles | Batches | Size (metres, X/Y/Z) |
| --- | --- | ---: | ---: | --- |
| `bollard` | `Prop_Bollard` | 190 | 1 | 0.216 / 0.892 / 0.227 |
| `planter` | `Prop_Planter_Single` | 118 | 2 | 2 / 0.6 / 2 |
| `planter-low` | `Sidewalk_Planter` | 598 | 2 | 1.973 / 0.506 / 1.765 |

Bench, bin, streetlamp and bike rack models are absent from the downloaded free
archive. They are not represented by fabricated or procedural substitutes.
No NYC buildings were imported. Planters include the original soil surface,
not vegetation; any planting added by the quarter remains separate original art.

## Deterministic adaptation

Original indexed triangles and normals are retained. Node transforms are baked,
the X/Z footprint is centred, and the lowest vertex is placed at Y=0. Position
and normal values are rounded to six decimals. No mesh simplification or shape
replacement is applied.

Original texture atlases (base colour, normal, ORM), both UV sets and wear
channels are intentionally omitted. This is an explicit **flat-colour palette
adaptation**, not texture-faithful rendering. Source `MI_Trim_MetalConcrete` is
mapped to `CITY_PALETTE.dark` for bollards and `CITY_PALETTE.sand` for planter
shells; `MI_Dirt` maps to `CITY_PALETTE.trunk`. Materials respond to the existing
day/night palette. Winter/newyear themes tint the stone/soil; this does not add
snow geometry. Original double-sided rendering is retained.

## Runtime integration (left to the quarter owner)

```js
import {createQuaterniusProps} from './quaternius-props.js';
const props = createQuaterniusProps({sites: [
  {kind: 'planter', x: 22, z: -40, y: groundY, yaw: 0, scale: 1},
  {kind: 'bollard', x: 25, z: -40, y: groundY},
]});
quarterGroup.add(props.group);
props.setAppearance(night, theme); // theme: default | winter | newyear
props.update(cameraDistance, quality); // quality: balanced | low
// On quarter teardown:
props.destroy();
```

Coordinates are quarter-local metres. `y` is the supplied ground level (default
0); the caller must place props on the correct plaza/path/road surface and
avoid entrances, landmarks and event interaction areas. `yaw` is radians;
`scale` is positive uniform scale (default 1, maximum 10). Unknown kinds and
nonfinite/invalid transforms are skipped and counted in `stats()`.

All sites of a kind share one geometry per original material primitive, with
shared role materials. Maximum: **5 instanced draw batches, 3 materials, zero
textures/lights/network requests**. At most 256 accepted sites; visible geometry
is capped at 60,000 triangles (low: 128 sites / 25,000 triangles). Distance
culling has hysteresis; bollards disappear sooner than planters. Bounds include
all allocated instances so quality changes cannot incorrectly cull restored
instances. `destroy()` is idempotent and disposes instance buffers, geometries,
and shared materials once. Separate controllers own separate resource sets.

`source/quaternius-props.json` is code-free, includes archive/per-file/license
hashes, and is bundled once with the engine through esbuild's existing JSON
loader. Original audit files and this license are not fetched at runtime.
No OTA schema or backend changes are required.

## Reproduce and test

From `02_Code/game-city`:

```sh
node import-quaternius.mjs --download /absolute/temp/path/standard.zip
node import-quaternius.mjs --archive /absolute/temp/path/standard.zip
node --test test/quaternius-props.test.mjs
```

Use a new destination for download; existing files are never overwritten by
the downloader. Import requires the pinned archive hash and reads only seven
named entries with `unzip -p`; nothing from the archive is executed. The three
original glTF files refer to omitted texture PNGs, so they are geometry audit
fixtures, not self-contained texture-faithful previews. Normal tests rebuild
all runtime meshes from these fixtures and compare the exact result.
