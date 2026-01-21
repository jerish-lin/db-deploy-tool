-- Verification script for feature-12349-add-user-email-index.apply.sql
-- Check if email index exists

-- Check index exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: idx_users_email index exists'
    ELSE 'FAIL: idx_users_email index does not exist'
END AS index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_users_email' AND tbl_name='users';

-- List all indexes on users table
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='users' AND sql IS NOT NULL;