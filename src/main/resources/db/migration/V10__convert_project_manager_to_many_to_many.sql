-- Migration: Convert project manager 1:1 foreign key relationship to M:N relationship with role in projects_users join table

-- 1. Add role column to projects_users with default 'MEMBER' and role constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects_users' AND column_name = 'role'
    ) THEN
        ALTER TABLE projects_users
            ADD COLUMN role TEXT NOT NULL DEFAULT 'MEMBER';

        ALTER TABLE projects_users
            ADD CONSTRAINT chk_projects_users_role CHECK (role IN ('MEMBER', 'MANAGER'));
    END IF;
END $$;

-- 2. Migrate existing project leaders to projects_users as MANAGER
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects' AND column_name = 'leader_id'
    ) THEN
        -- If the leader already has an entry in projects_users, upgrade their role to MANAGER
        UPDATE projects_users pu
        SET role = 'MANAGER'
        FROM projects p
        WHERE pu.project_id = p.id AND pu.user_id = p.leader_id;

        -- If the leader is not yet in projects_users, insert them with role MANAGER
        INSERT INTO projects_users (project_id, user_id, role)
        SELECT p.id, p.leader_id, 'MANAGER'
        FROM projects p
        WHERE p.leader_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM projects_users pu
              WHERE pu.project_id = p.id AND pu.user_id = p.leader_id
          );

        -- 3. Drop foreign key constraint, index, and column leader_id from projects
        ALTER TABLE projects
            DROP CONSTRAINT IF EXISTS fk_projects_leader_id;

        DROP INDEX IF EXISTS idx_projects_leader_id;

        ALTER TABLE projects
            DROP COLUMN leader_id;
    END IF;
END $$;
