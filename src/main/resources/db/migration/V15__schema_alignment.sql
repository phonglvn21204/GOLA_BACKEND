-- =============================================================================
-- V15__schema_alignment.sql
-- Aligns the PostgreSQL schema with all JPA entity definitions so that
-- Hibernate can run in ddl-auto: validate mode without errors.
-- =============================================================================


-- =============================================================================
-- 1. badges  (extends BaseEntity → needs id✓  created_at✓  updated_at✗)
--    JPA also maps: criteria TEXT, is_active BOOLEAN
-- =============================================================================
ALTER TABLE badges
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS criteria   TEXT,
    ADD COLUMN IF NOT EXISTS is_active  BOOLEAN NOT NULL DEFAULT TRUE;


-- =============================================================================
-- 2. comments  (extends BaseEntity → needs id✓  created_at✓  updated_at✗)
--    JPA also maps: parent_id UUID (self-referential)
-- =============================================================================
ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS parent_id  UUID REFERENCES comments(id) ON DELETE SET NULL;


-- =============================================================================
-- 3. device_tokens  (extends BaseEntity → needs id✓  created_at✓  updated_at✗)
--    JPA maps last_used_at; DB has last_seen – add the JPA column name
-- =============================================================================
ALTER TABLE device_tokens
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMPTZ;


-- =============================================================================
-- 4. notification_preferences  (extends BaseEntity → needs id UUID PK)
--    DB currently has a composite PK (user_id, channel, type); no id column.
--    JPA also maps is_enabled (DB has "enabled").
-- =============================================================================
-- 4a. Drop the composite primary-key constraint
ALTER TABLE notification_preferences
    DROP CONSTRAINT IF EXISTS notification_preferences_pkey;

-- 4b. Add the missing columns
ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS id         UUID DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS is_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 4c. Back-fill id for any pre-existing rows and set the new primary key
UPDATE notification_preferences SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE notification_preferences ALTER COLUMN id SET NOT NULL;
ALTER TABLE notification_preferences ADD PRIMARY KEY (id);


-- =============================================================================
-- 5. quest_progress  (extends BaseEntity → needs id✓  created_at✗  updated_at✗)
--    JPA also maps: proof_media_id UUID
-- =============================================================================
ALTER TABLE quest_progress
    ADD COLUMN IF NOT EXISTS created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proof_media_id UUID;


-- =============================================================================
-- 6. quest_tasks  (extends BaseEntity → needs id✓  created_at✗  updated_at✗)
-- =============================================================================
ALTER TABLE quest_tasks
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;


-- =============================================================================
-- 7. redemptions  (extends BaseEntity → needs id✓  created_at✗  updated_at✗)
-- =============================================================================
ALTER TABLE redemptions
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;


-- =============================================================================
-- 8. rewards  (extends BaseEntity → needs id✓  created_at✓  updated_at✗)
-- =============================================================================
ALTER TABLE rewards
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;


-- =============================================================================
-- 9. trip_invitations  (extends BaseEntity → needs id✓  created_at✓  updated_at✗)
-- =============================================================================
ALTER TABLE trip_invitations
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;


-- =============================================================================
-- 10. user_preferences  (extends BaseEntity → needs id UUID PK, created_at)
--     DB currently has user_id UUID PRIMARY KEY with no id column.
--     JPA uses @ElementCollection for travelStyles and interests (separate tables).
-- =============================================================================
-- 10a. Drop the existing user_id primary-key constraint
ALTER TABLE user_preferences
    DROP CONSTRAINT IF EXISTS user_preferences_pkey;

-- 10b. Add missing columns
ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS id         UUID DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 10c. Back-fill id and promote to primary key
UPDATE user_preferences SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE user_preferences ALTER COLUMN id SET NOT NULL;
ALTER TABLE user_preferences ADD PRIMARY KEY (id);

-- 10d. Element collection tables for UserPreferences.travelStyles and .interests
CREATE TABLE IF NOT EXISTS user_pref_travel_styles (
    user_preferences_id UUID        NOT NULL REFERENCES user_preferences(id) ON DELETE CASCADE,
    style               VARCHAR(100) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_upref_travel_styles ON user_pref_travel_styles(user_preferences_id);

CREATE TABLE IF NOT EXISTS user_pref_interests (
    user_preferences_id UUID        NOT NULL REFERENCES user_preferences(id) ON DELETE CASCADE,
    interest            VARCHAR(100) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_upref_interests ON user_pref_interests(user_preferences_id);


-- =============================================================================
-- 11. incidents  – JPA entity maps latitude / longitude (DOUBLE PRECISION)
--     DB has geom (geography) but not latitude/longitude columns.
-- =============================================================================
ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;


-- =============================================================================
-- 12. CREATE TABLE reports
--     Mapped by Report entity to table "reports".
--     (DB only has safety_reports – a different table for safety flags.)
-- =============================================================================
CREATE TABLE IF NOT EXISTS reports (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID        NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id   UUID        NOT NULL,
    reason      TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_id ON reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_reports_target      ON reports(target_type, target_id);


-- =============================================================================
-- 13. CREATE TABLE expenses
--     Mapped by Expense entity. No existing table found in migrations.
-- =============================================================================
CREATE TABLE IF NOT EXISTS expenses (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id     UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    payer_id    UUID           NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'USD',
    description TEXT           NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_expense_trip ON expenses(trip_id);


-- =============================================================================
-- 14. CREATE TABLE trip_notes
--     Mapped by TripNote entity. No existing table found in migrations.
-- =============================================================================
CREATE TABLE IF NOT EXISTS trip_notes (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id    UUID          NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL,
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_trip_note_trip ON trip_notes(trip_id);
