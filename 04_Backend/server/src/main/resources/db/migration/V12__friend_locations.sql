-- V12 — гео-шеринг для друзей (C10/L5 «Только люди»).
-- В отличие от location_pings (анонимные агрегаты пульса), здесь ИМЕННАЯ позиция,
-- видимая ТОЛЬКО подтверждённым друзьям (friendships.accepted) и только свежая (≤ 15 мин).
-- Пишется только при включённом тумблере Sharing (I2); выключение — DELETE строки.

CREATE TABLE IF NOT EXISTS friend_locations (
    user_id       VARCHAR(64) PRIMARY KEY,
    lat           DOUBLE PRECISION NOT NULL,
    lng           DOUBLE PRECISION NOT NULL,
    updated_at_ms BIGINT      NOT NULL DEFAULT 0
);
