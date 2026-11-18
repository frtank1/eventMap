# EventMap — API-контракт + схема БД

v0.1 · черновик под MVP. Backend: Ktor + PostgreSQL 16 + PostGIS, Exposed ORM, JWT, Cloudflare R2. Дедлайн ревью: **04.06.2026**.

Base URL: `https://api.eventmap.app/v1`. Формат: JSON (kotlinx.serialization). Auth: `Authorization: Bearer <accessToken>`.

---

## 1. Конвенции

- Время — ISO-8601 UTC (`2026-05-31T16:00:00Z`).
- ID — UUID (string).
- Координаты — `{ "lat": 43.238, "lng": 76.945 }`.
- Пагинация — cursor: `?limit=20&cursor=<opaque>`, ответ `{ "items": [...], "nextCursor": "..." }`.
- Ошибки — единый конверт:

```json
{ "error": { "code": "EVENT_NOT_FOUND", "message": "Событие не найдено" } }
```
Коды HTTP: 200/201, 400 (валидация), 401 (нет/протух токен), 403 (нет прав), 404, 409 (конфликт), 422, 429 (rate limit), 500/503.

---

## 2. Auth (раздел A)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/auth/register` | email+пароль / oauth(google,apple) |
| POST | `/auth/login` | вход |
| POST | `/auth/refresh` | обновить accessToken по refreshToken |
| POST | `/auth/logout` | отозвать refreshToken |
| POST | `/auth/password/forgot` | запрос кода на email (A7) |
| POST | `/auth/password/reset` | сброс по коду |
| POST | `/auth/interests` | сохранить выбранные жанры (A8) |

```http
POST /auth/login → 200
{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900, "user": { ...UserShort } }
```

---

## 3. События (разделы C, D)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/events?bbox=swLat,swLng,neLat,neLng&from&to&categories&price&radius` | события в видимой области карты (PostGIS) |
| GET | `/events/{id}` | карточка события (D1) |
| GET | `/events/{id}/participants?type=going\|was` | участники (D3) |
| GET | `/events/{id}/media?cursor` | галерея (D2) |
| POST | `/events/{id}/media` | добавить медиа (D5), body = `mediaId` после upload |
| POST | `/events/{id}/attend` | «пойду» / отмена |
| POST | `/events` | создать событие (D6–D8) |
| PATCH | `/events/{id}` | редактировать |
| POST | `/events/{id}/purchase` | купить билет (платный ивент) → paymentUrl/SDK · ADR-002 |
| GET | `/events/{id}/ticket` | мой билет / статус оплаты |
| POST | `/payments/webhook/{provider}` | вебхук эквайринга (cloudpayments/epay) |
| POST | `/events/{id}/rating` | оценка 1–5 + отзыв (только `was`) · ADR-002 |
| GET | `/events/{id}/ratings` | агрегаты + отзывы |

```http
GET /events?bbox=43.20,76.88,43.28,77.00&from=now → 200
{ "items": [ {
  "id":"...", "title":"Стендап-вечер", "category":"comedy",
  "status":"soon",                 // soon | live | ended
  "startAt":"2026-05-31T16:00:00Z", "endAt":"...",
  "location": { "lat":43.238,"lng":76.945, "name":"Бар «Подвал»" },
  "price": { "type":"free" },      // free | paid(amount,currency)
  "goingCount":48, "liveViewers":0,
  "importance":72,                 // 0..100 — вес пина на карте (ADR-002)
  "ratingAvg":4.6, "ratingCount":128,
  "myTicketStatus":null,           // null | pending | paid (для платных)
  "cover":"https://cdn.../c.jpg"
} ], "nextCursor": null }
```

Создание (валидация по 3 шагам макета):
```http
POST /events
{ "title":"...", "description":"...", "coverMediaId":"...",      // шаг 1
  "location":{"lat":..,"lng":..,"name":".."},                    // шаг 2
  "startAt":"...","endAt":"...","category":"music",
  "price":{"type":"paid","amount":2000,"currency":"KZT"},"limit":200 } // шаг 3
```

---

## 4. Видео и LIVE (раздел E, C5/C6)

Реализованный upload/VOD-контракт Mux (2026-11-05): [MUX_VIDEO.md](MUX_VIDEO.md).
Фото и прежние файлы остаются в storage. LIVE/WebSocket и архив ниже — целевой контракт, не гарантия реализации.

| Метод | Путь | Описание |
|---|---|---|
| POST | `/media/presign` | JWT; image → storage, video → Mux при VIDEO_PROVIDER=mux; eventId, sizeBytes обязательны для Mux |
| POST | `/media/confirm` | JWT; подтверждение передачи файла, статус uploading/processing/ready/errored |
| GET | `/media/{id}` | состояние и временные ссылки; проверка доступа к событию |
| GET | `/events/{id}/media` | медиа события со статусами; закрытое событие требует доступа |
| POST | `/videos` | JWT; mediaId Mux; идемпотентная публикация, 202 при обработке, 201 при готовности |
| GET | `/videos/now` | готовые ролики публичных событий; bbox пока не применяется |
| POST | `/webhooks/mux` | HMAC mux-signature; обработка/ошибка/удаление, дедупликация в БД |
| GET | `/events/{id}/live` | метаданные LIVE-эфира (C5) |
| GET | `/users/me/videos?cursor` | архив (E5) |
| POST | `/videos/{id}/react` | лайк/реакция |

**LIVE realtime** — WebSocket `wss://api.eventmap.app/v1/ws`:
```json
// клиент → сервер: подписка на видимую область
{ "op":"sub", "bbox":[43.20,76.88,43.28,77.00] }
// сервер → клиент: события на карте
{ "op":"event_live",  "eventId":"...", "viewers":230 }
{ "op":"event_soon",  "eventId":"..." }
{ "op":"video_new",   "eventId":"...", "videoId":"...", "thumb":"..." }
{ "op":"event_ended", "eventId":"..." }
```
*Транскод/раздача стрима — решение в Спринте 0 (HLS на R2 vs внешний LIVE-провайдер).*

