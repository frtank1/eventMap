# Mobile game-city pilot

Local adaptation of the existing Astana visualisation; the hosted private Site is untouched.

## Roof placement on BC «Moskva» and Beijing Palace — 16 November 2026 (Claude)

Owner report: the tops of both towers "go crazy". Cause, in `runtime/city-landmarks.js`:
roof embellishments were axis-aligned boxes at the model origin, while the bodies
follow the real OSM outlines. Moskva's cap (58 x 26 m) therefore lay **across** its
97 x 28 m tower, about 76 degrees off; Beijing Palace's green tiers were skewed
13.5 degrees and about 3 m off centre. They are now placed on the oriented bounding
rectangle of the tallest source part (`orientedRect`, `onCrown`).

Only these two models were re-exported (new assets `bb4b1e84…` and `87b80c8a…`,
manifest, `pilot-2`, seed, native bundle, backend `world/`). **The export was run
with the swatches the shipped pack already bakes** (ivory `f2dfbf`, glass `367d90`,
roof `618d91`, leaf `49885b`), not the current presentation palette, so the other
17 models stay byte-identical and `landmarkSwatch` keeps mapping them.

Open inconsistency, not introduced here: `check-export` regenerates with the
*current* `CITY_PALETTE` and therefore fails for the 11 `createCityLandmarks`
models (missing re-baked assets). Either pin the export swatches in the builder
or re-export the pack; until then `check-export` is not a valid gate.

