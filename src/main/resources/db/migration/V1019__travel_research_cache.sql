CREATE TABLE IF NOT EXISTS travel_research_cache (
    id UUID PRIMARY KEY,
    cache_key VARCHAR(160) NOT NULL UNIQUE,
    destination VARCHAR(255) NOT NULL,
    travel_month INTEGER NOT NULL,
    query TEXT,
    context_json TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_travel_research_cache_expires_at
    ON travel_research_cache (expires_at);
