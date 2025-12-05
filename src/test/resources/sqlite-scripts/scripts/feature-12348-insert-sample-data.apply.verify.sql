-- Verification script for insert-sample-data.apply.sql
-- Check if sample data was inserted correctly

-- Check departments count
SELECT CASE 
    WHEN COUNT(*) = 5 THEN 'PASS: 5 departments inserted'
    ELSE 'FAIL: ' || COUNT(*) || ' departments found (expected 5)'
END AS department_count_check
FROM departments;

-- Check specific departments
SELECT CASE 
    WHEN COUNT(*) = 5 THEN 'PASS: All expected department IDs exist'
    ELSE 'FAIL: Only ' || COUNT(*) || ' of 5 expected department IDs found'
END AS department_ids_check
FROM departments 
WHERE department_id IN ('DEPT001', 'DEPT002', 'DEPT003', 'DEPT004', 'DEPT005');

-- List departments
SELECT department_id || ': ' || department_name || ' (Budget: $' || printf('%.2f', budget) || ')' AS department_info
FROM departments
ORDER BY department_id;

-- Check employees count
SELECT CASE 
    WHEN COUNT(*) = 10 THEN 'PASS: 10 employees inserted'
    ELSE 'FAIL: ' || COUNT(*) || ' employees found (expected 10)'
END AS employee_count_check
FROM employees;

-- Check specific employees
SELECT CASE 
    WHEN COUNT(*) = 10 THEN 'PASS: All expected employee IDs exist'
    ELSE 'FAIL: Only ' || COUNT(*) || ' of 10 expected employee IDs found'
END AS employee_ids_check
FROM employees 
WHERE employee_id IN ('EMP001', 'EMP002', 'EMP003', 'EMP004', 'EMP005', 'EMP006', 'EMP007', 'EMP008', 'EMP009', 'EMP010');

-- Check department assignments
SELECT d.department_name || ': ' || COUNT(e.employee_id) || ' employees' AS department_staffing
FROM departments d
LEFT JOIN employees e ON d.id = e.department_id
GROUP BY d.department_id, d.department_name
ORDER BY d.department_id;