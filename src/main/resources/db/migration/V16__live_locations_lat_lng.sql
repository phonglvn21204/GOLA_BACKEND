-- Align live_locations with JPA entity (lat/lng columns for application reads/writes).
-- geom is retained for PostGIS queries; new rows should populate both via application or trigger.

ALTER TABLE live_locations
    ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS lng DOUBLE PRECISION;

UPDATE live_locations
SET lat = ST_Y(geom::geometry),
    lng = ST_X(geom::geometry)
WHERE lat IS NULL AND geom IS NOT NULL;
