package com.scb.mrp.schemaflow.dbdeploy.integration.failure;

import com.scb.mrp.schemaflow.dbdeploy.integration.SQLiteDeployTestBase;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class for SQLite failure scenario tests.
 * Contains common verification methods used across different failure test classes.
 */
public abstract class SQLiteFailureTestBase extends SQLiteDeployTestBase {

    protected void verifySuccessfulDeploymentV1_0_1() {
        // Verify all expected tables exist
        String[] expectedTables = {"users", "orders", "schemaflow_changelog_script", "schemaflow_changelog_audit", "schemaflow_deploy_lock"};

        for (String tableName : expectedTables) {
            String sql = "SELECT count(name) FROM sqlite_master WHERE type='table' AND name=?";
            Integer count = dbDeployJdbcTemplate.queryForObject(sql, Integer.class, tableName);
            assertTrue(count != null && count > 0,
                    String.format("Table '%s' should exist after v1.0.1 deployment", tableName));
        }

        // Verify v1.0.1 scripts were executed successfully
        String[] v1_0_1_Scripts = {
                "feature-12346/feature-12346-create-users-table",
                "feature-12347-create-orders-table",
                "feature-12348/feature-12348-insert-sample-users"
        };

        for (String scriptId : v1_0_1_Scripts) {
            Integer scriptCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name=? AND ca.execution_status='SUCCESS'",
                    Integer.class, scriptId);
            assertTrue(scriptCount != null && scriptCount >= 1,
                    String.format("Script '%s' should be executed successfully", scriptId));
        }

        // Verify data was inserted
        Integer userCount = dbDeployJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertTrue(userCount != null && userCount == 5,
                "Should have 5 users after v1.0.1 deployment");
    }

    protected void verifyFailureState(String failedSql) {
        // Verify that the first script in v1.0.2 (add-user-email-index) was executed successfully
        Integer firstScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12349-add-user-email-index' AND ca.execution_status='SUCCESS'",
                Integer.class);
        assertTrue(firstScriptCount != null && firstScriptCount >= 1,
                "First script in v1.0.2 should be executed successfully");

        // Verify that the failing script was marked as FAILED
        Integer failedScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name=? AND ca.execution_status='FAILED'",
                Integer.class, failedSql);
        assertTrue(failedScriptCount != null && failedScriptCount == 1,
                "Failing script should be marked as FAILED");

        // Verify that the third script in v1.0.2 (add-order-status-index) was NOT executed
        Integer thirdScriptCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12351-add-order-status-index'",
                Integer.class);
        assertTrue(thirdScriptCount != null && thirdScriptCount == 0,
                "Third script in v1.0.2 should not be executed after failure");

        // Verify that the user email index was created (first script succeeded)
        Integer emailIndexCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT count(name) FROM sqlite_master WHERE type='index' AND name='idx_users_email'",
                Integer.class);
        assertTrue(emailIndexCount != null && emailIndexCount > 0,
                "User email index should exist (first script succeeded)");

        // Verify that the order status index was NOT created (third script didn't execute)
        Integer statusIndexCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT count(name) FROM sqlite_master WHERE type='index' AND name='idx_orders_status'",
                Integer.class);
        assertTrue(statusIndexCount == null || statusIndexCount == 0,
                "Order status index should not exist (third script didn't execute)");
    }

    protected void verifyRollbackAfterFailure() {
        // Verify that the user email index was dropped (rollback of successful v1.0.2 script)
        Integer emailIndexCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT count(name) FROM sqlite_master WHERE type='index' AND name='idx_users_email'",
                Integer.class);
        assertTrue(emailIndexCount == null || emailIndexCount == 0,
                "User email index should be dropped after rollback");

        // Verify that the add-user-email-index script is marked as ROLLED_BACK (latest status)
        Integer rolledBackCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12349-add-user-email-index' AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='ROLLED_BACK'",
                Integer.class);
        assertTrue(rolledBackCount != null && rolledBackCount == 1,
                "feature-12349-add-user-email-index script should be marked as ROLLED_BACK (latest status)");

        // Verify that the failed script (create-user-orders-view) is also marked as ROLLED_BACK (latest status)
        Integer failedScriptRolledBackCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name='feature-12350-create-user-orders-view' AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='ROLLED_BACK'",
                Integer.class);
        assertTrue(failedScriptRolledBackCount != null && failedScriptRolledBackCount == 1,
                "feature-12350-create-user-orders-view script should be marked as ROLLED_BACK (latest status)");

        // Verify that v1.0.1 scripts are still marked as SUCCESS (latest status)
        String[] v1_0_1_Scripts = {
                "feature-12346/feature-12346-create-users-table",
                "feature-12347-create-orders-table",
                "feature-12348/feature-12348-insert-sample-users"
        };

        for (String scriptId : v1_0_1_Scripts) {
            Integer scriptCount = dbDeployJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name=? AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = cs.id) AND ca.execution_status='SUCCESS'",
                    Integer.class, scriptId);
            assertTrue(scriptCount != null && scriptCount >= 1,
                    String.format("Script '%s' should still be marked as SUCCESS (latest status)", scriptId));
        }

        // Verify data is still intact
        Integer userCount = dbDeployJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertTrue(userCount != null && userCount == 5,
                "Users data should still exist after rollback");
    }
}
