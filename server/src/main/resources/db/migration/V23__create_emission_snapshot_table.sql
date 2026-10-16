CREATE TABLE emission_period_snapshot (
    academic_period_id UUID PRIMARY KEY REFERENCES academic_period (id) ON DELETE CASCADE,
    institution_id UUID NOT NULL REFERENCES institution (id),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_emission_period_snapshot_institution_id ON emission_period_snapshot (institution_id);

ALTER TABLE emission_period_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE emission_period_snapshot FORCE ROW LEVEL SECURITY;

CREATE POLICY emission_period_snapshot_institution_isolation ON emission_period_snapshot
    USING (institution_id = current_setting('app.current_institution', true)::uuid);

CREATE TABLE emission_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    academic_period_id UUID NOT NULL
        REFERENCES emission_period_snapshot (academic_period_id) ON DELETE CASCADE,
    laboratory_id UUID REFERENCES laboratory (id) ON DELETE SET NULL,
    snapshot_date DATE NOT NULL,
    day_of_week SMALLINT NOT NULL,
    daily_energy_kwh NUMERIC(12,4) NOT NULL,
    daily_emission_kg NUMERIC(12,4) NOT NULL,
    emission_factor_value NUMERIC(10,6) NOT NULL,
    station_count INTEGER NOT NULL DEFAULT 0,
    is_school_day BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_emission_snapshot_energy CHECK (daily_energy_kwh >= 0),
    CONSTRAINT chk_emission_snapshot_emission CHECK (daily_emission_kg >= 0),
    CONSTRAINT chk_emission_snapshot_factor CHECK (emission_factor_value > 0),
    CONSTRAINT uq_emission_snapshot_period_date_laboratory
        UNIQUE (academic_period_id, snapshot_date, laboratory_id)
);

CREATE INDEX idx_emission_snapshot_institution_id ON emission_snapshot (institution_id);
CREATE INDEX idx_emission_snapshot_date ON emission_snapshot (snapshot_date);
CREATE INDEX idx_emission_snapshot_period ON emission_snapshot (academic_period_id);
CREATE INDEX idx_emission_snapshot_laboratory ON emission_snapshot (laboratory_id);

ALTER TABLE emission_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE emission_snapshot FORCE ROW LEVEL SECURITY;

CREATE POLICY emission_snapshot_institution_isolation ON emission_snapshot
    USING (institution_id = current_setting('app.current_institution', true)::uuid);
