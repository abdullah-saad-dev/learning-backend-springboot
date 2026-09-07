-- no foreign key on actor_id because logs should outlive deleted users
CREATE TABLE audit_logs
(
    id          UUID PRIMARY KEY    DEFAULT UUIDV7(),
    action      TEXT       NOT NULL,
    entity_type TEXT       NOT NULL,
    entity_id   UUID       NOT NULL,
    actor_id    UUID       NOT NULL,
    changes     JSONB      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_entity_created_at ON audit_logs (entity_type, entity_id, created_at DESC);
