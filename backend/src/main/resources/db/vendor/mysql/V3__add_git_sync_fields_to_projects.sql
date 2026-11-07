-- V3: Add GitHub repository sync fields to projects table
ALTER TABLE projects
    ADD COLUMN repo_identifier VARCHAR(255) NULL,
    ADD COLUMN default_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    ADD COLUMN git_sync_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED',
    ADD COLUMN git_sync_error VARCHAR(1000) NULL,
    ADD COLUMN last_synced_at TIMESTAMP NULL;

CREATE INDEX idx_projects_repo_identifier ON projects(repo_identifier);
