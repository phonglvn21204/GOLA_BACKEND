-- Quest Map admin/user flow compatibility.
-- Adds fields and enum values needed by trip-scoped quests without removing
-- legacy SOLO/TEAM/COMMUNITY quest templates or old progress rows.

ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'PHOTO_CHECKIN';
ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'GPS_CHECKIN';
ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'FOOD';
ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'SAFETY';
ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'REWARD';
ALTER TYPE quest_type ADD VALUE IF NOT EXISTS 'MINI_CHALLENGE';

ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'LOCKED';
ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'AVAILABLE';
ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'NOT_STARTED';
ALTER TYPE progress_status ADD VALUE IF NOT EXISTS 'FLAGGED';

ALTER TYPE proof_type ADD VALUE IF NOT EXISTS 'QR_CODE';

ALTER TABLE quests
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(20) NOT NULL DEFAULT 'EASY',
    ADD COLUMN IF NOT EXISTS icon_key VARCHAR(80),
    ADD COLUMN IF NOT EXISTS reward_id UUID REFERENCES rewards(id);

ALTER TABLE quest_progress
    DROP CONSTRAINT IF EXISTS quest_progress_quest_id_user_id_key;

ALTER TABLE quest_progress
    ADD COLUMN IF NOT EXISTS trip_id UUID,
    ADD COLUMN IF NOT EXISTS trip_stop_id UUID,
    ADD COLUMN IF NOT EXISTS distance_meters_from_target DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS xp_awarded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS flag_reason TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_by UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_quest_progress_trip'
    ) THEN
        ALTER TABLE quest_progress
            ADD CONSTRAINT fk_quest_progress_trip
            FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_quest_progress_trip_stop'
    ) THEN
        ALTER TABLE quest_progress
            ADD CONSTRAINT fk_quest_progress_trip_stop
            FOREIGN KEY (trip_stop_id) REFERENCES trip_stops(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_quest_progress_reviewed_by'
    ) THEN
        ALTER TABLE quest_progress
            ADD CONSTRAINT fk_quest_progress_reviewed_by
            FOREIGN KEY (reviewed_by) REFERENCES profiles(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_quests_type_active ON quests(type, is_active);
CREATE INDEX IF NOT EXISTS idx_quest_progress_trip_user ON quest_progress(trip_id, user_id);
CREATE INDEX IF NOT EXISTS idx_quest_progress_trip_stop ON quest_progress(trip_stop_id);
CREATE INDEX IF NOT EXISTS idx_quest_progress_status ON quest_progress(status);
CREATE UNIQUE INDEX IF NOT EXISTS ux_quest_progress_user_trip_stop_quest
    ON quest_progress(user_id, trip_id, trip_stop_id, quest_id)
    WHERE trip_id IS NOT NULL AND trip_stop_id IS NOT NULL;
