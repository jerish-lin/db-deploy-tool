-- Verify index exists
SELECT indexname FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_email';