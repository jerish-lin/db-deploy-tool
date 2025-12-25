-- Verification script for feature-12346-create-employees-table.rollback.sql
-- Check if employees table was dropped

-- Check table does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: employees table does not exist (rollback successful)'
    ELSE 'FAIL: employees table still exists (rollback failed)'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='employees';