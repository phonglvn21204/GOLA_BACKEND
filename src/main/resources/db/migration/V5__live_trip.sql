CREATE TABLE trip_sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id    UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    status     session_status NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at   TIMESTAMPTZ
);
CREATE INDEX idx_trip_sessions_trip_id ON trip_sessions(trip_id);
CREATE INDEX idx_trip_sessions_status ON trip_sessions(status);

CREATE TABLE live_locations (
    session_id UUID NOT NULL REFERENCES trip_sessions(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    geom       geography(Point, 4326) NOT NULL,
    heading    REAL,
    speed      REAL,
    accuracy   REAL,
    ts         TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (ts);

CREATE TABLE live_locations_default PARTITION OF live_locations DEFAULT;
CREATE INDEX idx_live_locations_session_ts ON live_locations(session_id, ts DESC);

CREATE TABLE trip_chat (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES trip_sessions(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES profiles(id),
    body       TEXT NOT NULL,
    ts         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trip_chat_session_id ON trip_chat(session_id, ts DESC);

CREATE TABLE traffic_alerts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES trip_sessions(id) ON DELETE CASCADE,
    type       VARCHAR(50),
    geom       geography(Point, 4326),
    message    TEXT,
    severity   VARCHAR(20),
    ts         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);