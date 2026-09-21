-- Rename role values from Portuguese to English
UPDATE user_institution SET role = 'MANAGER' WHERE role = 'GESTOR';
UPDATE user_institution SET role = 'RESEARCHER' WHERE role = 'PESQUISADOR';

ALTER TABLE user_institution DROP CONSTRAINT IF EXISTS user_institution_role_check;
ALTER TABLE user_institution ADD CONSTRAINT user_institution_role_check CHECK (role IN ('MANAGER', 'RESEARCHER'));
