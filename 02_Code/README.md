# EventMap — 02_Code

Kotlin Multiplatform + Compose Multiplatform приложение «живой карты событий». Основано на ядре CoreMultiplatform (сеть, хранилище, тема, DI, базовые экраны), поверх которого собраны фичи EventMap.

Таргеты: **Android, iOS, Desktop (JVM), Web (Wasm/JS)**. Пакет: `com.eventmap.*`. Навигация — Voyager, DI — Koin, сеть — Ktor.

> Статус: каркас приложения собран по макетам (разделы A–P). UI и навигация рабочие, данные пока на демо-фолбэках (см. [Что замокано](#что-замокано)). Карта рендерится только с Mapbox-токеном.

---

## Структура

```
composeApp/                точка входа всех платформ: App(), MainScreen (app shell), PlatformSDK (DI)
common/
  core/                    ядро (из CoreMultiplatform)
    network/               ktor-клиент, KtorResponse, safeRequest, CoreConfig, GlobalErrorBus
    datastore/             TokenStorage (сессии)
    ui/                    тема Aurora Violet, ui-kit, BaseScreenModel
    utils/ platform/ files/ biometry/ logger/ connectivity/
  feature/
    auth/                  A — онбординг и авторизация (A1–A8)
    map/                   C — живая карта (EventMap/overlay + чрома, фильтры, sheet)
    event/                 D + P — карточка, создание события, билеты, рейтинги
    feed/                  F — лента, уведомления, подписки, запросы
    search/                G — поиск (события/организаторы/люди/жанры)
    profile/               H + I — профиль, редактирование, настройки
    video/                 E — лента «Прямо сейчас», запись
    example/               эталонная фича из ядра (можно удалить)
iosApp/                    обёртка под Xcode
```

Каждая фича = три Gradle-модуля: `domain` (интерфейс репозитория + модели), `data` (ktor-датасорс, реализация, koin-модуль), `ui` (экраны, ViewModel, koin-модуль). Подробно — [FEATURE.md](./FEATURE.md).

---

## Реализованные разделы (по макетам 03_UI_UX)

| Раздел | Экраны | Где |
|---|---|---|
| **A** Онбординг/авторизация | A1 Splash, A2–A4 Welcome, A5 Регистрация, A6 Логин, A7 Сброс пароля, A8 Интересы | `feature/auth` |
| **C** Карта | C1/C2 карта, C4/C5 bottom-sheet события, C8 фильтры | `feature/map` |
| **D** Событие | D1–D3 табы (О событии/Галерея/Участники), D6–D8 создание | `feature/event` |
| **E** Видео/LIVE | E1/E2 вертикальная лента, E3 запись | `feature/video` |
| **F** Подписки | F1 подписки (табы), F2 лента, F3 запросы, F4 уведомления | `feature/feed` |
| **G** Поиск | G2 поиск по типам | `feature/search` |
| **H** Профиль | H1 профиль, H3 редактирование | `feature/profile` |
| **I** Настройки | уведомления, приватность, аккаунт | `feature/profile` |
| **P** Платежи/рейтинги | покупка билета, оценка (ADR-002) | `feature/event` |

Навигация: `Splash → (logged in ? MainScreen : Welcome)`. `MainScreen` — нижняя навигация (Карта · Лента · ＋Создать · Уведомл. · Профиль). Из карты: поиск, фильтры, «Прямо сейчас» (видео), тап события → карточка; создание → визард.

---

## Дизайн-система (Aurora Violet)

В `core/ui/theme`: палитра day/night, семантические токены (`live/soft/warning`), карта-цвета, типографика, spacing, радиусы — через `EventMapTheme` + `LocalSpacing`/`LocalEventMapColors`. ui-kit: `PrimaryGradientButton`, `SecondaryButton`, `StatusPill`, `clickableNoRipple`.

Шрифт по макету — **Manrope**; сейчас как fallback подключён bundled **Inter**. Чтобы переключить: положить `Manrope-*.ttf` в `core/ui/src/commonMain/composeResources/font/` и поправить `appFontFamily()` в `Type.kt`.

---

## Что замокано

Все репозитории ходят на реальные эндпоинты (`05_Docs/API_CONTRACT.md`), но при недоступном бэкенде ViewModel отдают **демо-данные** (sample-фолбэк), чтобы UI был виден без сервера. Чтобы перейти на боевые данные:

1. **Бэкенд** — задать `baseUrl` и пути в `CoreConfig` (дефолт в `composeApp/.../PlatformSdk.kt`: `https://api.eventmap.app/v1/`). Auth-флоу (login/register/refresh) уже под JSON-контракт EventMap.
2. **Mapbox** — карта (`feature/map`) рендерится только с токеном. Подключение — `feature/map/ui/MAPBOX_SETUP.md` (Android `MAPBOX_DOWNLOADS_TOKEN` + `MAPBOX_ACCESS_TOKEN`, iOS cocoapods + `MBXAccessToken`). На Desktop/Web сейчас placeholder.
3. **Списки из API** — `participants`/`media`/`feed`/`subscriptions` десериализуются как голый массив. Если бэкенд вернёт envelope `{ "items": [...], "nextCursor": ... }` — добавить wrapper-DTO в соответствующем `data/ktor`.
4. **Камера (E3)** и **плеер видео (E2)** — заглушки; подключаются платформенно (CameraX / AVFoundation, HLS-плеер).

---

## Запуск

```shell
# Android
./gradlew :composeApp:assembleDebug      # или :composeApp:installDebug

# Desktop
./gradlew :composeApp:run

# Web (Wasm) — статус экспериментальный, см. WASM.md
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

iOS — открыть `iosApp/` в Xcode.

Линтер: `./gradlew detekt` (конфиг `config/detekt/detekt.yml`).

> Каркас фич писался итеративно; перед наращиванием рекомендуется первый полный `assembleDebug` для отлова возможных ошибок компиляции/версий Compose-API.

---

## Что дальше

- Подключить Mapbox-токен и боевой бэкенд вместо фолбэков.
- D2/D5 — полноэкранная галерея и добавление медиа; E5 — архив видео в профиле.
- Платёжный провайдер (cloudpayments/epay) для P.
- F5/I3 — настройки уведомлений per-channel на бэке.
