# EventMap — Технологический стек

Финальное решение по стеку. Все компоненты подобраны под Compose Multiplatform с целью максимально переиспользовать код Android↔iOS↔Desktop.

---

## 1. Принципы выбора

1. **Один язык — Kotlin** на клиенте и сервере (KMP shared + Ktor backend).
2. **Open-source где возможно**, чтобы не платить лицензии на старте.
3. **Production-ready** библиотеки с активным сообществом — не bleeding edge.
4. **Возможность кастомизации** — особенно для карты с живыми анимациями.

---

## 2. Клиент — Compose Multiplatform

| Задача | Технология | Где работает | Обоснование |
|---|---|---|---|
| UI-фреймворк | Compose Multiplatform 1.7+ | Android, iOS, Desktop | Единая UI-логика; JetBrains stable |
| Состояние / архитектура | Compose State + ViewModel (`androidx.lifecycle.viewmodel.compose`) | shared | MVI поверх StateFlow |
| DI | **Koin 3.6+** | shared | Лёгкий, идиоматичный, KMP-friendly |
| Сеть | **Ktor Client 3.x** | shared | Тот же стек что и на бэке, kotlinx-serialization |
| JSON | **kotlinx.serialization** | shared | Стандарт KMP |
| Локальная БД | **SQLDelight 2.x** | shared | Кеш событий, профилей, подписок |
| Key-value хранилище | **Multiplatform Settings** (encrypted) | shared | JWT-токены, флаги |
| Изображения | **Coil 3.x** | shared | Multiplatform, замена Kamel/Picasso |
| Карта | **Mapbox SDK** (через expect/actual) | shared wrapper | См. раздел 3 — детально |
| Видео-плеер | **Media3 ExoPlayer** (Android) + **AVPlayer** (iOS) | через expect/actual | Стандарт каждой платформы |
| Камера / запись видео | **CameraX** (Android) + **AVFoundation** (iOS) | через expect/actual | Стандарт |
| Push | **Firebase Cloud Messaging** (Android) + **APNS** (iOS) | через expect/actual | Бесплатно |
| Permissions | **moko-permissions** | shared | KMP, единый API для геолокации, камеры, микрофона, уведомлений |
| Анимации карты | **Compose Canvas + animateFloat** | shared | Полный контроль над пульсациями, ауры, эффектами |
| Жесты карты | **Compose Modifier.pointerInput** + MapLibre gestures | mixed | Pinch zoom, drag — MapLibre встроено |
| Аналитика | **PostHog** или **Amplitude** | shared (через REST) | Бесплатный tier на старт |
| Crash reporting | **Sentry** | через expect/actual SDK | Бесплатный tier |

---

## 3. Карта — Mapbox SDK (зафиксировано)

EventMap — это не просто карта с пинами, это **живая сцена с анимациями поверх**. Главное требование: рисовать поверх карты что угодно (стадион, йога, скидка с дымом).

**Mapbox SDK выбран по 5 причинам:**

1. **Лучшая в классе кастомизация** — Mapbox Studio даёт визуальный редактор стилей, можно делать темы (день/ночь/Наурыз/НГ) и экспортировать одним JSON.
2. **Vector-based** — переключение тем мгновенное, без перерисовки тайлов.
3. **Нативный рендеринг 60fps** на обеих платформах через Metal (iOS) и Vulkan/GL (Android).
4. **Самая большая экосистема среди карт** — много готовых стилей, готовый Compose API (`mapbox-maps-android-compose`), хорошие доки и Stack Overflow.
5. **Бесплатный free tier до 50k MAU** — на старте и первые ~6 мес после релиза мы внутри лимита.

### Цены и контроль расходов

| Метрика | Free tier | Цена после |
|---|---|---|
| Maps SDK MAU | 50 000 / мес | $0.0006 за MAU |
| Style requests | 50 000 / мес | в составе MAU |
| Vector tile downloads | 100 000 / мес | $0.0003 за 1k |
| Navigation API | 1 000 / мес | если понадобится — отдельно |

При 100k MAU ≈ $30/мес — приемлемо. При 1M MAU ≈ $300/мес — пора думать о переходе на MapLibre если упрёмся в бюджет.

**Контроль:** ставим лимит в Mapbox дашборде и алерт.

### План B

Если упрёмся в бюджет или политику Mapbox, переходим на **MapLibre Native** (open-source fork оригинального Mapbox GL). Архитектура `карта + Compose overlay` это позволяет — анимации не переделываем.

