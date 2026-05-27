CREATE TABLE ai_jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    kind         ai_job_kind NOT NULL,
    status       ai_job_status NOT NULL DEFAULT 'QUEUED',
    input        JSONB,
    output       JSONB,
    tokens_in    INTEGER,
    tokens_out   INTEGER,
    cost_usd     NUMERIC(10,6),
    error        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_ai_jobs_user_id ON ai_jobs(user_id);
CREATE INDEX idx_ai_jobs_status ON ai_jobs(status);

CREATE TABLE ai_quotas (
    user_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    kind         ai_job_kind NOT NULL,
    period_start DATE NOT NULL,
    count        INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, kind, period_start)
);