-- Remove created_at/updated_at das tabelas em que os campos de auditoria não agregam valor.
-- Mantidos: institution, app_user, user_institution.

ALTER TABLE laboratory DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE equipment_model DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE monitor DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE configuration DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE laboratory_equipment DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE academic_period DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE academic_period_shift DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE laboratory_schedule DROP COLUMN created_at;
ALTER TABLE emission_factor DROP COLUMN created_at, DROP COLUMN updated_at;
