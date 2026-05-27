CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type       notif_type NOT NULL DEFAULT 'SYSTEM',
    title      VARCHAR(255),
    body       TEXT,
    payload    JSONB,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_id ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_read_at ON notifications(user_id) WHERE read_at IS NULL;

CREATE TABLE device_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    platform   VARCHAR(20) NOT NULL,
    token      TEXT NOT NULL,
    last_seen  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, token)
);

CREATE TABLE notification_preferences (
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    channel    VARCHAR(20) NOT NULL,
    type       notif_type NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, channel, type)
);