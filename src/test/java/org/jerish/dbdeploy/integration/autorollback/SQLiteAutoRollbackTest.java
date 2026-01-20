package org.jerish.dbdeploy.integration.autorollback;

import org.jerish.dbdeploy.config.DeploymentConfig;
import org.jerish.dbdeploy.integration.SQLiteDeployTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the enableAutoRollback configuration using SQLite.
 * This test verifies that:
 * 1. When enableAutoRollback is true, deployOrRollback will perform rollback automatically
 * 2. When enableAutoRollback is false, deployOrRollback will fail when rollback is needed
 */
public class SQLiteAutoRollbackTest extends SQLiteDeployTestBase {

    @Autowired
    private DeploymentConfig deploymentConfig;

    private boolean originalEnableAutoRollback;

    @BeforeEach
    public void saveOriginalConfig() {
        // Save the original value before each test
        originalEnableAutoRollback = deploymentConfig.isEnableAutoRollback();
    }

    @AfterEach
    public void restoreOriginalConfig() {
        // Restore the original value after each test
        deploymentConfig.setEnableAutoRollback(originalEnableAutoRollback);
    }

    @Test
    @DisplayName("Test deployOrRollback with enableAutoRollback enabled")
    void testDeployOrRollbackWithAutoRollbackEnabled() throws Exception {
        // Enable auto rollback
        deploymentConfig.setEnableAutoRollback(true);

        // First deploy with v1.0.1
        String changelogPathV2 = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";
        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "First deployment should complete without errors");

        // Verify v1.0.1 deployment
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 1,
                "Should have projects table");

        // Now use deployOrRollback with v1.0.0
        // This should rollback the projects table
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        // This should succeed because enableAutoRollback is enabled
        assertDoesNotThrow(() -> deployManager.deployOrRollback(changelogPathV1, false),
                "deployOrRollback should succeed when enableAutoRollback is enabled");

        // Verify v1.0.0 state - projects table should be gone
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCount == null || projectTableCount == 0,
                "Projects table should be rolled back");
    }

    @Test
    @DisplayName("Test deployOrRollback with enableAutoRollback disabled")
    void testDeployOrRollbackWithAutoRollbackDisabled() throws Exception {
        // Disable auto rollback
        deploymentConfig.setEnableAutoRollback(false);

        // First deploy with v1.0.1
        String changelogPathV2 = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";
        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "First deployment should complete without errors");

        // Verify v1.0.1 deployment
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 1,
                "Should have projects table");

        // Now try to use deployOrRollback with v1.0.0
        // This should rollback the projects table
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        // This should fail because enableAutoRollback is disabled and rollback is needed
        Exception exception = assertThrows(RuntimeException.class, () -> {
            deployManager.deployOrRollback(changelogPathV1, false);
        }, "deployOrRollback should fail when enableAutoRollback is disabled and rollback is needed");

        // Verify the error message (check the cause since it's wrapped)
        assertTrue(exception.getCause() != null && exception.getCause().getMessage().contains("enableAutoRollback"),
                "Error message should mention enableAutoRollback");

        // Verify v1.0.1 state is still intact - projects table should still exist
        Integer projectTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'",
                Integer.class);
        assertTrue(projectTableCount != null && projectTableCount == 1,
                "Projects table should still exist when deployOrRollback fails");
    }

    @Test
    @DisplayName("Test deployOrRollback with enableAutoRollback disabled when only deployment is needed")
    void testDeployOrRollbackWithAutoRollbackDisabledWhenOnlyDeployNeeded() throws Exception {
        // Disable auto rollback
        deploymentConfig.setEnableAutoRollback(false);

        // First deploy with v1.0.0
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";
        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Verify v1.0.0 deployment
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('employees', 'departments')",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 2,
                "Should have employees and departments tables");

        // Rollback to initial state
        String emptyChangelogPath = "classpath:sqlite-scripts/sqlite-test-changelog-empty.yml";
        assertDoesNotThrow(() -> deployManager.rollback(emptyChangelogPath, false),
                "Rollback should complete without errors");

        // Now try to deploy v1.0.0 again using deployOrRollback
        // This should succeed because only deployment is needed (no rollback)
        assertDoesNotThrow(() -> deployManager.deployOrRollback(changelogPathV1, false),
                "deployOrRollback should succeed when only deployment is needed, even with enableAutoRollback disabled");

        // Verify v1.0.0 state
        Integer employeeTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='employees'",
                Integer.class);
        assertTrue(employeeTableCount != null && employeeTableCount == 1,
                "Employees table should exist");
    }

    @Test
    @DisplayName("Test deployOrRollback with enableAutoRollback disabled when no action is needed")
    void testDeployOrRollbackWithAutoRollbackDisabledWhenNoActionNeeded() throws Exception {
        // Disable auto rollback
        deploymentConfig.setEnableAutoRollback(false);

        // First deploy with v1.0.0
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";
        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Verify v1.0.0 deployment
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('employees', 'departments')",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 2,
                "Should have employees and departments tables");

        // Now try to deploy the same changelog using deployOrRollback
        // This should succeed because no action is needed (already at target state)
        assertDoesNotThrow(() -> deployManager.deployOrRollback(changelogPathV1, false),
                "deployOrRollback should succeed when no action is needed, even with enableAutoRollback disabled");

        // Verify state is unchanged
        Integer employeeTableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='employees'",
                Integer.class);
        assertTrue(employeeTableCount != null && employeeTableCount == 1,
                "Employees table should still exist");
    }
}