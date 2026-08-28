-- DEVELOPMENT SEED. Repeatable, so Flyway re-runs it whenever this file's checksum
-- changes, and it must therefore be safe to run twice.
--
-- WARNING: this file lives in the default Flyway location, so it runs in EVERY
-- environment, production included - and it creates a login. Move it behind a
-- dev-only spring.flyway.locations before this is deployed anywhere real.
--
-- The account below owns the seeded tasks. Its id is a literal rather than
-- generated, so re-running this file adopts the same rows instead of orphaning them.
INSERT INTO users (id, username, email, password_hash, role, enabled)
VALUES ('01f114c2-5520-77a8-9226-cbfd1ea7e4b2', 'Abdullah Saad',
        'abdullahsaad@gmail.com',
        '$2a$12$m73wtVgdDN6KNF7m57W7DOYpSzeBQAa9b0K/.H0esHwB2YWydP71a',
        'ADMIN', true)
ON CONFLICT (id) DO NOTHING;

-- id is left to the column default so re-seeding never collides with existing rows,
-- and version to its DEFAULT 0 so seeded rows start where a freshly persisted one
-- would. The NOT EXISTS guard is what makes a second run a no-op: without it every
-- checksum change would append another four copies.
INSERT INTO tasks (title, details, created_at, done, owner_id)
SELECT v.title, v.details, v.created_at, v.done, u.id
FROM (VALUES
    ('Write the schema',       'DDL for the tasks table',                     now() - interval '3 days', true),
    ('Wire up JPA repository', 'Swap InMemoryTaskRepository for the JPA one',  now() - interval '2 days', true),
    ('Add integration tests',  'Cover create, list, update, delete',           now() - interval '1 day',  false),
    ('Document the API',       NULL,                                          now(),                     false)
) AS v(title, details, created_at, done)
CROSS JOIN (SELECT id FROM users WHERE id = '01f114c2-5520-77a8-9226-cbfd1ea7e4b2') AS u
WHERE NOT EXISTS (
    SELECT 1 FROM tasks t WHERE t.title = v.title AND t.owner_id = u.id
);
