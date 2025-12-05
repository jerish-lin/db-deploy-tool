-- Verification script for feature-12348-insert-sample-users.rollback.sql
-- Check if sample users were removed

-- Check sample users were deleted
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: Sample users were deleted'
    ELSE 'FAIL: ' || COUNT(*) || ' sample users still exist'
END AS users_check
FROM users 
WHERE username IN ('jane_smith', 'bob_wilson', 'alice_johnson');

-- Check original users still exist
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: Original 2 users still exist'
    ELSE 'FAIL: Only ' || COUNT(*) || ' original users found (expected 2)'
END AS original_users_check
FROM users 
WHERE username IN ('admin', 'john_doe');

-- Check total user count after rollback
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: Total user count is 2 after rollback'
    ELSE 'FAIL: Total user count is ' || COUNT(*) || ' (expected 2)'
END AS total_count_check
FROM users;