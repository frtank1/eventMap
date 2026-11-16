-- Existing events remain unassigned: proximity never proves building identity.
ALTER TABLE events ADD COLUMN building_id VARCHAR(100);
ALTER TABLE events ADD COLUMN floor_id VARCHAR(64);
ALTER TABLE events ADD COLUMN room_id VARCHAR(64);
ALTER TABLE events ADD CONSTRAINT event_venue_hierarchy CHECK (
    (floor_id IS NULL OR building_id IS NOT NULL) AND
    (room_id IS NULL OR floor_id IS NOT NULL)
);
CREATE INDEX events_building_public ON events(building_id, start_at) WHERE visibility = 'public';
