ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS provider_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_source VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_trip_stops_provider_id
    ON trip_stops (provider_id);
