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
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertEquals("Database connected but schema not initialized", status.getDatabaseHealthMessage());
        assertTrue(status.getCurrentTag() == null || status.getCurrentTag().isEmpty(),
                "Current tag should be null or empty on fresh database");
        assertTrue(status.isConfigurationValid(), "Configuration should be valid");
        assertEquals("Ready for initial deployment", status.getConfigurationMessage());
        assertEquals(0, status.getExecutedScripts(),
                "No scripts should be executed on fresh database");
        assertEquals(0, status.getFailedScripts(),
                "No scripts should be failed on fresh database");
        assertEquals(0, status.getRolledBackScripts(),
                "No scripts should be rolled back on fresh database");
        assertFalse(status.isDeploymentInProgress(), "No deployment should be in progress");
        assertTrue(status.getExecutedScriptNames().isEmpty(),
                "Executed script names should be empty on fresh database");
        assertTrue(status.getRecentDeployments().isEmpty(), "Recent deployments should be empty");
    }

    @Test
    @DisplayName("Test status after deployment")
    void testShowStatusAfterDeployment() throws Exception {
        // Deploy scripts first
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Run status after deployment
        DatabaseStatus status = deployManager.status();

        // Verify status after deployment
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertEquals("Database operational", status.getDatabaseHealthMessage());
        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
                "Current tag should match deployed tag");
        assertTrue(status.getExecutedScripts() >= 4,
                "Should have at least 4 executed scripts (including initial)");
        assertEquals(0, status.getFailedScripts(),
                "No scripts should be failed after successful deployment");
        assertEquals(0, status.getRolledBackScripts(),
                "No scripts should be rolled back after successful deployment");
        assertFalse(status.isDeploymentInProgress(), "No deployment should be in progress");
        assertTrue(status.isConfigurationValid(), "Configuration should be valid");
        assertFalse(status.getExecutedScriptNames().isEmpty(),
                "Executed script names should not be empty after deployment");
        assertNotNull(status.getLastDeploymentTime(),
                "Last deployment time should be set after deployment");
        assertTrue(status.getRecentDeployments().size() >= 1,
                "Should have at least 1 recent deployment");

        // Verify recent deployment entry
        DatabaseStatus.DeploymentHistoryEntry latestDeployment = status.getRecentDeployments().get(0);
        assertEquals("1.0.0.20231110.1", latestDeployment.getTagName());
        assertEquals("SUCCESS", latestDeployment.getStatus());
        assertTrue(latestDeployment.getScriptCount() >= 4);
    }

    @Test
    @DisplayName("Test status after rollback")
    void testShowStatusAfterRollback() throws Exception {
        // Deploy scripts first
        String changelogPathV1 = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagNameV1 = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, tagNameV1, false),
                "First deployment should complete without errors");

        // Deploy 1.0.1.20231110.1
        String changelogPathV2 = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";
        String tagNameV2 = "1.0.1.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, tagNameV2, false),
                "Second deployment should complete without errors");

        // Rollback to 1.0.0.20231110.1
        String rollbackTagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
                "Rollback should complete without errors");

        // Run status after rollback
        DatabaseStatus status = deployManager.status();

        // Verify status after rollback
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
                "Current tag should be the rollback target tag");
        assertTrue(status.getExecutedScripts() >= 4,
                "Should have at least 4 successful scripts after rollback");
        assertEquals(0, status.getFailedScripts(),
                "No scripts should be failed after rollback");
        assertTrue(status.getRolledBackScripts() >= 1,
                "Should have at least 1 rolled back script");
        assertFalse(status.isDeploymentInProgress(), "No deployment should be in progress");
        assertTrue(status.isConfigurationValid(), "Configuration should be valid");
        assertFalse(status.getExecutedScriptNames().isEmpty(),
                "Executed script names should not be empty after rollback");

        // Verify recent deployment entries show both deployment and rollback
        assertTrue(status.getRecentDeployments().size() >= 2,
                "Should have at least 2 recent deployments (deploy and rollback)");

        // Check that we have rolled back scripts listed
        assertFalse(status.getRolledBackScriptNames().isEmpty(),
                "Rolled back script names should not be empty after rollback");
    }

    @Test
    @DisplayName("Test status after rollback to initial")
    void testShowStatusAfterRollbackToInitial() throws Exception {
        // Deploy scripts first
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Rollback to initial
        String rollbackTagName = "initial";

        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
                "Rollback to initial should complete without errors");

        // Run status after rollback to initial
        DatabaseStatus status = deployManager.status();

        // Verify status after rollback to initial
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertEquals("initial", status.getCurrentTag(),
                "Current tag should be 'initial' after rollback");
        assertTrue(status.getRolledBackScripts() >= 4,
                "Should have at least 4 rolled back scripts");
        assertEquals(0, status.getFailedScripts(),
                "No scripts should be failed after rollback to initial");
        assertTrue(status.getExecutedScripts() >= 1,
                "Should have at least 1 executed script (initial)");
        assertFalse(status.isDeploymentInProgress(), "No deployment should be in progress");
        assertTrue(status.isConfigurationValid(), "Configuration should be valid");

        // Verify recent deployment entries
        assertTrue(status.getRecentDeployments().size() >= 1,
                "Should have at least 1 recent deployment");

        // Check that rolled back scripts are properly listed
        assertFalse(status.getRolledBackScriptNames().isEmpty(),
                "Rolled back script names should not be empty after rollback to initial");
    }

    @Test
    @DisplayName("Test status with failed script")
    void testShowStatusWithFailedScript() throws Exception {
        // Deploy scripts first
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Manually insert a failed script entry in the database to simulate a failure
        dbDeployJdbcTemplate.update("""
                INSERT INTO db_change_log (
                    script_name, script_checksum,
                    execution_status, execution_time, execution_duration_ms,
                    rollback_script_content, rollback_verify_script_content,
                    tag_name, created_at, updated_at
                ) VALUES (
                    'Test Failed Script', 'failed',
                    'FAILED', datetime('now'), 0,
                    '', '', '1.0.0.20231110.1', datetime('now'), datetime('now')
                )
                """);

        // Run status
        DatabaseStatus status = deployManager.status();

        // Verify status with failed script
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertTrue(status.getExecutedScripts() >= 4,
                "Should have at least 4 successful scripts");
        assertTrue(status.getFailedScripts() >= 1,
                "Should have at least 1 failed script");
        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
                "Current tag should still be the deployed tag despite failure");
        assertFalse(status.getFailedScriptNames().isEmpty(),
                "Failed script names should not be empty");
    }

    @Test
    @DisplayName("Test status with deployment lock")
    void testShowStatusWithDeploymentLock() throws Exception {
        // Deploy scripts first
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Manually insert a deployment lock to simulate active deployment
        dbDeployJdbcTemplate.update("""
                INSERT INTO database_lock (
                    lock_key, lock_owner, lock_acquired_at, lock_expires_at, is_active
                ) VALUES (
                    'db_deploy_tool_lock', 'test-user', datetime('now'), 
                    datetime('now', '+1 hour'), 1
                )
                """);

        // Run status
        DatabaseStatus status = deployManager.status();

        // Verify status with deployment lock
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertTrue(status.isDeploymentInProgress(), "Deployment should be in progress");
        assertEquals("test-user", status.getDeploymentLockOwner(),
                "Lock owner should match");
        assertNotNull(status.getDeploymentLockAcquiredAt(),
                "Lock acquired time should be set");
        assertNotNull(status.getDeploymentLockExpiresAt(),
                "Lock expires time should be set");
        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
                "Current tag should still be the deployed tag");
    }

    @Test
    @DisplayName("Test comprehensive status fields")
    void testShowStatusComprehensiveFields() throws Exception {
        // Deploy scripts first
        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
        String tagName = "1.0.0.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
                "Deployment should complete without errors");

        // Run status
        DatabaseStatus status = deployManager.status();

        // Verify comprehensive status fields
        assertNotNull(status, "Status should not be null");
        assertTrue(status.isDatabaseConnected(), "Database should be connected");
        assertTrue(status.isDatabaseHealthy(), "Database should be healthy");
        assertNotNull(status.getDatabaseVersion(), "Database version should be set");
        assertTrue(status.getConnectionResponseTime() >= 0, "Response time should be non-negative");
        assertTrue(status.isConfigurationValid(), "Configuration should be valid");
        assertNotNull(status.getConfigurationMessage(), "Configuration message should be set");

        // Verify deployment state
        assertEquals("1.0.0.20231110.1", status.getCurrentTag());
        assertNotNull(status.getLastDeploymentTime(), "Last deployment time should be set");
        assertTrue(status.getExecutedScripts() > 0, "Should have executed scripts");

        // Verify script details
        assertFalse(status.getExecutedScriptNames().isEmpty(), "Executed script names should not be empty");
        assertTrue(status.getFailedScriptNames().isEmpty(), "Failed script names should be empty");

        // Verify recent deployments
        assertFalse(status.getRecentDeployments().isEmpty(), "Recent deployments should not be empty");

        // Verify deployment history entry details
        DatabaseStatus.DeploymentHistoryEntry latest = status.getRecentDeployments().get(0);
        assertEquals("1.0.0.20231110.1", latest.getTagName());
        assertEquals("SUCCESS", latest.getStatus());
        assertTrue(latest.getScriptCount() > 0, "Script count should be positive");
        assertNotNull(latest.getDeploymentTime(), "Deployment time should be set");
    }
}