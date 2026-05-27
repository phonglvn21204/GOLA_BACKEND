CREATE TABLE emergency_contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    phone       VARCHAR(20) NOT NULL,
    relation    VARCHAR(50),
    priority    INTEGER NOT NULL DEFAULT 1,
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_emergency_contacts_user_id ON emergency_contacts(user_id);

CREATE TABLE emergency_hotlines (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country VARCHAR(10) NOT NULL,
    type    VARCHAR(50) NOT NULL,
    number  VARCHAR(30) NOT NULL
);
INSERT INTO emergency_hotlines (country, type, number) VALUES
  ('VN','Police','113'),('VN','Fire','114'),('VN','Ambulance','115'),
  ('VN','Rescue','1800599944'),('US','Emergency','911'),('UK','Emergency','999');

CREATE TABLE sos_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    trip_id      UUID REFERENCES trips(id),
    geom         geography(Point, 4326),
    status       sos_status NOT NULL DEFAULT 'ACTIVE',
    client_token VARCHAR(100) UNIQUE,
    resolved_at  TIMESTAMPTZ,
    resolved_by  UUID REFERENCES profiles(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sos_events_user_id ON sos_events(user_id);
CREATE INDEX idx_sos_events_status ON sos_events(status);

CREATE TABLE sos_dispatch_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sos_id      UUID NOT NULL REFERENCES sos_events(id) ON DELETE CASCADE,
    channel     dispatch_channel NOT NULL,
    target      VARCHAR(255),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_id TEXT,
    error       TEXT,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sos_dispatch_log_sos_id ON sos_dispatch_log(sos_id);

CREATE TABLE incidents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    trip_id     UUID REFERENCES trips(id),
    type        incident_type NOT NULL DEFAULT 'OTHER',
    description TEXT,
    geom        geography(Point, 4326),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    media_urls  TEXT[] DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_incidents_user_id ON incidents(user_id);
CREATE INDEX idx_incidents_status ON incidents(status);

CREATE TABLE safety_reports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id   UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    target_type   VARCHAR(50) NOT NULL,
    target_id     UUID NOT NULL,
    reason        VARCHAR(100) NOT NULL,
    detail        TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by   UUID REFERENCES profiles(id),
    resolved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);