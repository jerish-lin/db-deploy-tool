package com.scb.mrp.schemaflow.dbdeploy.integration.status;

import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.integration.SQLiteDeployTestBase;
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

        // Verify basic connection info
        assertNotNull(status, "Status should not be null");

        // Verify script summary
        assertNotNull(status.getScriptSummary(), "Script summary should not be null");
        assertEquals(0, status.getScriptSummary().getTotalScripts(),
                "Total scripts should be 0 on fresh database");
        assertEquals(0, status.getScriptSummary().getExecutedScripts(),
                "Executed scripts should be 0 on fresh database");
        assertEquals(0, status.getScriptSummary().getFailedScripts(),
                "Failed scripts should be 0 on fresh database");
        assertEquals(0, status.getScriptSummary().getRolledBackScripts(),
                "Rolled back scripts should be 0 on fresh database");
        assertNotNull(status.getScriptSummary().getScripts(),
                "Scripts list should not be null");
        assertTrue(status.getScriptSummary().getScripts().isEmpty(),
                "Scripts list should be empty on fresh database");

        // Verify lock info
        assertNotNull(status.getLockInfo(), "Lock info should not be null");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");

        // Verify recent audit history
        assertNotNull(status.getRecentAuditHistory(), "Recent audit history should not be null");
        assertTrue(status.getRecentAuditHistory().isEmpty(),
                "Recent audit history should be empty on fresh database");
    }

    @Test
    @DisplayName("Test status after deployment")
    void testShowStatusAfterDeployment() throws Exception {
        // Deploy scripts first
        String changelogPath = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, false),
                "Deployment should complete without errors");

        // Run status after deployment
        DatabaseStatus status = deployManager.status();

        // Verify basic connection info
        assertNotNull(status, "Status should not be null");

        // Verify script summary - should have 4 scripts
        assertNotNull(status.getScriptSummary(), "Script summary should not be null");
        assertEquals(4, status.getScriptSummary().getTotalScripts(),
                "Total scripts should be 4 after deployment");
        assertEquals(4, status.getScriptSummary().getExecutedScripts(),
                "Executed scripts should be 4 after deployment");
        assertEquals(0, status.getScriptSummary().getFailedScripts(),
                "No scripts should be failed after successful deployment");
        assertEquals(0, status.getScriptSummary().getRolledBackScripts(),
                "No scripts should be rolled back after successful deployment");

        // Verify scripts list
        assertNotNull(status.getScriptSummary().getScripts(),
                "Scripts list should not be null");
        assertEquals(4, status.getScriptSummary().getScripts().size(),
                "Scripts list size should be 4");

        // Verify all scripts have SUCCESS status
        for (DatabaseStatus.ChangeLogScriptStatus scriptStatus : status.getScriptSummary().getScripts()) {
            assertEquals(ScriptExecutionStatus.SUCCESS, scriptStatus.getLatestStatus(),
                    "All scripts should have SUCCESS status after successful deployment");
        }

        // Verify lock info
        assertNotNull(status.getLockInfo(), "Lock info should not be null");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");

        // Verify recent audit history - should have 4 entries
        assertNotNull(status.getRecentAuditHistory(), "Recent audit history should not be null");
        assertEquals(4, status.getRecentAuditHistory().size(),
                "Recent audit history should have 4 entries");

        // Verify all audit entries have SUCCESS status
        for (DatabaseStatus.AuditHistoryEntry entry : status.getRecentAuditHistory()) {
            assertEquals(ScriptExecutionStatus.SUCCESS, entry.getExecutionStatus(),
                    "All audit entries should have SUCCESS status after successful deployment");
            assertNotNull(entry.getAuditId(), "Audit ID should not be null");
            assertNotNull(entry.getScriptName(), "Script name should not be null");
            assertNotNull(entry.getExecutionTime(), "Execution time should not be null");
            assertNotNull(entry.getExecutionDurationMs(), "Execution duration should not be null");
            assertNull(entry.getErrorMessage(), "Error message should be null for successful deployments");
        }
    }

    @Test
    @DisplayName("Test status after rollback")
    void testShowStatusAfterRollback() throws Exception {
        // Deploy scripts first
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Deploy v2
        String changelogPathV2 = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "Second deployment should complete without errors");

        // Rollback to v1
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1, false),
                "Rollback should complete without errors");

        // Run status after rollback
        DatabaseStatus status = deployManager.status();

        // Verify basic connection info
        assertNotNull(status, "Status should not be null");

        // Verify script summary - should have 5 total scripts (4 from v1 + 1 from v2)
        assertNotNull(status.getScriptSummary(), "Script summary should not be null");
        assertEquals(5, status.getScriptSummary().getTotalScripts(),
                "Total scripts should be 5 after rollback");
        assertEquals(4, status.getScriptSummary().getExecutedScripts(),
                "Executed scripts should be 4 (v1 scripts) after rollback");
        assertEquals(0, status.getScriptSummary().getFailedScripts(),
                "No scripts should be failed after rollback");
        assertEquals(1, status.getScriptSummary().getRolledBackScripts(),
                "Rolled back scripts should be 1 (v2 script) after rollback");

        // Verify scripts list
        assertNotNull(status.getScriptSummary().getScripts(),
                "Scripts list should not be null");
        assertEquals(5, status.getScriptSummary().getScripts().size(),
                "Scripts list size should be 5");

        // Verify lock info
        assertNotNull(status.getLockInfo(), "Lock info should not be null");
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");

        // Verify recent audit history - should contain entries for all operations
        // Total operations: 4 v1 deploy + 1 v2 deploy + 1 v2 rollback = 6 entries
        // But getRecentAuditHistory returns last 10, so we should see up to 10
        assertNotNull(status.getRecentAuditHistory(), "Recent audit history should not be null");
        assertTrue(status.getRecentAuditHistory().size() >= 6,
                "Recent audit history should have at least 6 entries (4 v1 deploy + 1 v2 deploy + 1 v2 rollback)");
        assertTrue(status.getRecentAuditHistory().size() <= 10,
                "Recent audit history should have at most 10 entries");

        // Count entries by status
        long successCount = status.getRecentAuditHistory().stream()
                .filter(e -> ScriptExecutionStatus.SUCCESS.equals(e.getExecutionStatus()))
                .count();
        long rolledBackCount = status.getRecentAuditHistory().stream()
                .filter(e -> ScriptExecutionStatus.ROLLED_BACK.equals(e.getExecutionStatus()))
                .count();

        assertTrue(successCount > 0, "Should have SUCCESS entries");
        assertTrue(rolledBackCount > 0, "Should have ROLLED_BACK entries");
    }

    @Test
    @DisplayName("Test status with multiple operations")
    void testShowStatusWithMultipleOperations() throws Exception {
        // Deploy v1
        String changelogPathV1 = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "First deployment should complete without errors");

        // Deploy v2
        String changelogPathV2 = "classpath:sqlite-scripts/sqlite-test-changelog-v2.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, false),
                "Second deployment should complete without errors");

        // Rollback to v1
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1, false),
                "Rollback should complete without errors");

        // Check status after rollback
        DatabaseStatus status = deployManager.status();

        // Verify all status fields are populated
        assertNotNull(status, "Status should not be null");

        // All nested objects should not be null
        assertNotNull(status.getScriptSummary(), "Script summary should not be null");
        assertNotNull(status.getLockInfo(), "Lock info should not be null");
        assertNotNull(status.getRecentAuditHistory(), "Recent audit history should not be null");

        // Verify specific state
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress");
        assertEquals(0, status.getScriptSummary().getFailedScripts(),
                "No scripts should be failed after rollback");
        assertEquals(1, status.getScriptSummary().getRolledBackScripts(),
                "Rolled back scripts should be 1 after rollback");
    }

    @Test
    @DisplayName("Test status after failed deployment")
    void testShowStatusAfterFailedDeployment() throws Exception {
        // Deploy working scripts first
        String changelogPathV1 = "classpath:sqlite-scripts-failure/sqlite-test-changelog-first3.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, false),
                "Initial deployment should complete without errors");

        // Attempt to deploy with a failing script
        String changelogPathV2 = "classpath:sqlite-scripts-failure/sqlite-test-changelog.yml";

        assertThrows(Exception.class, () -> deployManager.deploy(changelogPathV2, false),
                "Deployment should fail due to intentional SQL error");

        // Check status after failed deployment
        DatabaseStatus status = deployManager.status();

        // Verify all status fields are populated
        assertNotNull(status, "Status should not be null");

        // Basic connection info
        // All nested objects should not be null
        assertNotNull(status.getScriptSummary(), "Script summary should not be null");
        assertNotNull(status.getLockInfo(), "Lock info should not be null");
        assertNotNull(status.getRecentAuditHistory(), "Recent audit history should not be null");

        // Verify specific state
        assertFalse(status.getLockInfo().isActive(), "No deployment should be in progress after failure");
        assertTrue(status.getScriptSummary().getFailedScripts() > 0,
                "Failed scripts should be greater than 0 after failed deployment");

        // Verify failed scripts in the list
        long failedScriptCount = status.getScriptSummary().getScripts().stream()
                .filter(s -> ScriptExecutionStatus.FAILED.equals(s.getLatestStatus()))
                .count();
        assertTrue(failedScriptCount > 0,
                "Failed scripts count should be > 0 after failed deployment");

        // Verify recent audit history contains failed entries
        long failedAuditCount = status.getRecentAuditHistory().stream()
                .filter(e -> ScriptExecutionStatus.FAILED.equals(e.getExecutionStatus()))
                .count();
        assertTrue(failedAuditCount > 0,
                "Failed audit entries should be > 0 after failed deployment");

        // Verify failed audit entries have error messages
        status.getRecentAuditHistory().stream()
                .filter(e -> ScriptExecutionStatus.FAILED.equals(e.getExecutionStatus()))
                .forEach(entry -> {
                    assertNotNull(entry.getErrorMessage(), "Failed entries should have error messages");
                    assertNotNull(entry.getAuditId(), "Failed entries should have audit IDs");
                    assertNotNull(entry.getScriptName(), "Failed entries should have script names");
                });
    }

    @Test
    @DisplayName("Test status fields consistency")
    void testStatusFieldsConsistency() throws Exception {
        // Deploy scripts
        String changelogPath = "classpath:sqlite-scripts/sqlite-test-changelog.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, false),
                "Deployment should complete without errors");

        DatabaseStatus status = deployManager.status();

        // Verify consistency between different status fields
        assertEquals(status.getScriptSummary().getTotalScripts(),
                status.getScriptSummary().getScripts().size(),
                "Total scripts should match scripts list size");

        assertEquals(status.getScriptSummary().getExecutedScripts(),
                (int) status.getScriptSummary().getScripts().stream()
                        .filter(s -> ScriptExecutionStatus.SUCCESS.equals(s.getLatestStatus()))
                        .count(),
                "Executed scripts should match SUCCESS status count");

        assertEquals(status.getScriptSummary().getFailedScripts(),
                (int) status.getScriptSummary().getScripts().stream()
                        .filter(s -> ScriptExecutionStatus.FAILED.equals(s.getLatestStatus()))
                        .count(),
                "Failed scripts should match FAILED status count");

        assertEquals(status.getScriptSummary().getRolledBackScripts(),
                (int) status.getScriptSummary().getScripts().stream()
                        .filter(s -> ScriptExecutionStatus.ROLLED_BACK.equals(s.getLatestStatus()))
                        .count(),
                "Rolled back scripts should match ROLLED_BACK status count");
    }
}