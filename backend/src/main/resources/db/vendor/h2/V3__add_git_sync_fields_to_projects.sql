-- V3 (H2 variant for tests): H2 does not accept MySQL's comma-separated ADD COLUMN list.
-- Keep in sync with db/vendor/mysql/V3__add_git_sync_fields_to_projects.sql
ALTER TABLE projects ADD COLUMN repo_identifier VARCHAR(255) NULL;
ALTER TABLE projects ADD COLUMN default_branch VARCHAR(100) NOT NULL DEFAULT 'main';
ALTER TABLE projects ADD COLUMN git_sync_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED';
ALTER TABLE projects ADD COLUMN git_sync_error VARCHAR(1000) NULL;
ALTER TABLE projects ADD COLUMN last_synced_at TIMESTAMP NULL;

CREATE INDEX idx_projects_repo_identifier ON projects(repo_identifier);
