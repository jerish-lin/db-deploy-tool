package org.example;

import org.example.config.DatabaseConfig;
import org.example.database.DatabaseConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the database rollback functionality using SQLite.
 * This test verifies that the rollback process works correctly by:
 * 1. Running a deployment
 * 2. Rolling back to a previous tag
 * 3. Verifying the rollback was successful
 */
public class SQLiteRollbackTest {

    //    private static final String DB_FILE = "testdb-rollback.sqlite";
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
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        File dbFile = new File(DB_FILE);
        if (dbFile.exists() && !dbFile.delete()) {
            System.err.println("Warning: Could not delete existing database file");
        }
    }

    @Test
    @DisplayName("Test basic rollback functionality")
    void testBasicRollback() throws Exception {
        // First deploy
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts",
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Deployment should complete without errors");

        // Verify deployment
        try (Connection connection = connectionManager.getConnection()) {
            // Check tables exist
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('employees', 'departments')")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 2,
                            "Should have employees and departments tables");
                }
            }

            // Check data exists
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM employees")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 10,
                            "Should have 10 employees");
                }
            }
        }

        // Second deploy with v1.0.3 using sqlite-scripts-v2
        String[] deployV2Args = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-v2/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts-v2",
                "--tag", "v1.0.3",
                "--build-version", "1.0.1.20231110.1",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployV2Args),
                "Second deployment should complete without errors");

        // Verify v1.0.3 state - projects table should exist
        try (Connection connection = connectionManager.getConnection()) {
            // Check projects table exists
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Projects table should exist");
                }
            }

            // Check projects data
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM projects")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 3,
                            "Should have 3 projects");
                }
            }
        }

        // Now rollback to v1.0.0
        String[] rollbackArgs = {
                "--action", "ROLLBACK",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--tag", "v1.0.2",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(rollbackArgs),
                "Rollback should complete without errors");

