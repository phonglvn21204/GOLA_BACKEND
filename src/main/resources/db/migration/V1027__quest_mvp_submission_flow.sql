ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'GPS_PHOTO';
ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'PHOTO_ONLY';

ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'ASSIGNED';
ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'SUBMITTED';
ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'APPROVED';
ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'REJECTED';

ALTER TABLE quests
    ADD COLUMN IF NOT EXISTS destination VARCHAR(255),
    ADD COLUMN IF NOT EXISTS target_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS target_lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS target_lng DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES profiles(id);

ALTER TABLE quest_progress
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES profiles(id),
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by UUID REFERENCES profiles(id),
    ADD COLUMN IF NOT EXISTS reject_reason TEXT,
    ADD COLUMN IF NOT EXISTS reward_points_awarded INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gps_valid BOOLEAN,
    ADD COLUMN IF NOT EXISTS note TEXT,
    ADD COLUMN IF NOT EXISTS admin_note TEXT;

CREATE INDEX IF NOT EXISTS idx_quests_destination_active ON quests(destination, is_active);
CREATE INDEX IF NOT EXISTS idx_quest_progress_trip_quest_user ON quest_progress(trip_id, quest_id, user_id);
CREATE INDEX IF NOT EXISTS idx_quest_progress_submitted_status ON quest_progress(status, submitted_at);
