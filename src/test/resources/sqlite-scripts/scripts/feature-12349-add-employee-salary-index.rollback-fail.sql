-- This rollback script intentionally fails to test ROLLBACK_FAILED status
-- It tries to drop a non-existent table to trigger an error
DROP TABLE IF EXISTS non_existent_table;

-- This line should never be reached
DROP INDEX IF EXISTS idx_employees_salary;