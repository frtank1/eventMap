# EventMap server (Ktor, Clean Architecture)

Бэкенд по слоям, PostgreSQL + Exposed, DI на Koin, JWT-аутентификация. Запускается локально через Docker; готов к деплою (Dockerfile).

## Слои (зависимости только внутрь)

```
presentation/   HTTP: routes + DTO (+мапперы domain→dto)   ──┐
        │ зависит от                                          │
domain/         model + repository (порты) + usecase          │  чистый, без фреймворков
        ▲ реализуется в                                       │
data/           Exposed-таблицы, DatabaseFactory, repo-impl  ──┘
security/       JwtService          di/  Koin-модуль          config/  env-конфиг
```

Правило: `domain` ничего не знает о Ktor/Exposed. `data` и `presentation` реализуют/используют порты из `domain`. Подмена БД или HTTP-слоя не трогает бизнес-логику.

## Запуск (локально, через Docker)

```bash
cd 04_Backend/server
docker compose up --build
```

Поднимутся Postgres + сервер на `http://localhost:8080`. При первом старте создаются таблицы и заливается сид по Астане.

Проверка:
```bash
curl http://localhost:8080/events
curl -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"email":"a@a.kz","password":"123456","name":"Азамат"}'
```

### Без Docker (если Postgres уже стоит локально)
Создай БД `eventmap` (user/pass `eventmap`/`eventmap`) или переопредели env (`DB_URL`, `DB_USER`, `DB_PASSWORD`), затем:
```bash
./gradlew run
```

## Эндпоинты

- `POST /auth/register` · `/auth/login` → `AuthResponse` (JWT access+refresh). Пароли хешируются bcrypt.
- `GET /events` · `/events/{id}` · участники/медиа/рейтинги · attend/rating/purchase.
- `GET /organizers/{id}` · `/organizers/{id}/events` · subscribe.

## Подключить приложение
В `02_Code/.../PlatformSdk.kt` → `CoreConfig.baseUrl = "http://10.0.2.2:8080/"` (Android-эмулятор) и убрать dev-байпас в `AuthRepositoryImpl`.

## Деплой (позже)
`Dockerfile` собирает fat-образ. На Railway/Render/Fly: подключить репозиторий, задать env (`DB_URL`, `JWT_SECRET`), привязать managed Postgres. Локальная структура и Docker уже готовы к этому.

## Миграции V2–V5 (ревизия 11.06.2026)

Добавлены под новые домены (контракт: `05_Docs/API_CONTRACT.md` §8):

| Файл | Что |
|---|---|
| `V2__business_tickets.sql` | events: +is_adult/+visibility/+geo_radius_m · users: +birth_date · бизнес-аккаунты (БИН, модерация, пин), наборы анимаций по категориям · билеты с гостями (owner NULL = анонимный «+N») |
| `V3__quests_gamification.sql` | квесты (точки, условия geo/photo/purchase, прохождения, чек-ины) · XP/значки/миссии · районы (GeoJSON, туман войны) · сезонный пасс · капсулы времени |
| `V4__social_friends_invitations.sql` | attendance · комментарии рилса + лайки · напоминания (колокольчик) · друзья (user_a<user_b) + группы · приглашения с link_token и RSVP |
| `V5__map_pulse.sql` | пульс карты: эфемерные пинги (UNLOGGED, user_hash, TTL 15 мин кроном), агрегаты glow, аномалии G9 (k≥30) |

Конвенции V1 сохранены: `VARCHAR(64)` id, lat/lng double (PostGIS — позже), новые таблицы используют `TIMESTAMPTZ`.
TODO при имплементации: krон чистки `location_pings`, джоба пересчёта `pulse_aggregates` (30 сек), HMAC-ключи QR-чеков квестов.

## Реализовано 11.06.2026: рилс-соцслой + честное присутствие

Вертикальный срез по Clean-слоям (D9–D11 + D1c, API §3/§8.6):

| Эндпоинт | Логика |
|---|---|
| `GET /events/{id}/comments` | комментарии + ответы + likedByMe (гостям тоже, optional-JWT) |
| `POST /events/{id}/comments` | добавить (валидация: непустой, ≤2000) |
| `POST /comments/{id}/like` | тоггл ♡, атомарный like_count |
| `POST /events/{id}/remind` | колокольчик вкл/выкл (source=bell; ticket — для авто после оплаты) |
| `POST /events/{id}/attend` | **CTA-логика D1c**: `going` — только до старта и только free (платное → 422 PAID_ONLY); `here` — только live + Haversine ≤ geo_radius_m (иначе 422 TOO_FAR с дистанцией); `cancel` |

Файлы: `Tables.kt` (+4 таблицы, +3 колонки events), `Models.kt`, `Repositories.kt`, `SocialRepositoryImpl.kt`, `SocialUseCases.kt`, `Dto.kt`, `SocialRoutes.kt`, DI + Application.

### Билеты с гостями (P1–P2, API §8.2) — реализовано 11.06.2026

| Эндпоинт | Логика |
|---|---|
| `POST /events/{id}/tickets/purchase` | `{guests:[{userId}\|{}], provider}` · только платные (free → 422 FREE_EVENT) · макс 10 · MVP: provider=mock → сразу paid · **авто-«иду» + авто-колокольчик** покупателю и гостям с аккаунтом · анонимные — только в счётчике |
| `GET /tickets/my` | мои билеты (+ название события, JOIN) |
| `GET /tickets/{id}/qr` | QR-payload, только владельцу (403 чужим) |
| `POST /tickets/{id}/checkin` | скан на входе: только организатор события, одноразово (active→used) |

