-- AI Quota Limits table
-- Stores per-role usage caps for each AI quota kind.
-- kind is stored as TEXT to match the AiQuotaKind Java enum
-- (values: TRIP_GENERATION, CAPTION, RECOMMENDATION) which differ
-- from the ai_job_kind Postgres enum.
CREATE TABLE ai_quota_limits (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id     UUID        NOT NULL,
    kind        TEXT        NOT NULL,
    max_uses    INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT uq_ai_quota_limits_role_kind UNIQUE (role_id, kind)
);

CREATE INDEX idx_ai_quota_limits_role_id ON ai_quota_limits(role_id);
