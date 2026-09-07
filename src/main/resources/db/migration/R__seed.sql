-- DEVELOPMENT SEED. Repeatable, so Flyway re-runs it whenever this file's checksum
-- changes, and it must therefore be safe to run twice.
--
-- WARNING: this file lives in the default Flyway location, so it runs in EVERY
-- environment, production included - and it creates a login. Move it behind a
-- dev-only spring.flyway.locations before this is deployed anywhere real.
--
-- The account below leads the seeded projects. Its id is a literal rather than
-- generated, so re-running this file adopts the same rows instead of orphaning them.
INSERT INTO users (id, username, email, password_hash, role, enabled)
VALUES ('01f114c2-5520-77a8-9226-cbfd1ea7e4b2', 'Abdullah Saad',
        'abdullahsaad@gmail.com',
        '$2a$12$m73wtVgdDN6KNF7m57W7DOYpSzeBQAa9b0K/.H0esHwB2YWydP71a',
        'ADMIN', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Tasks belong to a project, not a user, so a project has to exist before any task
-- can be seeded. Literal ids for the same reason the user has one: ON CONFLICT then
-- makes a second run a no-op instead of appending another pair of projects. status,
-- priority and version are left to their column defaults.
INSERT INTO projects (id, name, description, status, priority, leader_id, start_date, due_date)
VALUES ('01f114c3-1a40-7b21-9c55-2f6d0e83a411', 'Task API',
        'The service these seeded tasks were written against',
        'IN_PROGRESS', 'HIGH', '01f114c2-5520-77a8-9226-cbfd1ea7e4b2',
        CURRENT_DATE - 7, CURRENT_DATE + 30),
       ('01f114c3-1a40-7b21-9c55-2f6d0e83a412', 'Personal',
        'Somewhere to put tasks that belong to no larger effort',
        'PLANNED', 'LOW', '01f114c2-5520-77a8-9226-cbfd1ea7e4b2',
        CURRENT_DATE, CURRENT_DATE + 90)
ON CONFLICT (id) DO NOTHING;

-- Leading a project does not make you a member of it - project_users is the only
-- table the access rules read - so the leader is inserted here as well.
INSERT INTO project_users (project_id, user_id)
VALUES ('01f114c3-1a40-7b21-9c55-2f6d0e83a411', '01f114c2-5520-77a8-9226-cbfd1ea7e4b2'),
       ('01f114c3-1a40-7b21-9c55-2f6d0e83a412', '01f114c2-5520-77a8-9226-cbfd1ea7e4b2')
ON CONFLICT (project_id, user_id) DO NOTHING;

-- id is left to the column default so re-seeding never collides with existing rows,
-- and version to its DEFAULT 0 so seeded rows start where a freshly persisted one
-- would. The NOT EXISTS guard is what makes a second run a no-op: without it every
-- checksum change would append another four copies. It matches on any project this
-- user leads rather than on 'Task API' alone, because a database seeded before V7
-- had these same four tasks moved into the 'Personal' project that migration built,
-- and they must not be seeded a second time here.
INSERT INTO tasks (title, details, created_at, done, project_id)
SELECT v.title, v.details, v.created_at, v.done, '01f114c3-1a40-7b21-9c55-2f6d0e83a411'
FROM (VALUES
    ('Write the schema',       'DDL for the tasks table',                     NOW() - INTERVAL '3 days', TRUE),
    ('Wire up JPA repository', 'Swap InMemoryTaskRepository for the JPA one',  NOW() - INTERVAL '2 days', TRUE),
    ('Add integration tests',  'Cover create, list, update, delete',           NOW() - INTERVAL '1 day',  FALSE),
    ('Document the API',       NULL,                                          NOW(),                     FALSE)
) AS v(title, details, created_at, done)
WHERE NOT EXISTS (
    SELECT 1
    FROM tasks t
    JOIN projects p ON p.id = t.project_id
    WHERE t.title = v.title
      AND p.leader_id = '01f114c2-5520-77a8-9226-cbfd1ea7e4b2'
);
