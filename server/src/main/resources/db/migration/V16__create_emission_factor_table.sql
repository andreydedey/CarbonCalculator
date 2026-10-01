-- emission_factor: global table (NO RLS) — stores monthly CO₂ emission factors
-- published by MCTI for the SIN (Sistema Interligado Nacional)

CREATE TABLE emission_factor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year SMALLINT NOT NULL,
    month SMALLINT NOT NULL,
    value NUMERIC(10,6) NOT NULL,
    source VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_emission_factor_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_emission_factor_value CHECK (value > 0),
    CONSTRAINT uq_emission_factor_year_month UNIQUE (year, month)
);

CREATE INDEX idx_emission_factor_year_month ON emission_factor (year, month);
