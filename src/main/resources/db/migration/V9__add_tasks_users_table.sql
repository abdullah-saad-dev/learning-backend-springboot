CREATE TABLE tasks_users(
    task_id UUID,
    user_id UUID,
    PRIMARY KEY (task_id, user_id)
);
ALTER TABLE tasks_users
    ADD CONSTRAINT fk_tasks_users_task_id FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_tasks_users_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;