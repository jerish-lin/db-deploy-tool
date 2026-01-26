package com.scb.mrp.schemaflow.dbdeploy.integration.multinode;

import com.scb.mrp.schemaflow.dbdeploy.TestApplication;
import com.scb.mrp.schemaflow.dbdeploy.changelog.ConfigLoader;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogScript;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.integration.SQLiteDeployTestBase;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseDeployManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseDeployService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SQLiteMultiNodeTest extends SQLiteDeployTestBase {

    private static final String[] DB_FILES = {"testdb.sqlite", "node1.sqlite", "node2.sqlite", "node3.sqlite"};
    private static final String CHANGELOG_PATH = "classpath:sqlite-scripts-multinode/sqlite-test-changelog-multinode.yml";

    @Autowired
    private DatabaseDeployManager deployManager;

    @Autowired
    private DatabaseDeployService deployService;

    @Autowired
    private JdbcTemplate dbDeployJdbcTemplate;

    @Autowired
    @Qualifier("nodeJdbcTemplateMap")
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    @Autowired
    private SchemaInitializationManager schemaInitializationManager;

    @BeforeEach
    @Override
    public void setUp() {
        resetDatabaseFiles();
        // Initialize schema to ensure audit tables exist
        schemaInitializationManager.initializeSchemaIfNeeded();
        // Verify multi-node configuration is loaded
        assertNotNull(nodeJdbcTemplateMap, "Node JDBC template map should not be null");
        assertFalse(nodeJdbcTemplateMap.isEmpty(), "Node JDBC template map should not be empty");
        assertEquals(3, nodeJdbcTemplateMap.size(), "Should have 3 nodes configured");
        assertTrue(nodeJdbcTemplateMap.containsKey("node1"), "Should have node1");
        assertTrue(nodeJdbcTemplateMap.containsKey("node2"), "Should have node2");
        assertTrue(nodeJdbcTemplateMap.containsKey("node3"), "Should have node3");
    }

    @AfterEach
    void tearDown() {
//        resetDatabaseFiles();
    }

    private void resetDatabaseFiles() {
        for (String dbFile : DB_FILES) {
            resetDatabaseFile(dbFile);
        }
    }

    @Test
    void testMultiNodeDeployment() throws Exception {
        // Load changelog configuration
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
        assertNotNull(changeLogConfig, "ChangeLogConfig should not be null");
        assertEquals(4, changeLogConfig.getScripts().size(), "Should have 4 scripts in changelog");

        // Deploy scripts
        deployService.deploy(changeLogConfig, false);

        // Verify script metadata (target_nodes from script table)
        List<ChangeLogScript> scriptEntries = dbDeployJdbcTemplate.query(
                "SELECT id, script_name, target_nodes FROM schemaflow_changelog_script ORDER BY id ASC",
                (rs, rowNum) -> {
                    ChangeLogScript entry = new ChangeLogScript();
                    entry.setId(rs.getLong("id"));
                    entry.setScriptName(rs.getString("script_name"));
                    entry.setTargetNodes(rs.getString("target_nodes") != null ?
                            List.of(rs.getString("target_nodes").split(",")) : null);
                    return entry;
                }
        );

        assertEquals(4, scriptEntries.size(), "Should have 4 script entries");

        // Verify first script (ALL nodes)
        ChangeLogScript usersEntry = scriptEntries.get(0);
        assertEquals("create-users-table-multinode", usersEntry.getScriptName());
        assertNotNull(usersEntry.getTargetNodes(), "Target nodes should not be null");
        assertTrue(usersEntry.getTargetNodes().contains("ALL"), "Should target ALL nodes");

        // Verify users table exists on all nodes
        assertTrue(nodeJdbcTemplateMap.get("node1").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should exist on node1");
        assertTrue(nodeJdbcTemplateMap.get("node2").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should exist on node2");
        assertTrue(nodeJdbcTemplateMap.get("node3").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should exist on node3");

        // Verify second script (node1 only)
        ChangeLogScript productsEntry = scriptEntries.get(1);
        assertEquals("create-products-table-node1", productsEntry.getScriptName());
        assertNotNull(productsEntry.getTargetNodes(), "Target nodes should not be null");
        assertEquals(List.of("node1"), productsEntry.getTargetNodes(), "Should target only node1");

        // Verify products table exists only on node1
        assertTrue(nodeJdbcTemplateMap.get("node1").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='products'", Integer.class) > 0,
                "Products table should exist on node1");
        assertFalse(nodeJdbcTemplateMap.get("node2").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='products'", Integer.class) > 0,
                "Products table should NOT exist on node2");
        assertFalse(nodeJdbcTemplateMap.get("node3").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='products'", Integer.class) > 0,
                "Products table should NOT exist on node3");

        // Verify third script (node2 only)
        ChangeLogScript ordersEntry = scriptEntries.get(2);
        assertEquals("create-orders-table-node2", ordersEntry.getScriptName());
        assertNotNull(ordersEntry.getTargetNodes(), "Target nodes should not be null");
        assertEquals(List.of("node2"), ordersEntry.getTargetNodes(), "Should target only node2");

        // Verify orders table exists only on node2
        assertFalse(nodeJdbcTemplateMap.get("node1").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='orders'", Integer.class) > 0,
                "Orders table should NOT exist on node1");
        assertTrue(nodeJdbcTemplateMap.get("node2").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='orders'", Integer.class) > 0,
                "Orders table should exist on node2");
        assertFalse(nodeJdbcTemplateMap.get("node3").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='orders'", Integer.class) > 0,
                "Orders table should NOT exist on node3");

        // Verify fourth script (single-node, backward compatibility)
        ChangeLogScript scriptEntry = scriptEntries.get(3);
        assertEquals("single-node-script", scriptEntry.getScriptName());
        assertNull(scriptEntry.getTargetNodes(), "Target nodes should be null for single-node");

        // Verify sample_table exists only on default database
        assertTrue(dbDeployJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='sample_table'", Integer.class) > 0,
                "Sample table should exist on default database");

        // Verify audit log (4 entries)
        Integer auditCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit", Integer.class);
        assertEquals(4, auditCount, "Should have 4 audit entries");
    }

    @Test
    void testMultiNodeRollback() throws Exception {
        // Deploy scripts
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
        deployService.deploy(changeLogConfig, false);

        // Verify deployment
        assertTrue(nodeJdbcTemplateMap.get("node1").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0);
        assertTrue(nodeJdbcTemplateMap.get("node2").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0);
        assertTrue(nodeJdbcTemplateMap.get("node3").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0);

        // Rollback to initial state (empty changelog)
        ChangeLogConfig emptyConfig = new ChangeLogConfig();
        emptyConfig.setBasePath("classpath:multinode");
        emptyConfig.setFileName("changelog.yml");
        emptyConfig.setScripts(List.of());

        deployService.rollback(emptyConfig, false);

        // Verify tables are dropped from all nodes
        assertFalse(nodeJdbcTemplateMap.get("node1").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should be dropped from node1");
        assertFalse(nodeJdbcTemplateMap.get("node2").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should be dropped from node2");
        assertFalse(nodeJdbcTemplateMap.get("node3").queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should be dropped from node3");

        // Verify rollback entries in audit log
        Integer rollbackCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit WHERE execution_status = 'ROLLED_BACK'",
                Integer.class);
        assertTrue(rollbackCount > 0, "Should have rollback entries in audit log");
    }

    @Test
    void testNodeExecutionDetails() throws Exception {
        // Deploy scripts
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
        deployService.deploy(changeLogConfig, false);

        // Get the multi-node script entry
        ScriptExecutionEntry multiNodeEntry = dbDeployJdbcTemplate.queryForObject(
                "SELECT ca.*, cs.script_name FROM schemaflow_changelog_audit ca INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id WHERE cs.script_name = 'create-users-table-multinode'",
                (rs, rowNum) -> {
                    ScriptExecutionEntry entry = new ScriptExecutionEntry();
                    entry.setScriptName(rs.getString("script_name"));
                    entry.setExecutionStatus(ScriptExecutionStatus.fromValue(rs.getString("execution_status")));
                    entry.setNodeExecutionDetails(rs.getString("node_execution_details"));
                    return entry;
                }
        );

        assertNotNull(multiNodeEntry, "Multi-node entry should exist");
        assertEquals(ScriptExecutionStatus.SUCCESS, multiNodeEntry.getExecutionStatus());
        assertNotNull(multiNodeEntry.getNodeExecutionDetails(), "Node execution details should not be null");

        // Verify node execution details contains information for all nodes
        String nodeDetails = multiNodeEntry.getNodeExecutionDetails();
        assertTrue(nodeDetails.contains("node1"), "Should contain node1 details");
        assertTrue(nodeDetails.contains("node2"), "Should contain node2 details");
        assertTrue(nodeDetails.contains("node3"), "Should contain node3 details");
        assertTrue(nodeDetails.contains("\"success\":true"), "Should indicate success for all nodes");
    }

    @Test
    void testMultiNodeRollbackFailure() throws Exception {
        // Deploy scripts
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
        deployService.deploy(changeLogConfig, false);

        // Verify deployment - users table exists on all nodes
        assertTrue(nodeJdbcTemplateMap.get("node1").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should exist on node1");
        assertTrue(nodeJdbcTemplateMap.get("node2").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should exist on node2");
        assertTrue(nodeJdbcTemplateMap.get("node3").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should exist on node3");

        // Get the script_id for create-users-table-multinode
        Long scriptId = dbDeployJdbcTemplate.queryForObject(
                "SELECT id FROM schemaflow_changelog_script WHERE script_name='create-users-table-multinode'",
                Long.class);
        assertNotNull(scriptId, "Script ID should not be null");

        // Simulate a multi-node rollback failure by modifying the rollback script content to invalid SQL
        String invalidRollbackSql = "DROP TABLE non_existent_table; -- This will fail on all nodes";
        dbDeployJdbcTemplate.update(
                "UPDATE schemaflow_changelog_script SET rollback_script_content = ? WHERE id = ?",
                invalidRollbackSql, scriptId);

        // Try to rollback to empty state
        ChangeLogConfig emptyConfig = ConfigLoader.loadChangeLogConfig(
                "classpath:sqlite-scripts-multinode/sqlite-test-changelog-multinode-empty.yml");

        // Rollback should fail
        Exception exception = assertThrows(RuntimeException.class, () -> {
            deployService.rollback(emptyConfig, false);
        }, "Rollback should throw RuntimeException when rollback script fails");

        assertTrue(exception.getMessage().contains("Rollback execution failed"),
                "Exception message should indicate rollback execution failed");

        // Verify ROLLBACK_FAILED status is recorded in audit log
        Integer rollbackFailedCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit WHERE execution_status='ROLLBACK_FAILED'",
                Integer.class);
        assertTrue(rollbackFailedCount != null && rollbackFailedCount == 1,
                "Should have exactly 1 ROLLBACK_FAILED entry in audit log");

        // Verify the error message is recorded
        String errorMessage = dbDeployJdbcTemplate.queryForObject(
                "SELECT error_message FROM schemaflow_changelog_audit WHERE execution_status='ROLLBACK_FAILED'",
                String.class);
        assertNotNull(errorMessage, "Error message should be recorded");
        assertTrue(errorMessage.length() > 0, "Error message should not be empty");

        // Verify that users table still exists on all nodes (rollback failed, so tables should remain)
        assertTrue(nodeJdbcTemplateMap.get("node1").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should still exist on node1 after failed rollback");
        assertTrue(nodeJdbcTemplateMap.get("node2").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should still exist on node2 after failed rollback");
        assertTrue(nodeJdbcTemplateMap.get("node3").queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='users'", Integer.class) > 0,
                "Users table should still exist on node3 after failed rollback");

        // Verify that create-users-table-multinode script has ROLLBACK_FAILED as latest status
        Integer latestStatusCount = dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schemaflow_changelog_audit ca " +
                        "WHERE ca.script_id = ? " +
                        "AND ca.id = (SELECT MAX(ca2.id) FROM schemaflow_changelog_audit ca2 WHERE ca2.script_id = ?) " +
                        "AND ca.execution_status='ROLLBACK_FAILED'",
                Integer.class, scriptId, scriptId);
        assertTrue(latestStatusCount != null && latestStatusCount == 1,
                "create-users-table-multinode script should have ROLLBACK_FAILED as latest status");

        // Verify node execution details are recorded for the failed rollback
        String nodeExecutionDetails = dbDeployJdbcTemplate.queryForObject(
                "SELECT node_execution_details FROM schemaflow_changelog_audit " +
                        "WHERE execution_status='ROLLBACK_FAILED'",
                String.class);
        assertNotNull(nodeExecutionDetails, "Node execution details should be recorded for failed multi-node rollback");
        assertTrue(nodeExecutionDetails.contains("node1"), "Should contain node1 details");
        assertTrue(nodeExecutionDetails.contains("node2"), "Should contain node2 details");
        assertTrue(nodeExecutionDetails.contains("node3"), "Should contain node3 details");
        assertTrue(nodeExecutionDetails.contains("\"success\":false"), "Should indicate failure for nodes");
    }
}

