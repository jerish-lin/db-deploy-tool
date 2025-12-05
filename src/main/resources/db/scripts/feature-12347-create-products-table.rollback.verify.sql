-- Verification script for feature-12347-create-products-table.rollback.sql
-- Check if products table was dropped

-- Check table does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: products table does not exist (rollback successful)'
    ELSE 'FAIL: products table still exists (rollback failed)'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='products';