**Архитектурный паттерн «карта + Compose overlay»:**

```
┌─────────────────────────────────┐
│   Compose Box                   │
│  ┌───────────────────────────┐  │
│  │ Mapbox MapView            │  │  ← рендерит улицы, парки, здания
│  │  via UIViewController     │  │
│  │  / AndroidView            │  │
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │ Compose Canvas Layer      │  │  ← рендерит ВСЁ живое: стадион,
│  │  (на каждом frame:        │  │     йогу, пульсации, кружки видео,
│  │   проекция LatLng → XY    │  │     аватары людей
│  │   и отрисовка)            │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

MapView даёт API: `fun project(latLng: LatLng): Offset` — это пере-вычисляется при каждом изменении viewport (zoom/pan). Compose-overlay подписан на это и перерисовывает анимированные объекты в нужных координатах.

### Compose Multiplatform интеграция

В shared/commonMain:

```kotlin
expect class EventMapView {
    fun project(lat: Double, lon: Double): Offset
    fun setStyle(json: String)
    val viewportState: StateFlow<ViewportState>
}

@Composable
expect fun PlatformMap(
    state: EventMapState,
    modifier: Modifier
)
```

В androidMain:

```kotlin
actual class EventMapView(private val mapView: org.maplibre.android.maps.MapView) {
    actual fun project(lat: Double, lon: Double): Offset { ... }
}

@Composable
actual fun PlatformMap(state, modifier) {
    AndroidView(factory = { ctx -> MapView(ctx).apply { ... } })
}
```

В iosMain — аналогично через `UIKitView` и `MLNMapView`.

### Альтернативы (отвергнутые)

| Вариант | Плюс | Минус |
|---|---|---|
| **MapLibre Native** | $0 forever, open-source | Меньше доков, нет визуального редактора стилей |
| **Google Maps + MapKit** | Самые «дружелюбные» к разработчику API | Разные API на платформах, ограниченная кастомизация |
| **OSM через WebView** | 100% универсально | Тормозит, нет нативных жестов |

**Решение:** Mapbox SDK. План B: MapLibre Native если упрёмся в бюджет.

### Доступы и секреты

- **Public token** (`pk.*`) — кладётся в Android `R.string.mapbox_access_token` и iOS `Info.plist` ключ `MBXAccessToken`. Это публичный токен, ничего страшного что попадёт в бинарь.
- **Secret token** (`sk.*`) — только для скачивания SDK во время build (Gradle property `MAPBOX_DOWNLOADS_TOKEN`). Никогда не коммитим в репозиторий, кладём в `~/.gradle/gradle.properties` или в GitHub Secret.

### Стили (темы карты)

В Mapbox Studio (студия дизайнера, бесплатно) создаём стили:
- `eventmap-day-v1` — светлый базовый
- `eventmap-night-v1` — тёмный базовый
- `eventmap-nauryz-v1` — праздничный с орнаментом
- `eventmap-newyear-v1` — снежный

Каждый получает URL вида `mapbox://styles/your-account/abc123`. Переключение в коде: `mapView.style = newStyle`.

---

## 4. Backend — Ktor

| Задача | Технология | Обоснование |
|---|---|---|
| Сервер | **Ktor 3.x** | Тот же Kotlin, переиспользование DTO с клиентом |
| База | **PostgreSQL 16 + PostGIS 3.4** | Гео-запросы «события в радиусе» — нативно |
| ORM | **Exposed** или **jOOQ** | Exposed проще, jOOQ — если будем сложно запрашивать |
| Миграции | **Flyway** | Стандарт |
| Object storage (фото) | **Cloudflare R2** | S3-API, **бесплатные egress** — фото и обложки |
| Видео + LIVE | **Cloudflare Stream** (план Б — Mux) | Ingest+транскод+ABR+запись+плеер из коробки; см. ADR-001 |
| Auth | JWT (access 15 мин, refresh 30 дн), хранение в Keystore/Keychain |
| OAuth | Google Sign-In, Apple Sign-In | Обязательны для сторов |
| Push | **FCM** (Android) + **APNS** (iOS) | Через `kotlin-firebase-admin` |
| Search | PostgreSQL full-text сначала, потом **MeiliSearch** | Простота → масштаб |
| Очереди / фоновые задачи | **kotlinx.coroutines** + БД-таблица tasks | Redis опционально (rate limit/счётчики) |
| Rate limit / счётчики | **Redis** (опц. на MVP, иначе Postgres) | Лимиты на upload/reports/auth, см. ADR-001 |
| WebSocket | Ktor Sessions + WebSocket | Для live-обновлений ленты |

