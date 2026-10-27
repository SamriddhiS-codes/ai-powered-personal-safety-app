-- Run once on an existing safety_app database to add the login-security columns.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS totp_secret     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS totp_enabled    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS last_totp_step  BIGINT  NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS failed_attempts INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until    TIMESTAMP;
