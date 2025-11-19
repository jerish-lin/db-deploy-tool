-- Delete sample employees
DELETE FROM employees WHERE employee_id IN (
    'EMP001', 'EMP002', 'EMP003', 'EMP004', 'EMP005',
    'EMP006', 'EMP007', 'EMP008', 'EMP009', 'EMP010'
);

-- Delete sample departments
DELETE FROM departments WHERE department_id IN (
    'DEPT001', 'DEPT002', 'DEPT003', 'DEPT004', 'DEPT005'
);