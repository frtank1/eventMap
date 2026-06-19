---
name: eventmap-build-verify
description: >-
  Build, fix, and verify the EventMap Kotlin Multiplatform project (Compose app in
  02_Code + Ktor backend in 04_Backend/server). Use this skill WHENEVER the user
  asks to build the Android app, fix compilation/build errors, "make it compile",
  get the backend running or "to 100%", wire endpoints to features, or check the UI
  against the Figma design ("сверь с фигмой", "соответствие макету", design QA).
  Also trigger on vaguer asks like "почини проект", "собери андроид", "доведи бэк",
  "проверь экраны на соответствие" — even if EventMap, Gradle, Ktor, or Figma are
  not named explicitly, as long as the active project is EventMap. This is the
  default playbook for any build → fix → backend → design-verification work on EventMap.
---

# EventMap: build, fix, and verify

This skill is the playbook for getting the EventMap project compiling, finishing the
backend, and verifying screens against the Figma design. It encodes the project's real
layout, exact Gradle commands, environment requirements, and the order of operations so
you don't have to rediscover them each session.

EventMap is a Kotlin Multiplatform product:

- **`02_Code/`** — Compose Multiplatform client. Targets Android, iOS (arm64 + sim),
  desktop (JVM), and wasmJs. Feature modules are split `domain` / `data` / `ui`
  (auth, event, map, feed, profile, search, video) plus `core` modules. Navigation is
  Voyager, DI is Koin, networking is Ktor client. Android target compiles to **JVM 11**.
- **`04_Backend/server/`** — Ktor 2.3.12 server, Clean Architecture
  (`presentation` → `domain` ← `data`), Exposed + PostgreSQL, Koin DI, JWT auth, Flyway
  migrations. Runs on **JDK 17**, port 8080.
- **Figma** — file `Lf0iBDnxz7x80uA4LAAyDJ` ("EventMap · Design v2 (Reels)"). The API
  contract lives in `05_Docs/API_CONTRACT.md`.

All paths below are relative to the EventMap project root
(`.../Documents/Claude/Projects/EventMap`). The client and backend are **separate Gradle
builds** with their own wrappers — never run client tasks from the backend dir or vice versa.

## Work in phases, in this order

Do the phases in order and don't jump ahead: a green compile is the foundation for
trusting backend behavior, and a running backend is what makes design-verification of
data-driven screens meaningful. If the user only asks for one phase, do that one — but
still respect the dependencies (e.g. you can't verify a live screen against Figma if the
app doesn't build).

1. **Get the client compiling** (`02_Code`) — see `references/android-build.md`
2. **Finish and run the backend** (`04_Backend/server`) — see `references/backend.md`
3. **Verify screens against Figma** — see `references/figma-verify.md`
4. **Deploy & wire the app (go-live)** — see `references/deploy.md`

Phase 4 is what makes the app stop being "raw": deploy the backend to a public URL and
point the Compose app at it so auth and event creation actually persist. Before any
go-live work, read `STATE.md` (next to this file) for what's already done and what is
still blocked on the user (Render/GitHub access, secrets). The live gap list with
priorities is `05_Docs/GAPS_2026-06-18.md`.

Read the relevant reference file in full before starting that phase. They contain the
exact commands, the known gotchas (Mapbox token, JDK versions, dev auth bypass), and the
finish-line checklist for each phase.

## Environment preconditions (check once, up front)

These bite every session if skipped, so verify them before building:

- **Gradle needs network.** The repo note in the backend README explicitly says compile
  was never run because the previous environment had no network for Gradle. If
  dependency resolution fails, that's the cause — confirm network/repository access
  before assuming the code is broken.
- **Mapbox token.** `02_Code/settings.gradle.kts` reads `MAPBOX_DOWNLOADS_TOKEN` from
  `~/.gradle/gradle.properties`. Without it, `:common:feature:map:ui` and `composeApp`
  fail to resolve the Mapbox SDK. If you see a 401 from `api.mapbox.com`, the token is
  missing or expired — surface this to the user; do not try to work around it.
- **Two JDKs.** Client Android target = JVM 11; backend = JDK 17 (`jvmToolchain(17)`).
  A toolchain mismatch shows up as cryptic "invalid target release" or bytecode errors.
- **Sandbox disk space.** Gradle builds and the Kotlin daemon need real disk. If the
  Linux sandbox reports "not enough disk space", builds can't run — report it rather
  than producing fake results.

## How to fix compilation errors (the core loop)

The goal is a clean `assembleDebug`, reached by iterating — not by guessing. For each pass:

1. Run the build with `--console=plain` and capture the full output.
2. Read the **first real error**, not the last line. Kotlin error cascades: one missing
   symbol or signature change produces dozens of downstream errors. Fix the root and
   most of the cascade disappears.
3. Locate the symbol with `Grep`/`Glob` across the module and its dependencies. Because
   modules are split `domain`/`data`/`ui`, a missing type is often defined in a sibling
   module that isn't declared as a dependency — check the module's `build.gradle.kts`
   `dependencies` block, not just the source.
4. Make the smallest change that addresses the root cause. Prefer fixing the definition
   or the dependency wiring over silencing the symptom. Never delete code or stub a
   function just to make the compiler pass — that defeats the "to 100%" goal.
5. Rebuild. Repeat until green.

Explain each non-trivial fix to the user in one line (what was wrong, what you changed)
so the work is reviewable. Batch related fixes within a module before rebuilding to save
build cycles, but don't let unrelated changes pile up unreviewed.

## Definition of done

- `02_Code`: `./gradlew :composeApp:assembleDebug` succeeds with no errors.
- Backend: `./gradlew build` compiles, `./gradlew test` passes, server starts and
  `GET /events` responds.
- Figma: each in-scope screen has a written pass/fail verdict per
  `references/figma-verify.md`, with concrete diffs (spacing, color, type, missing
  elements) and file references for anything that needs changing.

Report results honestly. "Compiles but 3 screens diverge from Figma" is a useful answer;
a false "done on all 100%" is not.