Счётчик `goingCount` пересчитывается по правилу §8.2 (attendance + анонимные active-билеты) и кэшируется в `events.going_count`.
TODO: реальный эквайринг (Kaspi/карта) через вебхук — сейчас mock.

### Бизнес-аккаунты (M1–M6, API §8.1) — реализовано 11.06.2026

| Эндпоинт | Логика |
|---|---|
| `POST /business/apply` | заявка: БИН строго 12 цифр, 1 аккаунт/юзер (422 ALREADY_EXISTS), документы; статус pending. TODO: автопроверка БИН в egov |
| `GET /business/me` | моя заявка + статус/причина отказа (M6) |
| `GET /business/animations` | набор анимаций **только своей категории** — категория берётся из аккаунта, параметром не подделать (M4) |
| `PATCH /business/pin` | лого/цвет (#RRGGBB)/анимация; **анимация чужой категории → 422 WRONG_CATEGORY** |
| `POST /admin/business/{id}/review` | модерация: approved/rejected+причина · гард `X-Admin-Key` (env ADMIN_KEY, в prod обязателен) |
| `POST /admin/animation-sets` | админ грузит анимацию в набор категории (Lottie-ключ R2) |

### Промо-квесты (Q1–Q6, API §8.3) — реализовано 11.06.2026

| Эндпоинт | Логика |
|---|---|
| `GET /quests/nearby?bbox` | активные квесты с точкой в bbox + мой прогресс (optional-JWT) |
| `GET /quests/{id}` | карточка: точки (✓ по мне), цепочка, финишёры, мой prize_qr |
| `POST /quests` | создаёт ТОЛЬКО approved-бизнес (422 NO_BUSINESS/NOT_APPROVED) · 1–10 точек · condition geo/photo/purchase · статус moderation |
| `POST /quests/{id}/points/{pid}/checkin` | proof по типу точки: **geo** — Haversine ≤300 м · **photo** — mediaUrl + гео ≤300 м · **purchase** — QR с чека = HMAC-SHA256(pointId, ADMIN_KEY) · цепочка: CHAIN_LOCKED, повтор: ALREADY_CHECKED · XP: точка +15, финиш +100 → prize_qr |
| `GET /quests/{id}/points/{pid}/receipt-code` | код для кассы — только владельцу квеста (печатает у себя) |
| `POST /admin/quests/{id}/review` | модерация (X-Admin-Key) → active/rejected |

XP пишется в `xp_events` (фундамент R-раздела). `ADMIN_KEY` добавлен в AppConfig (prod-обязателен).

### Друзья и приглашения (N1–N5, API §8.7) — реализовано 11.06.2026

| Эндпоинт | Логика |
|---|---|
| `GET /friends` · `POST /friends/request` · `POST /friends/respond` | взаимная дружба: заявка → ответ ТОЛЬКО адресатом; статусы accepted/pending_in/pending_out; каноничная пара user_a<user_b |
| `POST /invitations` | создаёт организатор события (403 чужим), один на событие; allowLink → link_token |
| `GET /invitations/{id}` | открытка гостю — только если в списке; БЕЗ списка остальных гостей |
| `POST /invitations/{id}/rsvp` | yes/no + plusOne (0..5) |
| `GET /i/{token}` · `POST /i/{token}/rsvp` | **публичная веб-открытка без аккаунта** — имя обязательно (канал роста) |
| `GET /events/{id}/rsvp` | дашборд N5 — только организатору: полный список + счётчики придут (с +1)/не смогут/молчат |

Приватность: `events.visibility='private'` исключается из `GET /events` (фильтр в EventRepository.search).
TODO: PATCH события на private при создании приглашения, пуши гостям, напоминание «молчунам».

### Геймификация (R1–R3, API §8.4) — реализовано 11.06.2026

| Эндпоинт | Логика |
|---|---|
| `GET /game/profile` | XP (сумма xp_events), уровень+титул по порогам (Новичок→…→Хозяин города), значки, % районов |
| `GET /game/missions` | активные миссии (генерация миссий — TODO джоба) |
| `GET /map/fog` | туман войны G3: id открытых районов |
| `GET /districts/leaderboard` | топ-10 районов по визитам (G10, публичный) |
| `POST /capsules` | капсула G15: получатель user (только друг!) / self_1y (откроется через год) / public; пустая → 422 |
| `GET /capsules/nearby?bbox` | пины капсул; содержимое скрыто до открытия (кроме своих) |
| `POST /capsules/{id}/open` | только на месте (Haversine ≤100 м), по праву получателя, self_1y — не раньше срока |

### Пульс карты (G1/G2/G9, API §8.5) — реализовано 11.06.2026

| Эндпоинт | Логика |
|---|---|
| `POST /map/ping` | пинг локации (клиент шлёт ТОЛЬКО при opt-in «аналитика», I2); user_hash = HMAC(userId, секрет+день) — соль ежедневная |
| `GET /map/pulse?bbox` | публичные агрегаты: на событие glow 0..1 (G1) + crowd за 10 мин (G2) + активные аномалии G9 |

Фоновая джоба (каждые 30 сек, стартует в Application): TTL-чистка пингов (15 мин) → glow по live/soon событиям → грид-кластеризация (~300 м): ячейка с ≥30 уникальных хэшей БЕЗ события рядом → аномалия «здесь что-то происходит»; остыла (<10) → закрывается.
TODO: WS-пуши `{op:"pulse"}`/`{op:"anomaly"}`, фильтр жилых зон для G9.

---

**Контракт §8 закрыт полностью: 7 доменов, 40 эндпоинтов, миграции V2–V5.**

⚠️ Компиляция не запускалась (в окружении нет сети для Gradle) — прогнать локально: `./gradlew compileKotlin`.
