-- afterMigrate.sql will reseed all data
TRUNCATE emission_factor;

-- Drop old constraints and indexes
ALTER TABLE emission_factor DROP CONSTRAINT IF EXISTS uq_emission_factor_year_month;
ALTER TABLE emission_factor DROP CONSTRAINT IF EXISTS chk_emission_factor_month;
DROP INDEX IF EXISTS idx_emission_factor_year_month;

-- Replace year/month with reference_month DATE (stores first day of month)
ALTER TABLE emission_factor DROP COLUMN year;
ALTER TABLE emission_factor DROP COLUMN month;
ALTER TABLE emission_factor ADD COLUMN reference_month DATE NOT NULL;

-- Add institution scope
ALTER TABLE emission_factor ADD COLUMN institution_id UUID NOT NULL REFERENCES institution(id);

-- New constraints and indexes
ALTER TABLE emission_factor ADD CONSTRAINT uq_emission_factor_institution_month
    UNIQUE (institution_id, reference_month);
CREATE INDEX idx_emission_factor_institution_id ON emission_factor (institution_id);
CREATE INDEX idx_emission_factor_reference_month ON emission_factor (reference_month);

-- RLS
ALTER TABLE emission_factor ENABLE ROW LEVEL SECURITY;
ALTER TABLE emission_factor FORCE ROW LEVEL SECURITY;

CREATE POLICY emission_factor_institution_isolation ON emission_factor
    USING (institution_id = current_setting('app.current_institution', true)::uuid);
