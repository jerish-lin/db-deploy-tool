-- Verification script for feature-12346-create-users-table.rollback.sql
-- Check if users table was dropped

-- Check table does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: users table does not exist (rollback successful)'
    ELSE 'FAIL: users table still exists (rollback failed)'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='users';