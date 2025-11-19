-- Verification script for create-employees-table.apply.sql
-- Check if employees table exists with correct structure

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: employees table exists'
    ELSE 'FAIL: employees table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='employees';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 12 THEN 'PASS: employees table has all 12 columns'
    ELSE 'FAIL: employees table has ' || COUNT(*) || ' columns (expected 12)'
END AS column_count_check
FROM pragma_table_info('employees');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('employees')
WHERE name IN ('id', 'employee_id', 'first_name', 'last_name', 'email', 'phone', 'hire_date', 'job_title', 'salary', 'department_id', 'is_active', 'created_at', 'updated_at');

-- Check indexes exist
SELECT CASE 
    WHEN COUNT(*) = 4 THEN 'PASS: employees table has all 4 indexes'
    ELSE 'FAIL: employees table has ' || COUNT(*) || ' indexes (expected 4)'
END AS index_count_check
FROM sqlite_master 
WHERE type='index' AND tbl_name='employees' AND sql IS NOT NULL;

-- List indexes
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='employees' AND sql IS NOT NULL;