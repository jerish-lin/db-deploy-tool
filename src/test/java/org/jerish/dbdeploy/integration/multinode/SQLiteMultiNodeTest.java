package org.jerish.dbdeploy.integration.multinode;

import org.jerish.dbdeploy.TestApplication;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.entity.ScriptExecutionStatus;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
import org.jerish.dbdeploy.service.DatabaseDeployManager;
import org.jerish.dbdeploy.service.DatabaseDeployService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SQLiteMultiNodeTest {

    private static final String[] DB_FILES = {"testdb.sqlite", "node1.sqlite", "node2.sqlite", "node3.sqlite"};
    private static final String CHANGELOG_PATH = "src/test/resources/sqlite-scripts-multinode/sqlite-test-changelog-multinode.yml";

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
    void setUp() {
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
        try {
            System.gc();
            System.runFinalization();
            Thread.sleep(100);

            for (String dbFile : DB_FILES) {
                File file = new File(dbFile);
                if (file.exists()) {
                    try (java.io.FileWriter writer = new java.io.FileWriter(file, false)) {
                        writer.write("");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not reset database files: " + e.getMessage());
        }
    }

    @Test
    void testMultiNodeDeployment() throws Exception {
        // Load changelog configuration
        ChangeLogConfig changeLogConfig = org.jerish.dbdeploy.changelog.ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
        assertNotNull(changeLogConfig, "ChangeLogConfig should not be null");
        assertEquals(4, changeLogConfig.getScripts().size(), "Should have 4 scripts in changelog");

        // Deploy scripts
        deployService.deploy(changeLogConfig, false);

        // Verify audit log
        List<ChangeLogEntry> auditEntries = dbDeployJdbcTemplate.query(
                "SELECT * FROM schemaflow_change_log ORDER BY execution_time ASC",
                (rs, rowNum) -> {
                    ChangeLogEntry entry = new ChangeLogEntry();
                    entry.setId(rs.getLong("id"));
                    entry.setScriptName(rs.getString("script_name"));
                    entry.setExecutionStatus(ScriptExecutionStatus.fromValue(rs.getString("execution_status")));
                    entry.setTargetNodes(rs.getString("target_nodes") != null ?
                            List.of(rs.getString("target_nodes").split(",")) : null);
                    entry.setNodeExecutionDetails(rs.getString("node_execution_details"));
                    return entry;
                }
        );

        assertEquals(4, auditEntries.size(), "Should have 4 audit entries");

        // Verify first script (ALL nodes)
        ChangeLogEntry usersEntry = auditEntries.get(0);
        assertEquals("create-users-table-multinode", usersEntry.getScriptName());
        assertEquals(ScriptExecutionStatus.SUCCESS, usersEntry.getExecutionStatus());
        assertNotNull(usersEntry.getTargetNodes(), "Target nodes should not be null");
        assertTrue(usersEntry.getTargetNodes().contains("ALL"), "Should target ALL nodes");
        assertNotNull(usersEntry.getNodeExecutionDetails(), "Node execution details should not be null");

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
        ChangeLogEntry productsEntry = auditEntries.get(1);
        assertEquals("create-products-table-node1", productsEntry.getScriptName());
        assertEquals(ScriptExecutionStatus.SUCCESS, productsEntry.getExecutionStatus());
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
        ChangeLogEntry ordersEntry = auditEntries.get(2);
        assertEquals("create-orders-table-node2", ordersEntry.getScriptName());
        assertEquals(ScriptExecutionStatus.SUCCESS, ordersEntry.getExecutionStatus());
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
        ChangeLogEntry auditEntry = auditEntries.get(3);
        assertEquals("single-node-script", auditEntry.getScriptName());
        assertEquals(ScriptExecutionStatus.SUCCESS, auditEntry.getExecutionStatus());
        assertNull(auditEntry.getTargetNodes(), "Target nodes should be null for single-node");
        assertNull(auditEntry.getNodeExecutionDetails(), "Node execution details should be null for single-node");

        // Verify sample_table exists only on default database
        assertTrue(dbDeployJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='sample_table'", Integer.class) > 0,
                "Sample table should exist on default database");
    }

    @Test
    void testMultiNodeRollback() throws Exception {
        // Deploy scripts
        ChangeLogConfig changeLogConfig = org.jerish.dbdeploy.changelog.ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
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
        emptyConfig.setBasePath("src/test/resources/multinode"); emptyConfig.setFileName("changelog.yml");
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
                "SELECT COUNT(*) FROM schemaflow_change_log WHERE execution_status = 'ROLLED_BACK'",
                Integer.class);
        assertTrue(rollbackCount > 0, "Should have rollback entries in audit log");
    }

    @Test
    void testNodeExecutionDetails() throws Exception {
        // Deploy scripts
        ChangeLogConfig changeLogConfig = org.jerish.dbdeploy.changelog.ConfigLoader.loadChangeLogConfig(CHANGELOG_PATH);
        deployService.deploy(changeLogConfig, false);

        // Get the multi-node script entry
        ChangeLogEntry multiNodeEntry = dbDeployJdbcTemplate.queryForObject(
                "SELECT * FROM schemaflow_change_log WHERE script_name = 'create-users-table-multinode'",
                (rs, rowNum) -> {
                    ChangeLogEntry entry = new ChangeLogEntry();
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
}