Verification: 200/200 JS tests on the rebuilt tree; exported geometry checked
numerically (cap, gold slab and tiers share the tower's axis and centre); both
roofs inspected in the owner's running preview on real tiles. No APK build, no
deployment. `placementYaw` (MEGA, Palace of Peace) and `withheldModels` (museum)
in `main.js` are still runtime corrections.

## Whole-Astana six-layer foundation — 16 November 2026

`astana-six-layers-1` extends the existing game-city presentation across the
**current whole-Astana data**, not just the Baiterek NSDI trial. It reuses the
76,025-building overview, geographic z14 streets and authored landmarks. It
does not import more NSDI data, invent building heights/addresses, or mean that
every building has received an individually authored model.

| Presentation band | Camera-to-focus distance | Main purpose |
| --- | --- | --- |
| Person | <60 m | Existing avatar/editor close-up |
| Building | 60–350 m | Facades, entrances and nearby event covers |
| Street | 350–1,000 m | Street objects, trees and nearby people |
| Quarter | 1–2.6 km | Building groups and simplified surroundings |
| District | 2.6–7.5 km | Gradual detailed-tile / persistent-city handover |
| City | >7.5 km | Volumetric silhouettes, landmarks and event groups |

Band labels have 10% hysteresis; **detail weights are continuous**, not six
copies of the map or six sudden geometry swaps. Independent smooth ranges:
people 500–950 m, furniture 850–1,700 m, vehicles 1–2.1 km, roof units
1.7–3.1 km, trees 3.2–5.7 km. Low quality shortens those ranges by 20%.
Detailed tiles hand over to the persistent backdrop over **4.2–6.1 km** with
complementary, screen-fixed ordered coverage (16 steps), not transparent
overlapping city copies. The tile/background range stays identical in both
quality modes. Without a ready backdrop, the existing tile fallback stays
opaque. Cached geometry, 2-request / 9-visible / 16-cache limits, 30 FPS cap,
DPR limits and event-animation budgets remain unchanged.

The overview now carries filtered daytime facade rhythm matching the street
palette. Small houses fade per building between 6.5 and 9 km; chunk culling
waits until all members can be outside that range. Large buildings retain their
volume. Night lighting, bridges, winter appearance, avatar and cover balloons
remain. Baiterek's decorative people use the same fade range. **Not all seams
are removed:** authored-quarter ownership, some landmark details and bridge
railings still use their existing thresholds; those need a later measured pass.

WebView profiling also identified the event-label update path as a major CPU
consumer. Labels now cache visibility, level, animation/selection classes and
position in a `WeakMap`; unchanged labels neither read nor rewrite DOM state
each frame. Positions are rounded to 0.1 CSS px, without shifting the geographic
anchor or changing tap handlers. Hidden landmark labels no longer receive
position writes. Regression tests cover 300 unchanged updates and real changes.

Verification: **200/200 JS tests**, **25 map desktop tests**, Android debug
build/install and release asset merge passed. Release assets still exclude
the NSDI snapshot and festival fixtures. Android caught a GLSL concatenation
error that unit tests initially missed; it was fixed and a preprocessor-line
regression assertion added. A subsequent live WebView console check reported
no shader errors. No backend deployment, production writes or git push.

Debug acceptance runner: `node test/android-city-lod-acceptance.mjs emulator-5556`
(only with the owner's authorization). A fresh launcher starts at Center; the
new **Центр / Север / Восток / Запад** button selects the sweep area and moves
the camera there. The runner checks 150 retained fixtures, five zoom bands
plus the avatar's person band, native cover taps, two-pointer gestures and
day/night. Measurements and screenshots go to ignored
`test-results/2026-11-16-city-lod/`; `--foreground` and `--optimized` retain
separate comparisons, and `--interactions-only` retains sweep results.

**Performance acceptance is not passed yet.** Before label-write caching, the
Mac was confirmed locked and the central sweeps measured **5.70 / 4.97 FPS**.
After the owner unlocked it, the repeat was still **6.99 / 6.08 FPS**; unlocking
alone did not resolve this. Those failed-target measurements are retained.
Cold preparation of the city also exceeded the 90-second QA readiness deadline,
including after the label optimization. The overview subsequently became ready;
this is a startup-performance failure, not a missing 7 MB bundled file.

After label caching, the warmed center reached 29.39 FPS and the north 30.00;
the first center sweep and other districts still had stalls (table below).
**All of these final sweeps used the persistent city / authored-quarter fallback:**
OpenFreeMap metadata requests failed in the WebView (resource status 0), so
`coverage.maxTiles / maxBuildings / streamFrames` were all zero. This is not
performance proof for fully loaded detailed streets or their blended handover.
The earlier source tests exercise a real saved MVT fixture and the handover
math, but do not substitute for that Android acceptance. Physical-phone heat,
FPS, cold start and iOS remain unverified. No provider security checks were
disabled and no sample tiles were silently substituted for live requests.

Final Android API 36 sweeps, Apple M4 Pro host GPU, 412×628 CSS WebView,
150 synthetic events, automatic quality (low during these sweeps), DPR 1:
3-second warmup + 23 measured seconds, without discarding stalls.

| Fallback-map sweep | FPS | p95 interval | Frames >50 ms | Peak draws / triangles |
| --- | --- | --- | --- | --- |
| Center / first day | 17.84 | 150.0 ms | 133 | 155 / 579,443 |
| Center / warm day | 29.39 | 33.4 ms | 12 | 155 / 579,443 |
| North / day | 30.00 | 33.4 ms | 1 | 42 / 281,568 |
| East / day | 25.57 | 83.3 ms | 71 | 47 / 160,691 |
| West / day | 21.71 | 99.9 ms | 130 | 41 / 187,841 |
| West / night | 30.00 | 33.4 ms | 0 | 42 / 187,879 |

Functional Android checks passed: 343 → 1,715 → 343 m real two-pointer zoom,
cover tap opened the native **Стритбол 3 × 3** fixture card, full-body avatar
editor was visible at 8.19 m (person band), editor closed and day mode restored.
All 150 fixtures remained. The app is left at daytime Baiterek. These checks
verify interaction and rendering, not event publication or GPS sharing.

Next gates: prepare/cache city geometry before first render rather than rebuild
all footprints on the UI thread; recover detailed-street loading after provider
failure; repeat all districts with nonzero streamed coverage on a physical phone.

## Isolated Baiterek NSDI comparison — 16 November 2026

The **current map is still the default**. In **EventMap City Preview**, choose
**Байтерек → Текущая → НИПД**; the same comparison is in the browser's scene
settings. Switching prepares a complete alternative before replacing geometry,
retains the camera, avatar and all 150 debug events, and can restore the current
map. Read/validation/preparation failures leave the existing map in place.
Preparation has a 30-second cancellation deadline; concurrent or stale builds
cannot replace the active scene.
This does not publish content, alter production events or replace the whole city.

- Source: [NSDI dataset 1316](https://map.gov.kz/catalogue/#/dataset/1316), bounded
  WFS snapshot of `openmap:buildings_a`, longitude/latitude box
  `[71.425, 51.125, 71.435, 51.132]`. **133 records retrieved; 132 accepted**.
  Invalid source polygon `buildings_a.1191826` is rejected; overlapping current
  geometry is retained, rather than silently inventing a replacement footprint.
- Raw source heights are absent. Rendering uses **levels × 3.3 m**, explicitly
  an unverified visual estimate, not a measured building height. No addresses,
  entrances, ownership or ground truth are invented. Source IDs and null heights
  remain in the snapshot. Rendering simplifies dense rings by at most 8 cm and
  revalidates topology; original snapshot coordinates remain unchanged.
- The existing game palette, day/night, authored landmarks, avatar and event
  covers remain. Close-up geometry keeps actual footprints/courtyards with
  stylized facades; further away a small batched silhouette uses the same
  footprints, heights and palette. It survives district and whole-city zooms,
  with one owner per distance range, not overlapping detailed/overview buildings.
  No extra per-building lights, network calls or independent animation loops.
- The data's licence is **not specified**. The ~148 KB snapshot is deliberately
  ignored by Git and restricted to local evaluation / Android debug assets.
  It is not copied to the backend, ordinary Compose resources or release assets.
  Do not publish this dataset without establishing reuse rights.

Reproduce from a manually obtained, complete bounded GeoJSON file:

```sh
node import-nsdi-quarter.mjs /absolute/path/to/bounded-WFS.json
node build.mjs --native-only
cd ..
./gradlew :androidApp:assembleDebug :common:feature:map:ui:desktopTest :androidApp:mergeReleaseAssets
```

The importer reads a file, not a live provider API. `--native-only` rebuilds the
renderer and debug assets without restaging backend geography/model releases.
Without a local snapshot the ordinary build still works; requesting the trial
reports a load failure and preserves the existing map. The phone never queries
the government portal. Browser-only build: `node build.mjs --preview-only`, then
`npm run preview` and scene settings on the local preview.

Verification: **194/194 JS tests**, **25 map desktop tests**, Android debug
build/install and release asset merge passed. Tests cover holes, height/palette
consistency across LOD, malformed/truncated data, missing local file (real HTTP
404), stale activation, preparation failure, rollback and debug-only staging.
The merged release assets were checked: no NSDI snapshot or festival fixtures.
The separate authored-model export check passes its first three checks but then
cannot find generated asset `4fcb1fcd19eaf384099f5d0841c3238a5aa4116eca6039713700614cdfa30b76.json`;
this slice does not regenerate or modify the authored model pack to hide that gap.

Android camera comparison: API 36 / Apple M4 Pro host GPU, 1080×2400 display,
412×628 CSS WebView, balanced, DPR 1.35, 150 synthetic events; 3-second warmup
and 23 measured seconds per sweep. Stalls remain in the measurements.

| Variant / sweep | FPS | p95 interval | Frames >50 ms | Peak draws / triangles |
| --- | --- | --- | --- | --- |
| Current / first day | 29.50 | 50.0 ms | 16 | 245 / 797,463 |
| Current / warm day | 30.00 | 33.4 ms | 4 | 249 / 797,463 |
| NSDI / first day | 29.30 | 50.0 ms | 19 | 255 / 797,757 |
| NSDI / warm day | 29.78 | 33.4 ms | 5 | 272 / 815,413 |
| NSDI / night | 29.22 | 33.5 ms | 10 | 291 / 832,251 |

Recorded NSDI transitions cover city → district → quarter → near → quarter →
district → city; near geometry and the replacement silhouette alternate ownership.
Matching close-up screenshots retain exactly the same 343.051 m camera distance.
The replacement far layer contains 128 rendered buildings / 5,972 triangles;
four source overlaps are owned by existing authored landmarks instead.

**Not a long-session stability claim:** an earlier emulator session fell to
5.05/5.55 FPS even on the current map and timed out during trial preparation.
The table is the repeat after restarting only the debug app and making the
emulator window visible. Those two changes were not isolated, so the cause of
that slowdown remains unproven; the earlier logs are retained, not discarded.

Android acceptance evidence: ignored `test-results/2026-11-16-nsdi-quarter/`.
Run `node test/android-nsdi-acceptance.mjs emulator-5556` only with authorization
to drive that emulator. It uses actual Android taps, two-pointer WebView input,
and five measured camera sweeps; it does not call click handlers or publish events.
`--interactions-only` reruns taps/gestures/avatar/A-B while retaining sweep results.
Camera assertions and avatar screenshots wait for the editor's animated framing
and return to settle; a boolean editor-open flag alone is not visual acceptance.
Final interaction rerun passed: 343 → 1,715 → 343 m two-finger zoom, visible
native **Стритбол 3 × 3** sheet (`festival-demo-002`) after a cover tap, visible
full-body avatar editor, closing it, current → NSDI restoration with the same
camera and 150 events. Last A/B preparations took 5.82 / 5.53 seconds while the
old map stayed visible. The launcher is left on the NSDI Baiterek daytime view;
a fresh launch still defaults to the current map. No app data was cleared.
Physical-phone thermal/performance and iOS acceptance remain separate gates.

## Android 150-event acceptance and daytime pass — 16 November 2026

The owner explicitly approved ADB UI testing and restarting **only API 36** with
host graphics. The emulator originally used **SwiftShader** (CPU rendering,
roughly 7 FPS); it now runs with `-gpu host`, verified as **Apple M4 Pro** in the
WebView. No app data was cleared, no production records were created, and no
backend deployment or git push was performed. These are **emulator results,
not physical-phone or iOS certification**.

Fixed during the actual run:

- The 7 MB local overview was aborted by the shared 6-second read deadline
  (observed request duration 7.59 s). Only this explicit bundled read now gets a
  bounded 30-second allowance; ordinary/API reads remain unchanged. Debug zoom
  testing waits for the overview-ready message, rather than starting on a fallback.
- Zooming out no longer immediately destroys z14 tiles. Existing 16-entry LRU
  and 9-visible/2-request limits still apply; zooming back reuses warm geometry.
- Unchanged balloon groups reuse their DOM, decoded covers and activity meshes.
  Anchor heights refresh when geometry coverage changes, not every 250 ms on
  an otherwise unchanged map. Label/animation budgets are unchanged.
- `city-day-diorama-2`: shared warm-stone/blue-slate/jade daytime palette,
  deeper river, less yellow directional lighting, subtle facade-base shading
  and readable dark daytime labels. No extra lights or render passes. The
  night palette/mood is preserved; old landmark-pack swatches still map to the
  correct night colors. No geographic heights/footprints were altered.

Actual Android UI checks: four two-pointer WebView zoom gestures through an
ADB-forwarded debug connection, native cover taps at city/street scales,
cluster → list of two events → correct native sheet. Observed titles include
**Кофе и винил** (`festival-demo-035`) and **Найди светящийся символ**
(`festival-demo-006`); the final daytime street-scale tap opened **Электронная
ночь** (`festival-demo-008`). Cover taps used Android input, not calls to event handlers.
Gestures did not create spurious event taps; all 150 fixtures remained loaded.
This checks the isolated debug journey, not authenticated API publication.

Camera-sweep measurements: Android API 36, 1080×2400 display, 412×676 CSS WebView,
balanced, DPR 1.35, 150 events. Each run includes 3 seconds of warmup then
23 seconds measured, with stalls retained. First sweep after scene preparation:
**28.0 FPS / p95 50.1 ms / 39 frames >50 ms**. Repeated warm night sweep:
**29.6 FPS / p95 33.4 ms / 7 frames >50 ms**, zoom-in 29.1 and zoom-out 29.8 FPS.
Before the cache/balloon changes, two equivalent night passes were 25.1/26.2 FPS
and p95 66.7 ms. Remaining first-approach stalls are not claimed fixed completely.
The final warm **day** sweep reached **29.96 FPS / p95 33.4 ms / 1 frame >50 ms**;
zoom-in 30.0 FPS, zoom-out 29.88 FPS. Earlier day pass: 29.2 FPS, p95 33.4 ms.
WebView version 133.0.6943.137. No application crash was observed; logcat does
contain a startup graphics `GL_INVALID_ENUM / GetIntegerv` warning, which has
not been diagnosed as a device-independent engine defect.

Verification: **180/180 JS tests**, **25 map desktop tests**, debug build/install,
release asset merge (no festival fixtures/covers), and `git diff --check`.
Reproducible ADB/WebView inspection and two-pointer gestures:
`node test/android-qa.mjs inspect|pinch-in|pinch-out emulator-5556` (requires the
owner's permission and the debug launcher already open). Evidence is under
ignored `test-results/2026-11-16-android-festival/`.

## Android 150-event zoom test preparation — 16 November 2026

The debug launcher **EventMap City Preview** now starts with the same 150 festival
fixtures as the browser, at the whole-city night view. The native sheet handles
event clicks and has a visible tap counter; joining/removal remains in-memory.
The normal signed-in application and production database are untouched.

- `stage-festival.mjs`, called by `build.mjs`, stages 150 records and seven SVG
  illustrations into **androidApp/src/debug/assets only**. Dates are omitted so
  an old APK's fixtures do not expire. The APK was inspected: 150 unique IDs and
  seven cover references; the merged release assets contain none of these files.
- Android computes the debug opt-in from `ApplicationInfo.FLAG_DEBUGGABLE` plus
  explicit preview state. Dense mode additionally checks the appassets origin
  and native bridge. Ordinary release/native payloads cannot activate this path.
  This supersedes the previous blanket native exclusion in the browser-only slice.
- **Прогон зума** runs a 26-second camera sweep: 3-second warmup, city overview,
  8-second approach to building scale, near hold, 8-second pull-back. Actual
  rendered-frame intervals are reported per phase, including stalls. This is
  **not a synthetic pinch/tap test**. Gesture, popup, background or context loss
  cancels the pass; a missing overview does not produce a fabricated result.
  The summary is visible in the native header; debug-only `EventMapCityQA` logs
  contain the detailed report and event-tap IDs, not auth/GPS/account data.
- Event grouping now has a 12% dead band at zoom boundaries. Small pinch jitter
  no longer repeatedly destroys/recreates balloon DOM at the same threshold.
  Balloon heads use touch-action manipulation; animation budgets stay 4 / 2.

Verified: **177/177 JS tests**, **25 map UI desktop tests**, Android debug build
and installation on emulator-5556 (data retained), release-assets isolation and
packaged fixture counts. Browser control run of the new moving-camera harness
(Mac, 2915×1343, DPR 1, balanced, 150 events): 30.0 FPS overall, p95 40.2 ms,
3 intervals over 50 ms; zoom-in 30.0 FPS / p95 40.2 ms and zoom-out 30.0 FPS /
p95 40.1 ms. Peak 375 draw calls / 1,074,364 triangles, so a physical-device
measurement is still essential; this browser pass is not an Android claim.

**Android UI run is pending:** the native window is visible but the computer-use
input path returns `noWindowsAvailable`. The owner was asked whether to allow
ADB UI automation as an alternative. Until that is answered, do not mark native
pinch, balloon taps, Android FPS, or physical-phone smoothness as verified.
Manual entry: open **EventMap City Preview**, tap **Прогон зума**, then tap several
balloon covers after completion; each should open the native event sheet and
increment **Тапы**. Test manual pinch separately from the camera sweep.

## Festival visual load preview — 16 November 2026

Open the loopback preview with `?festival=1` (or its **Festival · 150 events**
button). This is an explicitly fictional, in-memory visual stress scenario, not
an events import or a published city programme. No API/database writes, real
attendance, reservations or GPS. Reloading discards demo participation changes.

- 150 deterministic fixtures across 15 synthetic activity hubs, seven categories
  and seven original local SVG covers. Filters, event cards, local demo joining,
  day/night and a dense/calm comparison are available. All 150 remain in the
  nearby list; clustering/collision handling decides which balloons fit on screen.
- Dense mode budgets: up to 64 balloon labels on desktop / 28 below 700 px;
  economy mode 32 / 16. Ordinary production budgets remain 8 / 5. Covers remain
  visible at city zoom in this mode, with compact district labels on approach.
- Two static instanced meshes mark activity with coloured rings and tapered
  beams. They add no point lights, shadow maps or per-event animation loops.
  Nearby category miniatures retain the existing 4 / 2 animated-event limit.
- The runtime accepts dense mode only with `preview:true`, an explicit festival
  request, loopback hostname and **no native bridge**. Fixtures, cards and demo
  SVG files live in `test/`, are served only by `preview.mjs`, and are not copied
  into native assets. Native event limits, real covers and API contracts are unchanged.

Verification: **171/171 JS tests** pass, including deterministic fixture bounds,
seven cover paths, production/native exclusion, label budgets, geometry/resource
disposal, and isolated HTTP routes. Read-only model export checks and
`:androidApp:assembleDebug` pass; the APK was updated on emulator-5556 without
clearing data. Native UI/physical-phone performance remains unverified.

Browser QA: a cover click opens its local card, demo participation changes its
count, music filtering returns 22 fixtures, and calm mode removes the instanced
beacons and restores ordinary clustering. No shader/console errors observed.
Render-hook measurements (Mac GPU, DPR 1, balanced, 3 s warmup + 12 s samples,
150 fixture events loaded in each run; visible labels depend on collision/culling):

| View | Viewport | FPS | p95 interval | Draw calls | Triangles |
| --- | --- | --- | --- | --- | --- |
| Whole city / night | 1280×720 | 30.0 | 40.1 ms | 194 | 481,322 |
| Whole city / night | 390×844 | 30.0 | 40.3 ms | 141 | 413,704 |
| Baiterek / night, two visible animated signs | 390×844 | 30.0 | 40.1 ms | 141 | 566,875 |

These are browser results, **not mobile-device certification**. No long-session
thermal test, physical Android run or iOS run was performed. The fixture mode
itself is intentionally unavailable to the native bridge. Screenshots are under
ignored `test-results/2026-11-16-continuous-city/festival-*.png`.

## Continuous city volume at every zoom — 16 November 2026 (after the look pass)

This supersedes the distance/coverage behaviour below, **not** Claude's palette,
sky, traffic, landmark placement corrections or the museum hold-back.

- The old far LOD capped buildings at 6 m and removed them completely at z10/11.
  A persistent indexed silhouette now keeps source heights and courtyards across
  the product's Astana bounds. The same day/night/season palette is used at every
  zoom. Buildings are not vertically exaggerated; this is not a surveyed digital twin.
- `import-overview.mjs` is a manual public-data import (two requests at most,
  timeouts/retries, ignored source cache). It reads the provider's advertised
  z14 building layer: z13 does **not** contain height properties. The generated
  `source/astana-overview.json` is 7,350,596 bytes: 76,260 source polygons,
  787 green/water polygons and 1,284 main-road lines. ODbL provenance is in the
  file. Provider height estimates/defaults are not independently verified heights.
- `runtime/city-backdrop.js` builds indexed, spatially batched meshes in yielding
  work slices. No per-building objects, window meshes, traffic, point lights or shadows
  in this layer. Small low-rise buildings are distance-culled independently of
  the main skyline. Normals and colours use compact normalized byte attributes.
  Landmark replacement still requires matching source footprints, never a radius.
- Ready detailed tiles mask the background by building anchor, avoiding two
  surfaces on the same footprint. The authored quarter uses a bounded mask. Roads
  and water continue beyond the detailed tile ring; horizon fog no longer hides
  the edge of the quarter. Selection retains original geographic rings and the
  typed place/address bridge; no fake stable building IDs are introduced.
- With the backdrop ready, only nearby z14 detail is requested; beyond 6.2 km
  camera distance there are no detail requests. Existing limits remain 2 requests,
  9 visible / 16 cached tiles. The old stream remains a failure fallback.
- `frame-pacer.js` accumulates refresh fractions: the 30 FPS cap no longer becomes
  25 FPS on a 75 Hz display. It does not render catch-up bursts after suspension.
- At maximum zoom-out, the near clipping plane grows with distance and overview
  ground surfaces have a small depth bias. This removes the water/grass striping
  found during visual QA without changing geographic surface heights.
- 342 source-tagged elevated path sections now have stylized decks, ramps at
  real path endpoints, piers and night-lit railings. These are **not 342 distinct
  named bridges** and include elevated rail/LRT. The source determines the route;
  clearance/profile is artistic, not surveyed engineering geometry. Adjacent
  tile fragments are clipped/joined, and the old flat stream ribbon is suppressed
  once the continuous bridge layer is ready.
- Distant night rendering uses warm emissive road surfaces and minification-aware
  facade windows: small window patterns converge to averaged light instead of
  sparkling subpixels. No city-wide PointLight population or bloom pass is added.

Verification before the festival extension: **167/167 JS tests**, read-only deterministic export checks, and
`./gradlew :androidApp:assembleDebug` pass. The new tests cover source heights,
courtyard roof triangulation/winding, bounded input, cancellation, near/far stream
transitions, and pacing at 60/75/90/120/144 Hz. No export geometry was changed.
Earlier browser render-hook measurements **before the bridges/night-emission pass**
(12 seconds after warmup, Mac GPU, balanced,
DPR 1): city at 1280×720 — 30.0 FPS, p95 40.0 ms, 147 calls / 405,112 triangles;
city at 390×844 — 30.0 FPS, p95 40.3 ms, 100 calls / 351,354 triangles;
Baiterek night at 390×844 — 30.0 FPS, p95 40.1 ms, 84 calls / 484,366 triangles.
No shader/console errors were observed. These are **browser measurements, not
physical-phone certification**. Android APK was built and installed with data
preserved. Emulator screenshots were accessible but native clicks returned
`noWindowsAvailable`, so an Android render/interaction/FPS pass was **not completed**.
The owner was asked to open the debug launcher manually; do not treat installation
as proof that the Android scenario passed.

Scope/limits: the new overview is currently **bundled geographic data**, refreshed
by `node import-overview.mjs` + `npm run build`. It has not yet been added to the
versioned remote geography manifest, so refreshing this particular base snapshot
currently needs a new bundle. Existing remote landmark/address/entrance updates
are unchanged. No production data, backend deployment or Git push in this pass.
Physical Android, iOS, long-session thermal/memory profiling, full offline cold
startup and broad low-end-device testing remain unverified.

## Look pass `baiterek-golden-1` — 16 November 2026 (evening)

Owner feedback: the map is 90% of how the product is perceived and the previous
frame did not read as a living, attractive city. This pass changes **how the
existing scene is lit, coloured and framed**; it adds no data, API or geography.
It supersedes the rendering description of the Nūrjol Garden section below.
Target reference: `art/baiterek-approved-direction.png` (mood, not a pixel match).

- **Palette (day only).** Fresh park greens, warm roads, brighter water; open
  ground (`land`) is a neutral pale sage, **not lawn** — only mapped green areas
  are green. New keys `rooftop`, `canopy`, `amber`. `roof` and `leaf` keep their
  old values because they are baked into the exported landmark pack — no model was
  re-exported and `check-export` still passes byte-for-byte. `rooftop` equals the
  landmark roof teal on purpose (see the correction below). Night values unchanged.
- **Light.** Sun lowered to about 37° and warmed, hemisphere fill reduced, so
  shadows read. The single shadow map is 2048² and its frustum now follows the
  camera distance (150–420 m half-span) instead of a fixed 420 m. Tone mapping is
  `NeutralToneMapping` (keeps saturation) instead of ACES.
- **Sky.** `runtime/sky.js`: gradient dome, 18 soft clouds, stars at night — two
  draws, no textures. A small prefiltered copy of the same gradient is used as
  `scene.environment`, rebuilt only on a day/night/theme change, so glass and the
  golden sphere reflect the sky instead of rendering as dark metal.
- **Depth.** Fog now ends where content ends: about 1.45 km for the authored
  quarter and the current tile-ring radius (`city.reach()`) for the streamed
  city, so unloaded ground dissolves into the horizon instead of showing as an
  empty field. The streaming budget itself (9 visible tiles) is unchanged.
- **Camera.** The default square view is lower (about 12° down) so the horizon
  and sky are in frame. Controls and limits are unchanged.
- **Buildings.** Towers above 70 m are split between teal and new amber glass
  (deterministic by OSM id). Glass is brighter by day; stone buildings keep
  darker panes.
- **Vegetation.** Crowns are five-lobe clusters with a baked dark-to-light
  gradient (280 triangles each, was 240). A lawn tone/mowing-band shader was
  tried and removed the same evening: fine bands shimmer at grazing angles.
- **Motion.** `runtime/traffic.js`: up to 28 decorative cars (12 in economical
  mode) on the quarter's own secondary/tertiary/residential centrelines, one
  instanced draw, no lights or shadows. They are street dressing like the
  pedestrians — **not** live traffic or user positions. Pedestrians: 44 at 1.9×
  readability scale (was 32 at 1.2×).
- **Chrome.** A CSS-only vignette; no post-processing pass was added.

Budgets touched on purpose: quarter mesh batches < 48 (was < 45: amber glass and
traffic); hero view measured 110 draws / 250k triangles (was 104 / 219k).

Verification: **159 JS tests** (two new in `test/traffic.test.mjs`; the batch
budget and palette-key assertions were updated) and `check-export` pass. Day, night,
overview, close-up and 390×844 portrait frames were rendered from the real engine
in headless Chromium and inspected; no shader or console errors.

**Correction after the owner's first look (same evening).** The first version of
this pass was only inspected at the Baiterek quarter. On the streamed city the
owner reported landmarks that looked "shifted" and an empty lawn to the horizon.
Checked on real tiles in the owner's running preview: landmark geometry is
unchanged (hash-checked pack, side-by-side renders against the previous build),
but three choices of this pass made existing weaknesses obvious and were reverted
or fixed: lawn-green `land` made car parks and unloaded areas read as grass (now
neutral); warm ordinary roofs merged with ivory landmarks (now teal again); the
lower camera exposed the edge of the tile ring (now fogged). The authored
**National Museum model is withheld** (`withheldModels` in `main.js`): its anchor is
about 45 m off the real outline, so it was drawn beside the real footprint as two
overlapping buildings; the real outline alone is shown until it is re-placed.
An orientation audit of all 19 models against the catalogue outlines found
authored models that were never rotated to the real building: **MEGA 18.8°** and
the **Palace of Peace 14.6°** are corrected at load (`placementYaw` in `main.js`,
to be folded into the source at the next export). Talan Towers (about 14° off the
street grid, long side possibly on the wrong axis) and Abu Dhabi Plaza (about
36 m off centre) are **not corrected**: the right answer is not evident from the
outline alone. Still crude from above and needing new models rather than a
colour fix: MEGA (a low wavy roof slab) and the roof cap of BC «Moskva».

**Measured / not measured.** On the owner's Mac in the desktop app's browser pane
(574×859 CSS px, DPR 1.35, balanced): Baiterek quarter 100 draws / 249k triangles,
MEGA area 129 draws / 238k triangles, both at the 30 FPS cap with p95 34 ms and
no frame above 50 ms over the 4-second sample. That is one desktop GPU: there is
still **no phone measurement** — the larger shadow map, environment lighting and
extra triangles must be measured on a physical phone with `?visualperf=1`.
Android WebView and iOS were not run. The native bundle under `composeResources`
was rebuilt from this source; no APK was built and nothing was deployed.

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
