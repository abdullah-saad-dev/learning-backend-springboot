-- tasks.id was an integer identity column; the entity now declares a UUID v7.
--
-- Nothing has a foreign key to tasks.id, so this is a straight column replacement
-- rather than the multi-release dance a referenced primary key would force. If any
-- table ever does point here, this migration is no longer the right shape.
--
-- The default matters twice over. uuidv7() is volatile, so ADD COLUMN evaluates it
-- once per existing row and each keeps a distinct id - a non-volatile default would
-- hand every row the same value and the primary key below would fail. It also stays
-- in place afterwards for inserts that do not name an id, which is how R__seed.sql
-- writes; application writes get theirs from Hibernate's @UuidGenerator instead.
ALTER TABLE tasks ADD COLUMN id_uuid uuid NOT NULL DEFAULT uuidv7();


ALTER TABLE tasks DROP COLUMN id CASCADE;
ALTER TABLE tasks RENAME COLUMN id_uuid TO id;
ALTER TABLE tasks ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);
