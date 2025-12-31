package org.jerish.dbdeploy.integration.rollback;

import org.jerish.dbdeploy.integration.SQLiteDeployTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the database rollback functionality using SQLite.
 * This test verifies that the rollback process works correctly by:
 * 1. Running a deployment
 * 2. Rolling back to a previous tag
 * 3. Verifying the rollback was successful
 */
public class SQLiteRollbackTest extends SQLiteDeployTestBase {
    @Test
    @DisplayName("Test basic rollback functionality")
    void testBasicRollback() throws Exception {
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Verify deployment
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

        // Second deploy with 1.0.1.20231110.1 using sqlite-scripts
        String changelogPathV2 = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";
        String tagNameV2 = "1.0.1.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, tagNameV2, false),
                "Second deployment should complete without errors");

        // Verify 1.0.1.20231110.1 state - projects table should exist
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

        String rollbackTagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
                "Rollback should complete without errors");

//         Verify rollback - projects table should be gone but employees and departments should remain
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

        // Check employee salary index still exists (1.0.0.20231110.1 state)
        Integer salaryIndexCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_employees_salary'", 
                Integer.class);
        assertTrue(salaryIndexCount != null && salaryIndexCount == 1,
                "Employee salary index should still exist");

        // Verify changelog and tags after rollback
        verifyBasicRollbackAuditState();
    }

    private void verifyBasicRollbackAuditState() {
        // Verify 1.0.0.20231110.1 tag is still active (target of rollback)
        Integer targetTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.0.20231110.1' AND is_active=1", 
                Integer.class);
        assertTrue(targetTagCount != null && targetTagCount == 1,
                "Target tag '1.0.0.20231110.1' should still be active after rollback");

        // Verify 1.0.1.20231110.1 tag is deactivated (rolled back)
        Integer rolledBackTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.1' AND is_active=0", 
                Integer.class);
        assertTrue(rolledBackTagCount != null && rolledBackTagCount == 1,
                "Rolled back tag '1.0.1.20231110.1' should be deactivated");

        // Verify initial tag is still active
        Integer initialTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='initial' AND is_active=1", 
                Integer.class);
        assertTrue(initialTagCount != null && initialTagCount == 1,
                "Initial tag should still be active");

        // Verify add-projects-table script is marked as ROLLED_BACK
        Integer rolledBackScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE script_name='feature-12350-add-projects-table' AND execution_status='ROLLED_BACK'", 
                Integer.class);
        assertTrue(rolledBackScriptCount != null && rolledBackScriptCount == 1,
                "feature-12350-add-projects-table script should be marked as ROLLED_BACK");

        // Verify 1.0.0.20231110.1 scripts are still marked as SUCCESS
        Integer successScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE tag_name='1.0.0.20231110.1' AND execution_status='SUCCESS'", 
                Integer.class);
        assertTrue(successScriptCount != null && successScriptCount == 4,
                "1.0.0.20231110.1 scripts should still be marked as SUCCESS");
    }

    @Test
    @DisplayName("Test deploy then rollback to initial version")
    void testDeployThenRollbackToInitial() throws Exception {
        // Deploy all scripts
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Verify deployment was successful
        verifyDeploymentState();

        // Rollback to initial version
        String rollbackTagName = "initial";

        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
                "Rollback to initial should complete without errors");

        // Verify rollback to initial state
        verifyInitialRollbackState();
    }

    private void verifyDeploymentState() {
        // Verify all tables exist after deployment
        String[] expectedTables = {"employees", "departments", "db_change_log", "deployment_tags", "database_lock"};

        for (String tableName : expectedTables) {
            Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", 
                    Integer.class, tableName);
            assertTrue(tableCount != null && tableCount == 1,
                    String.format("Table '%s' should exist after deployment", tableName));
        }

        // Verify data was inserted
        Integer employeeCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees", Integer.class);
        assertTrue(employeeCount != null && employeeCount == 10,
                "Should have 10 employees after deployment");

        Integer departmentCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM departments", Integer.class);
        assertTrue(departmentCount != null && departmentCount == 5,
                "Should have 5 departments after deployment");

        // Verify indexes were created
        String[] expectedIndexes = {
                "idx_employees_employee_id",
                "idx_employees_email",
                "idx_employees_department_id",
                "idx_employees_hire_date",
                "idx_employees_salary",
                "idx_employees_department_salary",
                "idx_departments_department_id",
                "idx_departments_manager_id",
                "idx_departments_location"
        };

        for (String indexName : expectedIndexes) {
            Integer indexCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?", 
                    Integer.class, indexName);
            assertTrue(indexCount != null && indexCount == 1,
                    String.format("Index '%s' should exist after deployment", indexName));
        }

        // Verify deployment tag was created
        Integer deploymentTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.0.20231110.1'", 
                Integer.class);
        assertTrue(deploymentTagCount != null && deploymentTagCount == 1,
                "Deployment tag '1.0.0.20231110.1' should be created");

        // Verify scripts were executed successfully
        Integer successScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE execution_status='SUCCESS' AND tag_name='1.0.0.20231110.1'", 
                Integer.class);
        assertTrue(successScriptCount != null && successScriptCount == 4,
                "All 4 scripts should be executed successfully for 1.0.0.20231110.1");
    }

    private void verifyInitialRollbackState() {
        // Verify user tables are gone after rollback to initial
        String[] droppedTables = {"employees", "departments"};

        for (String tableName : droppedTables) {
            Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", 
                    Integer.class, tableName);
            assertTrue(tableCount != null && tableCount == 0,
                    String.format("Table '%s' should be dropped after rollback to initial", tableName));
        }

        // Verify system tables still exist
        String[] systemTables = {"db_change_log", "deployment_tags", "database_lock"};

        for (String tableName : systemTables) {
            Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", 
                    Integer.class, tableName);
            assertTrue(tableCount != null && tableCount == 1,
                    String.format("System table '%s' should still exist after rollback", tableName));
        }

        // Verify indexes are gone
        String[] droppedIndexes = {
                "idx_employees_employee_id",
                "idx_employees_email",
                "idx_employees_department_id",
                "idx_employees_hire_date",
                "idx_employees_salary",
                "idx_employees_department_salary",
                "idx_departments_department_id",
                "idx_departments_manager_id",
                "idx_departments_location"
        };

        for (String indexName : droppedIndexes) {
            Integer indexCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?", 
                    Integer.class, indexName);
            assertTrue(indexCount != null && indexCount == 0,
                    String.format("Index '%s' should be dropped after rollback to initial", indexName));
        }

        // Verify script execution status was updated to ROLLED_BACK
        Integer rolledBackScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE execution_status='ROLLED_BACK' AND tag_name='1.0.0.20231110.1'", 
                Integer.class);
        assertTrue(rolledBackScriptCount != null && rolledBackScriptCount == 4,
                "All 4 scripts should be marked as ROLLED_BACK");

        // Verify initial tag still exists and is active
        Integer initialTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='initial' AND is_active=1", 
                Integer.class);
        assertTrue(initialTagCount != null && initialTagCount == 1,
                "Initial tag should still exist and be active");

        // Verify deployment tag was deactivated
        Integer deactivatedTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.0.20231110.1' AND is_active=0", 
                Integer.class);
        assertTrue(deactivatedTagCount != null && deactivatedTagCount == 1,
                "Deployment tag '1.0.0.20231110.1' should be deactivated after rollback");
    }

    @Test
    @DisplayName("Test deploy, rollback, then deploy again with same changes")
    void testDeployRollbackRedeploy() throws Exception {
        // First deployment
        String changelogPathV1 = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagNameV1 = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, tagNameV1, false),
                "First deployment should complete without errors");

        // Verify first deployment
        verifyFirstDeploymentState();

        // Second deploy with 1.0.1.20231110.1 using sqlite-scripts
        String changelogPathV2 = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";
        String tagNameV2 = "1.0.1.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, tagNameV2, false),
                "Second deployment should complete without errors");

        // Verify second deployment
        verifySecondDeploymentState();

        // Rollback to 1.0.0.20231110.1
        String rollbackTagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
                "Rollback should complete without errors");

        // Verify rollback state
        verifyRollbackState();

        // Deploy 1.0.1.20231110.1 again with new tag
        String changelogPathRedeploy = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";
        String tagNameRedeploy = "1.0.1.20231110.2";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathRedeploy, tagNameRedeploy, false),
                "Redeployment should complete without errors");

        // Verify redeployment state
        verifyRedeploymentState();
    }

    private void verifyFirstDeploymentState() {
        // Verify 1.0.0.20231110.1 tag exists and is active
        Integer tagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.0.20231110.1' AND is_active=1", 
                Integer.class);
        assertTrue(tagCount != null && tagCount == 1,
                "1.0.0.20231110.1 tag should be created and active");

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
        // Verify 1.0.1.20231110.1 tag exists and is active
        Integer tagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.1' AND is_active=1", 
                Integer.class);
        assertTrue(tagCount != null && tagCount == 1,
                "1.0.1.20231110.1 tag should be created and active");

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
        // Verify 1.0.0.20231110.1 tag is still active
        Integer tagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.0.20231110.1' AND is_active=1", 
                Integer.class);
        assertTrue(tagCount != null && tagCount == 1,
                "1.0.0.20231110.1 tag should still be active after rollback");

        // Verify 1.0.1.20231110.1 tag is deactivated
        Integer deactivatedTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.1' AND is_active=0", 
                Integer.class);
        assertTrue(deactivatedTagCount != null && deactivatedTagCount == 1,
                "1.0.1.20231110.1 tag should be deactivated after rollback");

        // Verify projects table is gone
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'", 
                Integer.class);
        assertTrue(projectTableCount != null && projectTableCount == 0,
                "Projects table should be dropped after rollback");

        // Verify add-projects-table script is marked as ROLLED_BACK
        Integer rolledBackScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE script_name='feature-12350-add-projects-table' AND execution_status='ROLLED_BACK'", 
                Integer.class);
        assertTrue(rolledBackScriptCount != null && rolledBackScriptCount == 1,
                "feature-12350-add-projects-table script should be marked as ROLLED_BACK");
    }

    private void verifyRedeploymentState() {
        // Verify 1.0.1.20231110.2 tag exists and is active
        Integer tagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.2' AND is_active=1", 
                Integer.class);
        assertTrue(tagCount != null && tagCount == 1,
                "1.0.1.20231110.2 tag should be created and active");

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

        // Verify add-projects-table script is executed successfully again
        Integer successScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_change_log WHERE script_name='feature-12350-add-projects-table' AND tag_name='1.0.1.20231110.2' AND execution_status='SUCCESS'", 
                Integer.class);
        assertTrue(successScriptCount != null && successScriptCount == 1,
                "feature-12350-add-projects-table script should be executed successfully in redeployment");

        // Verify 1.0.0.20231110.1 tag is still active
        Integer originalTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.0.20231110.1' AND is_active=1", 
                Integer.class);
        assertTrue(originalTagCount != null && originalTagCount == 1,
                "1.0.0.20231110.1 tag should still be active after redeployment");

        // Verify 1.0.1.20231110.1 tag is still deactivated
        Integer deactivatedTagCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.1' AND is_active=0", 
                Integer.class);
        assertTrue(deactivatedTagCount != null && deactivatedTagCount == 1,
                "Original 1.0.1.20231110.1 tag should remain deactivated");
    }
}