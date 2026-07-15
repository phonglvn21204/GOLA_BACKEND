ALTER TYPE sos_status ADD VALUE IF NOT EXISTS 'ACKNOWLEDGED';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'incident_severity') THEN
        CREATE TYPE incident_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH');
    END IF;
END $$;

ALTER TABLE sos_events
    ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS acknowledged_by UUID REFERENCES profiles(id);

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS severity incident_severity NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS verified_count INTEGER NOT NULL DEFAULT 0;
