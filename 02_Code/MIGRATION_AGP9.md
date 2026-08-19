# Миграция на AGP 9 (com.android.kotlin.multiplatform.library)

Дата: 2026-06-20. Что и зачем изменено в `02_Code`.

## Зачем

AGP **9.x** убрал совместимость плагинов `com.android.library` и `com.android.application`
с `org.jetbrains.kotlin.multiplatform` (ломается на этапе конфигурации). Старый обход
(`android.builtInKotlin=false` / `android.newDsl=false`) в AGP 9.2 уже не работает.
Официальное решение (developer.android.com/kotlin/multiplatform/plugin):
- библиотечные модули → новый плагин **`com.android.kotlin.multiplatform.library`**;
- модуль приложения (`com.android.application`) прямой замены не имеет → Android-приложение
  **выносится в отдельный модуль**.

Версии оставлены новыми: AGP 9.2.1, Gradle 9.4.1, Kotlin 2.3.0.

## Что изменено

### 1. Каталог версий `gradle/libs.versions.toml`
Добавлены алиасы плагинов:
- `androidKotlinMultiplatformLibrary` → `com.android.kotlin.multiplatform.library`
- `kotlinAndroid` → `org.jetbrains.kotlin.android` (для нового модуля приложения)

### 2. Convention-плагины `build-logic/src/main/kotlin/`
`core.kmp.gradle.kts` и `core.kmp.compose.gradle.kts`:
- `id("com.android.library")` → `id("com.android.kotlin.multiplatform.library")`;
- `androidTarget()` и верхнеуровневый блок `android { }` заменены на блок **внутри** `kotlin`:
  ```kotlin
  kotlin {
      android {
          compileSdk = ...; minSdk = ...
          compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
          androidResources { enable = true }   // в новом плагине ресурсы по умолчанию выключены
      }
      // iosX64/iosArm64/iosSimulatorArm64, jvm("desktop"), wasmJs { browser() }
  }
  ```

### 3. 39 библиотечных модулей `common/**/build.gradle.kts`
Верхнеуровневый блок `android { namespace = "..." }` перенесён внутрь `kotlin`:
```kotlin
kotlin {
    android {
        namespace = "..."
    }
}
```
(скриптом, namespace у каждого свой; compileSdk/minSdk/jvmTarget приходят из convention-плагина).

### 4. `composeApp` — стал KMP-БИБЛИОТЕКОЙ
`composeApp/build.gradle.kts`:
- плагин `com.android.application` → `androidKotlinMultiplatformLibrary`;
- android-конфиг (namespace, compileSdk, minSdk, jvmTarget) перенесён в `kotlin { android { } }`;
- убрано всё app-only: `applicationId`, `targetSdk`, `versionCode/Name`, `buildTypes`,
  `packaging`, `compileOptions`, блок `dependencies { debugImplementation(...) }`;
- остался общий код: `commonMain` (App, MainScreen, PlatformSdk), `desktopMain`, `iosMain`
  (фреймворк `ComposeApp`), `wasmJsMain`, блок `compose.desktop { application { ... } }`.

### 5. Новый модуль `androidApp` — Android-приложение
- `androidApp/build.gradle.kts`: `com.android.application` + `kotlinAndroid` + Compose,
  `applicationId = "com.eventmap"`, зависит от `:composeApp` (+ напрямую от
  `core.platform`, `core.biometry`, `filekit`, т.к. composeApp тянет их через `implementation`).
- Перенесены из `composeApp/src/androidMain` в `androidApp/src/main`:
  `MainActivity.kt`, `EventMapApp.kt`, `AndroidManifest.xml`, `res/` (иконки, `strings.xml`
  с токенами Mapbox/app_name).

### 6. `settings.gradle.kts`
Добавлено `include(":androidApp")`.

### 7. Корневой `build.gradle.kts`
В блок `plugins { ... apply false }` добавлены `androidKotlinMultiplatformLibrary` и `kotlinAndroid`.

## Как запускать теперь

Раньше Android-приложением был `composeApp`. Теперь это **`androidApp`**:
- В Android Studio выбери конфигурацию запуска **`androidApp`** (composeApp стал библиотекой и
  больше не запускается как приложение).
- `File → Sync Project with Gradle Files`, затем Run на `androidApp`.
- Desktop по-прежнему запускается из `composeApp` (`compose.desktop`), iOS — через фреймворк
  `ComposeApp`, wasm — из `composeApp` (wasmJsMain).

## На что смотреть при первой сборке (правки могли «не сесть» с первого раза)

Эта миграция вносилась **без локальной компиляции**, поэтому возможны точечные правки:
- точные имена в новом DSL `kotlin { android { } }` (`compilerOptions`, `androidResources`,
  `minSdk`/`compileSdk`) — если Gradle ругнётся, сверить с актуальной докой плагина;
- модули с собственным `androidMain.dependencies` (например `map/ui` — Mapbox SDK,
  `core/network` — ktor-android) — проверить, что зависимости подхватились;
- если какой-то Android-вход не видит класс из core-модуля — добавить этот модуль в зависимости
  `androidApp` напрямую (как сделано для platform/biometry/filekit);
- Compose-preview и hot-reload на новом плагине имеют известные нюансы (см. «Known issues» в доке).

Любую ошибку сборки — в чат, чиним точечно.
