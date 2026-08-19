# Хостинг фото и видео · 01.07.2026

> Дополнение (та же дата): воспроизведение тоже готово — кроссплатформенный `VideoPlayer`
> (`core/ui/views/video`): Android — media3/ExoPlayer, iOS — AVPlayer+Looper, desktop/wasm — постер.
> Играет в ленте «Сейчас» (VideoFeedScreen) и в рилсе события (EventReelScreen, ролик из /events/{id}/media).
> Бэк: `mediaUrl` добавлен в VideoDto, `/media/files` отдаёт HTTP Range (перемотка).
> Съёмка камерой тоже готова (та же дата, вторая итерация): `CameraRecorder`
> (`core/ui/views/camera`) — Android CameraX (Preview+VideoCapture, HD, звук при разрешении),
> iOS AVCaptureSession (.mov, фронт/тыл, фонарик), desktop/wasm — демо. RecordVideoScreen пишет
> реальный файл, превью играет в VideoPlayer, публикация — publishReel (presign→PUT→confirm→POST /videos).
> Разрешения: RECORD_AUDIO в манифесте + камера/микрофон в Info.plist. Лимит записи 60 с.

Один контракт для клиента в обоих режимах (мобилка не меняется):

```
POST /media/presign {kind, contentType}  →  {mediaId, uploadUrl, publicUrl}
PUT  uploadUrl  (байты, без bearer)
POST /media/confirm {mediaId, url, ...}  →  метаданные сохранены
```

Режим выбирается на бэке автоматически (di/AppModule): заданы `S3_*` env → R2/S3, не заданы → фолбэк в Postgres.

## Режим 1 · Фолбэк в Postgres (работает из коробки, V10)

Для теста/демо на free-Render, ничего настраивать не нужно.

- `uploadUrl` = `{PUBLIC_BASE_URL}/media/upload/{key}?exp&sig` — подпись HMAC-SHA256(ADMIN_KEY), TTL 15 мин.
- `publicUrl` = `{PUBLIC_BASE_URL}/media/files/{key}` — публичная отдача с `Cache-Control: immutable`.
- Байты лежат в таблице `media_blobs` (BYTEA). Лимиты: image ≤ 8 МБ, video ≤ 64 МБ (совпадают с клиентскими).
- `PUBLIC_BASE_URL` не задаётся вручную на Render: берётся `RENDER_EXTERNAL_URL`. Локально — `http://localhost:8080`.

Ограничения (потому и «демо»): free-Postgres всего 1 ГБ (~15 роликов по 60 МБ) и живёт 30 дней; файл проходит через JVM (буфер в памяти — на 512 МБ инстансе заливать по одному большому видео); отдача файлов грузит Ktor, CDN нет.

## Режим 2 · Cloudflare R2 (прод)

R2 выбран из-за нулевой платы за исходящий трафик (важно для видео). 10 ГБ хранилища бесплатно.

1. Cloudflare Dashboard → R2 → Create bucket (например `eventmap-media`).
2. R2 → Manage API Tokens → Create API Token (Object Read & Write, только этот бакет) → получаешь Access Key ID / Secret Access Key.
3. Бакету включить публичный доступ: R2 → Settings → Public access (r2.dev-домен) или привязать свой домен — это значение для `S3_PUBLIC_BASE`.
4. В Render → сервис eventmap → Environment добавить:

```
S3_ENDPOINT   = https://<account_id>.r2.cloudflarestorage.com
S3_REGION     = auto
S3_BUCKET     = eventmap-media
S3_ACCESS_KEY = <Access Key ID>
S3_SECRET_KEY = <Secret Access Key>
S3_PUBLIC_BASE = https://pub-<hash>.r2.dev   # или свой домен
```

5. Redeploy. Presign начнёт выдавать ссылки прямо в R2 — файлы летят мимо сервера, `media_blobs` больше не пополняется.

Миграция старых файлов из фолбэка в R2 при переключении не автоматическая: старые `publicUrl` (…/media/files/…) продолжают работать, пока жива БД. Для демо это ок.

## Что где в коде

- `data/storage/StorageService.kt` — SigV4 presign для R2/S3 (чистый JDK).
- `data/storage/DbStorageService.kt` — фолбэк: подписанные ссылки + `MediaBlobRepositoryImpl`.
- `presentation/routes/MediaRoutes.kt` — presign/confirm + `PUT /media/upload/{key}` и `GET /media/files/{key}` (фолбэк).
- Миграция `V10__media_blobs.sql`.
- Выбор режима — `di/AppModule.kt` (`StoragePort`).

## TODO прод-качества

- Транскод/сжатие видео (сейчас клиент льёт исходник ≤ 60 МБ), постеры-«thumb» генерирует клиент.
- Чистка осиротевших blob'ов (загружено, но confirm не пришёл).
- Rate-limit на /media/presign.
- Потоковое чтение PUT c обрывом сверх лимита (сейчас буфер в памяти; chunked без Content-Length проверяется после чтения).
- `allowMethod(Put)` в CORS — только если появится веб-клиент с загрузкой.

Сделано по ревью: whitelist content-type на PUT + `X-Content-Type-Options: nosniff`, upsert вместо update→insert, constant-time сравнение подписи, fail-fast в prod без PUBLIC_BASE_URL/S3.
