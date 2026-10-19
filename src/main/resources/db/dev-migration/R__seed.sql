-- DEVELOPMENT SEED. Repeatable, so Flyway re-runs it whenever this file's checksum
-- changes, and it must therefore be safe to run twice.
--
-- This file lives in db/dev-migration, which only application-dev.properties adds to
-- spring.flyway.locations. It creates logins with a committed password hash, so it must
-- never be on the classpath scan of a production profile.
--
-- The account below leads the seeded projects. Its id is a literal rather than
-- generated, so re-running this file adopts the same rows instead of orphaning them.
INSERT INTO users (id, username, email, password_hash, role, enabled)
VALUES ('01f114c2-5520-77a8-9226-cbfd1ea7e4b2', 'Abdullah Saad',
        'abdullahsaad@gmail.com',
        '$2a$12$SZy.77vRu0c7TRwTyely8uIpkFb5IBgYDDREENL74YjK7U4VbWO.q',
        'ADMIN', TRUE),
       ('01f114c2-5520-77a8-9226-cbfd1ea7e4b3', 'Alice Smith',
        'alice@example.com',
        '$2a$12$SZy.77vRu0c7TRwTyely8uIpkFb5IBgYDDREENL74YjK7U4VbWO.q',
        'USER', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Tasks belong to a project, not a user, so a project has to exist before any task
-- can be seeded. Literal ids for the same reason the user has one: ON CONFLICT then
-- makes a second run a no-op instead of appending another pair of projects. status,
-- priority and version are left to their column defaults.
INSERT INTO projects (id, name, description, status, priority, start_date, due_date)
VALUES ('01f114c3-1a40-7b21-9c55-2f6d0e83a411', 'Task API',
        'The service these seeded tasks were written against',
        'IN_PROGRESS', 'HIGH',
        CURRENT_DATE - 7, CURRENT_DATE + 30),
       ('01f114c3-1a40-7b21-9c55-2f6d0e83a412', 'Personal',
        'Somewhere to put tasks that belong to no larger effort',
        'PLANNED', 'LOW',
        CURRENT_DATE, CURRENT_DATE + 90),
       ('01f114c3-1a40-7b21-9c55-2f6d0e83a413', 'Secret Internal Audit',
        'Confidential audit project that Abdullah Saad is not permitted to see',
        'IN_PROGRESS', 'HIGH',
        CURRENT_DATE - 5, CURRENT_DATE + 30)
ON CONFLICT (id) DO NOTHING;

-- Project membership and roles (MANAGER, MEMBER) are stored in the projects_users join table.
INSERT INTO projects_users (project_id, user_id, role)
VALUES ('01f114c3-1a40-7b21-9c55-2f6d0e83a411', '01f114c2-5520-77a8-9226-cbfd1ea7e4b2', 'MANAGER'),
       ('01f114c3-1a40-7b21-9c55-2f6d0e83a412', '01f114c2-5520-77a8-9226-cbfd1ea7e4b2', 'MEMBER'),
       ('01f114c3-1a40-7b21-9c55-2f6d0e83a413', '01f114c2-5520-77a8-9226-cbfd1ea7e4b3', 'MANAGER')
ON CONFLICT (project_id, user_id) DO UPDATE SET role = EXCLUDED.role;

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
    JOIN projects_users pu ON p.id = pu.project_id
    WHERE t.title = v.title
      AND pu.user_id = '01f114c2-5520-77a8-9226-cbfd1ea7e4b2'
);

INSERT INTO tasks (title, details, created_at, done, project_id)
SELECT v.title, v.details, v.created_at, v.done, '01f114c3-1a40-7b21-9c55-2f6d0e83a413'
FROM (VALUES
    ('Conduct penetration test',    'Test perimeter security and API defenses', NOW() - INTERVAL '2 days', FALSE),
    ('Review SOC2 compliance',      'Verify access controls and data isolation', NOW() - INTERVAL '1 day',  TRUE),
    ('Rotate cloud provider keys',  'Regenerate root credentials',               NOW(),                     FALSE)
) AS v(title, details, created_at, done)
WHERE NOT EXISTS (
    SELECT 1
    FROM tasks t
    WHERE t.title = v.title
      AND t.project_id = '01f114c3-1a40-7b21-9c55-2f6d0e83a413'
);

-- Deadlines and assignees (V12 / tasks_users). Both are applied as follow-up statements
-- rather than in the inserts above so that a database seeded before V12 - whose tasks
-- already exist and are skipped by the NOT EXISTS guards - gets them too. due_date is
-- only filled where still NULL, so a date changed through the API is never overwritten.
UPDATE tasks t
SET due_date = v.due_date
FROM (VALUES
    ('01f114c3-1a40-7b21-9c55-2f6d0e83a411'::uuid, 'Add integration tests',      CURRENT_DATE + 3),
    ('01f114c3-1a40-7b21-9c55-2f6d0e83a411'::uuid, 'Document the API',           CURRENT_DATE + 10),
    ('01f114c3-1a40-7b21-9c55-2f6d0e83a413'::uuid, 'Conduct penetration test',   CURRENT_DATE + 5),
    ('01f114c3-1a40-7b21-9c55-2f6d0e83a413'::uuid, 'Rotate cloud provider keys', CURRENT_DATE + 1)
) AS v(project_id, title, due_date)
WHERE t.project_id = v.project_id
  AND t.title = v.title
  AND t.due_date IS NULL;

-- Each project's tasks go to a member of that project: an assignee outside the project
-- would be able to see the task through the assignment but not the project it lives in.
INSERT INTO tasks_users (task_id, user_id)
SELECT t.id, v.user_id
FROM (VALUES
    ('01f114c3-1a40-7b21-9c55-2f6d0e83a411'::uuid, '01f114c2-5520-77a8-9226-cbfd1ea7e4b2'::uuid),
    ('01f114c3-1a40-7b21-9c55-2f6d0e83a413'::uuid, '01f114c2-5520-77a8-9226-cbfd1ea7e4b3'::uuid)
) AS v(project_id, user_id)
JOIN tasks t ON t.project_id = v.project_id
ON CONFLICT (task_id, user_id) DO NOTHING;
