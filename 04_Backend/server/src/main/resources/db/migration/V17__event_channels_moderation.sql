-- Event V3 (18.11.2026): channels, moderation and map presentation.
-- Every existing event stays published and keeps its point presentation:
-- older clients see exactly what they saw before.
ALTER TABLE events ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16) NOT NULL DEFAULT 'published';
ALTER TABLE events ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(64);
ALTER TABLE events ADD COLUMN IF NOT EXISTS reviewed_at_ms BIGINT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(1000);
ALTER TABLE events ADD COLUMN IF NOT EXISTS created_at_ms BIGINT NOT NULL DEFAULT 0;
-- Simple weekly rule as JSON: {"weekdays":[6,7],"until":"2026-12-31"} (ISO weekdays, Astana dates).
ALTER TABLE events ADD COLUMN IF NOT EXISTS recurrence TEXT;
-- Territory as JSON: {"polygon":[[lng,lat],...],"entrances":[{"lng":..,"lat":..,"name":".."}]}.
ALTER TABLE events ADD COLUMN IF NOT EXISTS area TEXT;
-- S = point with cover, M = territory with zones and entrances, L = 3D scene from a template.
ALTER TABLE events ADD COLUMN IF NOT EXISTS presentation VARCHAR(2) NOT NULL DEFAULT 'S';
ALTER TABLE events ADD COLUMN IF NOT EXISTS scene_template VARCHAR(40);
ALTER TABLE events ADD COLUMN IF NOT EXISTS scene_params TEXT;
-- Editorial picks link to where the event was announced.
ALTER TABLE events ADD COLUMN IF NOT EXISTS source_url VARCHAR(500);
ALTER TABLE events ADD COLUMN IF NOT EXISTS source_name VARCHAR(120);
-- Optional contact left by a guest for the moderator. Never part of a public response.
ALTER TABLE events ADD COLUMN IF NOT EXISTS contact VARCHAR(200);
-- Stable id of an imported editorial row, so a re-import updates instead of duplicating.
ALTER TABLE events ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);

ALTER TABLE events ADD CONSTRAINT event_moderation_status
    CHECK (moderation_status IN ('pending', 'published', 'rejected', 'expired'));
ALTER TABLE events ADD CONSTRAINT event_presentation
    CHECK (presentation IN ('S', 'M', 'L'));
CREATE UNIQUE INDEX IF NOT EXISTS events_external_id ON events(external_id) WHERE external_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS events_moderation_queue ON events(moderation_status, created_at_ms);
