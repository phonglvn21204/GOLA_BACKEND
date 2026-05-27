CREATE TABLE places (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_place_id VARCHAR(255) UNIQUE,
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(100),
    geom            geography(Point, 4326),
    address         TEXT,
    city            VARCHAR(100),
    country         VARCHAR(100),
    photos          TEXT[] DEFAULT '{}',
    opening_hours   JSONB,
    rating          NUMERIC(3,2),
    refreshed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_places_geom ON places USING GIST(geom);
CREATE INDEX idx_places_google_place_id ON places(google_place_id);
CREATE INDEX idx_places_category ON places(category);

CREATE TABLE place_categories (
    id   VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(100)
);

CREATE TABLE place_favorites (
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    place_id   UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, place_id)
);

CREATE TABLE reviews (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id   UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body       TEXT,
    is_hidden  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (place_id, user_id)
);
CREATE INDEX idx_reviews_place_id ON reviews(place_id);