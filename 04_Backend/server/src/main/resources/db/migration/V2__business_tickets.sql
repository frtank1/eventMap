-- V2: ревизия 11.06.2026 — бизнес-аккаунты (M) и билеты с гостями (P)
-- стиль V1: VARCHAR(64) id; время — TIMESTAMPTZ для новых таблиц

-- события: 18+, приватность, радиус гео-чекина
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_adult     BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS visibility   VARCHAR(16) NOT NULL DEFAULT 'public'; -- public|private (N)
ALTER TABLE events ADD COLUMN IF NOT EXISTS geo_radius_m INT         NOT NULL DEFAULT 300;
CREATE INDEX IF NOT EXISTS events_visibility_idx ON events (visibility);

-- пользователи: дата рождения для фильтра 18+
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE NULL;

-- ===== Бизнес-аккаунты (раздел M, API 8.1) =====
CREATE TABLE IF NOT EXISTS business_accounts (
    id            VARCHAR(64)  PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    bin           VARCHAR(12)  NOT NULL,
    legal_name    VARCHAR(255) NOT NULL,
    brand_name    VARCHAR(255) NOT NULL,
    category      VARCHAR(64)  NOT NULL,
    phone         VARCHAR(32)  NULL,
    social        VARCHAR(255) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending', -- pending|approved|rejected
    reject_reason TEXT         NULL,
    logo_url      VARCHAR(512) NULL,
    pin_color     VARCHAR(9)   NOT NULL DEFAULT '#FF5C8A',
    animation_id  VARCHAR(64)  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at   TIMESTAMPTZ  NULL
);
CREATE INDEX IF NOT EXISTS business_status_idx ON business_accounts (status);

CREATE TABLE IF NOT EXISTS business_documents (
    id          VARCHAR(64)  PRIMARY KEY,
    business_id VARCHAR(64)  NOT NULL REFERENCES business_accounts(id) ON DELETE CASCADE,
    kind        VARCHAR(32)  NOT NULL, -- registration|license|other
    file_url    VARCHAR(512) NOT NULL
);

-- наборы анимаций пина: грузит админ, бизнес видит только свою категорию
CREATE TABLE IF NOT EXISTS pin_animations (
    id         VARCHAR(64)  PRIMARY KEY,
    category   VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    lottie_key VARCHAR(512) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS pin_anim_cat_idx ON pin_animations (category) WHERE is_active;

-- ===== Билеты (раздел P, API 8.2) =====
-- правило: goingCount = attendance(going) + билеты с owner_user_id IS NULL (анонимные «+N»)
CREATE TABLE IF NOT EXISTS ticket_orders (
    id        VARCHAR(64) PRIMARY KEY,
    event_id  VARCHAR(64) NOT NULL REFERENCES events(id),
    buyer_id  VARCHAR(64) NOT NULL REFERENCES users(id),
    qty       INT         NOT NULL CHECK (qty BETWEEN 1 AND 10),
    amount    INT         NOT NULL,
    currency  VARCHAR(8)  NOT NULL DEFAULT 'KZT',
    provider  VARCHAR(16) NOT NULL,                 -- kaspi|card|applepay
    status    VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending|paid|failed|refunded
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at    TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS ticket_orders_event_idx ON ticket_orders (event_id, status);

CREATE TABLE IF NOT EXISTS tickets (
    id            VARCHAR(64) PRIMARY KEY,
    order_id      VARCHAR(64) NOT NULL REFERENCES ticket_orders(id) ON DELETE CASCADE,
    event_id      VARCHAR(64) NOT NULL REFERENCES events(id),
    owner_user_id VARCHAR(64) NULL REFERENCES users(id), -- NULL = анонимный гость
    qr_secret     VARCHAR(128) NOT NULL UNIQUE,
    status        VARCHAR(16) NOT NULL DEFAULT 'active', -- active|used|refunded
    used_at       TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS tickets_event_idx ON tickets (event_id, status);
CREATE INDEX IF NOT EXISTS tickets_owner_idx ON tickets (owner_user_id);
