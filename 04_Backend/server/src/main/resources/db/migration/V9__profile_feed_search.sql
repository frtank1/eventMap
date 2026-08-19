-- V9 — профиль пользователя (H1), подписки (F1), уведомления (F4), поиск (G2), оценки (P4).
-- Закрывает контракт мобилки: /users/me, /users/{id}/history, /feed, /notifications,
-- /subscriptions, /subscription-requests, /search, /events/{id}/rating(s).

-- Профиль поверх users (H1: имя, @username, био, аватар, город)
ALTER TABLE users ADD COLUMN IF NOT EXISTS username   VARCHAR(64)  NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio        TEXT         NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS city       VARCHAR(64)  NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS interests  TEXT         NOT NULL DEFAULT ''; -- csv категорий (A8)

-- Подписки на организаторов (F1); раньше POST /organizers/{id}/subscribe был заглушкой
CREATE TABLE IF NOT EXISTS organizer_subscriptions (
    user_id       VARCHAR(64) NOT NULL,
    organizer_id  VARCHAR(64) NOT NULL,
    created_at_ms BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, organizer_id)
);
CREATE INDEX IF NOT EXISTS idx_org_subs_org ON organizer_subscriptions(organizer_id);

-- Заявки на подписку (приватные профили, F1 «Заявки»)
CREATE TABLE IF NOT EXISTS subscription_requests (
    id             VARCHAR(64) PRIMARY KEY,
    target_user_id VARCHAR(64) NOT NULL, -- кому пришла заявка
    from_user_id   VARCHAR(64) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending|approved|rejected
    created_at_ms  BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sub_req_target ON subscription_requests(target_user_id);

-- Уведомления (F4). Пишутся при покупке билета, заявке в друзья, финише квеста.
CREATE TABLE IF NOT EXISTS notifications (
    id            VARCHAR(64) PRIMARY KEY,
    user_id       VARCHAR(64) NOT NULL,
    type          VARCHAR(16) NOT NULL DEFAULT 'info', -- info|event|subscribe|live
    text          TEXT        NOT NULL,
    created_at_ms BIGINT      NOT NULL DEFAULT 0,
    read          BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

-- Оценки событий (P4). events.rating_avg / rating_count пересчитываются при upsert.
CREATE TABLE IF NOT EXISTS event_ratings (
    id            VARCHAR(64) PRIMARY KEY,
    event_id      VARCHAR(64) NOT NULL,
    user_id       VARCHAR(64) NOT NULL,
    value         INT         NOT NULL,
    review        TEXT        NOT NULL DEFAULT '',
    created_at_ms BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (event_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_event_ratings_event ON event_ratings(event_id);
