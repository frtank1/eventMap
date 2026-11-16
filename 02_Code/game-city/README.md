# Mobile game-city pilot

Local adaptation of the existing Astana visualisation; the hosted private Site is untouched.

## Current reference quarter: Nūrjol Garden — 16 November 2026

The shared engine now uses `nurjol-garden-2`. This is a working map pass, not an
image mockup. Baiterek's authored quarter stays active when network tiles finish
loading; it and streamed geometry remain mutually exclusive. Leaving its bounded
area restores whole-city streaming with hysteresis. Nearby catalog landmarks are
shown too: suppressing their source footprints must not leave empty building sites.

- Shared warm stone / terracotta / teal palette, larger grouped tree crowns,
  facade bays, podiums and roof edges; finer, filtered individual night windows,
  warm ground light pools and tree-contact shading. No per-window point lights or
  fullscreen SSAO. Buildings retain source footprints and heights; street dressing
  is stylised, not evidence of actual installations or entrances.
- **Actual Quaternius Standard CC0 geometry is now imported**: bollard and two
  planter types. Forty-seven placements use five instanced batches / 11,690
  triangles, zero texture requests and zero lights. The original files, license,
  hashes and reproducible importer are in `art/quaternius/`. Benches, lamps,
  landmarks and the avatar remain our procedural models. No claim that an entire
  vendor city kit or Alba/Pocket City art was imported.
- Avatar editor and walking circuit have explicit planting clearance. Event
  balloons/covers, opening events, address/entrance picking, existing native
  creation callbacks, avatar appearance persistence, day/night, seasonal material
  uniforms and three opt-in effects remain connected. GPS friends/multiplayer are
  **not** added by this pass.
- New **Auto quality** starts balanced (DPR capped at 1.35), and latches to low
  (DPR 1) only after sustained slow measured frames. Warmup, pauses, background
  and blocked frames do not trigger a false downgrade; manual overrides remain.
  Existing scene cap is 30 FPS, not a 60 FPS promise. Trees, effects and small props
  have bounded per-quality budgets. Picking uses a spatial index; balloon layout
  caches chrome bounds instead of repeatedly forcing layout every frame.

Verification for this pass: **156 JS tests**, **22 map desktop tests**, Android
`:androidApp:assembleDebug`, and deterministic/static-schema export checks pass.
Browser checks exercise the actual engine: event-cover opening, local join/cancel,
avatar editing/persistence, day/night, effects and whole-city navigation. These use
explicit local fixtures, not real attendance or an API publication.

### Reproducible performance check and remaining gates

Open `http://127.0.0.1:8784/?stage1=1&bubbles=1&visualperf=1`, select a scenario and
press Start. The local-only harness takes 3 s warmup + 12 s of **rendered** frame
intervals, excludes hidden/paused frames and exports JSON. It does not use a
second requestAnimationFrame loop as a fake FPS counter. Keep the viewport and
quality fixed; do not move the camera during the measurement.

On this Mac's 1280×720 in-app browser, balanced/auto, DPR 1.35, ten fixture events,
effects off, final 12-second runs measured:

| Scenario | Frames | FPS | p95 interval | Draws | Triangles |
|---|---:|---:|---:|---:|---:|
| Baiterek day | 353 | 29.4 | 34.2 ms | 104 | 219,403 |
| Baiterek night | 359 | 29.9 | 34.0 ms | 107 | 220,337 |

Earlier same-pass Arena/full-city runs were approximately 30 FPS / p95 34.2 ms.
This is **not physical-phone
performance**, battery/thermal endurance or cold-start acceptance. Detailed current
quarter is about 104 daytime / 107 nighttime draws and 219k–221k triangles; the
overview measured 167 draws / 369k triangles. Props alone are only five draws.
Screenshots are under `../androidApp/build/reports/city-preview/` (Git-ignored):
`nurjol-2026-11-16-{day,night,avatar,created-event,performance-day,performance-night}.png`.
The final browser functional pass created/opened/ended a local event and checked
join/cancel on a fixture. The economical dropdown changed DPR to 1 and reduced
the visible scene from 104 draws / 219,403 triangles to 96 / 180,447, then restored
Auto. No browser error messages were captured in the final render/benchmark pass.

Android API 36 / WebView 133 displayed the new scene and the real native address /
main-entrance sheet; selecting Baiterek's main entrance transferred
`51.1282731, 71.4305449` into the local creation form. A final native creation/result
and FPS check could not be completed because window input stopped working after
Mac lock/unlock. The rebuilt APK is not proof of that end-to-end result. Final
physical Android, iOS, production API → created event → cover on a second device,
and forced context-loss acceptance remain required. Existing full-city address and
landmark accuracy limitations below still apply.

