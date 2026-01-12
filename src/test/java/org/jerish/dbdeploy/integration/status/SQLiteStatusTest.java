package org.jerish.dbdeploy.integration.status;

import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.integration.SQLiteDeployTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the status functionality using SQLite.
 * This test verifies that the status command works correctly by:
 * 1. Running a deployment
 * 2. Checking the DatabaseStatus object
 * 3. Running a rollback
 * 4. Checking the DatabaseStatus object again
 */
public class SQLiteStatusTest extends SQLiteDeployTestBase {

    @Test
    @DisplayName("Test status action on fresh database")
    void testShowStatusOnFreshDatabase() throws Exception {
        // Run status on fresh database (schema not initialized)
        DatabaseStatus status = deployManager.status();

        // Verify status for fresh database
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.getHealthInfo().isHealthy(), "Database should be healthy");
        assertEquals("Database connected but schema not initialized", status.getHealthInfo().getHealthMessage());
        assertTrue(status.getDeploymentState().getCurrentTag() == null || status.getDeploymentState().getCurrentTag().isEmpty(),
                "Current tag should be null or empty on fresh database");
        assertTrue(status.getConfigurationInfo().isValid(), "Configuration should be valid");
        assertEquals("Ready for initial deployment", status.getConfigurationInfo().getMessage());
        assertEquals(0, status.getDeploymentState().getSuccessfulScripts(),
                "No scripts should be executed on fresh database");
        assertEquals(0, status.getDeploymentState().getFailedScripts(),
                "No scripts should be failed on fresh database");
        assertEquals(0, status.getDeploymentState().getRolledBackScripts(),
                "No scripts should be rolled back on fresh database");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");
        assertTrue(status.getScriptStatus().getExecutedScriptNames().isEmpty(),
                "Executed script names should be empty on fresh database");
        assertTrue(status.getRecentDeployments().isEmpty(), "Recent deployments should be empty");
    }

    @Test
    @DisplayName("Test status after deployment")
    void testShowStatusAfterDeployment() throws Exception {
        // Deploy scripts first
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, false),
                "Deployment should complete without errors");

        // Run status after deployment
        DatabaseStatus status = deployManager.status();

        // Verify status after deployment
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.getHealthInfo().isHealthy(), "Database should be healthy");
        assertEquals("Database health check passed", status.getHealthInfo().getHealthMessage());
        assertEquals(0, status.getDeploymentState().getFailedScripts(),
                "No scripts should be failed after successful deployment");
        assertEquals(0, status.getDeploymentState().getRolledBackScripts(),
                "No scripts should be rolled back after successful deployment");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");
        assertTrue(status.getConfigurationInfo().isValid(), "Configuration should be valid");
        assertNotNull(status.getDeploymentState().getDeploymentTime(),
                "Last deployment time should be set after deployment");
    }

    @Test
    @DisplayName("Test status after rollback")
    void testShowStatusAfterRollback() throws Exception {
        // Deploy scripts first
        String changelogPathV1 = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Deploy 1.0.1.20231110.1
        String changelogPathV2 = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "Second deployment should complete without errors");

        // Rollback to v1.0.0.20231110.1
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1, false),
                "Rollback should complete without errors");

        // Run status after rollback
        DatabaseStatus status = deployManager.status();

        // Verify status after rollback
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.getHealthInfo().isHealthy(), "Database should be healthy");
        assertEquals(0, status.getDeploymentState().getFailedScripts(),
                "No scripts should be failed after rollback");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");
        assertTrue(status.getConfigurationInfo().isValid(), "Configuration should be valid");
    }

    @Test
    @DisplayName("Test status with multiple operations")
    void testShowStatusWithMultipleOperations() throws Exception {
        // Deploy v1.0.0.20231110.1
        String changelogPathV1 = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Deploy v1.0.1.20231110.1
        String changelogPathV2 = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "Second deployment should complete without errors");

        // Rollback to v1.0.0.20231110.1
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1, false),
                "Rollback should complete without errors");

        // Check status after rollback
        DatabaseStatus status = deployManager.status();

        // Verify basic status fields
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.getHealthInfo().isHealthy(), "Database should be healthy");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");
        assertTrue(status.getConfigurationInfo().isValid(), "Configuration should be valid");
    }

    @Test
    @DisplayName("Test status after failed deployment")
    void testShowStatusAfterFailedDeployment() throws Exception {
        // Deploy working scripts first
        String changelogPathV1 = "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-first3.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "Initial deployment should complete without errors");

        // Attempt to deploy with a failing script
        String changelogPathV2 = "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog.yml";

        assertThrows(Exception.class, () -> deployManager.deploy(changelogPathV2, false),
                "Deployment should fail due to intentional SQL error");

        // Check status after failed deployment
        DatabaseStatus status = deployManager.status();

        // Verify basic status fields
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.getHealthInfo().isHealthy(), "Database should be healthy");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress after failure");
        assertTrue(status.getConfigurationInfo().isValid(), "Configuration should be valid");
    }
}