-- V5: живая карта — пульс (G1/G2/G8/G9, API 8.5)
-- ПРИВАТНОСТЬ: только агрегаты, k-анонимность >=10 (аномалии >=30);
-- сырые пинги эфемерны: user_hash = HMAC(user_id, daily_salt), чистка кроном (TTL 15 мин)

CREATE UNLOGGED TABLE IF NOT EXISTS location_pings (
    user_hash     VARCHAR(64) NOT NULL,
    lat           DOUBLE PRECISION NOT NULL,
    lng           DOUBLE PRECISION NOT NULL,
    created_at_ms BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS pings_geo_idx  ON location_pings (lat, lng);
CREATE INDEX IF NOT EXISTS pings_time_idx ON location_pings (created_at_ms);

-- агрегат пульса по событиям (обновляется джобой каждые 30 сек)
CREATE TABLE IF NOT EXISTS pulse_aggregates (
    event_id        VARCHAR(64) PRIMARY KEY REFERENCES events(id) ON DELETE CASCADE,
    glow            REAL   NOT NULL DEFAULT 0, -- 0..1 для PulseGlow (G1)
    crowd_10m       INT    NOT NULL DEFAULT 0,  -- пинги рядом за 10 мин (поток G2)
    viewers         INT    NOT NULL DEFAULT 0,
    updated_at_ms   BIGINT NOT NULL DEFAULT 0
);

-- аномалии плотности без события (G9), k >= 30
CREATE TABLE IF NOT EXISTS pulse_anomalies (
    id         VARCHAR(64) PRIMARY KEY,
    lat        DOUBLE PRECISION NOT NULL,
    lng        DOUBLE PRECISION NOT NULL,
    user_count    INT    NOT NULL,
    started_at_ms BIGINT NOT NULL DEFAULT 0,
    ended_at_ms   BIGINT NULL
);
CREATE INDEX IF NOT EXISTS anomalies_active_idx ON pulse_anomalies (ended_at_ms) WHERE ended_at_ms IS NULL;
