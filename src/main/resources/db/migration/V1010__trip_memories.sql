ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS trip_memories (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    summary      TEXT,
    status       VARCHAR(32) NOT NULL DEFAULT 'NOT_GENERATED',
    share_status VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    generated_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    CONSTRAINT uq_trip_memories_trip_user UNIQUE (trip_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_memories_user ON trip_memories(user_id);
CREATE INDEX IF NOT EXISTS idx_trip_memories_trip ON trip_memories(trip_id);
