-- Remove the unique index
DROP INDEX IF EXISTS idx_users_email_unique;

-- Recreate the old non-unique index
CREATE INDEX idx_users_email ON users(email);