Engine, native bundle and backend content are staged **locally only**. No push or
deployment was performed. This reference-quarter pass does not mean every Astana
building has bespoke art. Earlier dated sections below are historical snapshots;
this section supersedes their rendering/verification counts.

## Geographic identity and addresses — 16 November 2026

The art pass did **not** make this a surveyed city model. This follow-up fixes
specific identity/placement problems without claiming every building is correct.

- `source/astana-addresses.json`: 37,465 complete street + house-number records
  from OpenStreetMap, snapshot `2026-11-16T05:43:36Z`, ODbL-1.0, 4.02 MB.
  It covers the product bounds, not an administrative-city completeness guarantee.
  Reproduce with `node import-addresses.mjs` (manual, one bounded Overpass extract).
  The importer whitelists map fields and preserves the previous file on failure.
  The public Overpass instance is **not** used as an application backend.
- The data has an independent **server geography channel** (and bundled
  `addresses.json` / `places.json` fallback), with a spatial index. The catalog
  contains 19 explicit landmark → OSM mappings. Ordinary
  buildings require an exact source reference, matching actual catalog rings
  (≤1 m vertex quantisation tolerance, holes retained), or address nodes inside
  the polygon; a bounding box only narrows candidates and never proves identity.
  Ordinary streamed footprints without this evidence remain unaddressed even if
  a bbox looks similar; full-city address footprint import remains future work.
  No nearest-street inference. Ambiguous/missing
  data is shown explicitly. This is an OSM match, not official cadastral validation.
- A facade-boundary tap remains a building tap (35 cm numerical tolerance), and
  creation keeps the tapped coordinates instead of a possibly outside centroid.
  The native place sheet shows address/source and passes the address into the
  existing event-location name (255-character prefill). A door can be explicitly
  selected; only then do its coordinates replace the tapped point. A database
  Place registry, address-search endpoint and persisted place FK are **not**
  implemented; this is a versioned public read-only catalog.
- MVT IDs can group polygons: view-local footprint IDs are no longer labelled
  as raw OSM IDs. `sourceRef` identifies the matched source address object.
- `source/astana-places.json`: 19 landmarks / 34 source polygons / 27 tagged
  entrance nodes at eight objects; snapshot `2026-11-16T06:10:36.000Z`. All 22
  previously reviewed source polygons are retained, including building parts.
  The remaining eleven radius masks are removed: only ≥90% real polygon overlap
  can suppress a base building. A model anchor outside its source footprint
  disables suppression rather than hiding the real building. Decoration clearance
  still uses radii, not building identity. This is not a whole-city survey.
- Entrances must belong to a matched source boundary; runtime also checks ≤2 m
  boundary tolerance. Private/no/service/emergency/exit-only candidates are not
  offered; at most eight choices cross the bridge, main first. `needs_review`
  places do not offer entrances yet. OSM door/access/wheelchair tags are not proof
  of current public access, permission to hold an event, or accessibility on site.
- Arena's entire root is rotated 76.59927° to align its long axis with snapshot
  object `166223262`, including its child activity geometry. Anchors are not shifted
  to polygon centroids: a centroid is not proof of an entrance or surveyed origin.

Audit results: 15 `matched`, four `needs_review`. This does not certify facade
accuracy or exact modeled heights. Remaining concrete corrections:

| Object | Outstanding evidence / model issue |
|---|---|
| Abu Dhabi Plaza | Model ≈313 m vs one OSM height tag 382 m; footprints verified, height not resolved |
| EXPO sphere | Current source/operator use Alem.ai; model label/use needs updating after review |
| National Museum | Authored anchor ≈44.7 m outside actual building outline; asymmetric model needs placement/size work, not automatic centroid snapping |
| Қазақ Елі | Only base footprint verified; full colonnade/composition and height interpretation remain unverified |

The catalog records per-object source URLs, footprint dimensions and review notes.
It does **not** silently move models or turn decorative stairs into entrance nodes.

### Publishing geography without an APK

`GET /world/places-manifest.json` points to two SHA-256 JSON assets under
`/world/assets/`. The client validates both files before activation; a distinct
IndexedDB cache retains the last working pair and the app bundles a reserve copy.
Open/resume (five-minute throttle)/manual refresh can install compatible updates.
Address-only updates do not rebuild meshes or move the camera. Geometry updates
rebuild exclusions, keeping the camera, events and character state.

