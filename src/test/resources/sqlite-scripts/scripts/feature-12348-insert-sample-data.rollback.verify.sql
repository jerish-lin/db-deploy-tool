-- Verification script for feature-12348-insert-sample-data.rollback.sql
-- Check if sample data was removed

-- Check sample employees were deleted
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: Sample employees were deleted'
    ELSE 'FAIL: ' || COUNT(*) || ' sample employees still exist'
END AS employees_check
FROM employees 
WHERE employee_id IN (
    'EMP001', 'EMP002', 'EMP003', 'EMP004', 'EMP005',
    'EMP006', 'EMP007', 'EMP008', 'EMP009', 'EMP010'
);

-- Check sample departments were deleted
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: Sample departments were deleted'
    ELSE 'FAIL: ' || COUNT(*) || ' sample departments still exist'
END AS departments_check
FROM departments 
WHERE department_id IN (
    'DEPT001', 'DEPT002', 'DEPT003', 'DEPT004', 'DEPT005'
);