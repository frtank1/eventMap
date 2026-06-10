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

| Метод | Путь | Описание |
|---|---|---|
| POST | `/media/upload-url` | выдать presigned R2 URL (фото/видео) |
| POST | `/videos` | зарегистрировать видео после загрузки (привязка к событию) |
| GET | `/videos/now?bbox` | лента «Прямо сейчас» (E1) |
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

## Открытые вопросы (решить в Спринте 0)
1. Транскод/раздача LIVE: HLS на R2 своими силами или внешний провайдер (Mux/Cloudflare Stream)?
2. Расчёт `status` события (soon→live→ended): cron/триггер или вычисление на лету?
3. Гео-приватность Sharing: TTL точки, кому видно (только подписчики?).
4. Rate limit и анти-спам на `/reports`, `/media/upload-url`.

---

*PM-док. Версионируется. Связан с `ARCHITECTURE.md`, `ROADMAP.md`, `03_UI_UX/DEV_SPEC.md`. Обновлено 31.05.2026.*
