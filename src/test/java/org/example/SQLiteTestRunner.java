//package org.example;
//
//import org.example.config.DatabaseConfig;
//import org.example.database.DatabaseConnectionManager;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.sql.*;
//
//public class SQLiteTestRunner {
//    private static final Logger logger = LoggerFactory.getLogger(SQLiteTestRunner.class);
//
//    public static void main(String[] args) {
//        try {
//            if (args.length > 0 && "verify".equals(args[0])) {
//                // Run verification script
//                if (args.length < 2) {
//                    logger.error("Usage: SQLiteTestRunner verify <sql-file-path>");
//                    System.exit(1);
//                }
//                runVerificationScript(args[1], args);
//            } else {
//                // Clean up any existing database file
//                //            cleanupDatabase();
//
//                // Run the deployment
//                logger.info("=== Running SQLite Database Deployment Test ===");
//                runDeployment();
//
//                // Wait a moment for deployment to complete
//                Thread.sleep(2000);
//
//                // Verify the results
//                logger.info("=== Verifying SQLite Deployment Results ===");
//                verifyDeploymentResults();
//            }
//
//        } catch (Exception e) {
//            logger.error("SQLite test failed", e);
//        }
//    }
//
//    private static void cleanupDatabase() {
//        File dbFile = new File("testdb.sqlite");
//        if (dbFile.exists()) {
//            if (dbFile.delete()) {
//                logger.info("Cleaned up existing database file");
//            } else {
//                logger.warn("Could not delete existing database file");
//            }
//        }
//    }
//
//    private static void runDeployment() throws Exception {
//        String[] deployArgs = {
//                "--action", "DEPLOY",
//                "--database-config", "sqlite-test-config.yml",
//                "--changelog", "sqlite-scripts/sqlite-test-changelog.yml",
//                "--script-base-path", "sqlite-scripts",
//                "--tag", "v1.0.2-sqlite-test",
//                "--build-version", "1.0.2.20231110.1",
//                "--environment", "sqlite-test",
//                "--verbose"
//        };
//
//        DatabaseDeployTool.main(deployArgs);
//    }
//
//    private static void verifyDeploymentResults() {
//        DatabaseConfig dbConfig = new DatabaseConfig();
//        dbConfig.setUrl("jdbc:sqlite:testdb.sqlite");
//        dbConfig.setDriver("org.sqlite.JDBC");
//
//        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(dbConfig);
//        try {
//            Connection connection = connectionManager.getConnection();
//
//            // Check if tables exist
//            printSection("TABLE VERIFICATION");
//            verifyTables(connection);
//
//            // Check departments data
//            printSection("DEPARTMENTS DATA");
//            printDepartments(connection);
//
//            // Check employees data
//            printSection("EMPLOYEES DATA");
//            printEmployees(connection);
//
//            // Check indexes
//            printSection("INDEX VERIFICATION");
//            verifyIndexes(connection);
//
//            // Check audit tables
//            printSection("AUDIT INFORMATION");
//            printAuditInfo(connection);
//
//            connection.close();
//        } catch (SQLException e) {
//            logger.error("Failed to verify deployment results", e);
//        } finally {
//            connectionManager.close();
//        }
//    }
//
//    private static void verifyTables(Connection connection) throws SQLException {
//        String[] expectedTables = {"employees", "departments", "db_change_log", "deployment_tags", "database_lock"};
//
//        for (String tableName : expectedTables) {
//            String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
//            PreparedStatement stmt = null;
//            ResultSet rs = null;
//            try {
//                stmt = connection.prepareStatement(sql);
//                stmt.setString(1, tableName);
//                rs = stmt.executeQuery();
//                if (rs.next()) {
//                    logger.info("[OK] Table '{}' exists", tableName);
//                } else {
//                    logger.error("[FAIL] Table '{}' does NOT exist", tableName);
//                }
//            } finally {
//                if (rs != null) {
//                    rs.close();
//                }
//                if (stmt != null) {
//                    stmt.close();
//                }
//            }
//        }
//    }
//
//    private static void printDepartments(Connection connection) throws SQLException {
//        String sql = "SELECT department_id, department_name, location, budget FROM departments ORDER BY department_id";
//
//        PreparedStatement stmt = null;
//        ResultSet rs = null;
//        try {
//            stmt = connection.prepareStatement(sql);
//            rs = stmt.executeQuery();
//
//            System.out.println("\n" + "-".repeat(60));
//            System.out.println("DEPARTMENTS");
//            System.out.println("-".repeat(60));
//            System.out.printf("%-15s %-20s %-15s %-15s%n", "Dept ID", "Name", "Location", "Budget");
//            System.out.println("-".repeat(60));
//
//            while (rs.next()) {
//                System.out.printf("%-15s %-20s %-15s $%,12.2f%n",
//                        rs.getString("department_id"),
//                        rs.getString("department_name"),
//                        rs.getString("location"),
//                        rs.getDouble("budget"));
//            }
//
//            System.out.println("-".repeat(60));
//            System.out.println();
//        } finally {
//            if (rs != null) {
//                rs.close();
//            }
//            if (stmt != null) {
//                stmt.close();
//            }
//        }
//    }
//
//    private static void printEmployees(Connection connection) throws SQLException {
//        String sql = """
//                SELECT e.employee_id, e.first_name, e.last_name, e.email, e.job_title,
//                       e.salary, d.department_name
//                FROM employees e
//                LEFT JOIN departments d ON e.department_id = d.id
//                ORDER BY e.employee_id
//                """;
//
//        PreparedStatement stmt = null;
//        ResultSet rs = null;
//        try {
//            stmt = connection.prepareStatement(sql);
//            rs = stmt.executeQuery();
//
//            System.out.println("\n" + "-".repeat(90));
//            System.out.println("EMPLOYEES");
//            System.out.println("-".repeat(90));
//            System.out.printf("%-12s %-12s %-12s %-25s %-20s %-10s %-15s%n",
//                    "Emp ID", "First Name", "Last Name", "Email", "Job Title", "Salary", "Department");
//            System.out.println("-".repeat(90));
//
//            while (rs.next()) {
//                System.out.printf("%-12s %-12s %-12s %-25s %-20s $%,8.2f %-15s%n",
//                        rs.getString("employee_id"),
//                        rs.getString("first_name"),
//                        rs.getString("last_name"),
//                        rs.getString("email"),
//                        rs.getString("job_title"),
//                        rs.getDouble("salary"),
//                        rs.getString("department_name"));
//            }
//
//            System.out.println("-".repeat(90));
//            System.out.println();
//        } finally {
//            if (rs != null) {
//                rs.close();
//            }
//            if (stmt != null) {
//                stmt.close();
//            }
//        }
//    }
//
//    private static void verifyIndexes(Connection connection) throws SQLException {
//        String[] expectedIndexes = {
//                "idx_employees_employee_id",
//                "idx_employees_email",
//                "idx_employees_department_id",
//                "idx_employees_hire_date",
//                "idx_employees_salary",
//                "idx_employees_department_salary",
//                "idx_departments_department_id",
//                "idx_departments_manager_id",
//                "idx_departments_location"
//        };
//
//        for (String indexName : expectedIndexes) {
//            String sql = "SELECT name FROM sqlite_master WHERE type='index' AND name=?";
//            PreparedStatement stmt = null;
//            ResultSet rs = null;
//            try {
//                stmt = connection.prepareStatement(sql);
//                stmt.setString(1, indexName);
//                rs = stmt.executeQuery();
//                if (rs.next()) {
//                    logger.info("[OK] Index '{}' exists", indexName);
//                } else {
//                    logger.error("[FAIL] Index '{}' does NOT exist", indexName);
//                }
//            } finally {
//                if (rs != null) {
//                    rs.close();
//                }
//                if (stmt != null) {
//                    stmt.close();
//                }
//            }
//        }
//    }
//
//    private static void printAuditInfo(Connection connection) throws SQLException {
//        // Print deployment tags
//        String tagSql = "SELECT tag_name, build_version, deployment_time, environment FROM deployment_tags ORDER BY deployment_time DESC";
//        PreparedStatement stmt = null;
//        ResultSet rs = null;
//        try {
//            stmt = connection.prepareStatement(tagSql);
//            rs = stmt.executeQuery();
//
//            System.out.println("\n" + "=".repeat(40));
//            System.out.println("DEPLOYMENT TAGS");
//            System.out.println("=".repeat(40));
//            while (rs.next()) {
//                System.out.printf("Tag: %s | Build: %s | Time: %s | Env: %s%n",
//                        rs.getString("tag_name"),
//                        rs.getString("build_version"),
//                        rs.getString("deployment_time"),
//                        rs.getString("environment"));
//            }
//        } finally {
//            if (rs != null) {
//                rs.close();
//            }
//            if (stmt != null) {
//                stmt.close();
//            }
//        }
//
//        // Print change log entries
//        String changeLogSql = """
//                SELECT script_id, script_name, execution_status, execution_time, execution_duration_ms
//                FROM db_change_log
//                ORDER BY execution_time DESC
//                """;
//        stmt = null;
//        rs = null;
//        try {
//            stmt = connection.prepareStatement(changeLogSql);
//            rs = stmt.executeQuery();
//
//            System.out.println("\n" + "=".repeat(80));
//            System.out.println("SCRIPT EXECUTION HISTORY");
//            System.out.println("=".repeat(80));
//            System.out.printf("%-25s %-25s %-10s %-20s %-12s%n",
//                    "Script ID", "Script Name", "Status", "Execution Time", "Duration (ms)");
//            System.out.println("-".repeat(80));
//
//            while (rs.next()) {
//                System.out.printf("%-25s %-25s %-10s %-20s %,12d%n",
//                        rs.getString("script_id"),
//                        rs.getString("script_name"),
//                        rs.getString("execution_status"),
//                        rs.getString("execution_time"),
//                        rs.getLong("execution_duration_ms"));
//            }
//            System.out.println("=".repeat(80));
//        } finally {
//            if (rs != null) {
//                rs.close();
//            }
//            if (stmt != null) {
//                stmt.close();
//            }
//        }
//    }
//
//    private static void runVerificationScript(String scriptPath, String[] args) throws Exception {
//        logger.info("Running verification script: {}", scriptPath);
//
//        DatabaseConfig dbConfig = new DatabaseConfig();
//        String dbUrl = "jdbc:sqlite:testdb.sqlite";
//        // Check if a custom database URL is provided
//        if (args.length > 2) {
//            dbUrl = args[2];
//        }
//        dbConfig.setUrl(dbUrl);
//        dbConfig.setDriver("org.sqlite.JDBC");
//
//        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(dbConfig);
//        try {
//            Connection connection = connectionManager.getConnection();
//
//            // Read and execute the verification script
//            java.nio.file.Path path = java.nio.file.Paths.get(scriptPath);
//            String content = java.nio.file.Files.readString(path);
//
//            // Remove comments and split statements
//            String[] lines = content.split("\n");
//            StringBuilder sqlBuilder = new StringBuilder();
//
//            for (String line : lines) {
//                String trimmedLine = line.trim();
//                if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
//                    sqlBuilder.append(line).append("\n");
//                }
//            }
//
//            String cleanContent = sqlBuilder.toString().trim();
//            String[] statements = cleanContent.split(";");
//
//            for (int i = 0; i < statements.length; i++) {
//                String statement = statements[i].trim();
//                if (!statement.isEmpty()) {
//                    try {
//                        PreparedStatement stmt = connection.prepareStatement(statement);
//                        if (statement.toUpperCase().startsWith("SELECT")) {
//                            ResultSet rs = stmt.executeQuery();
//                            printResultSet(rs);
//                            rs.close();
//                        } else {
//                            stmt.execute();
//                        }
//                        stmt.close();
//                    } catch (SQLException e) {
//                        logger.error("Error executing statement: {}", statement, e);
//                    }
//                }
//            }
//
//            connection.close();
//        } finally {
//            connectionManager.close();
//        }
//    }
//
//    private static void printResultSet(ResultSet rs) throws SQLException {
//        ResultSetMetaData metaData = rs.getMetaData();
//        int columnCount = metaData.getColumnCount();
//
//        // Print headers
//        for (int i = 1; i <= columnCount; i++) {
//            System.out.printf("%-30s", metaData.getColumnName(i));
//        }
//        System.out.println();
//        System.out.println("-".repeat(columnCount * 30));
//
//        // Print data
//        while (rs.next()) {
//            for (int i = 1; i <= columnCount; i++) {
//                Object value = rs.getObject(i);
//                System.out.printf("%-30s", value != null ? value.toString() : "NULL");
//            }
//            System.out.println();
//        }
//        System.out.println();
//    }
//
//    private static void printSection(String sectionName) {
//        System.out.println("\n" + "=".repeat(60));
//        System.out.println(" " + sectionName);
//        System.out.println("=".repeat(60));
//    }
//}