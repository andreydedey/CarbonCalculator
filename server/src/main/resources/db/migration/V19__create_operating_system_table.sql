-- PRD 04: Create operating_system table and refactor configuration to use FK.

-- 1. Create operating_system table (no timestamps per design decision)
CREATE TABLE operating_system (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_operating_system_name UNIQUE (institution_id, name)
);

CREATE INDEX idx_operating_system_institution_id ON operating_system (institution_id);

ALTER TABLE operating_system ENABLE ROW LEVEL SECURITY;
ALTER TABLE operating_system FORCE ROW LEVEL SECURITY;

CREATE POLICY operating_system_institution_isolation ON operating_system
    USING (institution_id = current_setting('app.current_institution', true)::uuid);

-- 2. Populate operating_system from existing configuration values
INSERT INTO operating_system (institution_id, name)
SELECT DISTINCT c.institution_id, c.operating_system
FROM configuration c
WHERE c.operating_system IS NOT NULL;

-- 3. Add operating_system_id FK column to configuration
ALTER TABLE configuration
    ADD COLUMN operating_system_id UUID REFERENCES operating_system (id) ON DELETE RESTRICT;

CREATE INDEX idx_configuration_operating_system_id ON configuration (operating_system_id);

-- 4. Backfill operating_system_id from operating_system table
UPDATE configuration c
SET operating_system_id = os.id
FROM operating_system os
WHERE os.institution_id = c.institution_id
  AND os.name = c.operating_system;

-- 5. Make operating_system_id NOT NULL
ALTER TABLE configuration
    ALTER COLUMN operating_system_id SET NOT NULL;

-- 6. Drop old operating_system VARCHAR column and check constraint
ALTER TABLE configuration
    DROP CONSTRAINT IF EXISTS chk_operating_system;

ALTER TABLE configuration
    DROP COLUMN operating_system;

-- 7. Update unique constraint to use operating_system_id instead of operating_system
-- (dropping the operating_system column in step 6 already removed the old constraint)
ALTER TABLE configuration
    DROP CONSTRAINT IF EXISTS uq_configuration;

ALTER TABLE configuration
    ADD CONSTRAINT uq_configuration UNIQUE NULLS NOT DISTINCT (institution_id, equipment_model_id, operating_system_id, monitor_id);
