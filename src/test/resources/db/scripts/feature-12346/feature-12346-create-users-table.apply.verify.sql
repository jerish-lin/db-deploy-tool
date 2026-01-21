-- Verification script for feature-12346-create-users-table.apply.sql
-- Check if users table exists with correct structure and data

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: users table exists'
    ELSE 'FAIL: users table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='users';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 6 THEN 'PASS: users table has all 6 columns'
    ELSE 'FAIL: users table has ' || COUNT(*) || ' columns (expected 6)'
END AS column_count_check
FROM pragma_table_info('users');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('users')
WHERE name IN ('id', 'username', 'email', 'password_hash', 'created_at', 'updated_at');

-- Check initial data was inserted
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: All 2 initial users were inserted'
    ELSE 'FAIL: Only ' || COUNT(*) || ' initial users found (expected 2)'
END AS data_check
FROM users;

-- Check specific users exist
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: Both admin and john_doe users exist'
    ELSE 'FAIL: Only ' || COUNT(*) || ' expected users found'
END AS user_check
FROM users 
WHERE username IN ('admin', 'john_doe');