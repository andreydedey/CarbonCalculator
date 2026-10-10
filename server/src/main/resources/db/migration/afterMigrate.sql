-- ======================== GRANTS ========================
-- The 'app' role (non-superuser) is used by the application in dev so that
-- RLS policies are enforced. Grant DML on all tables and usage on sequences.
-- In production (Railway) this block is a no-op because there is no 'app' role.
DO $$
BEGIN
  IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'app') THEN
    EXECUTE (
      SELECT string_agg('GRANT SELECT, INSERT, UPDATE, DELETE ON ' || quote_ident(tablename) || ' TO app;', E'\n')
      FROM pg_tables WHERE schemaname = 'public'
    );
    EXECUTE (
      SELECT coalesce(string_agg('GRANT USAGE, SELECT ON SEQUENCE ' || quote_ident(sequencename) || ' TO app;', E'\n'), '')
      FROM pg_sequences WHERE schemaname = 'public'
    );
  END IF;
END $$;
