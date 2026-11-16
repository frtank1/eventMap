CREATE TABLE venue_plans (
    building_id VARCHAR(100) PRIMARY KEY,
    edit_version BIGINT NOT NULL CHECK (edit_version >= 0),
    building_name VARCHAR(120) NOT NULL,
    draft_json TEXT NOT NULL,
    published_json TEXT,
    updated_at_ms BIGINT NOT NULL
);
CREATE TABLE venue_revisions (
    revision VARCHAR(64) PRIMARY KEY,
    building_id VARCHAR(100) NOT NULL REFERENCES venue_plans(building_id),
    document_json TEXT NOT NULL,
    published_at_ms BIGINT NOT NULL
);
CREATE INDEX venue_revisions_building_time ON venue_revisions(building_id, published_at_ms DESC);
