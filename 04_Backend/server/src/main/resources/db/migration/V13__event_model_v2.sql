ALTER TABLE events ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'planned';
ALTER TABLE events ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'initiator';
ALTER TABLE events ADD COLUMN IF NOT EXISTS join_policy VARCHAR(20) NOT NULL DEFAULT 'open';
ALTER TABLE events ADD COLUMN IF NOT EXISTS audience VARCHAR(20) NOT NULL DEFAULT 'everyone';
ALTER TABLE events ADD COLUMN IF NOT EXISTS min_age INTEGER;
ALTER TABLE events ADD COLUMN IF NOT EXISTS expires_at_ms BIGINT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS proof_media_id VARCHAR(64);
ALTER TABLE events ADD COLUMN IF NOT EXISTS confirmation_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS interest_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS event_signals (
    event_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    signal_type VARCHAR(16) NOT NULL,
    created_at_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id, user_id, signal_type)
);
CREATE INDEX IF NOT EXISTS event_signals_event_idx ON event_signals(event_id);

CREATE TABLE IF NOT EXISTS event_join_requests (
    event_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    message VARCHAR(500) NOT NULL DEFAULT '',
    created_at_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id, user_id)
);
CREATE INDEX IF NOT EXISTS event_join_requests_event_idx ON event_join_requests(event_id);