---

## 5. Профиль, подписки, поиск (H, F, G)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/users/me` · `/users/{id}` | профиль (H1/H2) |
| PATCH | `/users/me` | редактирование (H3) |
| GET | `/users/{id}/history` | история посещений (H4/H5, фильтр по приватности) |
| GET | `/users/{id}/recommendations` | рекомендации (H6) |
| PATCH | `/users/me/privacy` | приватность (H7/I2) |
| PATCH | `/users/me/sharing` | Sharing «сейчас здесь» on/off + радиус (H11) |
| POST | `/users/{id}/subscribe` · DELETE | подписка/отписка |
| GET | `/subscriptions?tab=organizers\|genres\|people` | мои подписки (F1) |
| GET | `/feed?cursor` | лента подписок (F2) |
| GET | `/subscription-requests` · POST `/{id}/approve\|reject` | запросы (F3) |
| GET | `/notifications?cursor` | уведомления (F4) |
| PATCH | `/notifications/settings` | per-channel (F5/I3) |
| GET | `/search?q&type=events\|organizers\|people\|genres` | поиск (G2) |
| GET | `/genres/{id}/events` | карточка жанра (G3) |

---

## 6. Модерация, аккаунт (I, J)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/reports` | жалоба `{targetType, targetId, reason, comment}` (J1) |
| GET | `/reports/mine` | статусы жалоб (J2) |
| GET | `/users/me/restrictions` | бан/ограничения (J3) |
| POST | `/account/password` | смена пароля (I4) |
| GET | `/account/sessions` · DELETE | активные сессии |
| POST | `/account/data-export` | выгрузка данных (I2) |
| DELETE | `/account` | удаление |
| POST | `/devices` | регистрация push-токена (FCM/APNS) |

---

## 7. Схема БД (PostgreSQL 16 + PostGIS)

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         CITEXT UNIQUE,
  password_hash TEXT,                       -- null для oauth-only
  oauth_provider TEXT, oauth_sub TEXT,
  username      CITEXT UNIQUE NOT NULL,
  display_name  TEXT NOT NULL,
  avatar_url    TEXT,
  bio           TEXT,
  is_organizer  BOOLEAN DEFAULT FALSE,
  is_verified   BOOLEAN DEFAULT FALSE,
  -- приватность
  profile_public      BOOLEAN DEFAULT TRUE,
  history_visibility  TEXT DEFAULT 'friends',   -- public|friends|private
  gallery_visibility  TEXT DEFAULT 'subscribers',
  sharing_enabled     BOOLEAN DEFAULT FALSE,
  sharing_point       GEOGRAPHY(POINT,4326),    -- «сейчас здесь»
  sharing_updated_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE interests ( -- M2M жанров пользователя (A8)
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  genre   TEXT,
  PRIMARY KEY (user_id, genre)
);

CREATE TABLE events (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organizer_id UUID REFERENCES users(id),
  title        TEXT NOT NULL,
  description  TEXT,
  cover_url    TEXT,
  category     TEXT NOT NULL,
  geom         GEOGRAPHY(POINT,4326) NOT NULL,
  place_name   TEXT,
  start_at     TIMESTAMPTZ NOT NULL,
  end_at       TIMESTAMPTZ,
  price_type   TEXT DEFAULT 'free',     -- free|paid
  price_amount INT, price_currency TEXT,
  capacity     INT,
  status       TEXT DEFAULT 'soon',     -- soon|live|ended (вычисляется/обновляется)
  created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX events_geom_gix ON events USING GIST (geom);          -- гео-запрос по bbox/радиусу
CREATE INDEX events_time_idx ON events (start_at, end_at);
CREATE INDEX events_status_idx ON events (status);

CREATE TABLE attendance (
  user_id  UUID REFERENCES users(id) ON DELETE CASCADE,
  event_id UUID REFERENCES events(id) ON DELETE CASCADE,
  type     TEXT NOT NULL,               -- going|was
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, event_id)
);

