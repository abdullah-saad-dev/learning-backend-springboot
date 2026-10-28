-- V7 added tasks.project_id as nullable (expand), migrated every row, and then only
-- added the foreign key - the NOT NULL half of the contract step was never applied.
-- Task.java maps the column with nullable = false, but ddl-auto=validate does not check
-- nullability, so the mismatch went unnoticed. A task without a project sits outside
-- every tenant boundary: no membership join can reach it, so it is unreadable and
-- undeletable through the API. None should exist after V7; any that do are unreachable
-- garbage, and are removed rather than invented a home.
DELETE FROM tasks WHERE project_id IS NULL;

ALTER TABLE tasks
    ALTER COLUMN project_id SET NOT NULL;
