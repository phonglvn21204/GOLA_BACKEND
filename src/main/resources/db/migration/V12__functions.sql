-- Check if user has a role
CREATE OR REPLACE FUNCTION has_role(_uid UUID, _role app_role)
RETURNS BOOLEAN LANGUAGE sql STABLE AS
$$
  SELECT EXISTS(SELECT 1 FROM user_roles WHERE user_id = _uid AND role = _role)
$$;

-- Check if user has premium subscription
CREATE OR REPLACE FUNCTION is_premium(_uid UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE AS
$$
  SELECT EXISTS(
    SELECT 1 FROM subscriptions
    WHERE user_id = _uid
      AND status = 'ACTIVE'
      AND (current_period_end IS NULL OR current_period_end > NOW())
  )
$$;

-- Check if user is trip member
CREATE OR REPLACE FUNCTION is_trip_member(_uid UUID, _trip_id UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE AS
$$
  SELECT EXISTS(
    SELECT 1 FROM trip_members
    WHERE trip_id = _trip_id AND user_id = _uid
  )
$$;

-- Check if user is trip owner or editor
CREATE OR REPLACE FUNCTION is_trip_editor(_uid UUID, _trip_id UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE AS
$$
  SELECT EXISTS(
    SELECT 1 FROM trip_members
    WHERE trip_id = _trip_id AND user_id = _uid AND role IN ('OWNER','EDITOR')
  )
$$;

-- Increment hashtag counter trigger fn
CREATE OR REPLACE FUNCTION increment_hashtag_count()
RETURNS TRIGGER LANGUAGE plpgsql AS
$$
BEGIN
  INSERT INTO hashtags(tag, post_count, last_used_at)
  VALUES (NEW.tag, 1, NOW())
  ON CONFLICT (tag) DO UPDATE
    SET post_count = hashtags.post_count + 1, last_used_at = NOW();
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_hashtag_count
AFTER INSERT ON post_hashtags
FOR EACH ROW EXECUTE FUNCTION increment_hashtag_count();

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS
$$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_profiles_updated_at
  BEFORE UPDATE ON profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_trips_updated_at
  BEFORE UPDATE ON trips FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_posts_updated_at
  BEFORE UPDATE ON posts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_subscriptions_updated_at
  BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION set_updated_at();