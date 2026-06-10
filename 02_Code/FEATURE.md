# Как добавить новую фичу

Образец — `common/feature/example` (domain / data / ui). Копируешь его и правишь.

## 1. Модули

Фича = три Gradle-модуля:

```
common/feature/<name>/
  domain/   интерфейс репозитория + модели ответа
  data/     реализация репозитория, ktor-датасорс, koin-модуль
  ui/       экран, ViewModel, события, koin-модуль
```

Каждый `build.gradle.kts` минимальный (через convention-плагин):

```kotlin
// domain / data
plugins {
    id("core.kmp")
    alias(libs.plugins.serialization) // если есть @Serializable модели
}
kotlin { sourceSets { commonMain.dependencies { /* ... */ } } }
android { namespace = "<pkg>.feature.<name>.domain" }

// ui
plugins { id("core.kmp.compose") }
kotlin { sourceSets { commonMain.dependencies { /* ... */ } } }
android { namespace = "<pkg>.feature.<name>.ui" }
```

## 2. Зарегистрировать в сборке

`settings.gradle.kts`:
```kotlin
include(":common:feature:<name>:domain")
include(":common:feature:<name>:data")
include(":common:feature:<name>:ui")
```

`composeApp/build.gradle.kts` (commonMain):
```kotlin
implementation(projects.common.feature.<name>.ui)
implementation(projects.common.feature.<name>.data)
```

## 3. Подключить в DI

`composeApp/.../PlatformSdk.kt` → в `modules(...)` добавить koin-модули фичи
(`<name>RepositoryModule`, `<name>Module`).

## 4. Слои (как в example)

- **domain**: `interface XRepository { suspend fun ...: KtorResponse<...> }` + модели.
- **data**: `XRemoteDataSource` (ktor) → `XRepositoryImpl : XRepository` через `safeRequest { }`; koin-модуль.
- **ui**: `XViewModel : BaseScreenModel` (для ошибок → снек), use case в `domain/`, `XScreen : Screen` (Voyager).

## 5. Обязательное

- Репозиторий возвращает `KtorResponse<T>` (не кидает исключения).
- ViewModel наследует `BaseScreenModel`, на `Failure` → `emitError(error)` (см. [ERRORS.md](./ERRORS.md)).
- Логи — `AppLogger.d/i/w/e(tag, msg)`.
- Тесты use case — фейк-репозиторий + `runTest` (см. `example/ui/commonTest`).
