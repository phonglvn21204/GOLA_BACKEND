-- Add password_hash column to profiles table
ALTER TABLE profiles
ADD COLUMN password_hash VARCHAR(255);

-- Create index for potential future lookups
CREATE INDEX idx_profiles_password_hash ON profiles(password_hash) WHERE password_hash IS NOT NULL;

