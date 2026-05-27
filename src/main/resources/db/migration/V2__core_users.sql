-- User roles
CREATE TABLE user_roles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    role       app_role NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, role)
);

-- Profiles
CREATE TABLE profiles (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    display_name        VARCHAR(100),
    avatar_url          TEXT,
    bio                 TEXT,
    locale              VARCHAR(10) NOT NULL DEFAULT 'en',
    theme               VARCHAR(20) NOT NULL DEFAULT 'dark',
    home_city           VARCHAR(100),
    is_public           BOOLEAN NOT NULL DEFAULT TRUE,
    onboarded_at        TIMESTAMPTZ,
    email_verified_at   TIMESTAMPTZ,
    phone               VARCHAR(20),
    phone_verified_at   TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_email ON profiles(email);
CREATE INDEX idx_profiles_deleted_at ON profiles(deleted_at);

-- User preferences
CREATE TABLE user_preferences (
    user_id      UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    travel_style TEXT[] DEFAULT '{}',
    interests    TEXT[] DEFAULT '{}',
    dietary      TEXT[] DEFAULT '{}',
    budget_band  budget_band DEFAULT 'MID',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Wallets
CREATE TABLE wallets (
    user_id     UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    gola_coins  INTEGER NOT NULL DEFAULT 0 CHECK (gola_coins >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    device_info TEXT,
    ip_address  VARCHAR(45),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Audit logs
CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID,
    action     VARCHAR(100) NOT NULL,
    resource   VARCHAR(100),
    resource_id VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    detail     JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- Password reset audit
CREATE TABLE password_reset_audit (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID REFERENCES profiles(id),
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Follows
CREATE TABLE follows (
    follower_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    followee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id)
);

-- User blocks
CREATE TABLE user_blocks (
    blocker_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);