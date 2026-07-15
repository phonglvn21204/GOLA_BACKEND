ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(12, 2);
