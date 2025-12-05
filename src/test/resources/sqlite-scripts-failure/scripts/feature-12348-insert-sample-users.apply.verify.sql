-- Verification script for feature-12348-insert-sample-users.apply.sql
-- Check if sample users were inserted

-- Check total user count
SELECT CASE 
    WHEN COUNT(*) = 5 THEN 'PASS: All 5 users exist in database'
    ELSE 'FAIL: Only ' || COUNT(*) || ' users found (expected 5)'
END AS user_count_check
FROM users;

-- Check new users were inserted
SELECT CASE 
    WHEN COUNT(*) = 3 THEN 'PASS: All 3 new sample users were inserted'
    ELSE 'FAIL: Only ' || COUNT(*) || ' new sample users found (expected 3)'
END AS new_users_check
FROM users 
WHERE username IN ('jane_smith', 'bob_wilson', 'alice_johnson');

-- Check specific new users exist
SELECT 'PASS: User ' || username || ' exists' AS user_check
FROM users 
WHERE username IN ('jane_smith', 'bob_wilson', 'alice_johnson')
ORDER BY username;