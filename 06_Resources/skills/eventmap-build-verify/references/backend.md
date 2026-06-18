# Phase 2 — Finish and run the backend (`04_Backend/server`)

The backend is a Ktor server in Clean Architecture. Per the project README, the full §8
contract is implemented (7 domains, 40 endpoints, migrations V2–V5) **but Kotlin
compilation was never run** because the prior environment had no network for Gradle. So
"доведи бэк до 100%" means: make it compile, make the tests pass, get it serving, and
wire it to the client.

## Layout (dependencies point inward only)

```
presentation/   routes + DTO (+ domain→dto mappers)   ─┐
   │ depends on                                          │ pure, framework-free domain
domain/         model + repository ports + usecases      │
   ▲ implemented by                                       │
data/           Exposed tables, DatabaseFactory, repos  ─┘
security/ (JwtService)   di/ (Koin appModule)   config/ (env-based AppConfig)
```

Rule: `domain` knows nothing about Ktor or Exposed. When you add or fix a feature, the
flow is: define/adjust the port and model in `domain` → implement in `data` → expose in
`presentation/routes` → register the usecase in `di/AppModule.kt` → inject and mount the
route in `Application.kt`. Forgetting the DI registration is the most common runtime
failure (Koin "no definition" at startup), so check `AppModule.kt` and `Application.kt`
agree whenever you touch usecases or routes.

## Commands

Run from `04_Backend/server/` (separate Gradle build, **JDK 17**, `jvmToolchain(17)`):

```bash
cd 04_Backend/server

# Compile (this is the first thing to get green — it has never been run):
./gradlew compileKotlin --console=plain

# Full build + unit tests (Fakes-based usecase tests already exist):
./gradlew build --console=plain
./gradlew test --console=plain

# Run with Postgres via Docker (preferred — seeds Astana data on first start):
docker compose up --build           # Postgres + server on http://localhost:8080

# Run without Docker (needs local Postgres db `eventmap`, user/pass eventmap/eventmap,
# or override DB_URL / DB_USER / DB_PASSWORD):
./gradlew run
```

If Docker fails to connect, the daemon isn't running — that's an environment issue to
surface, not a code bug (this has come up before in this project).

## Smoke tests once it's up

```bash
curl http://localhost:8080/events
curl -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"email":"a@a.kz","password":"123456","name":"Азамат"}'
```

A successful `/auth/register` returns an `AuthResponse` with JWT access + refresh tokens.

## Fixing compile errors here

Same root-cause-first loop as the client. Backend-specific notes:

- Versions are inline `val`s at the top of `build.gradle.kts` (ktor 2.3.12, exposed 0.53.0,
  koin 3.5.6) — **not** a version catalog. Keep them consistent across files.
- **Ktor 2.x** API (not 3.x like the client's Ktor client). Don't mix 3.x server symbols.
- Exposed table/column definitions live in `data/db/Tables.kt`; DTOs in
  `presentation/dto/Dto.kt`; ports in `domain/repository/Repositories.kt`. A field added
  in one usually needs a matching change in the others plus the repo impl.
- Migrations are Flyway SQL (`V2__…` … `V5__…`). The README lists known TODOs:
  `location_pings` cleanup cron, `pulse_aggregates` recompute job (30s), quest QR HMAC
  keys. Implement these only if the user asks — they're noted as deferred.

## Wire the client to the backend

After the server runs, connect the app (per backend README):

- Set `CoreConfig.baseUrl = "http://10.0.2.2:8080/"` in the client's `PlatformSdk.kt`
  (`10.0.2.2` is the Android emulator's alias for the host's `localhost`).
- Remove the dev auth bypass in `AuthRepositoryImpl` so the app uses real `/auth` calls.

## Finish line for Phase 2

`./gradlew build` and `./gradlew test` are green, the server starts, `/events` and
`/auth/register` respond correctly, and the client points at the running backend.
