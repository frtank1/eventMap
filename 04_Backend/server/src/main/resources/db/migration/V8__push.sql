-- V8 — push-уведомления. Токены устройств пользователя (FCM/APNS через FCM).
-- Один пользователь — много устройств. token уникален (PRIMARY KEY).

CREATE TABLE IF NOT EXISTS device_tokens (
    token         VARCHAR(512) PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    platform      VARCHAR(16)  NOT NULL DEFAULT 'android', -- android | ios | web
    created_at_ms BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens(user_id);