One initial app update is required for this reader. After that, from `02_Code/game-city`:

```sh
node import-addresses.mjs
node import-places.mjs --refresh
node --test test/*.test.mjs
node stage-geography.mjs
```

Inspect source changes/review notes before release. `stage-geography.mjs` validates
both source files, writes immutable assets to content and backend-resource trees,
and atomically replaces each manifest only after assets exist. It does not build
the APK, modify model data, call Render or publish by itself. Deploy the reviewed
backend build next; classpath data currently changes through backend deployment,
not through an admin editor. Keep old immutable assets for rollback; restore the
previous **geography manifest** on the server to roll back, without downgrading APK.
`node build.mjs` bundles the current fallback and stages both content channels;
`--preview-only` only updates the local preview, not native/backend resources.

Verification: 117 JS tests (catalog/import/staging/update, existing map/art/bridge
tests and scene-activation rollback); 22 map desktop tests;
Ktor `WorldRoutesTest` (two tests, no database); Android debug build; deterministic
model export checks. Postman HTTP simulation exercised v1 → v2 → rollback, corrupt
hash, incompatible schema, 503, timeout and fresh-store cached offline restart.
The cache in that HTTP test is in-memory; it is not a mobile IndexedDB persistence
measurement. Browser UI confirmed library address, explicit main-door selection
and transfer of that door's coordinates to the demo event form. The final browser
pass also confirmed Baiterek's address and filtered main-door choice. Screenshot:
`../androidApp/build/reports/city-preview/geography-2026-11-16-baiterek.png`.
Native entrance visual QA and iOS file-backed loading remain separate device
checks; the active Android Studio session was another project, left untouched.

No production publication or real event API write was performed. Render needs the
reviewed backend deployment; local success is not proof the public URL is live.

Local HTTP fault reproduction (from repository root; unsigned Postman works):

```sh
postman simulation run postman/simulations/geography/geography.sim.yaml --port 4016 --no-history
# In another terminal:
node 02_Code/game-city/test/geo-http.mjs
```

