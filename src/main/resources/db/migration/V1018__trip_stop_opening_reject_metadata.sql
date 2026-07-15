ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS scheduled_open_status VARCHAR(40);

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS place_data_reject_reason TEXT;
