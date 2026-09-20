CREATE TABLE projects (
    id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
