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
 * Unit test for the database deployment tool using SQLite.
 * This test verifies that the deployment process works correctly by:
 * 1. Running a full deployment
 * 2. Verifying all tables were created with correct structure
 * 3. Verifying all data was inserted correctly
 * 4. Verifying all indexes were created
 * 5. Verifying audit information is correct
 */
public class SQLiteDeploymentTest {

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
    @DisplayName("Test complete database deployment with SQLite")
    void testCompleteDeployment() throws Exception {
        // Run the deployment
        String[] deployArgs = {
                "--action", "DEPLOY",
                "--database-config", "src/test/resources/sqlite-test-config.yml",
                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
                "--script-base-path", "src/test/resources/sqlite-scripts",
                "--tag", "v1.0.0-unit-test",
                "--build-version", "1.0.0.20231110.1",
                "--environment", "test",
                "--verbose"
        };

        assertDoesNotThrow(() -> DatabaseDeployTool.main(deployArgs),
                "Database deployment should complete without errors");

        // Verify all expected tables exist
        verifyTablesExist();

        // Verify table structures
        verifyTableStructures();

        // Verify data was inserted
        verifyDataInserted();

        // Verify indexes were created
        verifyIndexesCreated();

        // Verify audit information
        verifyAuditInformation();
    }

    private void verifyTablesExist() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            String[] expectedTables = {"employees", "departments", "db_change_log", "deployment_tags", "database_lock"};

            for (String tableName : expectedTables) {
                String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next(),
                                String.format("Table '%s' should exist", tableName));
                    }
                }
            }
        }
    }

    private void verifyTableStructures() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify employees table structure
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM pragma_table_info('employees') WHERE name IN " +
                            "('id', 'employee_id', 'first_name', 'last_name', 'email', 'phone', " +
                            "'hire_date', 'job_title', 'salary', 'department_id', 'is_active', " +
                            "'created_at', 'updated_at')")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 13,
                            "Employees table should have all 13 expected columns");
                }
            }

            // Verify departments table structure
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM pragma_table_info('departments') WHERE name IN " +
                            "('id', 'department_id', 'department_name', 'manager_id', " +
                            "'location', 'budget', 'is_active', 'created_at', 'updated_at')")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 9,
                            "Departments table should have all 9 expected columns");
                }
            }
        }
    }

    private void verifyDataInserted() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify departments data
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM departments")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "Should have 5 departments");
                }
            }

            // Verify employees data
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM employees")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 10,
                            "Should have 10 employees");
                }
            }

            // Verify specific department IDs exist
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM departments WHERE department_id IN " +
                            "('DEPT001', 'DEPT002', 'DEPT003', 'DEPT004', 'DEPT005')")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "All expected department IDs should exist");
                }
            }

            // Verify specific employee IDs exist
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM employees WHERE employee_id IN " +
                            "('EMP001', 'EMP002', 'EMP003', 'EMP004', 'EMP005', " +
                            "'EMP006', 'EMP007', 'EMP008', 'EMP009', 'EMP010')")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 10,
                            "All expected employee IDs should exist");
                }
            }
        }
    }

    private void verifyIndexesCreated() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify employees table indexes
            String[] expectedEmployeeIndexes = {
                    "idx_employees_employee_id",
                    "idx_employees_email",
                    "idx_employees_department_id",
                    "idx_employees_hire_date",
                    "idx_employees_salary",
                    "idx_employees_department_salary"
            };

            for (String indexName : expectedEmployeeIndexes) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT name FROM sqlite_master WHERE type='index' AND name=?")) {
                    stmt.setString(1, indexName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next(),
                                String.format("Index '%s' should exist", indexName));
                    }
                }
            }

            // Verify departments table indexes
            String[] expectedDepartmentIndexes = {
                    "idx_departments_department_id",
                    "idx_departments_manager_id",
                    "idx_departments_location"
            };

            for (String indexName : expectedDepartmentIndexes) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT name FROM sqlite_master WHERE type='index' AND name=?")) {
                    stmt.setString(1, indexName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next(),
                                String.format("Index '%s' should exist", indexName));
                    }
                }
            }
        }
    }

    private void verifyAuditInformation() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            // Verify deployment tag was created
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM deployment_tags WHERE tag_name='v1.0.0-unit-test'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1,
                            "Deployment tag should be created");
                }
            }

            // Verify all scripts were executed
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM db_change_log WHERE execution_status='SUCCESS'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 5,
                            "All 4 scripts and the additional initial changelog should be executed successfully");
                }
            }

            // Verify specific scripts were executed
            String[] expectedScripts = {
                    "create-employees-table",
                    "create-departments-table",
                    "insert-sample-data",
                    "add-employee-salary-index"
            };

            for (String scriptId : expectedScripts) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM db_change_log WHERE script_id=? AND execution_status='SUCCESS'")) {
                    stmt.setString(1, scriptId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1,
                                String.format("Script '%s' should be executed successfully", scriptId));
                    }
                }
            }
        }
    }
}