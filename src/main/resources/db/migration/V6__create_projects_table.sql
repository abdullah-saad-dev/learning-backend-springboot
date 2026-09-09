CREATE TABLE projects
(
    id          UUID PRIMARY KEY      DEFAULT UUIDV7(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      TEXT         NOT NULL DEFAULT 'IN_PROGRESS',
    priority    TEXT         NOT NULL DEFAULT 'MEDIUM',
    leader_id   UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    start_date  DATE         NOT NULL,
    due_date    DATE         NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_projects_leader_id FOREIGN KEY (leader_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_projects_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'PLANNED', 'BACKLOG')),
    CONSTRAINT chk_projects_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT start_date_check CHECK (start_date <= due_date)
);
CREATE INDEX idx_projects_leader_id ON projects (leader_id);

CREATE TABLE projects_users
(
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX idx_project_users_user_id ON projects_users (user_id);