CREATE TABLE media (                     -- фото/видео в R2
  id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id  UUID REFERENCES users(id),
  event_id  UUID REFERENCES events(id) ON DELETE SET NULL,
  kind      TEXT NOT NULL,               -- photo|video
  r2_key    TEXT NOT NULL,
  thumb_url TEXT,
  duration_s INT,                        -- для видео (<=30)
  is_live   BOOLEAN DEFAULT FALSE,
  caption   TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX media_event_idx ON media (event_id, created_at DESC);

CREATE TABLE subscriptions (
  follower_id UUID REFERENCES users(id) ON DELETE CASCADE,
  target_type TEXT NOT NULL,            -- user|organizer|genre
  target_id   TEXT NOT NULL,
  status      TEXT DEFAULT 'active',    -- active|pending (приватные профили, F3)
  created_at  TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (follower_id, target_type, target_id)
);

CREATE TABLE notifications (
  id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
  type      TEXT NOT NULL,              -- live|soon|nearby|new_follower|reaction
  payload   JSONB,
  read_at   TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX notif_user_idx ON notifications (user_id, created_at DESC);

CREATE TABLE reports (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id UUID REFERENCES users(id),
  target_type TEXT NOT NULL,            -- event|video|user|comment
  target_id   UUID NOT NULL,
  reason      TEXT NOT NULL,
  comment     TEXT,
  status      TEXT DEFAULT 'review',    -- review|accepted|rejected
  created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE restrictions (             -- бан/ограничения (J3)
  id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
  scope     TEXT NOT NULL,              -- create_events|upload_video|...
  until     TIMESTAMPTZ,
  reason    TEXT
);

CREATE TABLE devices (                  -- push
  id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
  platform  TEXT NOT NULL,              -- ios|android
  push_token TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

### Ключевой гео-запрос (события в области)
```sql
SELECT * FROM events
WHERE geom && ST_MakeEnvelope(:swLng,:swLat,:neLng,:neLat,4326)::geography
  AND end_at > now()
  AND (:category IS NULL OR category = :category)
ORDER BY start_at;
```

---

## 8. Новые домены (ревизия 11.06.2026) — v0.2

### 8.1 Бизнес-аккаунты (раздел M) · ✅ реализовано 11.06.2026 (без egov-проверки БИН и stats; админ — X-Admin-Key)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/business/apply` | заявка: БИН, бренд, категория, контакты, documentIds (M2–M5) |
| GET | `/business/me` | моя заявка/аккаунт + статус модерации (M6) |
| GET | `/business/animations?category=food` | набор анимаций пина ТОЛЬКО своей категории (M4) |
| PATCH | `/business/pin` | `{logoMediaId, color, animationId}` — animationId валидируется по категории |
| GET | `/business/stats` | просмотры пина, переходы, участники событий |
| POST | `/admin/animation-sets` | (админ) загрузить набор анимаций для категории |
| POST | `/admin/business/{id}/review` | (админ) `{decision: approved\|rejected, reason?}` → пуш+email |

```sql
CREATE TABLE business_accounts (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  bin          TEXT NOT NULL,              -- БИН/ИИН, проверка в реестре (egov API)
  legal_name   TEXT NOT NULL,              -- автоподстановка по БИН
  brand_name   TEXT NOT NULL,              -- имя на карте
  category     TEXT NOT NULL,              -- определяет набор анимаций
  phone TEXT, social TEXT,
  status       TEXT DEFAULT 'pending',     -- pending|approved|rejected
  reject_reason TEXT,
  logo_media_id UUID REFERENCES media(id),
  pin_color    TEXT DEFAULT '#FF5C8A',
  animation_id UUID,                       -- FK pin_animations, только своя категория (CHECK триггером)
  created_at   TIMESTAMPTZ DEFAULT now(), reviewed_at TIMESTAMPTZ
);
CREATE TABLE business_documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID REFERENCES business_accounts(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,                      -- registration|license|other
  media_id UUID REFERENCES media(id)
);
CREATE TABLE pin_animations (              -- наборы грузит админ
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category  TEXT NOT NULL,
  name      TEXT NOT NULL,                 -- «Пар над чашкой»
  lottie_key TEXT NOT NULL,                -- R2-ключ Lottie/Rive
  is_active BOOLEAN DEFAULT TRUE
);
CREATE INDEX pin_anim_cat_idx ON pin_animations (category) WHERE is_active;
```

### 8.2 Билеты и покупка друзьям (раздел P) · ✅ реализовано на сервере 11.06.2026 (mock-провайдер, без transfer)

Правила: платное событие → «Приду» недоступна, только покупка. Оплата прошла → покупатель и гости-с-аккаунтом автоматически в `attendance(going)`; гости без аккаунта НЕ в списке, но в счётчике: `goingCount = COUNT(attendance going) + COUNT(билеты без owner)`.

| Метод | Путь | Описание |
|---|---|---|
| POST | `/events/{id}/tickets/purchase` | `{qty, guests:[{userId}\|{}], provider}` → `paymentUrl` (P1) |
| GET | `/tickets/my?cursor` | мои билеты |
| GET | `/tickets/{id}/qr` | QR-payload билета (P2) |
| POST | `/tickets/{id}/checkin` | скан организатором на входе |
| POST | `/tickets/{id}/transfer` | передать билет аккаунту (анонимный → именной) |

```sql
CREATE TABLE ticket_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID REFERENCES events(id), buyer_id UUID REFERENCES users(id),
  qty INT NOT NULL CHECK (qty BETWEEN 1 AND 10),
  amount INT NOT NULL, currency TEXT DEFAULT 'KZT',
  provider TEXT NOT NULL,                  -- kaspi|card|applepay
  status TEXT DEFAULT 'pending',           -- pending|paid|failed|refunded
  created_at TIMESTAMPTZ DEFAULT now(), paid_at TIMESTAMPTZ
);
CREATE TABLE tickets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID REFERENCES ticket_orders(id) ON DELETE CASCADE,
  event_id UUID REFERENCES events(id),
  owner_user_id UUID REFERENCES users(id), -- NULL = анонимный гость («+N»)
  qr_secret TEXT UNIQUE NOT NULL,          -- подписанный payload
  status TEXT DEFAULT 'active',            -- active|used|refunded
  used_at TIMESTAMPTZ
);
CREATE INDEX tickets_event_idx ON tickets (event_id, status);
```

### 8.3 Промо-квесты (раздел Q) · ✅ реализовано 11.06.2026 (+receipt-code для касс; stats Q6 — TODO)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/quests/nearby?bbox` | каталог + точки для карты (Q1) |
| GET | `/quests/{id}` | карточка: точки, мой прогресс, награда (Q2) |
| POST | `/quests` | (бизнес) создать → модерация (Q5) |
| POST | `/quests/{id}/join` | начать прохождение |
| POST | `/quests/{id}/points/{pid}/checkin` | `{proof}` (Q3), см. ниже |
| GET | `/quests/{id}/stats` | (владелец) воронка, конверсия (Q6) |

Proof по типу условия точки: `geo` → `{lat,lng}`, сервер проверяет ≤300 м (та же механика, что «Я тоже здесь»); `photo` → `{mediaId}` с обязательной гео-меткой в радиусе; `purchase` → `{qrPayload}` — подписанный код с чека/кассы (HMAC, одноразовый). Точки `ordered=true` открываются по цепочке. Финиш последней точки → выдача `prize_qr` + значок + XP (Q4).

```sql
CREATE TABLE quests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID REFERENCES business_accounts(id),
  title TEXT NOT NULL, emoji TEXT,
  ordered BOOLEAN DEFAULT FALSE,           -- по цепочке
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ,
  prize_title TEXT NOT NULL, prize_count INT,
  participant_limit INT,
  status TEXT DEFAULT 'moderation',        -- moderation|active|ended|rejected
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE quest_points (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quest_id UUID REFERENCES quests(id) ON DELETE CASCADE,
  ord INT NOT NULL,
  geom GEOGRAPHY(POINT,4326) NOT NULL, place_name TEXT,
  condition TEXT NOT NULL                  -- geo|photo|purchase
);
CREATE TABLE quest_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quest_id UUID REFERENCES quests(id), user_id UUID REFERENCES users(id),
  started_at TIMESTAMPTZ DEFAULT now(), finished_at TIMESTAMPTZ,
  prize_qr TEXT,                           -- выдаётся на финише
  UNIQUE (quest_id, user_id)
);
CREATE TABLE quest_checkins (
  run_id UUID REFERENCES quest_runs(id) ON DELETE CASCADE,
  point_id UUID REFERENCES quest_points(id),
  proof_type TEXT NOT NULL, media_id UUID REFERENCES media(id),
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (run_id, point_id)
);
```

### 8.4 Геймификация (раздел R) · ✅ реализовано 11.06.2026 (wrapped и генерация миссий — TODO джобы; пасс — P2)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/game/profile` | уровень, XP, титул, значки, % исследования города (R1/H1) |
| GET | `/game/missions` | активные миссии (+совместные G14) |
| GET | `/game/pass` | сезонный пасс: прогресс, слоты лута (G13) |
| GET | `/districts/leaderboard?week` | районы недели (G10) |
| POST | `/capsules` | `{point, mediaId?, text, recipient: user(id)\|self_1y\|public}` (G15/R3) |
| GET | `/capsules/nearby?bbox` | мои доступные капсулы-пины |
| POST | `/capsules/{id}/open` | открыть — сервер валидирует гео ≤100 м |
| GET | `/game/wrapped/{year}` | агрегаты «Твой год в городе» (G12/R2) |

XP-начисления (константы сервера): «я тоже здесь» +10 · квест-точка +15 · финиш квеста +100 · новый район +50 · миссия — по полю reward. Уровень = таблица порогов.

```sql
CREATE TABLE xp_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  source TEXT NOT NULL,                    -- checkin|quest_point|quest_finish|district|mission
  source_id UUID, amount INT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX xp_user_idx ON xp_events (user_id, created_at);
CREATE TABLE badges ( id UUID PRIMARY KEY, code TEXT UNIQUE, title TEXT, emoji TEXT );
CREATE TABLE user_badges (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  badge_id UUID REFERENCES badges(id),
  source TEXT, granted_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, badge_id)
);
CREATE TABLE missions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,                      -- friend_event|new_district|new_genre|...
  payload JSONB,                           -- {friendId,...}
  progress INT DEFAULT 0, target INT NOT NULL,
  reward_xp INT, multiplier REAL DEFAULT 1,-- 2.0 для совместных
  status TEXT DEFAULT 'active',            -- active|done|expired
  expires_at TIMESTAMPTZ
);
CREATE TABLE districts ( id UUID PRIMARY KEY, city TEXT, name TEXT, geom GEOGRAPHY(POLYGON,4326) );
CREATE TABLE district_visits (             -- туман войны G3
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  district_id UUID REFERENCES districts(id),
  first_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, district_id)
);
CREATE TABLE season_passes (
  id UUID PRIMARY KEY, title TEXT, starts_at DATE, ends_at DATE,
  rewards JSONB                            -- [{ord, businessId, prize, questId}]
);
CREATE TABLE user_pass_progress (
  user_id UUID, pass_id UUID REFERENCES season_passes(id),
  completed INT DEFAULT 0, claimed JSONB DEFAULT '[]',
  PRIMARY KEY (user_id, pass_id)
);
CREATE TABLE time_capsules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID REFERENCES users(id),
  geom GEOGRAPHY(POINT,4326) NOT NULL,
  media_id UUID REFERENCES media(id), text TEXT,
  recipient_type TEXT NOT NULL,            -- user|self_1y|public
  recipient_id UUID, opens_at TIMESTAMPTZ, -- self_1y: now()+1y
  opened_at TIMESTAMPTZ, opened_by UUID
);
CREATE INDEX capsules_geom_gix ON time_capsules USING GIST (geom);
```

### 8.5 Живая карта — пульс (G1/G2/G8/G9) · ✅ реализовано 11.06.2026 (ping+pulse+джоба; forecast G8 и WS-пуши — TODO)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/map/pulse?bbox` | агрегаты: на событие `{glow 0..1, goingDelta10m, viewers}`; векторы потока (G2); аномалии (G9) |
| GET | `/map/forecast?bbox` | зоны «через час будет жарко» (G8): динамика «приду» + история |
| GET | `/map/fog` | мои исследованные районы + % (G3) |

WS-операции (добавка к §4): `{op:"pulse", events:[...]}` · `{op:"anomaly", geom, count, startedAt}` (G9) · `{op:"wave", eventId}` (G7).

**Приватность (жёстко):** только агрегаты с k-анонимностью ≥ 10; точки потока — сглаженные направления, не траектории; сырые пинги в `location_pings` эфемерны (TTL 15 мин, user_id хэширован, чистка кроном); opt-out — тумблер «аналитика» в I2. Аномалия G9 публикуется только при k ≥ 30 и вне жилых зон (фильтр по POI).

```sql
CREATE UNLOGGED TABLE location_pings (    -- эфемерно, TTL 15 мин
  user_hash TEXT NOT NULL,                -- HMAC(user_id, daily_salt)
  geom GEOGRAPHY(POINT,4326) NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX pings_geom_gix ON location_pings USING GIST (geom);
-- pulse_aggregates: materialized view, refresh каждые 30 c (glow, потоки, кластеры DBSCAN для G9)
```

### 8.6 Рилс-соцслой (D9–D11) и лента · ✅ comments/like/remind/attend реализованы 11.06.2026 (feed/reels — TODO)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/events/{id}/comments?cursor` | список + ответы (D9) |
| POST | `/events/{id}/comments` | `{text, parentId?}` |
| POST | `/comments/{id}/like` | ♡ |
| POST | `/events/{id}/remind` | `{enabled}` — колокольчик (D11); для платных вкл. автоматически после оплаты |
| GET | `/feed/reels?from={eventId}&filters` | лента свайпа: открытый → похожие (категория/жанр/близость) → сегодня (утро→вечер) → завтра…; при `filters` — по фильтру C8 |

```sql
CREATE TABLE event_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID REFERENCES events(id) ON DELETE CASCADE,
  author_id UUID REFERENCES users(id),
  parent_id UUID REFERENCES event_comments(id),
  text TEXT NOT NULL, like_count INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX comments_event_idx ON event_comments (event_id, created_at DESC);
CREATE TABLE comment_likes (
  comment_id UUID REFERENCES event_comments(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (comment_id, user_id)
);
CREATE TABLE reminders (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  event_id UUID REFERENCES events(id) ON DELETE CASCADE,
  source TEXT DEFAULT 'bell',              -- bell|ticket(авто)
  PRIMARY KEY (user_id, event_id)
);
```

### 8.7 Друзья и приглашения (раздел N) · ✅ реализовано 11.06.2026 (без групп и remind; private-фильтр в /events работает)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/friends/request` | заявка в друзья `{userId}` |
| POST | `/friends/{id}/respond` | `{accept: bool}` |
| GET | `/friends?status=accepted\|pending` | список + статусы присутствия (N1) |
| POST | `/friends/groups` | группа («семья», «универ») |
| POST | `/invitations` | создать: `{eventId, templateId, guests:[userId], allowLink}` (N2–N3) |
| GET | `/invitations/{id}` | открытка гостю (N4); публичная веб-версия по `link_token` |
| POST | `/invitations/{id}/rsvp` | `{answer: yes\|no, plusOne?}` |
| GET | `/events/{id}/rsvp` | дашборд организатора (N5) |
| POST | `/invitations/{id}/remind` | пуш «молчунам» |

Правила: `events.visibility = public|private` — private не попадает в `/events?bbox`, `/feed/reels`, поиск; пин виден только приглашённым (фильтр на сервере). Веб-открытка по `link_token` (подписанный, с TTL) — RSVP без аккаунта, имя вручную.

```sql
CREATE TABLE friendships (
  user_a UUID REFERENCES users(id) ON DELETE CASCADE,  -- user_a < user_b
  user_b UUID REFERENCES users(id) ON DELETE CASCADE,
  status TEXT DEFAULT 'pending',          -- pending|accepted
  requested_by UUID NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now(), accepted_at TIMESTAMPTZ,
  PRIMARY KEY (user_a, user_b)
);
CREATE TABLE friend_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL
);
CREATE TABLE friend_group_members (
  group_id UUID REFERENCES friend_groups(id) ON DELETE CASCADE,
  friend_id UUID REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (group_id, friend_id)
);
CREATE TABLE invitations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id UUID REFERENCES events(id) ON DELETE CASCADE,
  template_id TEXT NOT NULL,              -- анимированный шаблон открытки
  link_token TEXT UNIQUE,                 -- для гостей без аккаунта (NULL = только in-app)
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE invitation_guests (
  invitation_id UUID REFERENCES invitations(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id),      -- NULL = гость по ссылке
  guest_name TEXT,                        -- для гостей по ссылке
  rsvp TEXT,                              -- NULL|yes|no
  plus_one INT DEFAULT 0,
  responded_at TIMESTAMPTZ,
  PRIMARY KEY (invitation_id, COALESCE(user_id, gen_random_uuid()))
);
-- упрощение: PRIMARY KEY (invitation_id, user_id) + отдельный id для link-гостей при имплементации
```

Также: в `events` добавить `is_adult BOOLEAN DEFAULT FALSE` (18+, фильтр по дате рождения из профиля), `geo_radius_m INT DEFAULT 300` (радиус «Я тоже здесь»/гео-чекинов) и `visibility TEXT DEFAULT 'public'` (private = только по приглашению, §8.7); в `users` — `birth_date DATE` (для 18+).

---

## Открытые вопросы (решить в Спринте 0)
1. Транскод/раздача LIVE: HLS на R2 своими силами или внешний провайдер (Mux/Cloudflare Stream)?
2. Расчёт `status` события (soon→live→ended): cron/триггер или вычисление на лету?
3. Гео-приватность Sharing: TTL точки, кому видно (только подписчики?).
4. Rate limit и анти-спам на `/reports`, `/media/upload-url`.
5. (§8.5) Источник пингов для пульса: только пользователи приложения с opt-in или + анонимная телеметрия SDK? Частота пингов vs батарея.
6. (§8.2) Эквайринг для KZ: Kaspi Pay API — условия и анти-фрод возвратов билетов.
7. (§8.3) Анти-абьюз QR-чеков: HMAC-ключи на точку, ротация, лимит чек-инов/день.
8. (§8.4) Геометрия районов: откуда брать полигоны (OSM admin_level / свои)?

---

*PM-док. Версионируется. Связан с `ARCHITECTURE.md`, `ROADMAP.md`, `03_UI_UX/DEV_SPEC.md`, `MAP_CONCEPT.md` §10–11. Обновлено 11.06.2026 (v0.2: §8 — бизнес, билеты+друзья, квесты, геймификация, пульс карты, рилс-соцслой).*
# Event V2 (V13)

`POST /events` остаётся обратно совместимым и дополнительно принимает:

```json
{
  "kind": "planned|gathering|live_signal|private",
  "sourceType": "initiator|witness",
  "visibility": "public|unlisted|invite",
  "joinPolicy": "open|approval|invite",
  "audience": "everyone|family|adults",
  "minAge": 18,
  "expiresAtMs": 1780000000000,
  "proofMediaId": "media-id"
}
```

Ответ Event дополнительно содержит `capacity`, `confirmationCount` и
`interestCount`. Повышенные источники `business`, `organizer`, `government`
нельзя присвоить обычным JWT-запросом.

- `POST /events/{id}/signals/interest` — переключить «Мне интересно».
- `POST /events/{id}/signals/confirm` — переключить «Я тоже это вижу».
- `POST /events/{id}/join` — вступить или отправить заявку.
- `POST /events/{id}/join/review` — автор принимает/отклоняет заявку.

Живые сигналы очевидцев одинаковой категории, близкого заголовка, в радиусе
примерно 150–200 м объединяются в один Event и увеличивают подтверждения.

## Проверяемые уточнения контракта — 04.11.2026

Первый блок стабилизации новой концепции; это не отметка о готовности всего API.

- `POST /events` и `PATCH /events/{id}`: сервер проверяет координаты, название,
  категорию, ISO-8601 время с часовым поясом, порядок начала/окончания,
  перечисления правил доступа, цену, возраст и лимит участников. Ошибка —
  `400 BAD_REQUEST`; невалидные данные не сохраняются. Для набора людей лимит обязателен.
- Клиент переводит выбранное местное время в UTC, не подставляет случайную
  локацию и блокирует двойное нажатие публикации.
- Ошибка загрузки выбранной обложки останавливает публикацию. Если событие уже
  создано, но ролик не опубликован, клиент сохраняет ID и повторяет медиа-этап,
  а не создание события. Черновики разделены по аккаунтам; байты файлов после
  перезапуска пока нужно выбирать заново.
- Это **не серверная идемпотентность**: потеря ответа на первый POST или
  регистрацию ролика ещё требует отдельного idempotency-контракта.
- Ошибка покупки/участия/отзыва/сохранения профиля не считается успехом.
  Ошибки чтения не подменяются демонстрационными данными.
- Refresh `401/403` завершает сессию; `429`, `5xx`, сетевой сбой или некорректный
  ответ сохраняют её. Это не означает успех исходного запроса.
- Доступ к закрытым данным, атомарный лимит мест и настоящая обработка жалоб
  остаются отдельными блокерами выпуска до реализации и проверки.

Проверка первого блока (04.11.2026): Android debug с `eventmap.mapProvider=2gis`
собран; 40 клиентских unit-тестов на Desktop/JVM и 17 серверных тестов прошли.
Это не проверка на устройстве, не запуск с реальной БД и не приёмка Render/FCM/S3.
Ключ 2GIS по-прежнему требуется. Серверная идемпотентность и оставшиеся блокеры
выше не закрыты этим прогоном.

## Игровой город: публичный контент — 12.11.2026

Это отдельные публичные ресурсы в корне фактического `CoreConfig.baseUrl`, без `/v1`
и без JWT. Источник исполняемого контракта — `WorldRoutes.kt`, `WorldRoutesTest.kt`
и клиентский `game-city/runtime/content-store.js`.

| Метод / путь | Контракт |
|---|---|
| GET `/world/manifest.json` | 200 JSON, `Cache-Control: no-cache`, `schemaVersion:1`, `engineVersion:2`, `release`, `models` |
| GET `/world/assets/{sha256}.json` | 200 JSON, immutable на год, точная длина и SHA-256 из manifest |
| Неизвестный ресурс / произвольный путь | 404, без раздачи JS/конфигурации |

Manifest: 1–32 уникальные модели, сейчас 19. Запись содержит `id/title/lat/lng/height/
radius/file/sha256/bytes`; file строго `assets/<64-hex>.json`. Координаты ограничены
Астаной, файл ≤6 MiB, пакет ≤18 MiB. Модель schemaVersion=1: числовые материалы,
позиции/нормали/индексы мешей, boolean detail. Нет исполняемых сценариев, текстурных
URL или шейдерного кода. Полные ограничения — в документе игрового города.

CORS `*` только у публичного `/world`, credentials не нужны; политика закрытого API
не расширена. SHA-256 проверяет целостность, не является подписью издателя.
Ошибка сети/хеша/формата/совместимости сохраняет текущий или встроенный пакет.
Замена атомарная после проверки всего пакета; совместимый старый release допустим.
Движки v1/v2 отклоняют пакеты другой версии; обновление приложения до v2 требуется
один раз. Далее эти 19 моделей можно менять контентным выпуском без обновления APK.

WorldRoutesTest выполнен локально: 19 файлов, размеры/хеши, кэш/CORS и 404.
Это не подтверждает публикацию ресурса на Render: deploy в этом изменении не делался.

### Адреса, контуры замещения и входы — 16.11.2026

Совместимый **отдельный** канал данных, не JS и не смена картографического движка:

| Метод / путь | Контракт |
|---|---|
| GET `/world/places-manifest.json` | 200 JSON, no-cache; `schemaVersion:1`, `catalogVersion:1`, `release`, `addresses`, `places` |
| GET `/world/assets/{sha256}.json` | Существующий immutable-маршрут; адреса ≤12 MiB, места ≤1 MiB |

Оба дескриптора: `{file:"assets/<sha256>.json", sha256:<64 hex>, bytes:<integer>}`.
Пакет активируется только после проверки обоих файлов: размер, SHA-256, схема,
координаты в области Астаны, ограниченные строки/списки и отсутствие дублей.
Название release не заменяет проверку содержимого. Откат на ранее опубликованный
совместимый пакет допустим. HTTPS и текущий настроенный host; нет JWT, GPS и
запросов пользователей к публичному Overpass.

Адресный файл schemaVersion=1 сохраняет `records` из существующего OSM-экстракта.
Каталог мест schemaVersion=1 содержит `places`: стабильный ID модели, типизированные
`sourceRefs` (`way/…`, `relation/…`), контуры `footprints`, отмеченные `entrances`
и статус проверки `review`. Вход содержит OSM node ID, lat/lng, kind, access,
wheelchair. Это сведения источника, не подтверждение доступности двери сейчас.
Служебные/аварийные/закрытые входы не предлагаются как обычный вход. Отсутствие
входов отмечается явно; центр здания никогда не становится входом автоматически.
Сопоставление адреса требует явного исходного OSM ref, совпадающего реального
контура из каталога или адресного node внутри контура. Сходство bbox — только
поисковое ускорение, не подтверждение адреса; неподтверждённые записи не выводятся.
Мост place содержит `entranceStatus` (`mapped|not_mapped|unreviewed|unavailable`)
и не более восьми входов `{id,lat,lng,kind,wheelchair}`. Выбор входа явный;
без него сохраняются координаты нажатия. Для `needs_review` входы не предлагаются.

Клиент хранит последнюю проверенную пару в отдельном IndexedDB-кэше, сохраняет
встроенный резерв, не заменяет рабочие данные при 404/5xx/таймауте/битом пакете.
Проверка — при открытии карты, возвращении в приложение (не чаще 5 минут) и по
«Обновить карту и события». Новая логика требует одной установки приложения;
затем данные этого формата обновляются серверным выпуском без APK. Новые типы
рендеринга/схемы за пределами этой версии по-прежнему могут требовать обновления.

Публикация серверного пакета и проверка production URL — отдельный этап от
локальной сборки. Готовность фиксируется по фактически выполненным проверкам.

## Обложки событий в игровой карте — 13.11.2026

`EventDetail.cover` остаётся ID медиа, не URL. `GET /events/{id}/media` дополнительно
включает указанную обложку, даже если она загружена до создания события и имеет
`eventId=null`. Условия: `kind=image`, `status=ready`, владелец медиа совпадает с
организатором события, медиа не привязано к другому событию. Дубликаты исключаются.
Чужой ID не открывает чужую обложку. Проверка доступа к событию существующая:
публичное доступно гостю; закрытое — только разрешённому участнику/владельцу.
Эта поправка не меняет правила `/media/{id}` и не делает все unattached-файлы публичными.

Мобильный мост: JS → native `{v:1,type:"covers",ids:[eventId...]}` (≤8 уникальных ID
из текущей публичной выдачи); native → sync `covers:[{id,ref,data}]`, где `ref` —
актуальный cover ID, `data` — уменьшенный JPEG data URL. URL хранилища и JWT в WebView
не передаются. Соединение к хранилищу выполняет отдельный клиент без bearer.
Проверено тестом маршрута с H2 и клиентскими unit-тестами; не подтверждает deploy.
# Building / floor / room — 16 November 2026

`Event.venue` and POST/PATCH `/events` accept optional
`{"buildingId":"landmark:mega","floorId":"l1","roomId":"atrium"}`.
Only buildingId is required within venue. Room requires floor; floor/room must
belong to that building's current server catalog. Coordinates remain geographic
meeting coordinates, not coordinates in a floor drawing. No automatic attachment
by radius, address text or GPS. Legacy events remain venue=null.

- Building IDs: `landmark:<stable model id>` or `osm:way:<id>` /
  `osm:relation:<id>` from an identified footprint. View-local MVT IDs and address
  nodes are not building IDs. Unknown OSM buildings can have building-only events.
- `GET /events?buildingId=...` returns only public events with exactly that ID,
  independent of whether a plan exists. No private/unlisted event counts leak.
- `GET /venues/{buildingId}` is public read-only JSON, `Cache-Control: no-cache`:
  `{schemaVersion:1,buildingId,revision,name,floors:[{id,name,order,
  plan:{width,height,outline:[{x,y}]}|null,
  rooms:[{id,name,polygon:[{x,y}]}]}]}`.
  No floors or plan=null means no published drawing, not an HTTP failure.
  Valid uncatalogued OSM IDs return empty floors. Invalid IDs return 400;
  unknown landmark IDs return 404. A failed request is not presented as "no plan".
- Plans are bounded 2D geometry, never HTML, SVG scripts, remote code or arbitrary
  URLs. IDs remain stable across revisions; new compatible plans/floors/rooms need
  a server data release, not a new APK. A schema change needs a compatible reader.
  First installation of the reader is required. No surveyed MEGA plan is fabricated.
- PATCH accepts `clearVenue:true` to remove an association explicitly. Old clients
  omitting venue preserve it only if geographic coordinates remain unchanged;
  moving the event clears it. A supplied venue is validated for create and update.

## Админка внутренних планов — 16.11.2026

Планы хранятся в PostgreSQL; ресурсный каталог используется только как резерв для
зданий, которые ещё не редактировались. Публичный `GET /venues/{buildingId}` сохраняет
формат v1 и читает последнюю опубликованную версию из БД, без рестарта/deploy.
Черновик не доступен публично. Первая установка этой функции требует миграции V16.

Админка `/admin/venues`, авторизация всех операций — существующий `X-Admin-Key`.
Пустой ключ всегда запрещён. Ошибки имеют существующую оболочку `error.code/message`.

- `GET /admin/api/venues`: список известных зданий (ID, название, версия редактирования).
- `GET /admin/api/venues/{id}`: `{version,draft,published,history}`; для ещё не
  изменённого здания version=0, draft из ресурсного каталога/пустой OSM-записи.
- `PUT /admin/api/venues/{id}/draft`: `{expectedVersion,building}`; сохраняет целиком
  валидную схему и увеличивает version. buildingId должен совпасть с URL.
- `POST /admin/api/venues/{id}/publications`: `{expectedVersion}`; атомарно публикует
  сохранённый черновик, назначает новую revision, записывает неизменяемую историю и
  увеличивает version. Несохранённые изменения формы не публикуются.
- `GET /admin/api/venues/{id}/revisions/{revision}`: снимок опубликованной версии;
  его можно загрузить как черновик и повторно опубликовать после проверки.

Все записи используют оптимистическую блокировку: устаревшая expectedVersion → 409,
данные не перезаписываются. Повтор POST после потерянного ответа → 409, а не вторая
публикация. Нельзя удалять ранее опубликованные IDs этажей/помещений: это защищает
сохранённые события. Можно менять названия/контуры и убирать рисунок, сохранив IDs.
Ошибочная схема/ID/JSON → 400, неизвестное здание/ревизия → 404, нет доступа → 403,
слишком большой JSON → 413, получение тела дольше 15 секунд → 408.
Лимит тела 1 MiB, включая оболочку запроса.
История в карточке ограничена последними 20 публикациями; снимки не удаляются.
Каталог админки ограничен первыми 500 зданиями; любое известное OSM-здание можно
открыть по ID. Все ответы редактора no-store; публичный план no-cache.

# Event V3 (V17): каналы, модерация, вид на карте — 18.11.2026

Миграция `V17__event_channels_moderation.sql`. Все существующие события остаются
опубликованными и показываются точкой (`S`), старые клиенты ничего не замечают.

**Новые поля события** (в `EventDto` — все, кроме контакта):

| Поле | Значения |
|---|---|
| `presentation` | `S` точка с обложкой · `M` территория (нужен `area`) · `L` 3D-сцена (нужен `sceneTemplate`) |
| `area` | `{"polygon":[[lng,lat],…], "entrances":[{"lng","lat","name"}]}` — 3–200 точек, не больше ~5 км, точка события рядом |
| `recurrence` | `{"weekdays":[6,7], "until":"2026-12-31"}` — ISO-дни (1 = пн), дата по Астане, не дальше года; только `kind=planned` |
| `sceneTemplate`, `sceneParams` | `concert` · `fair` · `run` · `mall_sale`; параметры — JSON-объект до 8 КБ |
| `sourceUrl`, `sourceName` | ссылка http(s) на анонс; для `sourceType=editorial` обязательна |
| `sourceType` | добавлены `editorial` (подборка) и `guest` (предложение без аккаунта) |

Только на сервере и в админке: `moderationStatus` (`pending|published|rejected|expired`),
`reviewedBy/At`, `rejectReason`, `createdAtMs`, `contact` (контакт гостя), `externalId`
(для повторного импорта подборки без дублей).

**Правила:**

- Карта, поиск, лента, жанры и `GET /events/{id}` отдают только `published` + `public`.
- Аккаунт не может сам включить `L` (сцена по заявке). Сцена, выданная раньше, при редактировании сохраняется.
- `PATCH /events/{id}` без единого V17-поля (старое приложение) не трогает `area`, `recurrence`,
  `presentation` и источник. Клиент, отправивший хоть одно V17-поле, отправляет их все.
- Один валидатор (`EventValidation.kt`) для всех каналов: приложение, предложения, импорт, модерация.

## `POST /events/suggestions` — предложить событие без аккаунта

```json
{"title":"…","description":"…","category":"music","startAt":"2026-11-21T14:00:00Z","endAt":"",
 "location":{"lat":51.128,"lng":71.43,"name":"…"},"buildingId":"landmark:mega",
 "price":{"type":"free","amount":0},"sourceUrl":"https://…","contact":"@me","website":""}
```

- Ответ `202 {"status":"pending"}` — ничего из присланного не возвращается. Повторная отправка той же формы не создаёт копию.
- Категории — как у публичной карты: `sport, music, market, discovery, route, quest, community, newyear, kids, art, theatre, education`.
- Только Астана (50.9–51.4 с.ш., 71.1–71.9 в.д.), событие не прошло, начало — в ближайший год, длительность до месяца.
- Название до 120 символов, описание до 2000, тело запроса до 16 КБ (`413`).
- `website` — ловушка для ботов: если заполнено, ответ тот же `202`, но ничего не сохраняется.
- Лимит: 5 предложений в час с адреса (последний адрес из `X-Forwarded-For`, его добавляет прокси хостинга) и 300 в час на всех → `429` + `Retry-After: 3600`. Адреса хранятся только в памяти и только хешем.
- Очередь больше 500 ожидающих → `503 QUEUE_FULL`.
- Здание (`buildingId`) сохраняется, если оно есть в каталоге; этаж и помещение гость не задаёт.

## Очередь модерации (админка, `X-Admin-Key`)

- `GET /admin/api/events?status=pending|published|rejected|expired` — с контактом гостя; ожидающие — старые сверху.
- `POST /admin/events/{id}/review` `{"decision":"approved|rejected","reason":"…"}` → `200` событие;
  `409 NOT_PENDING`, если решение уже принято; `422`, если событие перестало проходить проверку (например, уже прошло).
- В консоли `/admin` раздел «Предложения событий».
