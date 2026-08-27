-- Seed rows for local development. Run after V1__create_tasks_table.sql:
--   psql -d task_api -f src/main/resources/db/R__seed.sql
-- id is left to the identity column so re-seeding never collides with existing rows, and
-- version to its DEFAULT 0 so seeded rows start where a freshly persisted one would.
INSERT INTO tasks (title, details, created_at, done, user_id) VALUES
    ('Write the schema',      'DDL for the tasks table',                now() - interval '3 days', true, '01f114c2-5520-77a8-9226-cbfd1ea7e4b2'),
    ('Wire up JPA repository','Swap InMemoryTaskRepository for the JPA one', now() - interval '2 days', true, '01f114c2-5520-77a8-9226-cbfd1ea7e4b2'),
    ('Add integration tests', 'Cover create, list, update, delete',     now() - interval '1 day',  false,'01f114c2-5520-77a8-9226-cbfd1ea7e4b2'),
    ('Document the API',      NULL,                                     now(),                     false,'01f114c2-5520-77a8-9226-cbfd1ea7e4b2');