CREATE TABLE posts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    body       TEXT,
    media_urls TEXT[] DEFAULT '{}',
    trip_id    UUID REFERENCES trips(id),
    is_hidden  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_posts_author_id ON posts(author_id);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX idx_posts_trip_id ON posts(trip_id);

CREATE TABLE hashtags (
    tag          VARCHAR(100) PRIMARY KEY,
    post_count   INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMPTZ
);

CREATE TABLE post_hashtags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag     VARCHAR(100) NOT NULL REFERENCES hashtags(tag) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag)
);
CREATE INDEX idx_post_hashtags_tag ON post_hashtags(tag);

CREATE TABLE comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    body       TEXT NOT NULL,
    is_hidden  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_comments_post_id ON comments(post_id);

CREATE TABLE reactions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    kind       VARCHAR(20) NOT NULL DEFAULT 'LIKE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, user_id, kind)
);
CREATE INDEX idx_reactions_post_id ON reactions(post_id);

CREATE TABLE trip_stories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id        UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    author_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    body           TEXT,
    cover_url      TEXT,
    published_at   TIMESTAMPTZ,
    view_count     INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE albums (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    trip_id       UUID REFERENCES trips(id),
    title         VARCHAR(255),
    cover_url     TEXT,
    is_public     BOOLEAN NOT NULL DEFAULT FALSE,
    is_ai_curated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE album_media (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id  UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    caption   TEXT,
    order_idx INTEGER NOT NULL DEFAULT 0,
    metadata  JSONB
);
CREATE INDEX idx_album_media_album_id ON album_media(album_id);