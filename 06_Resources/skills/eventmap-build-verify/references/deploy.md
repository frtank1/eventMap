# Phase 4 — Deploy backend & wire the mobile app (go-live)

Goal: a running backend on a public URL, and the Compose app talking to it for real —
auth and event creation actually persist. This phase makes the app stop being "raw".

Full operator guide lives in the repo root: `DEPLOY.md`. Infra-as-code: `render.yaml`.

## What is automatable (do this without the user)

- Edit `02_Code/composeApp/src/commonMain/kotlin/com/eventmap/PlatformSdk.kt` →
  set `baseUrl` to the deployed URL (trailing slash, no `/v1`).
- Remove dev stubs that mask a missing backend:
  - `AuthRepositoryImpl.orDevBypass` (fake "dev-token" on network failure) — **removed**.
  - `CreateEventViewModel.publish` Failure branch must NOT set `_created = true` — **fixed**
    (now shows the error, keeps the form open).
- Prepare commits, configs, env templates, build/compile checks.
- Verify `GET /health` and `GET /events` against a URL the user provides.

## What REQUIRES the user (no skill removes these — access, not instructions)

- Logging into Render / GitHub under their account.
- Clicking **New → Blueprint → Apply** in the Render dashboard.
- Secrets they alone hold: `CORS_HOSTS`, Mapbox secret/download token, payment keys.
- `git push` to `frtank1/repo_event_map` if their credentials aren't available in-session.

Be explicit about this boundary. Don't imply a skill lets you self-provision cloud infra.

## Deploy steps (Render Blueprint — the user does the dashboard parts)

1. Repo is already on GitHub: `frtank1/repo_event_map` (branches `main`, `staging`).
2. Render → **New → Blueprint** → select the repo → **Apply**. `render.yaml` defines
   2 Postgres DBs + 2 web services (`eventmap-staging`, `eventmap-prod`). `JWT_SECRET`
   and `ADMIN_KEY` are auto-generated.
3. Set `CORS_HOSTS` in each service (Environment tab) — **prod won't start without it**
   (guard in `AppConfig`). For a first smoke test, the service's own Render URL works.
4. Build runs in Docker (`gradle:8.7-jdk17`) — **this is where the backend compiles for
   real** (it was never built locally). Compile errors surface in Render **Logs**; pull
   them and fix per Phase 2 (`backend.md`).
5. When status is **Live**: `GET https://<service>.onrender.com/health` → `OK`.
6. Start with `staging` (free plan). Note: free plan **sleeps** after 15 min idle
   (~30 s cold start) and the free DB is time-limited — fine for testing, not for prod.

## Wire the app (automatable, once a Live URL exists)

1. `PlatformSdk.kt` → replace the `baseUrl` placeholder `https://REPLACE-ME.onrender.com/`
   with the real URL.
2. Confirm `demoMode = false`, and `debug = true` only for staging (debug logs tokens —
   must be `false` for prod; see review P1 item 7).
3. Android: Mapbox runtime token already in `composeApp/.../res/values/strings.xml`.
   iOS map is still a placeholder (`EventMapView.ios.kt`) — out of scope for go-live,
   tracked in `05_Docs/GAPS_2026-06-18.md`.
4. Rebuild client per Phase 1 (`android-build.md`) and smoke-test register → login →
   create event → see it on the map / in the list.

## Definition of done (Phase 4)

- `…onrender.com/health` returns `OK`; `GET /events` returns the Astana seed.
- App `baseUrl` points to the live URL; no dev-bypass; failed create shows an error.
- Register → login → create event round-trips through the real server and persists.
