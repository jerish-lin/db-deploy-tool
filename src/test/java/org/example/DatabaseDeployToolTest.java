//package org.example;
//
//import org.example.config.DatabaseConfig;
//import org.example.database.DatabaseConnectionManager;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.time.format.DateTimeFormatter;
//
//public class DatabaseDeployToolTest {
//    private static final Logger logger = LoggerFactory.getLogger(DatabaseDeployToolTest.class);
//
//    public static void main(String[] args) {
//        try {
//            // Test configuration
//            DatabaseConfig dbConfig = new DatabaseConfig();
//            dbConfig.setUrl("jdbc:postgresql://localhost:5432/testdb");
//            dbConfig.setUsername("testuser");
//            dbConfig.setPassword("testpass");
//            dbConfig.setDriver("org.postgresql.Driver");
//
//            // Run the deployment
//            logger.info("=== Running Database Deployment Test ===");
//            runDeployment();
//
//            // Wait a moment for deployment to complete
//            Thread.sleep(2000);
//
//            // Verify the results
//            logger.info("=== Verifying Deployment Results ===");
//            verifyDeploymentResults(dbConfig);
//
//        } catch (Exception e) {
//            logger.error("Test failed", e);
//        }
//    }
//
//    private static void runDeployment() throws Exception {
//        String[] deployArgs = {
//                "--action", "DEPLOY",
//                "--database-config", "test-database-config.yml",
//                "--changelog", "test-scripts/test-changelog.yml",
//                "--script-base-path", "test-scripts",
//                "--tag", "v1.0.2-test",
//                "--build-version", "1.0.2.20231110.1",
//                "--environment", "test",
//                "--verbose"
//        };
//
//        DatabaseDeployTool.main(deployArgs);
//    }
//
//    private static void verifyDeploymentResults(DatabaseConfig dbConfig) {
//        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(dbConfig);
//        try {
//            Connection connection = connectionManager.getConnection();
//            try {
//                // Check if tables exist
//                printSection("TABLE VERIFICATION");
//                verifyTables(connection);
//
//                // Check departments data
//                printSection("DEPARTMENTS DATA");
//                printDepartments(connection);
//
//                // Check employees data
//                printSection("EMPLOYEES DATA");
//                printEmployees(connection);
//
//                // Check indexes
//                printSection("INDEX VERIFICATION");
//                verifyIndexes(connection);
//
//                // Check audit tables
//                printSection("AUDIT INFORMATION");
//                printAuditInfo(connection);
//
//            } finally {
//                if (connection != null) {
//                    connection.close();
//                }
//            }
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
//            String sql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
//            PreparedStatement stmt = null;
//            ResultSet rs = null;
//            try {
//                stmt = connection.prepareStatement(sql);
//                stmt.setString(1, tableName);
//                rs = stmt.executeQuery();
//                if (rs.next() && rs.getBoolean(1)) {
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
//                        rs.getBigDecimal("budget"));
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
//                        rs.getBigDecimal("salary"),
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
//            String sql = "SELECT EXISTS (SELECT FROM pg_indexes WHERE indexname = ?)";
//            PreparedStatement stmt = null;
//            ResultSet rs = null;
//            try {
//                stmt = connection.prepareStatement(sql);
//                stmt.setString(1, indexName);
//                rs = stmt.executeQuery();
//                if (rs.next() && rs.getBoolean(1)) {
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
//                        rs.getTimestamp("deployment_time").toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
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
//                        rs.getTimestamp("execution_time").toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
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
//    private static void printSection(String sectionName) {
//        System.out.println("\n" + "=".repeat(60));
//        System.out.println(" " + sectionName);
//        System.out.println("=".repeat(60));
//    }
//}