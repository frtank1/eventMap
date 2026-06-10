-- V1: базовая схема EventMap (organizers, events, users, refresh_tokens)

CREATE TABLE IF NOT EXISTS organizers (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    bio         TEXT         NOT NULL DEFAULT '',
    avatar_url  VARCHAR(512) NULL,
    is_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    subscribers INT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS events (
    id            VARCHAR(64)  PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    category      VARCHAR(64)  NOT NULL DEFAULT '',
    status        VARCHAR(16)  NOT NULL DEFAULT 'soon',
    start_at      VARCHAR(40)  NOT NULL DEFAULT '',
    end_at        VARCHAR(40)  NOT NULL DEFAULT '',
    lat           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    lng           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    location_name VARCHAR(255) NOT NULL DEFAULT '',
    price_type    VARCHAR(16)  NOT NULL DEFAULT 'free',
    price_amount  INT          NOT NULL DEFAULT 0,
    currency      VARCHAR(8)   NOT NULL DEFAULT 'KZT',
    going_count   INT          NOT NULL DEFAULT 0,
    live_viewers  INT          NOT NULL DEFAULT 0,
    rating_avg    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count  INT          NOT NULL DEFAULT 0,
    cover         VARCHAR(512) NULL,
    organizer_id  VARCHAR(64)  NULL,
    capacity      INT          NULL
);

-- bbox-запросы карты (до перехода на PostGIS GIST)
CREATE INDEX IF NOT EXISTS events_geo_idx      ON events (lat, lng);
CREATE INDEX IF NOT EXISTS events_status_idx   ON events (status);
CREATE INDEX IF NOT EXISTS events_category_idx ON events (category);

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(64)  PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL DEFAULT '',
    password_hash VARCHAR(255) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_idx ON users (email);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    token_hash    VARCHAR(64) PRIMARY KEY,
    user_id       VARCHAR(64) NOT NULL,
    expires_at_ms BIGINT      NOT NULL,
    revoked       BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON refresh_tokens (user_id);