//         Verify rollback - projects table should be gone but employees and departments should remain
        try (Connection connection = connectionManager.getConnection()) {
            // Check projects table is gone
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 0,
                            "Projects table should be dropped by rollback");
                }
            }

            // Check employees table still exists
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM employees")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 10,
                            "Employees table should still have 10 records");
                }
            }

            // Check departments table still exists
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM departments")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "Departments table should still have 5 records");
                }
            }

            // Check employee salary index still exists (v1.0.2 state)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_employees_salary'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Employee salary index should still exist");
                }
            }

            // Verify changelog and tags after rollback
            verifyBasicRollbackAuditState();
        }
    }

    private void verifyBasicRollbackAuditState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify v1.0.2 tag is still active (target of rollback)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.2' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Target tag 'v1.0.2' should still be active after rollback");
                }
            }

            // Verify v1.0.3 tag is deactivated (rolled back)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.3' AND is_active=0")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Rolled back tag 'v1.0.3' should be deactivated");
                }
            }

            // Verify initial tag is still active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='initial' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Initial tag should still be active");
                }
            }

            // Verify add-projects-table script is marked as ROLLED_BACK
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_id='add-projects-table' AND execution_status='ROLLED_BACK'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "add-projects-table script should be marked as ROLLED_BACK");
                }
            }

            // Verify v1.0.2 scripts are still marked as SUCCESS
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE tag_name='v1.0.2' AND execution_status='SUCCESS'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 4,
                            "v1.0.2 scripts should still be marked as SUCCESS");
                }
            }
        }
    }

    @Test
    @DisplayName("Test deploy then rollback to initial version")
    void testDeployThenRollbackToInitial() throws Exception {
        // Deploy all scripts
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts",
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Deployment should complete without errors");

        // Verify deployment was successful
        verifyDeploymentState();

        // Rollback to initial version
        String[] rollbackArgs = {
                "--action", "ROLLBACK",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--tag", "initial",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(rollbackArgs),
                "Rollback to initial should complete without errors");

        // Verify rollback to initial state
        verifyInitialRollbackState();
    }

    private void verifyDeploymentState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify all tables exist after deployment
            String[] expectedTables = {"employees", "departments", "db_change_log", "deployment_tags", "database_lock"};

            for (String tableName : expectedTables) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("Table '%s' should exist after deployment", tableName));
                    }
                }
            }

            // Verify data was inserted
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM employees")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 10,
                            "Should have 10 employees after deployment");
                }
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM departments")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "Should have 5 departments after deployment");
                }
            }

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
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?")) {
                    stmt.setString(1, indexName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("Index '%s' should exist after deployment", indexName));
                    }
                }
            }

            // Verify deployment tag was created
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.2'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Deployment tag 'v1.0.2' should be created");
                }
            }

            // Verify scripts were executed successfully
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE execution_status='SUCCESS' AND tag_name='v1.0.2'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 4,
                            "All 4 scripts should be executed successfully for v1.0.2");
                }
            }
        }
    }

    private void verifyInitialRollbackState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify user tables are gone after rollback to initial
            String[] droppedTables = {"employees", "departments"};

            for (String tableName : droppedTables) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 0,
                                String.format("Table '%s' should be dropped after rollback to initial", tableName));
                    }
                }
            }

            // Verify system tables still exist
            String[] systemTables = {"db_change_log", "deployment_tags", "database_lock"};

            for (String tableName : systemTables) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("System table '%s' should still exist after rollback", tableName));
                    }
                }
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
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?")) {
                    stmt.setString(1, indexName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 0,
                                String.format("Index '%s' should be dropped after rollback to initial", indexName));
                    }
                }
            }

            // Verify script execution status was updated to ROLLED_BACK
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE execution_status='ROLLED_BACK' AND tag_name='v1.0.2'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 4,
                            "All 4 scripts should be marked as ROLLED_BACK");
                }
            }

            // Verify initial tag still exists and is active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='initial' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Initial tag should still exist and be active");
                }
            }

            // Verify deployment tag was deactivated
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.2' AND is_active=0")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Deployment tag 'v1.0.2' should be deactivated after rollback");
                }
            }
        }
    }

    @Test
    @DisplayName("Test deploy, rollback, then deploy again with same changes")
    void testDeployRollbackRedeploy() throws Exception {
        // First deployment
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts",
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "First deployment should complete without errors");

        // Verify first deployment
        verifyFirstDeploymentState();

        // Second deploy with v1.0.3 using sqlite-scripts-v2
        String[] deployV2Args = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-v2/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts-v2",
                "--tag", "v1.0.3",
                "--build-version", "1.0.1.20231110.1",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployV2Args),
                "Second deployment should complete without errors");

        // Verify second deployment
        verifySecondDeploymentState();

        // Rollback to v1.0.2
        String[] rollbackArgs = {
                "--action", "ROLLBACK",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--tag", "v1.0.2",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(rollbackArgs),
                "Rollback should complete without errors");

        // Verify rollback state
        verifyRollbackState();

        // Deploy v1.0.3 again
        String[] redeployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts-v2/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts-v2",
                "--tag", "v1.0.3-redeploy",
                "--build-version", "1.0.1.20231110.2",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(redeployArgs),
                "Redeployment should complete without errors");

        // Verify redeployment state
        verifyRedeploymentState();
    }

    private void verifyFirstDeploymentState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify v1.0.2 tag exists and is active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.2' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "v1.0.2 tag should be created and active");
                }
            }

            // Verify employees and departments tables exist
            String[] expectedTables = {"employees", "departments"};
            for (String tableName : expectedTables) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("Table '%s' should exist after first deployment", tableName));
                    }
                }
            }

            // Verify data exists
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM employees")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 10,
                            "Should have 10 employees");
                }
            }
        }
    }

    private void verifySecondDeploymentState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify v1.0.3 tag exists and is active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.3' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "v1.0.3 tag should be created and active");
                }
            }

            // Verify projects table exists
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Projects table should exist after second deployment");
                }
            }

            // Verify projects data
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM projects")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 3,
                            "Should have 3 projects");
                }
            }
        }
    }

    private void verifyRollbackState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify v1.0.2 tag is still active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.2' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "v1.0.2 tag should still be active after rollback");
                }
            }

            // Verify v1.0.3 tag is deactivated
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.3' AND is_active=0")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "v1.0.3 tag should be deactivated after rollback");
                }
            }

            // Verify projects table is gone
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 0,
                            "Projects table should be dropped after rollback");
                }
            }

            // Verify add-projects-table script is marked as ROLLED_BACK
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_id='add-projects-table' AND execution_status='ROLLED_BACK'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "add-projects-table script should be marked as ROLLED_BACK");
                }
            }
        }
    }

    private void verifyRedeploymentState() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify v1.0.3-redeploy tag exists and is active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.3-redeploy' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "v1.0.3-redeploy tag should be created and active");
                }
            }

            // Verify projects table exists again
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='projects'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Projects table should exist after redeployment");
                }
            }

            // Verify projects data exists again
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM projects")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 3,
                            "Should have 3 projects after redeployment");
                }
            }

            // Verify add-projects-table script is executed successfully again
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE script_id='add-projects-table' AND tag_name='v1.0.3-redeploy' AND execution_status='SUCCESS'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "add-projects-table script should be executed successfully in redeployment");
                }
            }

            // Verify v1.0.2 tag is still active
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.2' AND is_active=1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "v1.0.2 tag should still be active after redeployment");
                }
            }

            // Verify v1.0.3 tag is still deactivated
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.3' AND is_active=0")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Original v1.0.3 tag should remain deactivated");
                }
            }
        }
    }
}