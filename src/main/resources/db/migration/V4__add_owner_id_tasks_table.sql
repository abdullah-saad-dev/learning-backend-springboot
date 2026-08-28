-- Give every task an owner: expand, migrate, contract.
--
-- Expand. The column arrives nullable so rows written before this migration stay
-- valid, and so a running instance of the previous version can keep inserting.
ALTER TABLE tasks ADD COLUMN owner_id uuid;

-- Migrate. Rows that predate ownership need an owner before the column can be made
-- NOT NULL, and there is no way to know who wrote them, so they are adopted by a
-- placeholder account. That account cannot be logged into: enabled = false stops
-- AppUserDetails.isEnabled(), and 'x' is not a valid bcrypt digest, so no password
-- can ever match it. The whole block is skipped on a database with no orphan rows,
-- which is every fresh one - CI included - so nothing junk is created there.
DO $$
DECLARE
    placeholder_owner uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM tasks WHERE owner_id IS NULL) THEN
        INSERT INTO users (username, email, password_hash, role, enabled)
        VALUES ('unclaimed', 'unclaimed@invalid', 'x', 'USER', false)
        RETURNING id INTO placeholder_owner;

        UPDATE tasks SET owner_id = placeholder_owner WHERE owner_id IS NULL;
    END IF;
END $$;

-- Contract.
ALTER TABLE tasks
    ALTER COLUMN owner_id SET NOT NULL,
    ADD CONSTRAINT fk_tasks_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;

-- Every read is now scoped to one owner, and the list endpoint orders by created_at
-- descending, so the index leads with owner_id and carries the sort order with it.
CREATE INDEX idx_tasks_owner_id_created_at_desc ON tasks (owner_id, created_at DESC);
