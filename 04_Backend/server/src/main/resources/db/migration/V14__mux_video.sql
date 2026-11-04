-- Existing media remains playable; only new Mux media uses async processing.
ALTER TABLE media ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'storage';
ALTER TABLE media ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ready';
ALTER TABLE media ADD COLUMN playback_id VARCHAR(255);
ALTER TABLE videos ADD COLUMN media_id VARCHAR(64);
CREATE UNIQUE INDEX idx_videos_media ON videos(media_id);

CREATE TABLE mux_uploads (
    media_id VARCHAR(64) PRIMARY KEY REFERENCES media(id),
    upload_id VARCHAR(255) UNIQUE,
    asset_id VARCHAR(255) UNIQUE,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(64),
    created_at_ms BIGINT NOT NULL
);
CREATE TABLE mux_webhook_events (
    id VARCHAR(255) PRIMARY KEY,
    received_at_ms BIGINT NOT NULL
);
