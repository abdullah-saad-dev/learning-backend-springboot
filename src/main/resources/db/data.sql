-- Seed rows for local development. Run after schema.sql:
--   psql -d task_api -f src/main/resources/db/data.sql
-- id is left to the identity column so re-seeding never collides with existing rows, and
-- version to its DEFAULT 0 so seeded rows start where a freshly persisted one would.

BEGIN;

TRUNCATE TABLE tasks RESTART IDENTITY;

INSERT INTO tasks (title, details, created_at, done) VALUES
    ('Write the schema',      'DDL for the tasks table',                now() - interval '3 days', true),
    ('Wire up JPA repository','Swap InMemoryTaskRepository for the JPA one', now() - interval '2 days', true),
    ('Add integration tests', 'Cover create, list, update, delete',     now() - interval '1 day',  false),
    ('Document the API',      NULL,                                     now(),                     false);

COMMIT;
