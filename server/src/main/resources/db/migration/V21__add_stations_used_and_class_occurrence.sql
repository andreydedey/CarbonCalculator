-- ======================== laboratory_schedule.stations_used ========================
-- Stations used by each occupied class, aligned by index with occupied_slots.
-- Existing rows are backfilled with the laboratory capacity (the previous
-- "every station on" assumption).

ALTER TABLE laboratory_schedule ADD COLUMN stations_used SMALLINT[];

UPDATE laboratory_schedule ls
SET stations_used = array_fill(
        GREATEST(1, COALESCE((SELECT SUM(le.quantity)
                              FROM laboratory_equipment le
                              WHERE le.laboratory_id = ls.laboratory_id), 1))::SMALLINT,
        ARRAY[cardinality(ls.occupied_slots)]);

ALTER TABLE laboratory_schedule ALTER COLUMN stations_used SET NOT NULL;

ALTER TABLE laboratory_schedule
    ADD CONSTRAINT chk_schedule_stations_length
        CHECK (cardinality(stations_used) = cardinality(occupied_slots)),
    ADD CONSTRAINT chk_schedule_stations_positive
        CHECK (0 < ALL (stations_used));

-- ======================== class_occurrence ========================
-- Per-date exception to the weekly grid for one class (shift + slot):
--   stations_used = 0            -> class cancelled
--   slot occupied in the grid    -> stations differ from the grid
--   slot free in the grid        -> extra class

CREATE TABLE class_occurrence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    shift_id UUID NOT NULL REFERENCES academic_period_shift (id) ON DELETE CASCADE,
    laboratory_id UUID NOT NULL REFERENCES laboratory (id) ON DELETE CASCADE,
    date DATE NOT NULL,
    slot SMALLINT NOT NULL,
    stations_used SMALLINT NOT NULL,

    CONSTRAINT chk_occurrence_slot CHECK (slot >= 1),
    CONSTRAINT chk_occurrence_stations CHECK (stations_used >= 0),
    CONSTRAINT uq_occurrence_shift_lab_date_slot UNIQUE (shift_id, laboratory_id, date, slot)
);

CREATE INDEX idx_class_occurrence_shift ON class_occurrence (shift_id);
CREATE INDEX idx_class_occurrence_lab_date ON class_occurrence (laboratory_id, date);

ALTER TABLE class_occurrence ENABLE ROW LEVEL SECURITY;
ALTER TABLE class_occurrence FORCE ROW LEVEL SECURITY;

CREATE POLICY class_occurrence_institution_isolation ON class_occurrence
    USING (institution_id = current_setting('app.current_institution', true)::uuid);
