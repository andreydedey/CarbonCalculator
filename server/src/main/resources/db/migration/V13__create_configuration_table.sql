-- Introduce configuration as a reusable institution-level entity.
-- A configuration = equipment_model + operating_system + monitor.
-- laboratory_equipment now references configuration instead of individual fields.

-- 1. Create configuration table
CREATE TABLE configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    equipment_model_id UUID NOT NULL REFERENCES equipment_model (id) ON DELETE RESTRICT,
    operating_system VARCHAR(100) NOT NULL,
    monitor_id UUID REFERENCES monitor (id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_configuration UNIQUE NULLS NOT DISTINCT (institution_id, equipment_model_id, operating_system, monitor_id)
);

CREATE INDEX idx_configuration_institution_id ON configuration (institution_id);
CREATE INDEX idx_configuration_equipment_model_id ON configuration (equipment_model_id);
CREATE INDEX idx_configuration_monitor_id ON configuration (monitor_id);

ALTER TABLE configuration ENABLE ROW LEVEL SECURITY;
ALTER TABLE configuration FORCE ROW LEVEL SECURITY;

CREATE POLICY configuration_institution_isolation ON configuration
    USING (institution_id = current_setting('app.current_institution', true)::uuid);

-- 2. Populate configurations from existing laboratory_equipment
INSERT INTO configuration (institution_id, equipment_model_id, operating_system, monitor_id)
SELECT DISTINCT em.institution_id, le.equipment_model_id, le.operating_system, le.monitor_id
FROM laboratory_equipment le
JOIN equipment_model em ON em.id = le.equipment_model_id;

-- 3. Add configuration_id column to laboratory_equipment
ALTER TABLE laboratory_equipment
    ADD COLUMN configuration_id UUID REFERENCES configuration (id) ON DELETE RESTRICT;

CREATE INDEX idx_laboratory_equipment_configuration_id ON laboratory_equipment (configuration_id);

-- 4. Populate configuration_id from matching configurations
UPDATE laboratory_equipment le
SET configuration_id = c.id
FROM configuration c
WHERE c.equipment_model_id = le.equipment_model_id
  AND c.operating_system = le.operating_system
  AND (c.monitor_id IS NOT DISTINCT FROM le.monitor_id);

-- 5. Make configuration_id NOT NULL now that all rows are populated
ALTER TABLE laboratory_equipment
    ALTER COLUMN configuration_id SET NOT NULL;

-- 6. Drop old columns and constraint
ALTER TABLE laboratory_equipment
    DROP CONSTRAINT uq_lab_equipment_config;

ALTER TABLE laboratory_equipment
    DROP COLUMN equipment_model_id,
    DROP COLUMN operating_system,
    DROP COLUMN monitor_id;

-- 7. New unique constraint: one configuration per lab
ALTER TABLE laboratory_equipment
    ADD CONSTRAINT uq_lab_configuration UNIQUE (laboratory_id, configuration_id);
