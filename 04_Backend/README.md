# 04_Backend — Ktor server

## Стек

- **Ktor** (Kotlin) — единый язык с фронтом, переиспользование DTO
- **PostgreSQL 16 + PostGIS** — гео-запросы по событиям и людям
- **Exposed** — ORM
- **MinIO / Cloudflare R2** — object storage для фото и видео
- **Firebase Cloud Messaging + APNS** — push-уведомления

## Папки

- **api/** — HTTP-роуты, контроллеры, DTO
- **database/** — схемы, миграции (Flyway), сиды
- **auth/** — JWT, OAuth (Google, Apple), refresh-токены
- **storage/** — обёртка над S3-compatible хранилищем
- **push_notifications/** — FCM + APNS интеграция

## Ключевые таблицы (предварительно)

- `users` — id, email, name, avatar_url, role
- `organizers` — id, user_id, name, logo, verified
- `events` — id, organizer_id, title, description, lat, lon, starts_at, ends_at, category_id
- `event_categories` — id, name, parent_id (для жанров)
- `subscriptions` — id, user_id, target_type (organizer/category/user), target_id
- `live_videos` — id, user_id, event_id (nullable), video_url, lat, lon, created_at
- `user_status` — user_id, lat, lon, is_active, updated_at (для «человек как событие»)
