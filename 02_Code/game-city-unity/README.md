# EventMap · Unity engine experiment

Status: **isolated desktop prototype, not a migration of the mobile application**.
Unity Editor version: **6000.3.26f1**. Created 2026-11-12.

## What is here

- Original Baiterek, Ak Orda and Astana Arena models imported from the existing
  `../game-city/content` pack, retaining their IDs and geographic coordinates.
- Editable Unity scene, ordinary prefabs, meshes and materials; no paid assets.
- Orbit, pan, zoom, place labels, focus on a person, basic day/night switch.
- Temporary articulated avatar: skin/hair/clothing palettes, three hairstyles,
  backpack, local appearance persistence. Appearance-only `EMAV1:` copy/paste code.
- Simulated walking/driving and a clearly labelled simulated friend. **No GPS,
  location permission, account connection or position upload exists in this pilot.**
- Only three landmarks. Ordinary surrounding buildings, paths and roads are
  demonstrational scenery, not a geographic survey or routing network.

The city is not a finished art direction. This experiment answers whether the
existing assets can be carried into an editable engine project. It does not
establish that Unity is fast enough on target phones or that avatars are shipped.

## Run

In Unity Hub, Add project from disk → this `game-city-unity` directory. Open
`Assets/EventMap/Scenes/CityPilot.unity`, then Play.

On the development Mac a local standalone build is available at
`Builds/EventMap City Pilot.app`. Builds are intentionally not committed.
To recreate it: Unity menu **EventMap → 3 · Build macOS pilot**.

Controls: left drag rotates, wheel / + / − zoom, right drag or Shift+drag pans.
The left panel selects landmarks, zooms to the avatar and opens appearance editing.
Escape returns to Baiterek. The UI currently targets a desktop landscape window;
touch input and the final mobile layout are not implemented.

## Assets and model editing

- `Assets/EventMap/Imported`: generated architecture. Importing again can replace
  these assets. Keep hand-edited work outside this directory.
- `Assets/EventMap/Custom`: put your own FBX models/materials/prefabs here. The
  folder may be empty on checkout; create it if needed.
- Save a copy of `CityPilot.unity` before custom scene editing. The scene creation
  menu replaces this generated scene, with a confirmation in interactive mode.
- A landmark root carries `CityPlace` with a stable `placeId`, latitude/longitude
  and view distance. Replace its visual children, keeping that root's metadata.
- Use metres, ground-level pivot, Unity +Y up / +Z north. Export FBX from Blender
  with correct transforms; verify orientation and scale after import.
- The current importer accepts only the existing static JSON schema: no textures,
  rigs, animations or arbitrary downloaded scripts. The present pack contains only
  front-sided materials; double-sided rendering is not implemented by this importer.

Source architecture remains in `../game-city`. Import validates size, SHA256,
schema, positions, indices and palette ranges, then reflects Z and reverses winding
to convert the Three.js south-facing axis to the Unity north-facing axis.

## Checks performed

2026-11-12:

- Headless Unity import and C# compilation succeeded.
- **17 checks passed**: coordinate conversion/bounds, avatar code validation,
  winding/normals, three imported prefabs, generated scene availability.
- macOS development build succeeded; app was launched and Baiterek rendered.
- Manual interaction pass for avatar editing, driving, copy/paste and night mode
  is **not complete**. Work was paused at the user's request to save and discuss.
- Android/iOS builds and real-device memory/FPS/battery testing are **not done**.
  This Mac currently has only the macOS Unity build-support module installed.

Run checks from **EventMap → Run pilot checks**. Machine-readable output is
`Reports/pilot-checks.json` (ignored generated artifact).

## What must happen before mobile integration

1. Agree on the first product scenario and decide whether this visual experiment
   serves it. Do not replace the working map just because the engine can render it.
2. Install the required Unity mobile build-support modules; test a separate
   full-screen city scene on one Android phone and one iPhone. Measure startup,
   frame time, peak memory, heating and return to native screens.
3. Native Kotlin/Swift remains responsible for login, events, permissions and
   sharing consent. Unity receives bounded display data, not auth tokens. Define
   and validate the bridge before wiring it into the app.
4. Replace temporary avatar geometry with an original/licensed rigged model,
   add real LODs, touch controls and a properly responsive UI.
5. Only then implement real selected-friend sharing: disabled by default, explicit
   recipient and expiry, immediate revoke, blocked users excluded, clear stale /
   offline state, no invented movement when updates stop, no stored route history
   by default. Background tracking requires a separate explicit user choice.
6. Verify the full flow on two devices. A moving demo character is not evidence
   of real location sharing.

## Updating models without rebuilding the mobile app

**Not implemented in this Unity project.** The existing Three.js pilot has its
own remote data-pack flow; it is not automatically inherited by Unity.

The proposed Unity path is versioned Addressables content bundles and a remote
catalog, separately built for Android and iOS. Buildings, outfits and supported
animation assets can be content updates within a compatible player version.
Engine changes, new scripts/behaviour or incompatible schemas still need an app
release. Plan allowlisted HTTPS hosting, validation, cache, rollback and a bundled
fallback before enabling remote content. No CDN or paid service was created here.

Official references:

- [Unity as a Library](https://docs.unity3d.com/6000.0/Documentation/Manual/UnityasaLibrary.html):
  Android/iOS integration has full-screen rendering and lifecycle/memory constraints.
  This is why we are testing a separate scene rather than promising a drop-in map tab.
- [Addressables remote content](https://docs.unity3d.com/Packages/com.unity.addressables@2.7/manual/RemoteContentDistribution.html).
- [Addressables content updates](https://docs.unity3d.com/Packages/com.unity.addressables@2.7/manual/ContentUpdateWorkflow.html).

No current mobile screens, backend APIs, production settings or deployments were
changed for the Unity experiment.
