ALTER TABLE trip_stops
    ALTER COLUMN image_url TYPE TEXT,
    ALTER COLUMN place_address TYPE TEXT,
    ALTER COLUMN notes TYPE TEXT,
    ALTER COLUMN opening_hours_text TYPE TEXT,
    ALTER COLUMN next_open_close_text TYPE TEXT,
    ALTER COLUMN place_data_reject_reason TYPE TEXT,
    ALTER COLUMN provider_id TYPE TEXT,
    ALTER COLUMN provider_title TYPE TEXT;

ALTER TABLE trip_stops
    ALTER COLUMN category TYPE VARCHAR(50),
    ALTER COLUMN business_status TYPE VARCHAR(100),
    ALTER COLUMN scheduled_open_status TYPE VARCHAR(100),
    ALTER COLUMN provider_source TYPE VARCHAR(100),
    ALTER COLUMN data_source TYPE VARCHAR(100),
    ALTER COLUMN image_source TYPE VARCHAR(100),
    ALTER COLUMN enrichment_status TYPE VARCHAR(100);
