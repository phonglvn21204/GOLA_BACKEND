DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'review_status') THEN
    CREATE TYPE review_status AS ENUM ('VISIBLE', 'HIDDEN', 'PENDING');
  END IF;
END $$;

ALTER TABLE reviews
  ALTER COLUMN place_id DROP NOT NULL,
  ADD COLUMN IF NOT EXISTS trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS stop_id UUID REFERENCES trip_stops(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS place_name TEXT,
  ADD COLUMN IF NOT EXISTS status review_status NOT NULL DEFAULT 'VISIBLE',
  ADD COLUMN IF NOT EXISTS report_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS hidden_by UUID REFERENCES profiles(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS hide_reason TEXT;

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_place_id_user_id_key;

UPDATE reviews
SET status = CASE WHEN is_hidden THEN 'HIDDEN'::review_status ELSE 'VISIBLE'::review_status END,
    place_name = COALESCE(place_name, (SELECT p.name FROM places p WHERE p.id = reviews.place_id)),
    updated_at = COALESCE(updated_at, created_at, NOW());

CREATE INDEX IF NOT EXISTS idx_reviews_trip_id ON reviews(trip_id);
CREATE INDEX IF NOT EXISTS idx_reviews_stop_id ON reviews(stop_id);
CREATE INDEX IF NOT EXISTS idx_reviews_status ON reviews(status);
CREATE INDEX IF NOT EXISTS idx_reviews_created_at ON reviews(created_at DESC);
