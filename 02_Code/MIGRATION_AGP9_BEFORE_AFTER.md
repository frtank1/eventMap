# Миграция на AGP 9 — детально «было → стало»

Дата: 2026-06-20. Полный перечень изменений в `02_Code` с фрагментами кода и путями.
Версии оставлены новыми: AGP 9.2.1, Gradle 9.4.1, Kotlin 2.3.0.

Суть: `com.android.library` и `com.android.application` больше несовместимы с KMP-плагином.
Библиотеки → `com.android.kotlin.multiplatform.library`; приложение вынесено в отдельный модуль.

---

## 1. `gradle/libs.versions.toml` — каталог плагинов

**Где:** секция `[plugins]`.

**Было:**
```toml
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
```

**Стало (добавлены 2 строки):**
```toml
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
androidKotlinMultiplatformLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```
(старые алиасы не удалял — просто перестали использоваться.)

---

## 2. `build-logic/src/main/kotlin/core.kmp.gradle.kts` — convention-плагин для библиотек

**Было:**
```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}
kotlin {
    androidTarget()
    iosX64(); iosArm64(); iosSimulatorArm64()
    jvm("desktop")
    wasmJs { browser() }
}
android {                                   // верхнеуровневый блок
    compileSdk = libs.findVersion("android-compileSdk")...
    defaultConfig {
        minSdk = libs.findVersion("android-minSdk")...
    }
}
```

**Стало:**
```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")     // новый плагин
}
kotlin {
    android {                                           // android внутри kotlin{}
        compileSdk = libs.findVersion("android-compileSdk")...
        minSdk = libs.findVersion("android-minSdk")...
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        androidResources { enable = true }              // ресурсы по умолчанию выключены
    }
    iosX64(); iosArm64(); iosSimulatorArm64()
    jvm("desktop")
    wasmJs { browser() }
}
// верхнеуровневого android{} больше нет
```
Добавлен импорт `import org.jetbrains.kotlin.gradle.dsl.JvmTarget`.

---

## 3. `build-logic/src/main/kotlin/core.kmp.compose.gradle.kts` — то же для UI-модулей

Изменения идентичны п.2 (плагин + `kotlin{android{}}`), плагины Compose сохранены:
```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")     // было: com.android.library
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
```

---

## 4. 39 библиотечных модулей `common/**/build.gradle.kts`

**Где:** в конце каждого модульного build-файла (auth/event/map/video/profile/... — все 39).

**Было (пример `common/feature/event/data`):**
```kotlin
android { namespace = "com.eventmap.feature.event.data" }
```

**Стало:**
```kotlin
kotlin {
    android {
        namespace = "com.eventmap.feature.event.data"
    }
}
```
(compileSdk / minSdk / jvmTarget приходят из convention-плагина; модуль задаёт только namespace.)
Сделано скриптом по всем 39 модулям, namespace у каждого свой.

---

## 5. `composeApp/build.gradle.kts` — приложение стало KMP-БИБЛИОТЕКОЙ

**Было:**
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)             // ← приложение
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { ... framework "ComposeApp" }
    jvm("desktop")
    wasmJs { browser(); binaries.executable() }
    sourceSets {
        androidMain.dependencies { activity.compose; fragment; uiToolingPreview }
        commonMain.dependencies { ...весь общий UI и feature-модули... }
        ...
    }
}
android {                                              // ← app-конфиг
    namespace = "com.eventmap"
    compileSdk = ...
    defaultConfig { applicationId = "com.eventmap"; minSdk; targetSdk; versionCode; versionName }
    packaging { ... }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { VERSION_11 }
}
dependencies { debugImplementation(libs.compose.uiTooling) }
compose.desktop { application { mainClass = "com.eventmap.MainKt"; ... } }
```

**Стало:**
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)   // ← библиотека
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}
kotlin {
    android {                                          // android внутри kotlin{}
        namespace = "com.eventmap"
        compileSdk = ...; minSdk = ...
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { ... framework "ComposeApp" }
    jvm("desktop")
    wasmJs { browser(); binaries.executable() }
    sourceSets {
        commonMain.dependencies { ...весь общий UI и feature-модули (без изменений)... }
        // androidMain.dependencies удалён — android-вход уехал в :androidApp
        ...
    }
}
// верхнеуровневые android{}, dependencies{} и applicationId/buildTypes/packaging — УДАЛЕНЫ
compose.desktop { application { mainClass = "com.eventmap.MainKt"; ... } }   // остался (desktop)
```