---

## 5. Инфраструктура

| Задача | Технология | Стоимость в месяц |
|---|---|---|
| Hosting backend | **Fly.io** (для старта) → **Hetzner Cloud** (для масштаба) | $5–30 |
| База PostgreSQL | Fly Postgres / Neon Free / Supabase | $0–25 |
| Object storage | **Cloudflare R2** (egress = 0) | $0.015/GB stored |
| Видео/LIVE | **Cloudflare Stream** | доставка $0.001/мин · запись $0.005/мин · ingest $0 (~$55/мес на бете) |
| CDN | **Cloudflare** (free) | $0 |
| DNS | Cloudflare | $0 |
| Monitoring | **Sentry** + **Uptime Kuma** | $0 |
| CI/CD | **GitHub Actions** | $0 для public, $4/mo для private |

**Старт:** ~$10/мес. **При 10k MAU:** ~$50/мес. **При 100k MAU:** ~$300/мес.

---

## 6. Сторонние сервисы (что точно подключаем)

1. **Google Sign-In SDK** — Android + iOS (бесплатно)
2. **Apple Sign-In** — обязателен для App Store (бесплатно)
3. **Firebase Cloud Messaging** — push на Android (бесплатно)
4. **APNS** — push на iOS (бесплатно)
5. **Cloudflare R2** — хранение фото и обложек
6. **Cloudflare Stream** — видео ≤30 сек и LIVE-эфиры (транскод/раздача); см. ADR-001
7. **Sentry** — crash reporting
8. **PostHog** или **Amplitude** — продуктовая аналитика

---

## 7. Что НЕ берём на старте (и почему)

| Не берём | Почему |
|---|---|
| Firebase в целом (Firestore, Auth, Storage) | Привязка к гугл-стеку, vendor lock-in. Дороже на масштабе. FCM — берём, остальное — нет |
| Supabase / Backendless | Хочется свой Ktor backend для гибкости |
| Realm / Room для KMP | SQLDelight — стандарт сообщества, проще |
| GraphQL | REST + WebSocket покрывают всё. GraphQL — оверкилл для одного разработчика |
| Mapbox | Платный, MapLibre справится |
| Google Maps | Платный, нет гибкости стилей под темы |
| React Native / Flutter | Compose Multiplatform — твоя сильная сторона |
| Kubernetes | Один разраб → docker-compose на VPS, всё |

---

## 8. Финальная архитектурная картина

```
┌──────────────────────────────────────────────────────────────┐
│                  Compose Multiplatform Apps                  │
│   Android ─┐         iOS ─┐         Desktop ─┐               │
│            └──────────────┴─────────────────┘                │
│                          │                                   │
│                  ┌───────▼───────┐                           │
│                  │  shared (KMP) │                           │
│                  │  Compose UI   │                           │
│                  │  Koin DI      │                           │
│                  │  Ktor Client  │                           │
│                  │  SQLDelight   │                           │
│                  │  expect MapView, Camera, Video, Push      │
│                  └───────┬───────┘                           │
└──────────────────────────┼───────────────────────────────────┘
                           │ HTTPS + WebSocket
            ┌──────────────▼───────────────┐
            │   Ktor backend (Kotlin)      │
            │   ┌──────────────────────┐   │
            │   │ Exposed ORM          │   │
            │   │ JWT auth             │   │
            │   │ kotlinx-coroutines   │   │
            │   └──────────────────────┘   │
            └──────────────┬───────────────┘
   ┌─────────┬─────────────┼─────────────┬─────────────┐
   │         │             │             │             │
┌──▼──┐ ┌────▼────┐ ┌──────▼─────┐ ┌─────▼─────┐ ┌────▼────┐
│PG + │ │ R2 +    │ │ Mapbox     │ │ FCM /APNS │ │ Sentry  │
│PostGIS│ │ Stream │ │            │ │           │ │         │
│PostGIS│ │ video  │ │ tiles +    │ │ push      │ │ crash   │
└─────┘ └─────────┘ │ styles     │ └───────────┘ └─────────┘
                    │ (план B:   │
                    │ MapLibre)  │
                    └────────────┘
```
