CREATE TABLE refresh_tokens (
    id UUID DEFAULT UUIDV7() PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(255) NOT NULL,
    issued_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '7 days') NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ DEFAULT NULL,
    family_id UUID NOT NULL,
    CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_token_hash UNIQUE (token_hash),
    CONSTRAINT status_enum CHECK (status IN ('ACTIVE', 'REVOKED','ROTATED'))
   );
CREATE INDEX idx_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_absolute_expires_at ON refresh_tokens(absolute_expires_at);
