-- Ownership moves from the user to the project: expand, migrate, contract, as V4 did.
ALTER TABLE tasks
    ADD project_id UUID;

-- Migrate. Every existing task has an owner and no project, and the column it hangs
-- from is about to be dropped, so each owner gets a project here or their rows become
-- unreachable. One project per owner rather than a single shared default: project
-- membership is what grants access from now on, so putting two owners in the same
-- project would hand each of them the other's tasks. The owner leads the project and
-- is inserted as a member of it, which is the shape every later project has.
-- Dates are required by the table, and a one-day window starting today is the only
-- honest answer for a project invented by a migration.
DO $$
DECLARE
    orphan_owner  UUID;
    owner_project UUID;
BEGIN
    FOR orphan_owner IN SELECT DISTINCT owner_id FROM tasks WHERE project_id IS NULL LOOP
        INSERT INTO projects (name, description, leader_id, start_date, due_date)
        VALUES ('Personal', 'Tasks migrated from individual ownership', orphan_owner, CURRENT_DATE, CURRENT_DATE)
        RETURNING id INTO owner_project;

        INSERT INTO projects_users (project_id, user_id)
        VALUES (owner_project, orphan_owner);

        UPDATE tasks SET project_id = owner_project
        WHERE owner_id = orphan_owner AND project_id IS NULL;
    END LOOP;
END $$;

-- Contract.
ALTER TABLE tasks
    DROP COLUMN owner_id;
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_project_id FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

CREATE INDEX idx_tasks_project_id ON tasks (project_id, created_at DESC);
