//package org.jerish.dbdeploy;
//
//import org.jerish.dbdeploy.entity.DatabaseStatus;
//import org.jerish.dbdeploy.service.DatabaseDeployManager;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
/// **
// * Unit test for the status functionality using SQLite.
// * This test verifies that the status command works correctly by:
// * 1. Running a deployment
// * 2. Checking the DatabaseStatus object
// * 3. Running a rollback
// * 4. Checking the DatabaseStatus object again
// */
//public class SQLiteStatusTest extends SQLiteDeployTestBase{
//
//    @Test
//    @DisplayName("Test status action on fresh database")
//    void testShowStatusOnFreshDatabase() throws Exception {
//        // Run status on fresh database
//        DatabaseStatus status = deployManager.status();
//
//        // Verify status for fresh database
//        assertNotNull(status, "Status should not be null");
//        assertTrue(status.isDatabaseConnected(), "Database should be connected");
//        assertTrue(status.getCurrentTag() == null || status.getCurrentTag().isEmpty(),
//                "Current tag should be null or empty on fresh database");
//        assertEquals(0, status.getExecutedScripts(),
//                "No scripts should be executed on fresh database");
//        assertEquals(0, status.getFailedScripts(),
//                "No scripts should be failed on fresh database");
//        assertEquals(0, status.getRolledBackScripts(),
//                "No scripts should be rolled back on fresh database");
//        assertTrue(status.getExecutedScriptNames().isEmpty(),
//                "Executed script names should be empty on fresh database");
//    }
//
//    @Test
//    @DisplayName("Test status after deployment")
//    void testShowStatusAfterDeployment() throws Exception {
//        // Deploy scripts first
//        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
//        String tagName = "1.0.0.20231110.1";
//
//        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
//                "Deployment should complete without errors");
//
//        // Run status after deployment
//        DatabaseStatus status = deployManager.status();
//
//        // Verify status after deployment
//        assertNotNull(status, "Status should not be null");
//        assertTrue(status.isDatabaseConnected(), "Database should be connected");
//        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
//                "Current tag should match deployed tag");
//        assertTrue(status.getExecutedScripts() >= 4,
//                "Should have at least 4 executed scripts (including initial)");
//        assertEquals(0, status.getFailedScripts(),
//                "No scripts should be failed after successful deployment");
//        assertEquals(0, status.getRolledBackScripts(),
//                "No scripts should be rolled back after successful deployment");
//        assertFalse(status.getExecutedScriptNames().isEmpty(),
//                "Executed script names should not be empty after deployment");
//        assertNotNull(status.getLastDeploymentTime(),
//                "Last deployment time should be set after deployment");
//    }
//
//    @Test
//    @DisplayName("Test status after rollback")
//    void testShowStatusAfterRollback() throws Exception {
//        // Deploy scripts first
//        String changelogPathV1 = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
//        String tagNameV1 = "1.0.0.20231110.1";
//
//        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1, tagNameV1, false),
//                "First deployment should complete without errors");
//
//        // Deploy 1.0.1.20231110.1
//        String changelogPathV2 = "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml";
//        String tagNameV2 = "1.0.1.20231110.1";
//
//        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV2, tagNameV2, false),
//                "Second deployment should complete without errors");
//
//        // Rollback to 1.0.0.20231110.1
//        String rollbackTagName = "1.0.0.20231110.1";
//
//        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
//                "Rollback should complete without errors");
//
//        // Run status after rollback
//        DatabaseStatus status = deployManager.status();
//
//        // Verify status after rollback
//        assertNotNull(status, "Status should not be null");
//        assertTrue(status.isDatabaseConnected(), "Database should be connected");
//        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
//                "Current tag should be the rollback target tag");
//        assertTrue(status.getExecutedScripts() >= 4,
//                "Should have at least 4 successful scripts after rollback");
//        assertEquals(0, status.getFailedScripts(),
//                "No scripts should be failed after rollback");
//        assertTrue(status.getRolledBackScripts() >= 1,
//                "Should have at least 1 rolled back script");
//        assertFalse(status.getExecutedScriptNames().isEmpty(),
//                "Executed script names should not be empty after rollback");
//    }
//
//    @Test
//    @DisplayName("Test status after rollback to initial")
//    void testShowStatusAfterRollbackToInitial() throws Exception {
//        // Deploy scripts first
//        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
//        String tagName = "1.0.0.20231110.1";
//
//        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
//                "Deployment should complete without errors");
//
//        // Rollback to initial
//        String rollbackTagName = "initial";
//
//        assertDoesNotThrow(() -> deployManager.rollback(rollbackTagName, false),
//                "Rollback to initial should complete without errors");
//
//        // Run status after rollback to initial
//        DatabaseStatus status = deployManager.status();
//
//        // Verify status after rollback to initial
//        assertNotNull(status, "Status should not be null");
//        assertTrue(status.isDatabaseConnected(), "Database should be connected");
//        assertEquals("initial", status.getCurrentTag(),
//                "Current tag should be 'initial' after rollback");
//        assertTrue(status.getRolledBackScripts() >= 4,
//                "Should have at least 4 rolled back scripts");
//        assertEquals(0, status.getFailedScripts(),
//                "No scripts should be failed after rollback to initial");
//        assertTrue(status.getExecutedScripts() >= 1,
//                "Should have at least 1 executed script (initial)");
//    }
//
//    @Test
//    @DisplayName("Test status with failed script")
//    void testShowStatusWithFailedScript() throws Exception {
//        // Deploy scripts first
//        String changelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml";
//        String tagName = "1.0.0.20231110.1";
//
//        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, tagName, false),
//                "Deployment should complete without errors");
//
//        // Manually insert a failed script entry in the database to simulate a failure
//        try (var connection = connectionManager.getConnection()) {
//            String sql = """
//                        INSERT INTO db_change_log (
//                            script_name, script_checksum,
//                            execution_status, execution_time, execution_duration_ms,
//                            rollback_script_content, rollback_verify_script_content,
//                            tag_name, created_at, updated_at
//                        ) VALUES (
//                            'Test Failed Script', 'failed',
//                            'FAILED', datetime('now'), 0,
//                            '', '', '1.0.0.20231110.1', datetime('now'), datetime('now')
//                        )
//                    """;
//
//            try (var stmt = connection.prepareStatement(sql)) {
//                stmt.executeUpdate();
//            }
//        }
//
//        // Run status
//        DatabaseStatus status = deployManager.status();
//
//        // Verify status with failed script
//        assertNotNull(status, "Status should not be null");
//        assertTrue(status.isDatabaseConnected(), "Database should be connected");
//        assertTrue(status.getExecutedScripts() >= 4,
//                "Should have at least 4 successful scripts");
//        assertTrue(status.getFailedScripts() >= 1,
//                "Should have at least 1 failed script");
//        assertEquals("1.0.0.20231110.1", status.getCurrentTag(),
//                "Current tag should still be the deployed tag despite failure");
//    }
//}