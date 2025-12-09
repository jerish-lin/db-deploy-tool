package org.jerish.dbdeploy;

import org.jerish.dbdeploy.config.DatabaseConfig;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the showStatus action using SQLite.
 * This test verifies that the status command works correctly by:
 * 1. Running a deployment
 * 2. Checking the status output
 * 3. Running a rollback
 * 4. Checking the status output again
 */
public class SQLiteStatusTest {

    private static final String DB_FILE = "testdb.sqlite";
    private DatabaseConnectionManager connectionManager;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() throws SQLException {
        // Clean up any existing database file
        cleanupDatabase();

        // Initialize database connection manager
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setUrl("jdbc:sqlite:" + DB_FILE);
        dbConfig.setDriver("org.sqlite.JDBC");

        connectionManager = new DatabaseConnectionManager(dbConfig);

        // Redirect System.out to capture status output
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.close();
        }
        // Restore original System.out and System.err
        System.setOut(originalOut);
        System.setErr(originalErr);
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        File dbFile = new File(DB_FILE);
        if (dbFile.exists() && !dbFile.delete()) {
            System.err.println("Warning: Could not delete existing database file");
        }
    }

    @Test
    @DisplayName("Test showStatus action on fresh database")
    void testShowStatusOnFreshDatabase() throws Exception {
        // Run status on fresh database
        String[] statusArgs = {
                "--action", "STATUS",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(statusArgs),
                "Status command should complete without errors");

        String output = outContent.toString();

        // Verify status output for fresh database
        assertTrue(output.contains("Database Deployment Status"),
                "Status output should contain header");
        assertTrue(output.contains("No scripts have been executed yet"),
                "Status should indicate no scripts executed");
    }

    @Test
    @DisplayName("Test showStatus after deployment")
    void testShowStatusAfterDeployment() throws Exception {
        // Deploy scripts first
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Deployment should complete without errors");

        // Clear output buffer
        outContent.reset();

        // Run status after deployment
        String[] statusArgs = {
                "--action", "STATUS",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(statusArgs),
                "Status command should complete without errors");

        String output = outContent.toString();

        // Verify status output after deployment
        assertTrue(output.contains("Database Deployment Status"),
                "Status output should contain header");
        assertTrue(output.contains("Current deployment tag: v1.0.2"),
                "Status should show current tag");
        assertTrue(output.contains("Total scripts executed: 5"),
                "Status should show total scripts (4 + initial)");
        assertTrue(output.contains("Successful: 5"),
                "Status should show successful scripts");
        assertTrue(output.contains("Failed: 0"),
                "Status should show no failed scripts");
        assertTrue(output.contains("Rolled back: 0"),
                "Status should show no rolled back scripts");
    }

    @Test
    @DisplayName("Test showStatus after rollback")
    void testShowStatusAfterRollback() throws Exception {
        // Deploy scripts first
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Deployment should complete without errors");

        // Deploy v1.0.3
        String[] deployV2Args = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog-v2.yml",
                
                "--tag", "v1.0.3",
                "--build-version", "1.0.1.20231110.1",
                "--environment", "test"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployV2Args),
                "Second deployment should complete without errors");

        // Rollback to v1.0.2
        String[] rollbackArgs = {
                "--action", "ROLLBACK",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--tag", "v1.0.2"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(rollbackArgs),
                "Rollback should complete without errors");

        // Clear output buffer
        outContent.reset();

        // Run status after rollback
        String[] statusArgs = {
                "--action", "STATUS",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(statusArgs),
                "Status command should complete without errors");

        String output = outContent.toString();

        // Verify status output after rollback
        assertTrue(output.contains("Database Deployment Status"),
                "Status output should contain header");
        assertTrue(output.contains("Current deployment tag: v1.0.2"),
                "Status should show current tag after rollback");
        assertTrue(output.contains("Total scripts executed: 6"),
                "Status should show total scripts (5 + 1 rolled back)");
        assertTrue(output.contains("Successful: 5"),
                "Status should show successful scripts");
        assertTrue(output.contains("Rolled back: 1"),
                "Status should show rolled back scripts");
        assertTrue(output.contains("Failed: 0"),
                "Status should show no failed scripts");
    }

    @Test
    @DisplayName("Test showStatus after rollback to initial")
    void testShowStatusAfterRollbackToInitial() throws Exception {
        // Deploy scripts first
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Deployment should complete without errors");

        // Rollback to initial
        String[] rollbackArgs = {
                "--action", "ROLLBACK",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--tag", "initial"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(rollbackArgs),
                "Rollback to initial should complete without errors");

        // Clear output buffer
        outContent.reset();

        // Run status after rollback to initial
        String[] statusArgs = {
                "--action", "STATUS",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(statusArgs),
                "Status command should complete without errors");

        String output = outContent.toString();

        // Verify status output after rollback to initial
        assertTrue(output.contains("Database Deployment Status"),
                "Status output should contain header");
        assertTrue(output.contains("Current deployment tag: initial"),
                "Status should show initial tag after rollback");
        assertTrue(output.contains("Total scripts executed: 5"),
                "Status should show total scripts");
        assertTrue(output.contains("Successful: 1"),
                "Status should show only initial script as successful");
        assertTrue(output.contains("Rolled back: 4"),
                "Status should show all user scripts as rolled back");
        assertTrue(output.contains("Failed: 0"),
                "Status should show no failed scripts");
    }

    @Test
    @DisplayName("Test showStatus with failed script")
    void testShowStatusWithFailedScript() throws Exception {
        // Create a script that will fail
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                
                "--tag", "v1.0.2",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Deployment should complete without errors");

        // Manually insert a failed script entry in the database
        try (Connection connection = connectionManager.getConnection()) {
            String sql = """
                        INSERT INTO db_change_log (
                            script_id, script_name, script_path, script_checksum,
                            execution_status, execution_time, execution_duration_ms,
                            rollback_script_path, rollback_script_content,
                            tag_name, created_at, updated_at
                        ) VALUES (
                            'test-failed-script', 'Test Failed Script', 'test.sql', 'failed',
                            'FAILED', datetime('now'), 0,
                            '', '', 'v1.0.2', datetime('now'), datetime('now')
                        )
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.executeUpdate();
            }
        }

        // Clear output buffer
        outContent.reset();

        // Run status
        String[] statusArgs = {
                "--action", "STATUS",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(statusArgs),
                "Status command should complete without errors");

        String output = outContent.toString();

        // Verify status output with failed script
        assertTrue(output.contains("Database Deployment Status"),
                "Status output should contain header");
        assertTrue(output.contains("Total scripts executed: 6"),
                "Status should show total scripts");
        assertTrue(output.contains("Successful: 5"),
                "Status should show successful scripts");
        assertTrue(output.contains("Failed: 1"),
                "Status should show failed scripts");
        assertTrue(output.contains("Rolled back: 0"),
                "Status should show no rolled back scripts");
    }
}