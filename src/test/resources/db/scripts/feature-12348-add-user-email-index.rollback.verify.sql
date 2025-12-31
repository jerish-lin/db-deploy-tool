-- Verification script for feature-12348-add-user-email-index.rollback.sql
-- Check if rollback was successful

-- Check unique index does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: idx_users_email_unique index was removed'
    ELSE 'FAIL: idx_users_email_unique index still exists'
END AS unique_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_users_email_unique' AND tbl_name='users';

-- Check old non-unique index exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: idx_users_email index was recreated'
    ELSE 'FAIL: idx_users_email index does not exist'
END AS old_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_users_email' AND tbl_name='users';

-- List all indexes on users table
SELECT 'Index: ' || name || ' (Unique: ' || 
       CASE WHEN "unique" = 1 THEN 'YES' ELSE 'NO' END || ')' AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='users' AND sql IS NOT NULL;