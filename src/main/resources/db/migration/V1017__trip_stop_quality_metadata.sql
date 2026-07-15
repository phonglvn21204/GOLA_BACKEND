ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS data_source VARCHAR(80);

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS enrichment_status VARCHAR(30);

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS has_real_photo BOOLEAN DEFAULT FALSE;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS has_real_coordinates BOOLEAN DEFAULT FALSE;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS has_opening_hours BOOLEAN DEFAULT FALSE;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS opening_hours_text TEXT;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS open_now BOOLEAN;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS business_status VARCHAR(80);

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS next_open_close_text TEXT;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS system_stop BOOLEAN DEFAULT FALSE;
