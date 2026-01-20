package org.jerish.dbdeploy.integration.parameter;

import org.jerish.dbdeploy.integration.SQLiteDeployTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the parameter placeholder functionality using SQLite.
 * This test verifies that:
 * 1. SQL placeholders like ${param_name} are correctly replaced with parameter values
 * 2. Parameter replacement works for apply, apply verify, rollback, and rollback verify SQLs
 * 3. Deployment and rollback work correctly with parameters
 */
public class SQLiteParameterTest extends SQLiteDeployTestBase {

    @Test
    @DisplayName("Test deployment with parameter replacement")
    void testDeployWithParameters() throws Exception {
        // Create parameters map
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "test_products");
        parameters.put("item_name", "Test Product");
        parameters.put("item_status", "active");

        String changelogPath = "src/test/resources/sqlite-scripts-parameter/sqlite-test-changelog-parameter.yml";

        // Deploy with parameters
        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, false, parameters),
                "Deployment with parameters should complete without errors");

        // Verify the table was created with the parameterized name
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='test_products'",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 1,
                "Parameterized table 'test_products' should exist");

        // Verify the data was inserted with parameterized values
        Integer dataCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_products WHERE name='Test Product' AND status='active'",
                Integer.class);
        assertTrue(dataCount != null && dataCount == 1,
                "Data with parameterized values should be inserted");

        // Verify audit log entries exist
        Integer scriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_deploy_tool_change_log",
                Integer.class);
        assertTrue(scriptCount != null && scriptCount == 2,
                "Should have 2 audit log entries");
    }

    @Test
    @DisplayName("Test rollback with parameter replacement")
    void testRollbackWithParameters() throws Exception {
        // Create parameters map for deployment
        Map<String, String> deployParameters = new HashMap<>();
        deployParameters.put("table_name", "test_orders");
        deployParameters.put("item_name", "Test Order");
        deployParameters.put("item_status", "pending");

        String changelogPath = "src/test/resources/sqlite-scripts-parameter/sqlite-test-changelog-parameter.yml";

        // Deploy with parameters
        assertDoesNotThrow(() -> deployManager.deploy(changelogPath, false, deployParameters),
                "Deployment with parameters should complete without errors");

        // Verify the table was created
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='test_orders'",
                Integer.class);
        assertTrue(tableCount != null && tableCount == 1,
                "Parameterized table 'test_orders' should exist");

        // Verify the data was inserted
        Integer dataCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_orders WHERE name='Test Order' AND status='pending'",
                Integer.class);
        assertTrue(dataCount != null && dataCount == 1,
                "Data with parameterized values should be inserted");

        // Create parameters map for rollback (same table name is needed)
        Map<String, String> rollbackParameters = new HashMap<>();
        rollbackParameters.put("table_name", "test_orders");
        rollbackParameters.put("item_name", "Test Order");
        rollbackParameters.put("item_status", "pending");

        // Rollback with parameters
        String emptyChangelogPath = "src/test/resources/sqlite-scripts/sqlite-test-changelog-empty.yml";
        assertDoesNotThrow(() -> deployManager.rollback(emptyChangelogPath, false, rollbackParameters),
                "Rollback with parameters should complete without errors");

        // The table should be dropped by the first rollback script
        Integer tableCountAfterRollback = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='test_orders'",
                Integer.class);
        assertTrue(tableCountAfterRollback == null || tableCountAfterRollback == 0,
                "Parameterized table 'test_orders' should be dropped after rollback");

        // Verify audit log entries show rolled back status
        Integer rolledBackCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM db_deploy_tool_change_log t1 WHERE t1.id = (SELECT MAX(t2.id) FROM db_deploy_tool_change_log t2 WHERE t2.script_name = t1.script_name) AND t1.execution_status='ROLLED_BACK'",
                Integer.class);
        assertTrue(rolledBackCount != null && rolledBackCount == 2,
                "Both scripts should be marked as ROLLED_BACK (latest status)");
    }

    @Test
    @DisplayName("Test deployment without parameters")
    void testDeployWithoutParameters() throws Exception {
        String changelogPath = "src/test/resources/sqlite-scripts-parameter/sqlite-test-changelog-parameter.yml";

        // Deploy without parameters - should fail
        Exception exception = assertThrows(Exception.class, () -> {
            deployManager.deploy(changelogPath, false, null);
        }, "Deployment without parameters should fail");

        // Verify no tables were created
        Integer tableCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE 'test_%'",
                Integer.class);
        assertTrue(tableCount == null || tableCount == 0,
                "No tables should be created when parameters are missing");
    }
}