CREATE TABLE badges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon_url    TEXT,
    category    VARCHAR(50),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE quests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          quest_type NOT NULL DEFAULT 'SOLO',
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    requirements  JSONB,
    reward_coins  INTEGER NOT NULL DEFAULT 0,
    badge_id      UUID REFERENCES badges(id),
    geom          geography(Point, 4326),
    radius_m      DOUBLE PRECISION,
    is_featured   BOOLEAN NOT NULL DEFAULT FALSE,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_quests_geom ON quests USING GIST(geom);
CREATE INDEX idx_quests_is_active ON quests(is_active);

CREATE TABLE quest_tasks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quest_id     UUID NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    idx          INTEGER NOT NULL,
    description  TEXT NOT NULL,
    proof_type   proof_type NOT NULL DEFAULT 'CHECKIN',
    geom         geography(Point, 4326),
    radius_m     DOUBLE PRECISION,
    UNIQUE (quest_id, idx)
);

CREATE TABLE quest_progress (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quest_id        UUID NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    task_idx        INTEGER NOT NULL DEFAULT 0,
    status          progress_status NOT NULL DEFAULT 'IN_PROGRESS',
    proof_media_url TEXT,
    submitted_lat   DOUBLE PRECISION,
    submitted_lng   DOUBLE PRECISION,
    verified_at     TIMESTAMPTZ,
    verified_by     UUID REFERENCES profiles(id),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    UNIQUE (quest_id, user_id)
);
CREATE INDEX idx_quest_progress_user_id ON quest_progress(user_id);

CREATE TABLE user_badges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    badge_id        UUID NOT NULL REFERENCES badges(id),
    source_quest_id UUID REFERENCES quests(id),
    earned_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, badge_id)
);
CREATE INDEX idx_user_badges_user_id ON user_badges(user_id);

CREATE TABLE rewards (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    cost_coins  INTEGER NOT NULL,
    stock       INTEGER NOT NULL DEFAULT 0,
    partner_id  UUID,
    image_url   TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE redemptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    reward_id   UUID NOT NULL REFERENCES rewards(id),
    code        VARCHAR(100),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_redemptions_user_id ON redemptions(user_id);