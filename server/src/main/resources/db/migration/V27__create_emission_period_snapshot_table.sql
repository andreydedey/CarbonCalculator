DELETE FROM emission_snapshot;

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

ALTER TABLE emission_snapshot
    DROP CONSTRAINT emission_snapshot_academic_period_id_fkey,
    ADD CONSTRAINT emission_snapshot_period_snapshot_fkey
        FOREIGN KEY (academic_period_id) REFERENCES emission_period_snapshot (academic_period_id) ON DELETE CASCADE;
