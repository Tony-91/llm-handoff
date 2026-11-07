CREATE TABLE manifests (
    id BINARY(16) PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    schema_version VARCHAR(30),
    raw_content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_manifests_project_id ON manifests(project_id);
