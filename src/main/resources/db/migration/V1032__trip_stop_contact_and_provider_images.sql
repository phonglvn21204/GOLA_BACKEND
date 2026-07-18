ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS phone VARCHAR(100),
    ADD COLUMN IF NOT EXISTS website TEXT;

-- Store only original remote provider URLs. No image content is downloaded or proxied.
CREATE TABLE IF NOT EXISTS trip_stop_images (
    trip_stop_id UUID NOT NULL REFERENCES trip_stops(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    image_url TEXT NOT NULL,
    PRIMARY KEY (trip_stop_id, sort_order)
);
