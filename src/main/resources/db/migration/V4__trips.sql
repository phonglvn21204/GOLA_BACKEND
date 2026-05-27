CREATE TABLE trips (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    origin        VARCHAR(255),
    destination   VARCHAR(255),
    start_date    DATE,
    end_date      DATE,
    status        trip_status NOT NULL DEFAULT 'DRAFT',
    is_public     BOOLEAN NOT NULL DEFAULT FALSE,
    cover_url     TEXT,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_trips_owner_id ON trips(owner_id);
CREATE INDEX idx_trips_status ON trips(status);

CREATE TABLE trip_stops (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    place_id     UUID REFERENCES places(id),
    order_idx    DOUBLE PRECISION NOT NULL,
    name         VARCHAR(255),
    arrival_at   TIMESTAMPTZ,
    duration_min INTEGER,
    notes        TEXT,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trip_stops_trip_id ON trip_stops(trip_id);

CREATE TABLE trip_members (
    trip_id    UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    role       member_role NOT NULL DEFAULT 'VIEWER',
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (trip_id, user_id)
);

CREATE TABLE trip_invitations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id    UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    email      VARCHAR(255),
    phone      VARCHAR(20),
    user_id    UUID REFERENCES profiles(id),
    token      VARCHAR(100) NOT NULL UNIQUE,
    role       member_role NOT NULL DEFAULT 'VIEWER',
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trip_invitations_token ON trip_invitations(token);

CREATE TABLE trip_shares (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id    UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    token      VARCHAR(100) NOT NULL UNIQUE,
    scope      share_scope NOT NULL DEFAULT 'VIEW',
    expires_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES profiles(id),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trip_shares_token ON trip_shares(token);

CREATE TABLE routes_cache (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hash       VARCHAR(64) NOT NULL UNIQUE,
    geometry   TEXT,
    distance_m DOUBLE PRECISION,
    duration_s DOUBLE PRECISION,
    mode       VARCHAR(20),
    provider   VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);