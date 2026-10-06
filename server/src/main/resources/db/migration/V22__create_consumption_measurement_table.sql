-- PRD 04: Create consumption_measurement table for wattmeter readings.

CREATE TABLE consumption_measurement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    target_type VARCHAR(20) NOT NULL,
    equipment_model_id UUID REFERENCES equipment_model (id) ON DELETE RESTRICT,
    operating_system_id UUID REFERENCES operating_system (id) ON DELETE RESTRICT,
    monitor_id UUID REFERENCES monitor (id) ON DELETE RESTRICT,
    average_watts NUMERIC(8, 2) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    reading_interval_minutes INTEGER,
    measurement_date DATE NOT NULL,
    conditions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_measurement_target_type CHECK (
        target_type IN ('COMPUTER', 'MONITOR', 'COMBINED')
    ),
    CONSTRAINT chk_measurement_average_watts CHECK (average_watts > 0),
    CONSTRAINT chk_measurement_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_measurement_target_fields CHECK (
        (target_type = 'COMPUTER'
            AND equipment_model_id IS NOT NULL
            AND operating_system_id IS NOT NULL
            AND monitor_id IS NULL)
        OR
        (target_type = 'MONITOR'
            AND monitor_id IS NOT NULL
            AND equipment_model_id IS NULL
            AND operating_system_id IS NULL)
        OR
        (target_type = 'COMBINED'
            AND equipment_model_id IS NOT NULL
            AND operating_system_id IS NOT NULL
            AND monitor_id IS NOT NULL)
    )
);

CREATE INDEX idx_cm_institution_id ON consumption_measurement (institution_id);
CREATE INDEX idx_cm_computer_target ON consumption_measurement (equipment_model_id, operating_system_id)
    WHERE target_type = 'COMPUTER';
CREATE INDEX idx_cm_monitor_target ON consumption_measurement (monitor_id)
    WHERE target_type = 'MONITOR';
CREATE INDEX idx_cm_combined_target ON consumption_measurement (equipment_model_id, operating_system_id, monitor_id)
    WHERE target_type = 'COMBINED';

ALTER TABLE consumption_measurement ENABLE ROW LEVEL SECURITY;
ALTER TABLE consumption_measurement FORCE ROW LEVEL SECURITY;

CREATE POLICY consumption_measurement_institution_isolation ON consumption_measurement
    USING (institution_id = current_setting('app.current_institution', true)::uuid);
