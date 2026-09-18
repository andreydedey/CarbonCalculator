ALTER TABLE laboratory ENABLE ROW LEVEL SECURITY;
ALTER TABLE laboratory FORCE ROW LEVEL SECURITY;

CREATE POLICY laboratory_institution_isolation ON laboratory
    USING (institution_id = current_setting('app.current_institution', true)::uuid);
