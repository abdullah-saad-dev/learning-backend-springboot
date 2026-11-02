CREATE TABLE email_verification_otps (
    id UUID DEFAULT UUIDV7() PRIMARY KEY,
    email VARCHAR(255) NOT NULL COLLATE case_insensitive,
    otp_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_verification_email UNIQUE (email)
);
