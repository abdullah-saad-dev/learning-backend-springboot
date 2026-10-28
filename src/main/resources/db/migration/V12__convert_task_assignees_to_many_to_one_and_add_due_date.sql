-- Optional deadline. Nullable on purpose: plenty of tasks have none, and a fake default
-- date would be indistinguishable from a real one.
ALTER TABLE tasks
    ADD COLUMN due_date DATE;

-- Convert task assignment from M:N (tasks_users) to N:1 (tasks.assignee_id): a task has
-- at most one assignee, and if that user is deleted the task stays in its project
-- unassigned rather than disappearing with them.
ALTER TABLE tasks
    ADD COLUMN assignee_id UUID;

UPDATE tasks t
SET assignee_id = tu.user_id
FROM (
    SELECT DISTINCT ON (task_id) task_id, user_id
    FROM tasks_users
) tu
WHERE t.id = tu.task_id
  AND t.assignee_id IS NULL;

DROP TABLE tasks_users;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_assignee_id FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_assignee_id ON tasks (assignee_id);
