-- Create media table for general media objects
CREATE TABLE media (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    storage_path VARCHAR(500) NOT NULL,
    mime_type    VARCHAR(100) NOT NULL,
    width        INTEGER NOT NULL,
    height       INTEGER NOT NULL,
    ai_caption   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_owner_id ON media(owner_id);
