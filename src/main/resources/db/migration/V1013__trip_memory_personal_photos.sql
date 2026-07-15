ALTER TABLE albums
    ADD COLUMN IF NOT EXISTS album_source VARCHAR(32) NOT NULL DEFAULT 'ITINERARY';

CREATE TABLE IF NOT EXISTS trip_memory_photos (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_memory_id     UUID NOT NULL REFERENCES trip_memories(id) ON DELETE CASCADE,
    trip_id            UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    media_id           UUID REFERENCES media(id) ON DELETE SET NULL,
    image_url          TEXT NOT NULL,
    storage_path       TEXT,
    original_file_name TEXT,
    content_type       VARCHAR(128),
    file_size_bytes    BIGINT,
    day_index          INTEGER,
    trip_stop_id       UUID REFERENCES trip_stops(id) ON DELETE SET NULL,
    caption_note       TEXT,
    ai_caption         TEXT,
    sort_order         INTEGER NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_trip_memory_photos_memory ON trip_memory_photos(trip_memory_id);
CREATE INDEX IF NOT EXISTS idx_trip_memory_photos_trip_user ON trip_memory_photos(trip_id, user_id);

ALTER TABLE album_media
    ADD COLUMN IF NOT EXISTS memory_photo_id UUID REFERENCES trip_memory_photos(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_album_media_memory_photo_id ON album_media(memory_photo_id);
