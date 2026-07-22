-- V2: Auth support columns on users (the only sanctioned schema change for the MVP).
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN password_expires_at  DATETIME NULL;
