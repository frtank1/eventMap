# EventMap — Архитектура (Compose Multiplatform)

## Высокоуровневая схема

```
┌─────────────────────────────────────────────────────────┐
│              Compose Multiplatform Apps                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │ Android  │  │   iOS    │  │ Desktop  │  │  Web    │ │
│  │   App    │  │   App    │  │   App    │  │ (later) │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬────┘ │
│       └─────────────┴─────────────┴─────────────┘      │
│                          │                              │
│                ┌─────────▼──────────┐                   │
│                │   shared (KMP)     │                   │
│                │  ┌──────────────┐  │                   │
│                │  │ UI (Compose) │  │                   │
│                │  │ ViewModels   │  │                   │
│                │  │ UseCases     │  │                   │
│                │  │ Repositories │  │                   │
│                │  │ DI (Koin)    │  │                   │
│                │  └──────────────┘  │                   │
│                └─────────┬──────────┘                   │
└──────────────────────────┼──────────────────────────────┘
                           │ Ktor Client (HTTPS)
              ┌────────────▼────────────┐
              │   Backend (Ktor)        │
              │   ┌──────────────────┐  │
              │   │ REST + WebSocket │  │
              │   └──────────────────┘  │
              └────────────┬────────────┘
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────▼────┐       ┌─────▼────┐      ┌─────▼─────┐
   │Postgres │       │  S3      │      │ FCM/APNS  │
   │+PostGIS │       │ storage  │      │   Push    │
   └─────────┘       └──────────┘      └───────────┘
```

## Модули

### `shared/` — Kotlin Multiplatform общий код

**commonMain** — единый код для всех платформ:
- `ui/` — Compose UI (экраны, компоненты, темы)
- `presentation/` — ViewModel'и (StateFlow + MVI)
- `domain/` — UseCase'ы и модели (чистые data class)
- `data/` — Repository, DTO, mappers, Ktor client
- `di/` — Koin-модули

**androidMain / iosMain / desktopMain** — платформенные expect/actual:
- Карта (Android: Google Maps Compose, iOS: MapKit через UIKitView)
- Permissions (location, camera, mic)
- Camera/Video recording
- Биометрия для логина

### `androidApp/` — точка входа Android
Минимальный модуль с `MainActivity`, который запускает `App()` из shared.

### `iosApp/` — точка входа iOS
Xcode-проект, который импортирует `shared.xcframework` и запускает `MainViewController()`.

### `desktopApp/` — точка входа Desktop (JVM)
`main.kt` с `application { Window { App() } }`.

## Архитектурный паттерн: Clean Architecture + MVI

```
View (Compose) ── State ──► ViewModel ──► UseCase ──► Repository ──► API/DB
       ▲                       │
       └────── Intent ─────────┘
```

- **State** — иммутабельный data class.
- **Intent** — пользовательское действие (sealed class).
- **UseCase** — одна операция бизнес-логики (`GetEventsNearbyUseCase`, `CreateEventUseCase`).
- **Repository** — единая точка работы с данными (Network + Local cache).

## Локальное хранилище

- **SQLDelight** — реляционка для кешей событий, профилей, подписок.
- **Settings (Multiplatform Settings)** — токены, флаги.

## Сеть

- **Ktor Client** + kotlinx-serialization для JSON.
- WebSocket для live-обновлений ленты «Прямо сейчас».
- Retry + offline-queue для создания событий без сети.

## Backend

- **Ktor (Kotlin)** — единый язык с фронтом, легче переиспользовать модели.
- **Exposed** или **jOOQ** — ORM.
- **PostgreSQL 16 + PostGIS** — гео-запросы.
- **Cloudflare R2** — object storage для фото и обложек.
- **Cloudflare Stream** — видео ≤30 сек и LIVE (транскод/раздача/запись); план Б — Mux. См. `ADR-001_video_live.md`.
- **Redis** (опц. на MVP) — rate limit и счётчики.
- **Firebase Cloud Messaging + APNS** — push.

## Карты

- **Зафиксировано: Mapbox SDK** через `expect/actual` (Android: `mapbox-maps-android-compose`, iOS: MapView через UIKitView). Единый вид на всех платформах, визуальный редактор стилей в Mapbox Studio, free tier до 50k MAU.
- Паттерн «карта + Compose overlay»: Mapbox рендерит улицы, Compose Canvas рисует живые объекты поверх (см. TECH_STACK.md, раздел 3).
- **План B:** MapLibre Native, если упрёмся в бюджет/политику Mapbox. Архитектура overlay позволяет переход без переписывания анимаций.

## Безопасность

- JWT access-токен (15 мин) + refresh-токен (30 дней).
- HTTPS обязательно.
- Хранение токенов: Keystore (Android) / Keychain (iOS) через Multiplatform Settings encrypted.

## CI/CD

- GitHub Actions:
  - `lint` + `unit-tests` на каждый PR.
  - Сборка Android APK по тегу `android-v*`.
  - Сборка iOS через macOS-раннера по тегу `ios-v*`.
  - Авто-деплой backend в Fly.io / Hetzner по `main`.
