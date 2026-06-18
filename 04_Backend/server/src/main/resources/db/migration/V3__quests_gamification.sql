-- V3: промо-квесты (Q, API 8.3) и геймификация (R, API 8.4)

CREATE TABLE IF NOT EXISTS quests (
    id                VARCHAR(64)  PRIMARY KEY,
    business_id       VARCHAR(64)  NOT NULL REFERENCES business_accounts(id),
    title             VARCHAR(255) NOT NULL,
    emoji             VARCHAR(8)   NULL,
    ordered_chain     BOOLEAN      NOT NULL DEFAULT FALSE,
    starts_at         VARCHAR(40)  NULL, -- ISO, стиль events V1
    ends_at           VARCHAR(40)  NULL,
    prize_title       VARCHAR(255) NOT NULL,
    prize_count       INT          NULL,
    participant_limit INT          NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'moderation', -- moderation|active|ended|rejected
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS quests_status_idx ON quests (status);

CREATE TABLE IF NOT EXISTS quest_points (
    id         VARCHAR(64)  PRIMARY KEY,
    quest_id   VARCHAR(64)  NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    ord        INT          NOT NULL,
    lat        DOUBLE PRECISION NOT NULL,
    lng        DOUBLE PRECISION NOT NULL,
    place_name VARCHAR(255) NULL,
    condition  VARCHAR(16)  NOT NULL -- geo|photo|purchase
);
CREATE INDEX IF NOT EXISTS quest_points_quest_idx ON quest_points (quest_id, ord);
CREATE INDEX IF NOT EXISTS quest_points_geo_idx   ON quest_points (lat, lng);

CREATE TABLE IF NOT EXISTS quest_runs (
    id          VARCHAR(64) PRIMARY KEY,
    quest_id    VARCHAR(64) NOT NULL REFERENCES quests(id),
    user_id     VARCHAR(64) NOT NULL REFERENCES users(id),
    started_at_ms  BIGINT NOT NULL DEFAULT 0,
    finished_at_ms BIGINT NULL,
    prize_qr       VARCHAR(128) NULL,
    UNIQUE (quest_id, user_id)
);

CREATE TABLE IF NOT EXISTS quest_checkins (
    run_id     VARCHAR(64) NOT NULL REFERENCES quest_runs(id) ON DELETE CASCADE,
    point_id   VARCHAR(64) NOT NULL REFERENCES quest_points(id),
    proof_type    VARCHAR(16) NOT NULL, -- geo|photo|receipt_qr
    media_url     VARCHAR(512) NULL,
    created_at_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, point_id)
);

-- ===== Геймификация =====
-- XP-константы (сервер): checkin +10 · quest_point +15 · quest_finish +100 · district +50 · mission по reward
CREATE TABLE IF NOT EXISTS xp_events (
    id         VARCHAR(64) PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source     VARCHAR(32) NOT NULL, -- checkin|quest_point|quest_finish|district|mission
    source_id  VARCHAR(64) NULL,
    amount     INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS xp_user_idx ON xp_events (user_id, created_at);

CREATE TABLE IF NOT EXISTS badges (
    id    VARCHAR(64) PRIMARY KEY,
    code  VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(128) NOT NULL,
    emoji VARCHAR(8)  NULL
);
CREATE TABLE IF NOT EXISTS user_badges (
    user_id    VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_id   VARCHAR(64) NOT NULL REFERENCES badges(id),
    source     VARCHAR(64) NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_id)
);

CREATE TABLE IF NOT EXISTS missions (
    id         VARCHAR(64) PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       VARCHAR(32) NOT NULL, -- friend_event|new_district|new_genre|...
    payload    TEXT        NULL,     -- JSON
    progress   INT         NOT NULL DEFAULT 0,
    target     INT         NOT NULL,
    reward_xp  INT         NOT NULL DEFAULT 0,
    multiplier REAL        NOT NULL DEFAULT 1, -- 2.0 совместные
    status        VARCHAR(16) NOT NULL DEFAULT 'active', -- active|done|expired
    expires_at_ms BIGINT NULL
);
CREATE INDEX IF NOT EXISTS missions_user_idx ON missions (user_id, status);

-- районы: полигон как GeoJSON (до PostGIS)
CREATE TABLE IF NOT EXISTS districts (
    id      VARCHAR(64)  PRIMARY KEY,
    city    VARCHAR(64)  NOT NULL DEFAULT 'astana',
    name    VARCHAR(128) NOT NULL,
    geojson TEXT         NOT NULL
);
CREATE TABLE IF NOT EXISTS district_visits ( -- туман войны (G3)
    user_id     VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    district_id VARCHAR(64) NOT NULL REFERENCES districts(id),
    first_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, district_id)
);

CREATE TABLE IF NOT EXISTS season_passes (
    id        VARCHAR(64)  PRIMARY KEY,
    title     VARCHAR(128) NOT NULL,
    starts_on DATE         NOT NULL,
    ends_on   DATE         NOT NULL,
    rewards   TEXT         NOT NULL DEFAULT '[]' -- JSON [{ord,businessId,prize,questId}]
);
CREATE TABLE IF NOT EXISTS user_pass_progress (
    user_id   VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pass_id   VARCHAR(64) NOT NULL REFERENCES season_passes(id),
    completed INT         NOT NULL DEFAULT 0,
    claimed   TEXT        NOT NULL DEFAULT '[]',
    PRIMARY KEY (user_id, pass_id)
);

CREATE TABLE IF NOT EXISTS time_capsules ( -- G15
    id             VARCHAR(64) PRIMARY KEY,
    owner_id       VARCHAR(64) NOT NULL REFERENCES users(id),
    lat            DOUBLE PRECISION NOT NULL,
    lng            DOUBLE PRECISION NOT NULL,
    media_url      VARCHAR(512) NULL,
    body_text      TEXT        NULL,
    recipient_type VARCHAR(16) NOT NULL, -- user|self_1y|public
    recipient_id   VARCHAR(64) NULL,
    opens_at       TIMESTAMPTZ NULL,
    opened_at      TIMESTAMPTZ NULL,
    opened_by      VARCHAR(64) NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS capsules_geo_idx ON time_capsules (lat, lng);