Source references: [OSM address tagging](https://wiki.openstreetmap.org/wiki/Key:addr),
[OpenMapTiles schema](https://openmaptiles.org/docs/schema/),
[Overpass shared-service policy](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html).

## Unified miniature art pass — 16 November 2026

The preceding `astana-miniature-1` pass established `runtime/art-direction.js`:
warm ivory stone, muted teal glazing, sage planting, broad facade bays and quiet
night illumination. City tiles, authored landmarks, avatar clothing and map
chrome use the same palette. The architecture remains original procedural art,
not imported Zenly/Alba assets, a scan or a surveyed building catalogue.

- Baiterek, Arena, Akorda, Peace/Independence palaces, Khan Shatyr, EXPO and MEGA
  have revised silhouettes, entrances/plinths and grouped details. All 19 models
  remain hash-checked static data; other landmarks receive palette consistency,
  not an unsupported claim of equal hand-authored detail.
- Streamed buildings retain source heights (9 m only when absent). Near views
  add bounded roof/plinth details; z12 is real-footprint relief no taller than
  6 m. z10/11 are cartographic surfaces/roads, **not synthetic city blocks**.
  This supersedes the inflated/synthetic far-LOD description dated 13 November.
- One instanced batch supplies soft night light pools at landmarks. No light
  per window or fake live-event signal. The facade shader handles instanced
  world coordinates; appearance and existing seasonal controls are retained.
- The local character has reshaped face/hair, a field jacket, backpack, shoes,
  restrained idle and eased articulated walk. Maximum full appearance is now
  42 draws / 9,634 triangles, superseding the older budget below. Saved appearance
  indices remain compatible. This is still not GPS presence or multiplayer.
- Events have three distance levels with hysteresis: compact overview symbols,
  district covers, then larger near covers with tether to the true coordinate.
  Only current nearby events may show category miniatures (2 economical / 4
  balanced). Planned times use Astana's timezone. The event list paginates all
  loaded events instead of silently dropping entries after 20.
- Streaming remains bounded to 2 requests / 9 visible tiles / 16 cached tiles.
  Spatial indexing and bounded tree sampling limit generation work. WebGL context
  recovery restarts the rendering loop; forced context-loss QA remains outstanding.

Verification for this pass: 63 JS tests (including an isolated landmark suite),
deterministic regeneration/static-schema checks for all 19 models, Android debug
build, and 14 map desktop tests. Local browser QA loaded 8,044 overview footprints
at z12 with 9 visible tiles, 166 draws and ~369k triangles; warm p95 was 34.2 ms on
this Mac. **These are browser observations, not physical-phone acceptance.**
Android 36 / WebView 133 also displayed the updated day and night scene in the
native preview. Cold startup was noticeably slow; startup/FPS/thermal acceptance
on a physical phone is still outstanding. Android 26's installed WebView 58 is
rejected by the existing unsupported-WebView guard, which was not bypassed.
Native and backend resources are staged locally; no deployment/push was performed.
The production API → new event → second-device cover/presence flow is not certified
by local fixtures. Whole-city offline storage and hand-authored detail for every
building are not implemented.

## Current scope: all Astana, not two isolated districts

The world camera now spans the product viewport (71.10–71.85 E, 50.95–51.40 N).
`Сцена → Вся Астана` and the place selector expose 19 original stylised landmarks
and north/east/west area jumps. Network buildings use the same quarter facade,
night-window, path and foliage palette. Baiterek is now a persistent near reference
quarter; Arena is a bundled network fallback. Both replace, rather than overlay,
streamed geometry and neither is a navigation limit.

Event signs: sport/pitch, music/stage, market/canopy, discovery/telescope,
route/start arch, quest/flag, community/symbolic people. Planned events stay still;
live signs animate only nearby and within budget. Category miniatures are symbols,
not evidence of attendance, an actual installation or venue permission.

Run `npm run preview`, open `http://127.0.0.1:8784/?stage1=1` for 10 local public
fixtures and create/join/end checks. These never write to API and reset on reload.
The normal mobile screen continues to use the existing Kotlin event repository/form.

Current checks: 39 JS tests; Android debug build; 8 map + 19 event desktop tests;
backend WorldRoutesTest and deterministic geometry export verification.
Full event WASM tests have 8 unresolved failures; physical-phone/production E2E
acceptance is outstanding. Do not call the entire first stage production-complete.
All 19 landmarks now use the hash-checked manifest content channel (engineVersion 2,
up to 32 models / 18 MiB). Install this engine version once, then compatible geometry
releases do not require a new app build. Both old/new engines reject incompatible
packs and retain working content. No remote deployment was performed.
Earlier sections below record previous, narrower slices; the Russian document
linked next contains the current event semantics, limits and remaining gates.

See the complete Russian architecture, contract, rollout instructions and verification limits in
[`GAME_CITY_PILOT_2026-11-12.md`](../../05_Docs/GAME_CITY_PILOT_2026-11-12.md).

```sh
npm ci
npm test
node --experimental-vm-modules check-export.mjs
npm run build
npm run preview
```

`build` is for engine/bundled fallback changes. `stage-content` stages a backend-only content
release without touching mobile resources. Neither command deploys or publishes anything.

The local preview at `http://127.0.0.1:8784` adds a QA-only toolbar. A test server outage affects
the model channel, not the external street tiles; don't call it a full offline-city test.

Source geometry/streets attribution: OpenFreeMap, OpenMapTiles, © OpenStreetMap contributors.
Original stylised landmark geometry is exported to constrained data, not executable JS updates.
Third-party library licenses are shipped alongside the mobile bundle.

## City → event → people, 12 November 2026

The existing Three.js runtime now includes a local avatar editor (skin, hair,
clothes, hairstyle, backpack), persisted on this device. The editor does not
use GPS, upload a profile or share location. Outside the editor, the avatar is
hidden in the production map unless the explicitly labelled demo walk is enabled.
This is not consent-based live presence or the user's GPS location.

### Character art pass

`runtime/avatar.js` now builds an original, approximately 1.93 m stylised city
explorer. `avatar-geometry.js` supplies shaped cross-sections, rounded clothing
parts and curves; no downloaded character or generated-image texture is used.
The face has ears, a nose, eyes/brows, blinking and a subtle idle head movement.
The jacket has a hood/collar, pockets and a zip; sneakers and a canvas backpack
have their own silhouettes. Three existing hair/hat choices and all saved palette
indices remain compatible with `eventmap.avatar-look.v1`.

**Мой образ → Лицо / ¾ / Сзади** changes the inspection angle; **Шаг** previews
the walk in place without moving or sharing location. Elbow/knee/ankle pivots,
counter-swinging arms and a planted support foot replace rigid limb swings.
The local demo follows the quarter's plaza/path heights. This is not world-wide
collision, navigation, a skin-weighted animation rig, or a conversational AI.

Mobile editor framing reserves space for its bottom sheet and desktop preview
toolbar. A non-shadow-casting portrait light is active only in the editor, and
a soft contact shadow replaces a stale cached directional shadow on the avatar.
Near detail disappears beyond 42 m and returns below 32 m (hysteresis).
Per-joint/material batching keeps the full character at 46 draws / at most
14,402 triangles; far appearance uses 28–29 draws / 6,946–8,486 triangles.
The four demo players use a reduced geometry variant (≤4,422 triangles each).
These are geometry budgets, not FPS guarantees; no continuous mesh simplification
or GPU skinning is claimed. Avatar runtime changes are bundled in the app, not
covered by the landmark-model OTA manifest yet.

Verification: 25 JS tests including all existing content-update tests, avatar
finite geometry, appearance compatibility, detail hysteresis, joint motion,
support-foot height, reduced motion and resource disposal. Browser inspection at
390×844 covered full-body framing, front/back, hair, backpack, walking, day/night
and persistence after reload; no captured WebGL errors. Android debug APK builds.
This pass has not been performance-tested on a physical phone. QA screenshots
are under `androidApp/build/reports/city-preview/avatar-mobile-{day,night}.jpg`.

Event labels use native API data, group with distance, avoid overlapping and
hide expired/cancelled entries. At most five labels appear; `Рядом` opens the
nearby list. The production bridge opens the existing native event screen.
Map appearance/content-update controls are in `Вид`, rather than rows on the map.

Open `http://127.0.0.1:8784/?journey=1` after `npm run preview` for the isolated
football journey: demo event → fictional participants → local 8-second video →
local join/cancel. Android has the same scenario under **EventMap City Preview**
(debug only). The field is a visual test fixture, not a real venue at that location.
No event, attendance or location is written to the server. Production snapshots
do not contain the `preview` flag. The QA toolbar/video are not packaged in the
production WebView; the Android fixture uses the native player.

This is a first integrated slice, not live multiplayer. Friend tracking, avatar
sync across devices, physical-device performance and a real-server join flow
are not confirmed by this demo. City-wide street/building tiles still require network;
the Baiterek quarter described below is now included locally, not the whole city.

## Baiterek quarter: styled mobile slice

The default Baiterek focus uses `runtime/baiterek-quarter.js`, built from the already
saved `source/baiterek.json` OSM snapshot. It contains 278 building footprints,
paths, green areas and water, with original stylised facades/roofs/trees/lamps.
It is not an accurate architectural survey or proof that a venue exists.
Attribution stays visible; the source snapshot retains its ODbL metadata.
The local quarter and network city are alternative modes, not overlapping meshes.
Focusing Ak Orda or Arena still opens the network city mode.

Controls:

- Existing native **day/night** and seasonal appearance controls are unchanged.
- **Сцена → Подсветка**: ring and four soft beams around Baiterek.
- **Сцена → Салют**: one six-second sequence; camera frames the tower and burst.
- **Сцена → Снег**: bounded camera-near particles, not a weather report.
- **Сцена → Прогулка · демо**: avatar walks around the tower; dragging/zooming
  releases the follow camera. **Обзор / Площадь** stop the walk and reset the view.
- **Мой образ**: existing locally persisted character customisation.
- **Качество** and **Показать показатели**: render budget and measured frame times.

All three effects default to off. Crowd figures and the walking avatar are
explicit demonstrations, not real people being tracked. No new API writes,
location permissions, backend service or Unity dependency were introduced.

Performance implementation (targets, not a physical-device guarantee):

- 30 FPS render cap; DPR ≤1.35 balanced / ≤1 economical.
- Merged static meshes; instanced trees and 32 decorative pedestrians near camera.
- Flat road ribbons, simple crowns, distance-based details with hysteresis.
- Maximum 700 snow + 300 firework particles; economical 320 + 160.
- One cached 1024² directional shadow map in balanced near/quarter views;
  no real-time PointLight per lamp. Economical mode disables shadows.
- Fixed particle buffers/GPU motion; hidden/native-inactive scenes pause.
- Reduced-motion preference disables particles and walking animation.
- Rolling 300 rendered frame intervals: p95 and frames >50 ms. These include
  the 30 FPS pacing and are not GPU-only render-time measurements.

The quarter data/style/effect code is currently **bundled with the app**. Existing
manifest/SHA256 content updates still cover the authored landmark models only.
Moving quarter layouts/material parameters into a versioned downloadable content
pack is a later step; this change does not claim OTA updates for the entire quarter.

Verification for this slice: 25 JS tests, including geometry finiteness, LOD,
particle budgets, paused effects and cleanup; Android debug build. Browser checks
cover day/night, the controls, avatar, and effects. Browser FPS is not Android FPS.
Next device gate: record model/OS/WebView, 60 seconds per quality/day/night/effects
case after warm-up; target p95 ≤40 ms for the 30 FPS mode, then a 10-minute thermal
run, background/resume, rotations and an offline cold start. Do not roll out the
whole city based only on a desktop browser result.

## Approved art direction → first 3D pass

The user-approved [day/night concept](art/baiterek-approved-direction.png) is an
AI-generated **art reference**, not a screenshot, geographic plan or performance
promise. Its invented buildings are not transplanted onto the map.

This pass keeps the OSM coordinates and existing controls, and changes:

- Baiterek to twelve broad sculpted ribbons, a larger amber sphere and calmer
  materials. The new constrained model is ~431 KB / 12,920 triangles, in the same
  105m nominal height / 42m radius envelope. It uses the existing hashed asset path.
- Seven existing OSM buildings to terraced silhouettes, glazing plinths, cornices
  and larger facade bays; the rest remains lower-detail geographic context.
- Tree crowns to three rounded masses, 320 balanced / 180 economical instances;
  positions stay in recorded green areas and avoid path/building footprints.
- Warm paving, benches, low path lights and selective night-window clusters.
- Minor unnamed paths fade between 90–280m from the camera; main promenades stay
  visible. This is a visual level of detail, not deletion of source geography.
- The initial camera is the closer **Площадь** view; **Обзор** restores the wide view.

This is a first realtime interpretation, **not visual parity with the reference**:
hero facades, planting composition, character styling and lighting still need art
review. No new geolocation/sharing or event API was implemented in this pass.

### Flicker investigation

User reported roads/buildings flickering while moving. The old runtime put road
and footpath surfaces at the same Y=0.37 (40 strict crossings of unlike materials
in the snapshot). Near/far was 0.15/40000: with a 24-bit depth buffer, precision
around 500m is approximately 0.10m, larger than some ground-layer separations.

The fix separates ground layers (`GROUND`), uses a zoom-dependent near plane
(up to 4m) and 6500m far plane in the quarter, and offsets building-part materials
in depth relative to parent shells. Minor-path fade plus antialiasing reduces
subpixel edge shimmer. Sphere trim sits outside the sphere instead of intersecting it.
QA stats expose actual framebuffer depth bits and near/far; math tests assume
24-bit depth and are **not** proof of identical behavior on every mobile GPU.

20 JS tests pass, including ground-layer precision across supported quarter zooms,
building-part offsets, seven selected buildings and material day/night uniform
lifecycle. Export validation and Android build are part of the handoff. Native
device flicker, heat and sustained FPS still require a physical-device run.

All changes are local. `build` stages the new landmark manifest/assets under the
backend resources but **does not deploy them**. A regular app configured against
an older remote pack can intentionally receive that older landmark through the
existing rollback-capable content channel; City Preview uses the new bundled pack.

## Шарики с обложками — мобильная игровая карта (13.11.2026)

Открыть `http://127.0.0.1:8784/?stage1=1&bubbles=1`. «Шарики / 3D» переключает
новое представление и прежние миниатюры. Плавающая обложка с линией до координаты;
без картинки — SVG-иконка категории; группа показывает количество событий, не людей.
Видимые шарики ограничены, пересечения скрываются, точка привязки не сдвигается.
Три иллюстрации спорт/концерт/маркет — авторские векторные тестовые обложки.
В «Создать · демо» можно выбрать JPG/PNG/WebP: обложка кадрируется по центру до 256 px
и хранится только в памяти вкладки. Никаких отправок картинки и публикации в API.

Собрать **только браузерное превью**: `node build.mjs --preview-only`, затем
`npm run preview`. Флаг не копирует ресурсы в приложение или backend.
Обычный нативный snapshot теперь показывает шарики. Прежние 3D-миниатюры доступны
только как вариант QA. CSP не расширен; тестовые картинки в реальные события не попадают.

Цепочка: публичный EventDetail.cover (media ID) → `GET /events/{id}/media` → точное
совпадение ID/ready/photo → HTTPS-загрузка без bearer/редиректов → JPEG ≤192 px и
32 KiB → нативный snapshot `covers:[{id,ref,data}]`. JS проверяет актуальный coverRef.
Ошибка/отсутствие фото оставляет категорию, не случайную картинку. Событие открывается
по исходному ID. Обложки запрашиваются только для видимых групп: до 8, загрузок ≤2,
кэш ≤24, отрицательный кэш 60 секунд, debounce 180 мс, отмена при уходе с экрана.
Крупные входные файлы >4 MiB не скачиваются; Android downsampling до декодирования.
Закрытые/удалённые/заменённые события исключаются из кэша при обновлении списка.

**Требуется выпуск backend вместе с APK:** раньше обложка, загруженная до создания
события (`media.event_id=null`), не входила в его media-список. Репозиторий теперь
добавляет ровно указанную `events.cover` с проверкой владельца/типа/статуса; доступ
к самому событию по-прежнему проверяет существующий маршрут. Миграция не нужна.

### Дальний вид и бюджет

- z14: подробные исходные контуры, крыши, небольшое количество людей/машин/деревьев.
- z13: исходные здания без близких декоративных деталей.
- z12: поверхность из крупного тайла + здания четырёх z13-подтайлов; упрощённые
  объёмы по реальным положениям/габаритам, условно увеличенная высота для читаемости.
  Не более 2200 объёмов/тайл, две instanced-партии (корпус/крыша), без shadow maps
  и без ложных адресов/кликабельных ID у упрощённых объёмов.
- z10–11: условные кварталы внутри городского landuse, до 1400/тайл, исключения
  водоёмов/парков/дорог/основных моделей. Это не точная геометрия отдельных домов.
- ≤9 видимых тайлов, кэш ≤16, HTTP-запросов одновременно ≤2; дочерние тайлы идут
  последовательно, отменяются вместе с родительским. На z12 холодная загрузка
  требует до 45 небольших запросов, а не загрузки всего города в максимальном LOD.
- Цвета поверхности/свет стали спокойнее, дороги читаемее; день, ночь и сезоны
  сохранены. Старый LOD остаётся до готовности замены, совпадающие слои не рисуются.

Проверки: 44 JS-теста; Android assembleDebug, map desktopTest и компиляция
iOS Simulator ARM64; 11 backend-тестов MuxRoutes/MuxRepository (включая доступ к
обложке события). Браузерный обзор: 8148 объёмов, ~335 тыс. треугольников, 173 draw
calls, 30 FPS / p95 около 34 мс после прогрева на этом Mac. **Это не телефонный
бенчмарк.** Нативный UI-прогон заблокирован доступом к окну эмулятора. Живой цикл
«публикация на Render → второе устройство → шарик» ещё не проверен; deploy не сделан.

## Здание → этаж → помещение (16.11.2026)

Реализован общий для мобильной карты и формы создания сценарий:

1. Клик по идентифицированному зданию открывает его карточку и публичные события,
   привязанные к **точному buildingId**, а не к радиусу вокруг координаты.
2. Если внутреннего плана нет, список событий и создание события здания работают.
3. Если сервер опубликовал этажи, можно выбрать этаж, затем помещение на 2D-схеме
   или в доступном текстовом списке. Этаж и помещение необязательны.
4. Выбор передаётся в настоящую форму, черновик, POST/PATCH и карточку события.
   Перенос точки/изменение адреса в форме снимает старую привязку. Сервер проверяет
   принадлежность помещения этажу и этажу — зданию.

Географическая точка встречи/выбранный вход остаются lat/lng. Координаты контуров
помещения локальные, в плоскости схемы; они не заменяют GPS. Старые события остаются
без привязки: соседство или одинаковый адрес не доказывают принадлежность зданию.
Приватные и unlisted-события не показываются в публичном списке и счётчиках схемы.

### Публикация планов без нового APK

**Первый выпуск** требует backend с миграцией `V15__event_venues.sql` и приложения
с новым экраном. Последующие совместимые планы — серверные данные, не код клиента.

Основной источник после миграции V16 — PostgreSQL (`venue_plans` и `venue_revisions`),
редактор доступен по `/admin/venues` (ссылка из `/admin`). Ресурс
`04_Backend/server/src/main/resources/venues/catalog.json` — только начальный резерв
для ещё не редактировавшихся зданий. Он не перезаписывает опубликованный план из БД.
Формат: `{ "schemaVersion": 1, "buildings": [ ... ] }`.
Каталог сейчас пуст: реальный план MEGA не получен и не выдуман. Названия основных
зданий берутся из world manifest; остальные идентифицированные OSM-здания получают
состояние без плана. Этаж может быть опубликован списком даже без графической схемы.

Пример одной записи **только для пояснения формата, не реальная схема MEGA**:

```json
{
  "schemaVersion": 1,
  "buildingId": "landmark:mega",
  "revision": "reviewed-1",
  "name": "Пример формата, не реальная схема",
  "floors": [{
    "id": "l1", "name": "1 этаж", "order": 1,
    "plan": { "width": 100, "height": 60, "outline": [] },
    "rooms": [{
      "id": "hall", "name": "Пример помещения",
      "polygon": [{"x":5,"y":5},{"x":55,"y":5},{"x":55,"y":50},{"x":5,"y":50}]
    }]
  }]
}
```

Порядок обновления:

1. Получить разрешённый к публикации проверенный план. Не публиковать технические,
   закрытые и служебные зоны. Сопоставить его с точным ID объекта карты.
2. Открыть `/admin/venues` с существующим ADMIN_KEY, выбрать здание, добавить этажи
   и помещения. Контуры рисуются точками; также доступны импорт/экспорт JSON.
3. Сохранить черновик, проверить схему и нажать «Опубликовать». Сервер сам назначает
   новую revision, сохраняет историю и меняет публичную версию в одной транзакции.
   Никакого деплоя для этой операции нет. Первая установка редактора требует V16.
4. Уже установленный клиент получает план при новом открытии здания или по кнопке
   «Обновить план и события». В открытой карточке события обновляются каждые 30 с;
   сам план не опрашивается непрерывно. Предыдущую публикацию можно загрузить из
   истории в черновик; повторная публикация проходит те же проверки.

IDs: `landmark:<id из world manifest>` либо `osm:way:<id>` / `osm:relation:<id>`
из исходного контура. Временные MVT IDs, адресные nodes и свободные точки не подходят.
Переход со старого OSM ID на новый/landmark требует явной миграции привязок событий,
а не только переименования записи. Опубликованные IDs этажей и помещений нельзя
удалять или переносить на другой этаж: это защищает ссылки в событиях, включая
параллельное создание. Можно изменять названия, контуры или убрать рисунок,
сохранив записи. IDs ещё не опубликованных объектов можно менять/удалять в черновике.

API отдаёт только ограниченную 2D-геометрию: JSON ≤1 MiB на здание, ≤32 этажей,
≤200 помещений на этаж, ≤1000 на здание, ≤20 тыс. вершин суммарно. Нет HTML,
исполняемого кода, произвольных URL или скачивания SVG. Невалидный каталог отклоняется
при запуске backend; перед выпуском обязателен тест. Смена schemaVersion потребует
совместимого клиента; обещание «без APK» относится к данным поддерживаемой схемы.

Сетевая ошибка не подменяется сообщением «плана нет». При неудачном обновлении
остаётся последняя схема **в памяти открытого экрана**, с предупреждением.
Дискового офлайн-кэша планов, импорта PDF, 3D-интерьеров, поэтажного GPS и навигации
внутри здания в этом срезе нет. В админке PNG/JPEG/WebP служит **локальной подложкой
для обводки**: в БД/мобилку уходит векторная схема, не исходное изображение.
Подробности и команды проверки — в `04_Backend/server/README.md`, раздел редактора.

### Проверки этого среза

- Android `:androidApp:assembleDebug` прошёл; APK установлен на emulator-5556.
- 157 JS-тестов прошли, включая стабильную идентичность здания.
- 25 тестов map UI и 21 тест event UI прошли (включая черновик/публикацию venue).
- Backend: 42 успешно, один существующий внешний Mux-тест пропущен; все 7 новых
  VenuesTest успешны. Проверены реальные route/use-case/репозиторий на локальной H2.
- Реальный Ktor OkHttp клиент прошёл HTTP-тест через локальную Postman simulation:
  нет плана → v1 → v2 на том же клиенте, приватность/точность, 503, битый/большой JSON,
  неизвестная схема, таймаут и восстановление. Это не проверка production-сервера.
- UI-прогон нового экрана этажей не завершён: окно эмулятора видно, но управление
  возвращает ошибку доступа. Физический телефон/iOS и миграция V15 на PostgreSQL
  не проверены; production не развёрнут.

Воспроизведение HTTP-теста (две консоли, simulation запускается из корня репозитория):

```sh
postman simulation run postman/simulations/venues/venues.sim.yaml --port 4017 --no-history --output ndjson
```

```sh
cd 02_Code
EVENTMAP_VENUE_SIM_URL=http://127.0.0.1:4017/venues-api/ ./gradlew :common:feature:event:data:desktopTest --rerun-tasks
```

Без переменной окружения HTTP-тест явно пропускается. Синтетические данные находятся
в `postman/mocks/venues`, а не в рабочем каталоге планов. Никаких публикаций в Postman
workspace, авторизации или записей в production этот прогон не выполняет.
