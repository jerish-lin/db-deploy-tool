package org.jerish.dbdeploy.integration.rollback;

import org.jerish.dbdeploy.integration.SQLiteDeployTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the database rollback functionality using SQLite.
 * This test verifies that the rollback process works correctly by:
 * 1. Running a deployment with v1.0.0 scripts
 * 2. Running a deployment with v1.0.1 scripts
 * 3. Rolling back to v1.0.0 using the v1.0.0 changelog
 * 4. Verifying the rollback was successful
 */
public class SQLiteRollbackTest extends SQLiteDeployTestBase {
    @Test
    @DisplayName("Test basic rollback functionality")
    void testBasicRollback() throws Exception {
        // Deploy v1.0.0
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "Deployment v1.0.0 should complete without errors");

        // Verify v1.0.0 deployment
        // Check tables exist
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('employees', 'departments')",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 2,
                "Should have employees and departments tables");

        // Check data exists
        Integer employeeCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees", Integer.class);
        assertTrue(employeeCount != null && employeeCount == 10,
                "Should have 10 employees");

        // Deploy v1.0.1
        String changelogPathV2 = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "Deployment v1.0.1 should complete without errors");

        // Verify v1.0.1 state - projects table should exist
        // Check projects table exists
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCount != null && projectTableCount == 1,
                "Projects table should exist");

        // Check projects data
        Integer projectCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projects", Integer.class);
        assertTrue(projectCount != null && projectCount == 3,
                "Should have 3 projects");

        // Rollback to v1.0.0 using the v1.0.0 changelog
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1, false),
                "Rollback should complete without errors");

        // Verify rollback - projects table should be gone but employees and departments should remain
        // Check projects table is gone
        Integer projectTableCountAfterRollback = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCountAfterRollback != null && projectTableCountAfterRollback == 0,
                "Projects table should be dropped by rollback");

        // Check employees table still exists
        Integer employeeCountAfterRollback = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees", Integer.class);
        assertTrue(employeeCountAfterRollback != null && employeeCountAfterRollback == 10,
                "Employees table should still have 10 records");

        // Check departments table still exists
        Integer departmentCountAfterRollback = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM departments", Integer.class);
        assertTrue(departmentCountAfterRollback != null && departmentCountAfterRollback == 5,
                "Departments table should still have 5 records");

        // Check employee salary index still exists (v1.0.0 state)
        Integer salaryIndexCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_employees_salary'",
                Integer.class);
        assertTrue(salaryIndexCount != null && salaryIndexCount == 1,
                "Employee salary index should still exist");

        // Verify audit state after rollback
        verifyBasicRollbackAuditState();
    }

    private void verifyBasicRollbackAuditState() {
        // Verify add-projects-table script is marked as ROLLED_BACK (latest status)
        Integer rolledBackScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12350-add-projects-table' AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='ROLLED_BACK'",
                Integer.class);
        assertTrue(rolledBackScriptCount != null && rolledBackScriptCount == 1,
                "feature-12350-add-projects-table script should be marked as ROLLED_BACK (latest status)");

        // Verify 1.0.0.0.20231110.1 scripts are still marked as SUCCESS (latest status)
        // Count only the latest status for each script in the 1.0.0.0.20231110.1 set
        String[] v1_0_0_Scripts = {
                "feature-12346/feature-12346-create-employees-table",
                "feature-12347-create-departments-table",
                "feature-12348/feature-12348-insert-sample-data",
                "feature-12349-add-employee-salary-index"
        };

        for (String scriptId : v1_0_0_Scripts) {
            Integer scriptCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name=? AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='SUCCESS'",
                    Integer.class, scriptId);
            assertTrue(scriptCount != null && scriptCount == 1,
                    String.format("Script '%s' should still be marked as SUCCESS (latest status)", scriptId));
        }
    }

    @Test
    @DisplayName("Test deploy then rollback to empty state")
    void testDeployThenRollbackToEmpty() throws Exception {
        // Deploy v1.0.0
        String changelogPath = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, false),
                "Deployment should complete without errors");

        // Verify deployment was successful
        verifyDeploymentState();

        // Create an empty changelog to rollback to initial state
        // This will rollback all scripts
        String emptyChangelogPath = "classpath:sqlite-scripts/sqlite-test-changelog-empty.yml";
        assertDoesNotThrow(() -> deployManager.rollback(emptyChangelogPath, false),
                "Rollback to empty state should complete without errors");

        // Verify rollback - all tables should be gone except audit tables
        Integer employeeTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='employees'",
                Integer.class);
        assertTrue(employeeTableCount == null || employeeTableCount == 0,
                "Employees table should be dropped by rollback");

        // Verify audit tables still exist
        Integer changelogTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='schemaflow_changelog_audit'",
                Integer.class);
        assertTrue(changelogTableCount != null && changelogTableCount == 1,
                "schemaflow_changelog_audit table should still exist");

        // Verify all scripts are marked as ROLLED_BACK
        Integer rolledBackCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit WHERE execution_status='ROLLED_BACK'",
                Integer.class);
        assertTrue(rolledBackCount != null && rolledBackCount == 4,
                "All 4 scripts should be marked as ROLLED_BACK");
    }

    private void verifyDeploymentState() {
        // Check tables exist
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('employees', 'departments')",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 2,
                "Should have employees and departments tables");

        // Check data exists
        Integer employeeCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees", Integer.class);
        assertTrue(employeeCount != null && employeeCount == 10,
                "Should have 10 employees");

        Integer departmentCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM departments", Integer.class);
        assertTrue(departmentCount != null && departmentCount == 5,
                "Should have 5 departments");
    }

    @Test
    @DisplayName("Test deploy, rollback, then deploy again with same changes")
    void testDeployRollbackRedeploy() throws Exception {
        // First deployment
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Verify first deployment
        verifyFirstDeploymentState();

        // Second deploy with 1.0.1.20231110.1 using sqlite-scripts
        String changelogPathV2 = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "Second deployment should complete without errors");

        // Verify second deployment
        verifySecondDeploymentState();

        // Rollback to 1.0.0.20231110.1 using the v1 changelog
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1, false),
                "Rollback should complete without errors");

        // Verify rollback state
        verifyRollbackState();

        // Deploy 1.0.1.20231110.1 again
        String changelogPathRedeploy = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathRedeploy, false),
                "Redeployment should complete without errors");

        // Verify redeployment state
        verifyRedeploymentState();
    }

    private void verifyFirstDeploymentState() {
        // Verify employees and departments tables exist
        String[] expectedTables = {"employees", "departments"};
        for (String tableName : expectedTables) {
            Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                    Integer.class, tableName);
            assertTrue(tableCount != null && tableCount == 1,
                    String.format("Table '%s' should exist after first deployment", tableName));
        }

        // Verify data exists
        Integer employeeCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees", Integer.class);
        assertTrue(employeeCount != null && employeeCount == 10,
                "Should have 10 employees");
    }

    private void verifySecondDeploymentState() {
        // Verify projects table exists
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCount != null && projectTableCount == 1,
                "Projects table should exist after second deployment");

        // Verify projects data
        Integer projectCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projects", Integer.class);
        assertTrue(projectCount != null && projectCount == 3,
                "Should have 3 projects");
    }

    private void verifyRollbackState() {
        // Verify projects table is gone
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCount != null && projectTableCount == 0,
                "Projects table should be dropped after rollback");

        // Verify add-projects-table script is marked as ROLLED_BACK (latest status)
        Integer rolledBackScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12350-add-projects-table' AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='ROLLED_BACK'",
                Integer.class);
        assertTrue(rolledBackScriptCount != null && rolledBackScriptCount == 1,
                "feature-12350-add-projects-table script should be marked as ROLLED_BACK (latest status)");
    }

    private void verifyRedeploymentState() {
        // Verify projects table exists again
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCount != null && projectTableCount == 1,
                "Projects table should exist after redeployment");

        // Verify projects data exists again
        Integer projectCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projects", Integer.class);
        assertTrue(projectCount != null && projectCount == 3,
                "Should have 3 projects after redeployment");

        // Verify add-projects-table script is executed successfully again (latest status)
        Integer successScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12350-add-projects-table' AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='SUCCESS'",
                Integer.class);
        assertTrue(successScriptCount != null && successScriptCount == 1,
                "feature-12350-add-projects-table script should be executed successfully in redeployment (latest status)");
    }
}
