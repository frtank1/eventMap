# EventMap — current state (resume point)

Last updated: 2026-06-18. Update this file whenever you finish a meaningful step so the
next session resumes instantly. Full gap list: `05_Docs/GAPS_2026-06-18.md`.

## Where we are

Chosen path: **deploy backend to the cloud (Render)**, then wire the mobile app to it.
Backend business logic is largely done (~4500 lines, 40 endpoints, migrations V1–V5,
JWT + refresh/logout). It has **never been compiled** — the Render Docker build
(`gradle:8.7-jdk17`) will be the first real compile.

## Done

- Diagnosed why the app is "raw": no live backend; dev stubs masked it.
- Mobile prepped for go-live (all in `02_Code`):
  - `PlatformSdk.kt` → `baseUrl = "https://REPLACE-ME.onrender.com/"` (placeholder).
  - `AuthRepositoryImpl` → `orDevBypass` **removed** (no more fake token on net failure).
  - `CreateEventViewModel.publish` → Failure no longer fakes `created=true`; shows error.
- Deploy assets confirmed ready: `render.yaml`, `04_Backend/server/Dockerfile`, `DEPLOY.md`.
- DI sanity-checked: all 51 `by inject()` in `Application.kt` are registered in `AppModule`.
- Full static compile-readiness audit (2026-06-18): read all 37 backend `.kt` + build.gradle.kts,
  cross-checked DI/signatures/DTO-model-table/imports/Exposed types/when-exhaustiveness/suspend/deps.
  **0 high-confidence compile-breakers → likely-compiles.** Final green = the Render Docker build
  (can't compile in sandbox: no JDK17, zero network).
- NEW backend features implemented 2026-06-18 (Clean-arch slices, no new Gradle deps; second static
  audit of all 18 new/modified files = 0 compile-breakers):
  - **Media storage (V6):** `media` table, `StorageService` (S3/R2 SigV4 presign, pure JDK), ports
    StoragePort/MediaRepository, routes `POST /media/presign`, `POST /media/confirm`, real
    `GET /events/{id}/media`. Env: S3_ENDPOINT/REGION/BUCKET/ACCESS_KEY/SECRET_KEY/PUBLIC_BASE. Off → 503.
  - **Video reels (V7):** `videos`+`video_likes`, routes `GET /videos/now`, `POST /videos/{id}/react`,
    `POST /videos` — matches mobile VideoRemoteDataSource contract exactly.
  - **Push (V8):** `device_tokens`, `FcmPushSender` (FCM HTTP v1 via JDK HttpClient + RS256 SA auth),
    routes `POST /push/register`, `POST /admin/push/test` (X-Admin-Key). Env: FCM_PROJECT_ID/CLIENT_EMAIL/PRIVATE_KEY. Off → no-op.
  - All wired in AppModule + Application. `.env.example` updated.
  - Push triggers wired: friend-request (SendFriendRequestUseCase) + event-invitation
    (CreateInvitationUseCase) → `notifications.sendToUser(...)`, fire-and-forget (runCatching).
    Remaining triggers (reminders/moderation) use the same one-line pattern.
  - TODO (follow-up, low-risk): real LIVE streaming needs an external media server
    (Mux/Cloudflare Stream) — reels cover the in-app video.
  - RUNTIME-UNVERIFIED: SigV4 presign and FCM RS256/JWT are correctness-sensitive — test against real
    R2 + Firebase creds after deploy.

## Blocked on the user (access, not instructions)

1. Render: New → Blueprint → repo `frtank1/repo_event_map` → Apply.
2. Set `CORS_HOSTS` in both Render services (prod won't boot without it).
3. Provide the Live URL (`…onrender.com/health` → `OK`).

## Next action (automatable, the moment a Live URL exists)

- Replace the `baseUrl` placeholder in `PlatformSdk.kt` with the real URL.
- For prod builds set `debug = false` in `PlatformSdk.kt` (debug logs tokens).
- Smoke-test: register → login → create event → appears in `GET /events` / on the map.

## Known caveats

- Removing the dev-bypass means the app can't log in until a live backend exists. To play
  with the UI offline again, gate the bypass behind `CoreConfig.demoMode` instead of
  re-adding it raw.
- iOS map is still a placeholder (no Mapbox SDK) — separate task, not part of go-live.
- Render free plan sleeps after 15 min idle; free DB is time-limited.
