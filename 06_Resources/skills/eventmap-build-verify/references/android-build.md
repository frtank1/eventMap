# Phase 1 — Build the client and fix compilation (`02_Code`)

The client is a Compose Multiplatform app. The user's primary ask is usually "собери
андроид, он не компилируется", so the target that matters is the Android debug APK. The
other targets (iOS, desktop, wasmJs) share `commonMain`, so fixing common code fixes them
too — but build Android first and treat it as the source of truth.

## Commands

Run everything from `02_Code/` (it has its own `gradlew`):

```bash
cd 02_Code

# Primary goal: Android debug build
./gradlew :composeApp:assembleDebug --console=plain

# Faster signal while iterating on common code (no APK packaging):
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain

# Compile just one feature module you're working in, e.g. event/ui:
./gradlew :common:feature:event:ui:compileDebugKotlinAndroid --console=plain

# When stuck on stale state:
./gradlew --stop          # kill daemons
./gradlew clean
```

Prefer the narrowest task that gives signal — compiling a single module is far faster than
a full `assembleDebug` and isolates the error to one place. Only run the full
`assembleDebug` to confirm the whole graph is green.

## Module map (where things live)

`settings.gradle.kts` declares these. A "cannot find symbol" almost always means the type
exists in one of these but isn't on the current module's dependency list:

```
:composeApp                      app entry, wires all features
:common:core:{platform,datastore,network,ui,utils,files,biometry,logger,connectivity}
:common:feature:{example,map,auth,event,profile,feed,search,video}
        ↳ each feature (except map) = :domain + :data + :ui
```

Dependency direction inside a feature: `ui → domain`, `data → domain`. `ui` and `data`
never depend on each other directly; they meet through `domain` interfaces and DI (Koin).
If `ui` needs something from `data`, that's a smell — the contract belongs in `domain`.

## Known gotchas (check these first)

- **Mapbox token** — `:common:feature:map:ui` and `:composeApp` pull the Mapbox SDK from
  an authenticated Maven repo using `MAPBOX_DOWNLOADS_TOKEN` from
  `~/.gradle/gradle.properties`. A `401` from `api.mapbox.com` = missing/expired token.
  This is an environment fix, not a code fix — tell the user.
- **JVM 11 target** — the Android target sets `jvmTarget = JVM_11`. Don't "fix" a target
  error by bumping it; match the toolchain instead.
- **Type-safe project accessors** — the build uses `projects.common.feature.x.ui` style
  accessors (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`). When adding a module
  dependency, use that form to stay consistent with the existing `build.gradle.kts` files.
- **Versions are centralized** — all versions live in `gradle/libs.versions.toml`. Add or
  change dependencies there via `libs.*` aliases, never with hardcoded coordinates in a
  module build file.
- **`local.properties`** must point `sdk.dir` at a valid Android SDK. A missing SDK looks
  like an AGP failure, not a Kotlin error.

## Finish line for Phase 1

`./gradlew :composeApp:assembleDebug` completes with `BUILD SUCCESSFUL`. Note any warnings
worth flagging, then move to the backend.