**Что осталось в `composeApp`:** общий код `commonMain` (App.kt, MainScreen.kt, PlatformSdk.kt,
AuthStubScreen.kt), входы `desktopMain/main.kt`, `iosMain/MainViewController.kt`,
`wasmJsMain/main.kt`. Папка `composeApp/src/androidMain/` теперь пустая.

---

## 6. НОВЫЙ модуль `androidApp/` — Android-приложение

**Перенесённые файлы** (`git mv composeApp/src/androidMain/...` → `androidApp/src/main/...`):

| Было | Стало |
|---|---|
| `composeApp/src/androidMain/kotlin/com/eventmap/MainActivity.kt` | `androidApp/src/main/kotlin/com/eventmap/MainActivity.kt` |
| `composeApp/src/androidMain/kotlin/com/eventmap/EventMapApp.kt` | `androidApp/src/main/kotlin/com/eventmap/EventMapApp.kt` |
| `composeApp/src/androidMain/AndroidManifest.xml` | `androidApp/src/main/AndroidManifest.xml` |
| `composeApp/src/androidMain/res/` (иконки, `strings.xml` с токеном Mapbox) | `androidApp/src/main/res/` |

(сами `MainActivity.kt` / `EventMapApp.kt` / манифест **не менялись** — только переехали.)

**Новый файл `androidApp/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.androidApplication)             // обычное Android-приложение
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
android {
    namespace = "com.eventmap"
    compileSdk = ...
    defaultConfig { applicationId = "com.eventmap"; minSdk; targetSdk; versionCode = 1; versionName = "1.0" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { VERSION_11 }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
dependencies {
    implementation(projects.composeApp)                // общий UI и PlatformSDK
    implementation(projects.common.core.platform)      // PlatformConfiguration
    implementation(projects.common.core.biometry)      // BiometricActivityHolder
    implementation(libs.filekit)                       // FileKit
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(compose.runtime); implementation(compose.foundation)
    implementation(compose.material3); implementation(compose.ui)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
```
Три модуля (platform/biometry/filekit) подключены **напрямую**, т.к. `composeApp` тянет их через
`implementation` и транзитивно не отдаёт.

---

## 7. `settings.gradle.kts`

**Было:**
```kotlin
include(":composeApp")
include(":common:core:platform")
```
**Стало:**
```kotlin
include(":composeApp")
include(":androidApp")                                 // ← добавлено
include(":common:core:platform")
```

---

## 8. Корневой `build.gradle.kts`

**Было:**
```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    ...
}
```
**Стало (добавлены 2 строки):**
```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false   // ← добавлено
    alias(libs.plugins.kotlinAndroid) apply false                       // ← добавлено
    alias(libs.plugins.composeHotReload) apply false
    ...
}
```

---

## Как запускать после миграции

Раньше Android-приложение = `composeApp`. Теперь = **`androidApp`**.
В Android Studio выбери конфигурацию запуска **`androidApp`** (composeApp стал библиотекой).
Desktop — из `composeApp` (`compose.desktop`), iOS — фреймворк `ComposeApp`, wasm — `composeApp`.

## Не связано с миграцией, но менялось в эту сессию

- `composeApp/.../PlatformSdk.kt`: `baseUrl` → боевой `https://eventmap-guo1.onrender.com/`; `debug = false` (после утечки пароля в лог).
- Модуль `video`: добавлена клиентская обвязка загрузки (`VideoRepository.publishReel` + датасорс + `UploadModels.kt`).

> Полная схема структуры модулей и причины — в `MIGRATION_AGP9.md` (краткая версия).
