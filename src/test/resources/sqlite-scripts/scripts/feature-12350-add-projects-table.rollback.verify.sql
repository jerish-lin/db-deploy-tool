-- Verification script for feature-12350-add-projects-table.rollback.sql
-- Check if projects table was dropped

-- Check table does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: projects table does not exist (rollback successful)'
    ELSE 'FAIL: projects table still exists (rollback failed)'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='projects';