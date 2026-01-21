-- Verification script for feature-12347-create-orders-table.rollback.sql
-- Check if orders table was dropped

-- Check table does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: orders table does not exist (rollback successful)'
    ELSE 'FAIL: orders table still exists (rollback failed)'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='orders';