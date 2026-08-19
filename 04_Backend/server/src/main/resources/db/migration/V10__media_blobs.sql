-- V10 — фолбэк-хранилище медиа в Postgres (когда S3_*/R2 не настроены).
-- Файлы льются через PUT /media/upload/{key} (подпись HMAC) и отдаются GET /media/files/{key}.
-- Прод-режим — R2 (S3_* env), тогда эта таблица не используется. См. 05_Docs/STORAGE_SETUP.md.

CREATE TABLE IF NOT EXISTS media_blobs (
    key           VARCHAR(255) PRIMARY KEY,     -- "<kind>/<userId>/<mediaId>.<ext>"
    content_type  VARCHAR(128) NOT NULL,
    bytes         BYTEA        NOT NULL,
    size          INT          NOT NULL DEFAULT 0,
    created_at_ms BIGINT       NOT NULL DEFAULT 0
);
