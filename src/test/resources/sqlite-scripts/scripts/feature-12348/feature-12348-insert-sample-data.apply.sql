-- Insert sample departments
INSERT INTO departments (department_id, department_name, manager_id, location, budget) VALUES
('DEPT001', 'Engineering', NULL, 'Building A', 500000.00),
('DEPT002', 'Sales', NULL, 'Building B', 300000.00),
('DEPT003', 'Marketing', NULL, 'Building B', 200000.00),
('DEPT004', 'Human Resources', NULL, 'Building A', 150000.00),
('DEPT005', 'Finance', NULL, 'Building C', 250000.00);

-- Insert sample employees
INSERT INTO employees (employee_id, first_name, last_name, email, phone, hire_date, job_title, salary, department_id) VALUES
('EMP001', 'John', 'Smith', 'john.smith@company.com', '555-0101', '2022-01-15', 'Software Engineer', 85000.00, 1),
('EMP002', 'Jane', 'Doe', 'jane.doe@company.com', '555-0102', '2022-02-01', 'Senior Software Engineer', 95000.00, 1),
('EMP003', 'Bob', 'Johnson', 'bob.johnson@company.com', '555-0103', '2022-03-10', 'Sales Manager', 75000.00, 2),
('EMP004', 'Alice', 'Williams', 'alice.williams@company.com', '555-0104', '2022-04-05', 'Marketing Specialist', 60000.00, 3),
('EMP005', 'Charlie', 'Brown', 'charlie.brown@company.com', '555-0105', '2022-05-20', 'HR Manager', 70000.00, 4),
('EMP006', 'Diana', 'Miller', 'diana.miller@company.com', '555-0106', '2022-06-15', 'Financial Analyst', 65000.00, 5),
('EMP007', 'Edward', 'Davis', 'edward.davis@company.com', '555-0107', '2022-07-01', 'Junior Developer', 55000.00, 1),
('EMP008', 'Fiona', 'Wilson', 'fiona.wilson@company.com', '555-0108', '2022-08-10', 'Sales Representative', 45000.00, 2),
('EMP009', 'George', 'Moore', 'george.moore@company.com', '555-0109', '2022-09-05', 'Content Writer', 50000.00, 3),
('EMP010', 'Helen', 'Taylor', 'helen.taylor@company.com', '555-0110', '2022-10-15', 'Recruiter', 55000.00, 4);