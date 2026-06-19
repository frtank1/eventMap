-- V7 — видео-рилсы («Сейчас»). Файл лежит в object storage (media_url), метаданные — здесь.
-- Лайки — отдельной таблицей для тоггла и честного счётчика.

CREATE TABLE IF NOT EXISTS videos (
    id              VARCHAR(64) PRIMARY KEY,
    author_user_id  VARCHAR(64)  NOT NULL,
    event_id        VARCHAR(64),
    media_url       VARCHAR(1024) NOT NULL,
    thumb_url       VARCHAR(1024),
    caption         VARCHAR(500) NOT NULL DEFAULT '',
    category        VARCHAR(64)  NOT NULL DEFAULT '',
    viewers         INTEGER      NOT NULL DEFAULT 0,
    like_count      INTEGER      NOT NULL DEFAULT 0,
    created_at_ms   BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_videos_event   ON videos(event_id);
CREATE INDEX IF NOT EXISTS idx_videos_created ON videos(created_at_ms DESC);

CREATE TABLE IF NOT EXISTS video_likes (
    video_id  VARCHAR(64) NOT NULL,
    user_id   VARCHAR(64) NOT NULL,
    PRIMARY KEY (video_id, user_id)
);
