package org.jerish.dbdeploy;

import org.jerish.dbdeploy.config.DatabaseConfig;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for failure scenarios in the database deployment tool using SQLite.
 * This test verifies that the deployment tool handles failures correctly by:
 * 1. Deploying working scripts up to v1.0.1
 * 2. Attempting to deploy v1.0.2 which contains a script that will fail
 * 3. Verifying the tool stops at the failed script and doesn't execute subsequent scripts
 * 4. Verifying rollback works correctly after failure
 * 5. Verifying audit information correctly tracks the failure
 */
public class SQLiteFailureTest {

    private static final String DB_FILE = "testdb.sqlite";
    private DatabaseConnectionManager connectionManager;

    @BeforeEach
    void setUp() throws SQLException {
        // Clean up any existing database file
        cleanupDatabase();

        // Initialize database connection manager
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setUrl("jdbc:sqlite:" + DB_FILE);
        dbConfig.setDriver("org.sqlite.JDBC");

        connectionManager = new DatabaseConnectionManager(dbConfig);
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.close();
        }
//        cleanupDatabase();
    }

    private void cleanupDatabase() {
        File dbFile = new File(DB_FILE);
        if (dbFile.exists() && !dbFile.delete()) {
            System.err.println("Warning: Could not delete existing database file");
        }
    }

    @Test
    @DisplayName("Test deployment failure handling")
    void testDeploymentFailure() throws Exception {
        // First deploy up to v1.0.1 (working scripts)
        String[] deployArgsV1_0_1 = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-first3.yml",
                "--tag", "1.0.1.20231110.1",
                
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgsV1_0_1),
                "Initial deployment up to v1.0.1 should complete without errors");

        // Verify v1.0.1 deployment was successful
        verifySuccessfulDeploymentV1_0_1();

        // Now attempt to deploy v1.0.2 which contains a failing script
        String[] deployArgsV1_0_2 = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog.yml",
                "--tag", "1.0.2.20231110.1",
                
                "--verbose"
        };

        // This should fail due to the intentional SQL error in create-user-orders-view
        assertThrows(Exception.class, () -> DatabaseDeployTool.main(deployArgsV1_0_2),
                "Deployment should fail due to intentional SQL error");

        // Verify failure state
        verifyFailureState("feature-12350-create-user-orders-view");
    }

    @Test
    @DisplayName("Test rollback after failure")
    void testRollbackAfterFailure() throws Exception {
        // First deploy up to v1.0.1
        String[] deployArgsV1_0_1 = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-first3.yml",
                "--tag", "1.0.1.20231110.1",
                
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgsV1_0_1),
                "Initial deployment up to v1.0.1 should complete without errors");

        // Attempt to deploy v1.0.2 which will fail
        String[] deployArgsV1_0_2 = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog.yml",
                "--tag", "1.0.2.20231110.1",
                
                "--verbose"
        };

        assertThrows(Exception.class, () -> DatabaseDeployTool.main(deployArgsV1_0_2),
                "Deployment should fail due to intentional SQL error");

        // Verify failure state
        verifyFailureState("feature-12350-create-user-orders-view");

        // Now rollback to v1.0.1
        String[] rollbackArgs = {
                "--action", "ROLLBACK",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog.yml",
                "--tag", "1.0.1.20231110.1",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(rollbackArgs),
                "Rollback should complete without errors");

        // Verify rollback was successful
        verifyRollbackAfterFailure();
    }

    private void verifySuccessfulDeploymentV1_0_1() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify all expected tables exist
            String[] expectedTables = {"users", "orders", "db_change_log", "deployment_tags", "database_lock"};

            for (String tableName : expectedTables) {
                String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next(),
                                String.format("Table '%s' should exist after v1.0.1 deployment", tableName));
                    }
                }
            }

            // Verify v1.0.1 scripts were executed successfully
            String[] v1_0_1_Scripts = {
                    "feature-12346/feature-12346-create-users-table",
                    "feature-12347-create-orders-table",
                    "feature-12348/feature-12348-insert-sample-users"
            };

            for (String scriptId : v1_0_1_Scripts) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM db_change_log WHERE script_name=? AND execution_status='SUCCESS'")) {
                    stmt.setString(1, scriptId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("Script '%s' should be executed successfully", scriptId));
                    }
                }
            }

            // Verify data was inserted
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM users")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "Should have 5 users after v1.0.1 deployment");
                }
            }

            // Verify deployment tag was created
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.1' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Deployment tag 1.0.1.20231110.1 should be active");
                }
            }
        }
    }

    private void verifyFailureState(String failedSql) throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify that the first script in v1.0.2 (add-user-email-index) was executed successfully
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_name='feature-12349-add-user-email-index' AND execution_status='SUCCESS'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "First script in v1.0.2 should be executed successfully");
                }
            }

            // Verify that the failing script (create-user-orders-view) was marked as FAILED
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_name='%s' AND execution_status='FAILED'".formatted(failedSql))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Failing script should be marked as FAILED");
                }
            }

            // Verify that the third script in v1.0.2 (add-order-status-index) was NOT executed
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_name='feature-12351-add-order-status-index'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 0,
                            "Third script in v1.0.2 should not be executed after failure");
                }
            }

            // Verify that the deployment tag for v1.0.2 was NOT created
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.2.20231110.1'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 0,
                            "Deployment tag for failed version should not be created");
                }
            }

            // Verify that the user email index was created (first script succeeded)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_users_email'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(),
                            "User email index should exist (first script succeeded)");
                }
            }

            // Verify that the order status index was NOT created (third script didn't execute)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_orders_status'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertFalse(rs.next(),
                            "Order status index should not exist (third script didn't execute)");
                }
            }
        }
    }

    private void verifyRollbackAfterFailure() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify that the user email index was dropped (rollback of successful v1.0.2 script)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_users_email'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertFalse(rs.next(),
                            "User email index should be dropped after rollback");
                }
            }

            // Verify that the add-user-email-index script is marked as ROLLED_BACK
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_name='feature-12349-add-user-email-index' AND execution_status='ROLLED_BACK'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "feature-12349-add-user-email-index script should be marked as ROLLED_BACK");
                }
            }

            // Verify that v1.0.1 scripts are still marked as SUCCESS
            String[] v1_0_1_Scripts = {
                    "feature-12346/feature-12346-create-users-table",
                    "feature-12347-create-orders-table",
                    "feature-12348/feature-12348-insert-sample-users"
            };

            for (String scriptId : v1_0_1_Scripts) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM db_change_log WHERE script_name=? AND execution_status='SUCCESS'")) {
                    stmt.setString(1, scriptId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("Script '%s' should still be marked as SUCCESS", scriptId));
                    }
                }
            }

            // Verify that v1.0.1 tag is still active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='1.0.1.20231110.1' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "1.0.1.20231110.1 tag should still be active after rollback");
                }
            }

            // Verify data is still intact
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM users")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "Users data should still exist after rollback");
                }
            }
        }
    }

    @Test
    @DisplayName("Test verification failure handling")
    void testVerificationFailure() throws Exception {
        // First deploy working scripts up to feature-12348 (before the failing verification)
        String[] deployArgsInitial = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-first3.yml",
                "--tag", "1.0.1.20231110.1",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgsInitial),
                "Initial deployment should complete without errors");

        // Verify initial deployment was successful
        verifySuccessfulDeploymentV1_0_1();

        // Now attempt to deploy including the script with failing verification (feature-12349)
        String[] deployArgsWithVerificationFailure = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-verify-failure.yml",
                "--tag", "1.0.2.20231110.1",
                "--verbose"
        };

        // This should fail due to the intentional SQL error in create-user-orders-view
        assertThrows(Exception.class, () -> DatabaseDeployTool.main(deployArgsWithVerificationFailure),
                "Deployment should fail due to intentional SQL error");

        // Verify failure state
        verifyFailureState("verify-failure/feature-12350-create-user-orders-view");
    }
}