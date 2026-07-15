ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS rating NUMERIC(3, 2);

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS review_count INTEGER;

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS image_source VARCHAR(40);

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS place_address TEXT;
