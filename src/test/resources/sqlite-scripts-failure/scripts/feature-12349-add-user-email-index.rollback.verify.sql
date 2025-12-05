-- Verification script for feature-12349-add-user-email-index.rollback.sql
-- Check if email index was dropped

-- Check index does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: idx_users_email index was dropped'
    ELSE 'FAIL: idx_users_email index still exists'
END AS index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_users_email' AND tbl_name='users';

-- List remaining indexes on users table
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='users' AND sql IS NOT NULL;