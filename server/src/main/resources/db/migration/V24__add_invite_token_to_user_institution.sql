ALTER TABLE user_institution
    ADD COLUMN invite_token_hash VARCHAR(64),
    ADD COLUMN invite_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_user_institution_invite_token_hash
    ON user_institution (invite_token_hash)
    WHERE invite_token_hash IS NOT NULL;
