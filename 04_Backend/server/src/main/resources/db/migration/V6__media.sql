-- V6 — медиа-хранилище (R2/S3). Запись о загруженном файле; ссылка ведёт в object storage.
-- Загрузка идёт напрямую клиентом по presigned-URL (см. StorageService), сервер хранит метаданные.

CREATE TABLE IF NOT EXISTS media (
    id             VARCHAR(64) PRIMARY KEY,
    owner_user_id  VARCHAR(64)  NOT NULL,
    url            VARCHAR(1024) NOT NULL,
    content_type   VARCHAR(128) NOT NULL DEFAULT '',
    kind           VARCHAR(16)  NOT NULL DEFAULT 'image',  -- image | video
    event_id       VARCHAR(64),                            -- привязка к событию (nullable)
    created_at_ms  BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_media_event ON media(event_id);
CREATE INDEX IF NOT EXISTS idx_media_owner ON media(owner_user_id);
