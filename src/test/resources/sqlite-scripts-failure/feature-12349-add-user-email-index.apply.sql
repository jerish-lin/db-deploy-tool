-- Add index on user email
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);