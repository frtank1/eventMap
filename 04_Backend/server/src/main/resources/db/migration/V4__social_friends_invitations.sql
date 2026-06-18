-- V4: рилс-соцслой (D9–D11, API 8.6), друзья и приглашения (N, API 8.7)

CREATE TABLE IF NOT EXISTS attendance ( -- «иду»/«был» (если ещё не создана кодом)
    user_id    VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id   VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    type       VARCHAR(8)  NOT NULL, -- going|was
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event_id)
);

CREATE TABLE IF NOT EXISTS event_comments (
    id         VARCHAR(64) PRIMARY KEY,
    event_id   VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    author_id  VARCHAR(64) NOT NULL REFERENCES users(id),
    parent_id  VARCHAR(64) NULL REFERENCES event_comments(id),
    body          TEXT   NOT NULL,
    like_count    INT    NOT NULL DEFAULT 0,
    created_at_ms BIGINT NOT NULL DEFAULT 0 -- стиль кода (refresh_tokens.expires_at_ms)
);
CREATE INDEX IF NOT EXISTS comments_event_idx ON event_comments (event_id, created_at_ms DESC);

CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id VARCHAR(64) NOT NULL REFERENCES event_comments(id) ON DELETE CASCADE,
    user_id    VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (comment_id, user_id)
);

CREATE TABLE IF NOT EXISTS reminders ( -- колокольчик D11; source=ticket — авто после оплаты
    user_id  VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    source   VARCHAR(16) NOT NULL DEFAULT 'bell', -- bell|ticket
    PRIMARY KEY (user_id, event_id)
);

-- ===== Друзья (N, 8.7): user_a < user_b лексикографически =====
CREATE TABLE IF NOT EXISTS friendships (
    user_a       VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b       VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending|accepted
    requested_by VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at  TIMESTAMPTZ NULL,
    PRIMARY KEY (user_a, user_b),
    CHECK (user_a < user_b)
);

CREATE TABLE IF NOT EXISTS friend_groups (
    id       VARCHAR(64)  PRIMARY KEY,
    owner_id VARCHAR(64)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS friend_group_members (
    group_id  VARCHAR(64) NOT NULL REFERENCES friend_groups(id) ON DELETE CASCADE,
    friend_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, friend_id)
);

CREATE TABLE IF NOT EXISTS invitations (
    id          VARCHAR(64)  PRIMARY KEY,
    event_id    VARCHAR(64)  NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    template_id VARCHAR(64)  NOT NULL,
    link_token  VARCHAR(128) NULL UNIQUE, -- веб-открытка для гостей без аккаунта
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS invitation_guests (
    id            VARCHAR(64) PRIMARY KEY,
    invitation_id VARCHAR(64) NOT NULL REFERENCES invitations(id) ON DELETE CASCADE,
    user_id       VARCHAR(64) NULL REFERENCES users(id), -- NULL = гость по ссылке
    guest_name    VARCHAR(128) NULL,
    rsvp            VARCHAR(8)  NULL, -- yes|no|NULL (молчит)
    plus_one        INT         NOT NULL DEFAULT 0,
    responded_at_ms BIGINT      NULL,
    UNIQUE (invitation_id, user_id)
);
CREATE INDEX IF NOT EXISTS inv_guests_idx ON invitation_guests (invitation_id, rsvp);
