-- Enable btree_gist for EXCLUDE constraint on date ranges
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ======================== academic_period ========================

CREATE TABLE academic_period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_academic_period_dates CHECK (end_date > start_date),
    CONSTRAINT excl_academic_period_overlap
        EXCLUDE USING gist (
            institution_id WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        )
);

CREATE INDEX idx_academic_period_institution_id ON academic_period (institution_id);

-- RLS
ALTER TABLE academic_period ENABLE ROW LEVEL SECURITY;
ALTER TABLE academic_period FORCE ROW LEVEL SECURITY;

CREATE POLICY academic_period_institution_isolation ON academic_period
    USING (institution_id = current_setting('app.current_institution', true)::uuid);

-- ======================== academic_period_holiday ========================

CREATE TABLE academic_period_holiday (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    academic_period_id UUID NOT NULL REFERENCES academic_period (id) ON DELETE CASCADE,
    date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,

    CONSTRAINT chk_holiday_type CHECK (type IN ('NATIONAL', 'STATE', 'MUNICIPAL', 'RECESS')),
    CONSTRAINT uq_holiday_period_date UNIQUE (academic_period_id, date)
);

-- ======================== academic_period_shift ========================

CREATE TABLE academic_period_shift (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    academic_period_id UUID NOT NULL REFERENCES academic_period (id) ON DELETE CASCADE,
    shift_type VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    classes_per_day SMALLINT NOT NULL,
    class_duration_minutes SMALLINT NOT NULL,
    break_duration_minutes SMALLINT NOT NULL,
    active_days SMALLINT[] NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_shift_type CHECK (shift_type IN ('MORNING', 'AFTERNOON', 'EVENING')),
    CONSTRAINT chk_shift_times CHECK (end_time > start_time),
    CONSTRAINT chk_shift_classes_per_day CHECK (classes_per_day > 0),
    CONSTRAINT chk_shift_class_duration CHECK (class_duration_minutes > 0),
    CONSTRAINT chk_shift_break_duration CHECK (break_duration_minutes >= 0),
    CONSTRAINT uq_shift_period_type UNIQUE (academic_period_id, shift_type)
);

CREATE INDEX idx_academic_period_shift_period ON academic_period_shift (academic_period_id);

-- ======================== laboratory_schedule ========================

CREATE TABLE laboratory_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id UUID NOT NULL REFERENCES academic_period_shift (id) ON DELETE CASCADE,
    laboratory_id UUID NOT NULL REFERENCES laboratory (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    occupied_slots SMALLINT[] NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_schedule_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT uq_schedule_shift_lab_day UNIQUE (shift_id, laboratory_id, day_of_week)
);

CREATE INDEX idx_laboratory_schedule_shift_lab ON laboratory_schedule (shift_id, laboratory_id);
