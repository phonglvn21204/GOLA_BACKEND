ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS thumbnail_urls TEXT[] DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS medium_urls TEXT[] DEFAULT '{}';
