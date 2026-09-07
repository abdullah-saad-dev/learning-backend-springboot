CREATE COLLATION case_insensitive(
    provider = icu,
    locale = 'und-u-ks-level2',
    deterministic = FALSE
    );


CREATE TABLE users (
    id    UUID DEFAULT UUIDV7() PRIMARY KEY,
    username  VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL COLLATE case_insensitive,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_email UNIQUE(email),
    CONSTRAINT check_role CHECK(role IN ('USER', 'ADMIN'))
);