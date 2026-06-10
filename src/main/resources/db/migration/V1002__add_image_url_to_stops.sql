-- Add image_url column to trip_stops table
ALTER TABLE trip_stops ADD COLUMN IF NOT EXISTS image_url TEXT;
