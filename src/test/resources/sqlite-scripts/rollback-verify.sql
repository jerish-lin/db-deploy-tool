-- Verification script for rollback functionality
-- Check if rollback was successful

-- Check departments table after rollback
SELECT CASE 
    WHEN COUNT(*) = 5 THEN 'PASS: Departments table has 5 records after rollback'
    ELSE 'FAIL: Departments table has ' || COUNT(*) || ' records (expected 5)'
END AS departments_rollback_check
FROM departments;

-- Check if DEPT006 was removed
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: DEPT006 was removed by rollback'
    ELSE 'FAIL: DEPT006 still exists (should be removed)'
END AS dept006_removed_check
FROM departments 
WHERE department_id = 'DEPT006';

-- Check employees table after rollback
SELECT CASE 
    WHEN COUNT(*) = 10 THEN 'PASS: Employees table has 10 records after rollback'
    ELSE 'FAIL: Employees table has ' || COUNT(*) || ' records (expected 10)'
END AS employees_rollback_check
FROM employees;

-- Check if EMP011 and EMP012 were removed
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: EMP011 and EMP012 were removed by rollback'
    ELSE 'FAIL: EMP011 and/or EMP012 still exist (should be removed)'
END AS new_employees_removed_check
FROM employees 
WHERE employee_id IN ('EMP011', 'EMP012');

-- Check if projects table was dropped
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: Projects table was dropped by rollback'
    ELSE 'FAIL: Projects table still exists (should be dropped)'
END AS projects_table_dropped_check
FROM sqlite_master 
WHERE type='table' AND name='projects';

-- Check audit log for rolled back scripts
SELECT CASE 
    WHEN COUNT(*) >= 1 THEN 'PASS: Audit log shows rolled back scripts'
    ELSE 'FAIL: No rolled back scripts found in audit log'
END AS audit_rollback_check
FROM db_change_log 
WHERE execution_status = 'ROLLED_BACK';

-- Check original scripts are still marked as SUCCESS
SELECT CASE 
    WHEN COUNT(*) = 4 THEN 'PASS: Original 4 scripts still marked as SUCCESS'
    ELSE 'FAIL: Only ' || COUNT(*) || ' of 4 original scripts marked as SUCCESS'
END AS original_scripts_success_check
FROM db_change_log 
WHERE script_id IN ('create-employees-table', 'create-departments-table', 'insert-sample-data', 'add-employee-salary-index')
AND execution_status = 'SUCCESS';

-- List current departments after rollback
SELECT department_id || ': ' || department_name AS current_departments
FROM departments
ORDER BY department_id;

-- List current employees after rollback (limit to first 5)
SELECT employee_id || ': ' || first_name || ' ' || last_name AS current_employees
FROM employees
ORDER BY employee_id
LIMIT 5;