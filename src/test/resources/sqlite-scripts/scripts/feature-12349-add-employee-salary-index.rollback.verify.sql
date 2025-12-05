-- Verification script for feature-12349-add-employee-salary-index.rollback.sql
-- Check if salary indexes were dropped

-- Check salary index does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: idx_employees_salary index was dropped'
    ELSE 'FAIL: idx_employees_salary index still exists'
END AS salary_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_employees_salary' AND tbl_name='employees';

-- Check department salary index does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: idx_employees_department_salary index was dropped'
    ELSE 'FAIL: idx_employees_department_salary index still exists'
END AS dept_salary_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_employees_department_salary' AND tbl_name='employees';

-- List remaining indexes on employees table
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='employees' AND sql IS NOT NULL;