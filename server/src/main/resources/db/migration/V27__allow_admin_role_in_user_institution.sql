-- Allow ADMIN as a temporary role marker for pending admin invitations.
-- When the invite is accepted, ADMIN is resolved to MANAGER + global admin flag.
ALTER TABLE user_institution DROP CONSTRAINT IF EXISTS user_institution_role_check;
ALTER TABLE user_institution ADD CONSTRAINT user_institution_role_check CHECK (role IN ('ADMIN', 'MANAGER', 'RESEARCHER'));
