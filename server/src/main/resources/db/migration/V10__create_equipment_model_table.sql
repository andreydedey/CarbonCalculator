CREATE TABLE equipment_model (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    name VARCHAR(255) NOT NULL,
    equipment_type VARCHAR(50),
    processor VARCHAR(255),
    tdp_watts INTEGER,
    core_count INTEGER,
    memory_gb INTEGER,
    monitor_name VARCHAR(255),
    monitor_watts INTEGER,
    operating_system VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_equipment_model_institution_id ON equipment_model (institution_id);

ALTER TABLE equipment_model ENABLE ROW LEVEL SECURITY;
ALTER TABLE equipment_model FORCE ROW LEVEL SECURITY;

CREATE POLICY equipment_model_institution_isolation ON equipment_model
    USING (institution_id = current_setting('app.current_institution', true)::uuid);
