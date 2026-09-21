-- Allow pending invitations for users who haven't registered yet
ALTER TABLE user_institution ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE user_institution ADD COLUMN user_email VARCHAR(255);

-- Drop the unique index on (user_id, institution_id) since user_id can be null
DROP INDEX idx_user_institution_unique;

-- Create a unique index that handles both cases:
-- when user exists, unique on (user_id, institution_id)
-- when pending, unique on (user_email, institution_id)
CREATE UNIQUE INDEX idx_user_institution_user_unique
    ON user_institution (user_id, institution_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX idx_user_institution_email_unique
    ON user_institution (user_email, institution_id) WHERE user_email IS NOT NULL;
