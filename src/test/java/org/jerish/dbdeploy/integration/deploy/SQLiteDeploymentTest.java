package org.jerish.dbdeploy.integration.deploy;

import org.jerish.dbdeploy.integration.SQLiteDeployTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the database deployment tool using SQLite.
 * This test verifies that the deployment process works correctly by:
 * 1. Running a full deployment
 * 2. Verifying all tables were created with correct structure
 * 3. Verifying all data was inserted correctly
 * 4. Verifying all indexes were created
 * 5. Verifying audit information is correct
 */

public class SQLiteDeploymentTest extends SQLiteDeployTestBase {
    @Test
    @DisplayName("Test complete database deployment with SQLite")
    void testCompleteDeployment() throws Exception {
        // Define deployment parameters
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        boolean dryRun = false;

        // Run the deployment using DatabaseDeployManager
        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, dryRun),
                "Database deployment should complete without errors");

        // Verify all expected tables exist
        verifyTablesExist();

        // Verify table structures
        verifyTableStructures();

        // Verify data was inserted
        verifyDataInserted();

        // Verify indexes were created
        verifyIndexesCreated();

        // Verify audit information
        verifyAuditInformation();
    }

    private void verifyTablesExist() {
        String[] expectedTables = {"employees", "departments", "db_change_log", "database_lock"};

        for (String tableName : expectedTables) {
            String sql = "SELECT count(name) FROM sqlite_master WHERE type='table' AND name=?";
            Integer count = dbDeployJdbcTemplate.queryForObject(sql, Integer.class, tableName);
            assertTrue(count != null && count > 0,
                    String.format("Table '%s' should exist", tableName));
        }
    }

    private void verifyTableStructures() {
        // Verify employees table structure
        String employeesSql = "SELECT COUNT(*) FROM pragma_table_info('employees') WHERE name IN " +
                "('id', 'employee_id', 'first_name', 'last_name', 'email', 'phone', " +
                "'hire_date', 'job_title', 'salary', 'department_id', 'is_active', " +
                "'created_at', 'updated_at')";
        Integer employeeColumnCount = dbDeployJdbcTemplate.queryForObject(employeesSql, Integer.class);
        assertTrue(employeeColumnCount != null && employeeColumnCount == 13,
                "Employees table should have all 13 expected columns");

        // Verify departments table structure
        String departmentsSql = "SELECT COUNT(*) FROM pragma_table_info('departments') WHERE name IN " +
                "('id', 'department_id', 'department_name', 'manager_id', " +
                "'location', 'budget', 'is_active', 'created_at', 'updated_at')";
        Integer departmentColumnCount = dbDeployJdbcTemplate.queryForObject(departmentsSql, Integer.class);
        assertTrue(departmentColumnCount != null && departmentColumnCount == 9,
                "Departments table should have all 9 expected columns");
    }

    private void verifyDataInserted() {
        // Verify departments data
        Integer departmentCount = dbDeployJdbcTemplate.queryForObject("SELECT COUNT(*) FROM departments", Integer.class);
        assertTrue(departmentCount != null && departmentCount == 5,
                "Should have 5 departments");

        // Verify employees data
        Integer employeeCount = dbDeployJdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Integer.class);
        assertTrue(employeeCount != null && employeeCount == 10,
                "Should have 10 employees");

        // Verify specific department IDs exist
        Integer deptIdCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM departments WHERE department_id IN " +
                        "('DEPT001', 'DEPT002', 'DEPT003', 'DEPT004', 'DEPT005')", Integer.class);
        assertTrue(deptIdCount != null && deptIdCount == 5,
                "All expected department IDs should exist");

        // Verify specific employee IDs exist
        Integer empIdCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE employee_id IN " +
                        "('EMP001', 'EMP002', 'EMP003', 'EMP004', 'EMP005', " +
                        "'EMP006', 'EMP007', 'EMP008', 'EMP009', 'EMP010')", Integer.class);
        assertTrue(empIdCount != null && empIdCount == 10,
                "All expected employee IDs should exist");
    }

    private void verifyIndexesCreated() {
        // Verify employees table indexes
        String[] expectedEmployeeIndexes = {
                "idx_employees_employee_id",
                "idx_employees_email",
                "idx_employees_department_id",
                "idx_employees_hire_date",
                "idx_employees_salary",
                "idx_employees_department_salary"
        };

        for (String indexName : expectedEmployeeIndexes) {
            String sql = "SELECT count(name) FROM sqlite_master WHERE type='index' AND name=?";
            Integer count = dbDeployJdbcTemplate.queryForObject(sql, Integer.class, indexName);
            assertTrue(count != null && count > 0,
                    String.format("Index '%s' should exist", indexName));
        }

        // Verify departments table indexes
        String[] expectedDepartmentIndexes = {
                "idx_departments_department_id",
                "idx_departments_manager_id",
                "idx_departments_location"
        };

        for (String indexName : expectedDepartmentIndexes) {
            String sql = "SELECT count(name) FROM sqlite_master WHERE type='index' AND name=?";
            Integer count = dbDeployJdbcTemplate.queryForObject(sql, Integer.class, indexName);
            assertTrue(count != null && count > 0,
                    String.format("Index '%s' should exist", indexName));
        }
    }

    private void verifyAuditInformation() {
        // Verify all scripts were executed
        Integer successCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE execution_status='SUCCESS'", Integer.class);
        assertTrue(successCount != null && successCount == 4,
                "All 4 scripts should be executed successfully");

        // Verify specific scripts were executed
        String[] expectedScripts = {
                "feature-12346/feature-12346-create-employees-table",
                "feature-12347-create-departments-table",
                "feature-12348/feature-12348-insert-sample-data",
                "feature-12349-add-employee-salary-index"
        };

        for (String scriptId : expectedScripts) {
            Integer scriptCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_name=? AND execution_status='SUCCESS'",
                    Integer.class, scriptId);
            assertTrue(scriptCount != null && scriptCount == 1,
                    String.format("Script '%s' should be executed successfully", scriptId));
        }
    }
}