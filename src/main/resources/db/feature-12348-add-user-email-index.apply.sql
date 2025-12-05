CREATE UNIQUE INDEX idx_users_email_unique ON users(email);

-- Remove the old non-unique index
DROP INDEX IF EXISTS idx_users_email;