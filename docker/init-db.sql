-- Create a non-superuser role for the application.
-- RLS policies are bypassed for superusers, so the app MUST connect
-- with this role for tenant isolation to work.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app') THEN
    CREATE ROLE app LOGIN PASSWORD 'app';
  END IF;
END
$$;

GRANT CONNECT ON DATABASE carboncalculator TO app;

-- Permissions are granted on existing objects; Flyway migrations also
-- call afterMigrate.sql which re-grants on every new table.
GRANT USAGE ON SCHEMA public TO app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app;
