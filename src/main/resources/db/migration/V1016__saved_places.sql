CREATE TABLE IF NOT EXISTS saved_places (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    place_id          UUID REFERENCES places(id) ON DELETE SET NULL,
    external_place_id VARCHAR(255),
    name              VARCHAR(255) NOT NULL,
    address           TEXT,
    category          VARCHAR(100),
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    provider          VARCHAR(50),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_saved_places_user_external
    ON saved_places(user_id, external_place_id)
    WHERE external_place_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_saved_places_user_created
    ON saved_places(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_saved_places_place
    ON saved_places(place_id);
