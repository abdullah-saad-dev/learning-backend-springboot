CREATE TABLE projects
(
    id          UUID NOT NULL,
    name        VARCHAR(255),
    description VARCHAR(255),
    status      VARCHAR(255),
    priority    VARCHAR(255),
    leader_id   UUID NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE,
    start_date  date,
    due_date    date,
    version     BIGINT,
    CONSTRAINT pk_projects PRIMARY KEY (id)
);

CREATE TABLE projects_users
(
    project_id UUID NOT NULL,
    user_id    UUID NOT NULL
);

CREATE TABLE refresh_tokens
(
    id                  UUID NOT NULL,
    user_id             UUID,
    token_hash          VARCHAR(255),
    status              VARCHAR(255),
    issued_at           TIMESTAMP WITHOUT TIME ZONE,
    expires_at          TIMESTAMP WITHOUT TIME ZONE,
    absolute_expires_at TIMESTAMP WITHOUT TIME ZONE,
    rotated_at          TIMESTAMP WITHOUT TIME ZONE,
    family_id           UUID,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id)
);

CREATE TABLE tasks
(
    id         UUID NOT NULL,
    title      VARCHAR(255),
    details    VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE,
    project_id UUID NOT NULL,
    done       BOOLEAN,
    version    BIGINT,
    CONSTRAINT pk_tasks PRIMARY KEY (id)
);

CREATE TABLE tasks_users
(
    task_id      UUID NOT NULL,
    assignee_id UUID NOT NULL
);

CREATE TABLE users
(
    id            UUID NOT NULL,
    username      VARCHAR(255),
    email         VARCHAR(255),
    password_hash VARCHAR(255),
    enabled       BOOLEAN,
    role          VARCHAR(255),
    created_at    TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE projects
    ADD CONSTRAINT FK_PROJECTS_ON_LEADER FOREIGN KEY (leader_id) REFERENCES users (id);

ALTER TABLE tasks
    ADD CONSTRAINT FK_TASKS_ON_PROJECT FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE projects_users
    ADD CONSTRAINT fk_prouse_on_project FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE projects_users
    ADD CONSTRAINT fk_prouse_on_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE tasks_users
    ADD CONSTRAINT fk_tasuse_on_task FOREIGN KEY (task_id) REFERENCES tasks (id);

ALTER TABLE tasks_users
    ADD CONSTRAINT fk_tasuse_on_user FOREIGN KEY (assignee_id) REFERENCES users (id);