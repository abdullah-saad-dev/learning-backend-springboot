--expand fill contract pattern
ALTER TABLE tasks ADD COLUMN owner_id UUID;

INSERT INTO users (id ,username, email, password_hash, role, enabled) VALUES
    ('01f114c2-5520-77a8-9226-cbfd1ea7e4b2','Abdullah Saad',
     'abdullahsaad@gmail.com',
     '$2a$12$m73wtVgdDN6KNF7m57W7DOYpSzeBQAa9b0K/.H0esHwB2YWydP71a',
     'ADMIN', true);

UPDATE tasks
SET owner_id = (SELECT id FROM users WHERE username = 'Abdullah Saad');

ALTER TABLE tasks
ALTER COLUMN owner_id SET NOT NULL,
ADD CONSTRAINT fk_owner_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ADD INDEX idx_owner_id ON tasks (owner_id, created_at DESC);