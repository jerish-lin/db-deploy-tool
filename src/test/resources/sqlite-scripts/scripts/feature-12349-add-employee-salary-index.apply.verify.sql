-- Verification script for add-employee-salary-index.apply.sql
-- Check if salary indexes were created

-- Check total number of indexes on employees table
SELECT CASE 
    WHEN COUNT(*) = 6 THEN 'PASS: employees table has all 6 indexes'
    ELSE 'FAIL: employees table has ' || COUNT(*) || ' indexes (expected 6)'
END AS index_count_check
FROM sqlite_master 
WHERE type='index' AND tbl_name='employees' AND sql IS NOT NULL;

-- Check if salary index exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: idx_employees_salary index exists'
    ELSE 'FAIL: idx_employees_salary index does not exist'
END AS salary_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_employees_salary';

-- Check if composite department-salary index exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: idx_employees_department_salary index exists'
    ELSE 'FAIL: idx_employees_department_salary index does not exist'
END AS composite_index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_employees_department_salary';

-- List all indexes on employees table
SELECT name || ': ' || sql AS index_details
FROM sqlite_master 
WHERE type='index' AND tbl_name='employees' AND sql IS NOT NULL
ORDER BY name;

-- Test index usage by running a query that would benefit from salary index
EXPLAIN QUERY PLAN 
SELECT employee_id, first_name, last_name, salary 
FROM employees 
WHERE salary > 70000 
ORDER BY salary DESC;

-- Test index usage by running a query that would benefit from composite index
EXPLAIN QUERY PLAN 
SELECT e.employee_id, e.first_name, e.last_name, e.salary, d.department_name
FROM employees e
JOIN departments d ON e.department_id = d.id
WHERE e.department_id = 1
ORDER BY e.salary DESC;