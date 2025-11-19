-- Create index on salary for performance
CREATE INDEX idx_employees_salary ON employees(salary);

-- Create composite index on department and salary for reporting
CREATE INDEX idx_employees_department_salary ON employees(department_id, salary);