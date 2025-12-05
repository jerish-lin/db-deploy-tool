-- Verification script for feature-12347-create-departments-table.rollback.sql
-- Check if departments table was dropped

-- Check table does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: departments table does not exist (rollback successful)'
    ELSE 'FAIL: departments table still exists (rollback failed)'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='departments';