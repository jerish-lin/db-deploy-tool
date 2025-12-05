-- Verification script for create-departments-table.apply.sql
-- Check if departments table exists with correct structure

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: departments table exists'
    ELSE 'FAIL: departments table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='departments';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 9 THEN 'PASS: departments table has all 9 columns'
    ELSE 'FAIL: departments table has ' || COUNT(*) || ' columns (expected 9)'
END AS column_count_check
FROM pragma_table_info('departments');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('departments')
WHERE name IN ('id', 'department_id', 'department_name', 'manager_id', 'location', 'budget', 'is_active', 'created_at', 'updated_at');

-- Check indexes exist
SELECT CASE 
    WHEN COUNT(*) = 3 THEN 'PASS: departments table has all 3 indexes'
    ELSE 'FAIL: departments table has ' || COUNT(*) || ' indexes (expected 3)'
END AS index_count_check
FROM sqlite_master 
WHERE type='index' AND tbl_name='departments' AND sql IS NOT NULL;

-- List indexes
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='departments' AND sql IS NOT NULL;