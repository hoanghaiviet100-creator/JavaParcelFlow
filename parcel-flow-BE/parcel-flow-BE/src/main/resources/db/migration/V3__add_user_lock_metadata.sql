-- V3: Permanent-lock metadata on users.
-- Stores WHY and WHEN an account was locked so support/admin can audit and the
-- permanent-lock status survives Redis flushes / restarts (Redis only holds the
-- transient failed-attempt counters and the 15-minute temporary lock).
ALTER TABLE users
    ADD COLUMN lock_reason VARCHAR(100) NULL,
    ADD COLUMN locked_at   DATETIME     NULL;

-- Email is already UNIQUE (V1). Add a helper index for the common lookup-by-email path.
CREATE INDEX idx_users_email ON users (email);
