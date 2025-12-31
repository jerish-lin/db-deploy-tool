-- Verification script for feature-12346-create-users-table.apply.sql
-- Check if users table exists with correct structure

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: users table exists'
    ELSE 'FAIL: users table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='users';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 9 THEN 'PASS: users table has all 9 columns'
    ELSE 'FAIL: users table has ' || COUNT(*) || ' columns (expected 9)'
END AS column_count_check
FROM pragma_table_info('users');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('users')
WHERE name IN ('id', 'username', 'email', 'password_hash', 'first_name', 'last_name', 'created_at', 'updated_at', 'is_active');

-- Check indexes exist
SELECT CASE 
    WHEN COUNT(*) = 3 THEN 'PASS: users table has all 3 indexes'
    ELSE 'FAIL: users table has ' || COUNT(*) || ' indexes (expected 3)'
END AS index_count_check
FROM sqlite_master 
WHERE type='index' AND tbl_name='users' AND sql IS NOT NULL;

-- List indexes
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='users' AND sql IS NOT NULL;