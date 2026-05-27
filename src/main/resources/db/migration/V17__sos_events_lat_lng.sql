-- Align sos_events with JPA entity (latitude/longitude columns).
ALTER TABLE sos_events
    ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

UPDATE sos_events
SET latitude  = ST_Y(geom::geometry),
    longitude = ST_X(geom::geometry)
WHERE latitude IS NULL AND geom IS NOT NULL;
