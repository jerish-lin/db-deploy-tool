-- Verification script for feature-12348-add-user-email-index.apply.sql
-- Check if unique email index exists

-- Check unique index exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: idx_users_email_unique index exists'
    ELSE 'FAIL: idx_users_email_unique index does not exist'
END AS index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_users_email_unique' AND tbl_name='users';

-- Check old non-unique index does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: old idx_users_email index was removed'
    ELSE 'FAIL: old idx_users_email index still exists'
END AS old_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_users_email' AND tbl_name='users';

-- List all indexes on users table
SELECT 'Index: ' || name || ' (Unique: ' || 
       CASE WHEN "unique" = 1 THEN 'YES' ELSE 'NO' END || ')' AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='users' AND sql IS NOT NULL;