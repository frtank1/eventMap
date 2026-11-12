# Mobile game-city pilot

Local adaptation of the existing Astana visualisation; the hosted private Site is untouched.

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
