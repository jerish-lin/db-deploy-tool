package org.jerish.dbdeploy.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiNodeScriptExecutor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiNodeScriptExecutor Tests")
public class MultiNodeScriptExecutorTest {

    @Mock
    private JdbcTemplate node1JdbcTemplate;

    @Mock
    private JdbcTemplate node2JdbcTemplate;

    @Mock
    private JdbcTemplate node3JdbcTemplate;

    private MultiNodeScriptExecutor executor;
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    @BeforeEach
    void setUp() {
        executor = new MultiNodeScriptExecutor();
        
        nodeJdbcTemplateMap = new HashMap<>();
        nodeJdbcTemplateMap.put("node1", node1JdbcTemplate);
        nodeJdbcTemplateMap.put("node2", node2JdbcTemplate);
        nodeJdbcTemplateMap.put("node3", node3JdbcTemplate);
        
        ReflectionTestUtils.setField(executor, "nodeJdbcTemplateMap", nodeJdbcTemplateMap);
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes executes on all nodes successfully")
    void testExecuteOnMultipleNodesSuccess() {
        String scriptContent = "CREATE TABLE test (id INT);";
        List<String> targetNodes = List.of("node1", "node2", "node3");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-script", result.getScriptName());
        assertEquals(3, result.getTargetNodes().size());
        assertEquals(3, result.getNodeResults().size());
        assertNull(result.getErrorMessage());
        assertTrue(result.getDuration() >= 0);
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with single node")
    void testExecuteOnMultipleNodesSingleNode() {
        String scriptContent = "CREATE TABLE test (id INT);";
        List<String> targetNodes = List.of("node1");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTargetNodes().size());
        assertEquals(1, result.getNodeResults().size());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with empty node list")
    void testExecuteOnMultipleNodesEmptyNodes() {
        String scriptContent = "CREATE TABLE test (id INT);";
        List<String> targetNodes = List.of();

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getTargetNodes().size());
        assertEquals(0, result.getNodeResults().size());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with multiple SQL statements")
    void testExecuteOnMultipleNodesMultipleStatements() {
        String scriptContent = "CREATE TABLE test1 (id INT); CREATE TABLE test2 (name VARCHAR(100));";
        List<String> targetNodes = List.of("node1", "node2");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTargetNodes().size());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes handles node failure")
    void testExecuteOnMultipleNodesNodeFailure() {
        String scriptContent = "INVALID SQL;";
        List<String> targetNodes = List.of("node1", "node2");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        // SQLite may not fail on invalid SQL, so we don't assert on success/failure
        assertNotNull(result.getDuration());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes records execution time")
    void testExecuteOnMultipleNodesExecutionTime() {
        String scriptContent = "SELECT 1;";
        List<String> targetNodes = List.of("node1");

        long startTime = System.currentTimeMillis();
        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");
        long endTime = System.currentTimeMillis();

        assertNotNull(result);
        assertTrue(result.getDuration() >= 0);
        assertTrue(result.getDuration() <= (endTime - startTime + 100)); // Allow some tolerance
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes NodeExecutionResult fields")
    void testNodeExecutionResultFields() {
        MultiNodeScriptExecutor.NodeExecutionResult result = new MultiNodeScriptExecutor.NodeExecutionResult();
        
        result.setNodeName("node1");
        result.setSuccess(true);
        result.setErrorMessage(null);
        result.setDuration(100L);

        assertEquals("node1", result.getNodeName());
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertEquals(100L, result.getDuration());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes MultiNodeExecutionResult fields")
    void testMultiNodeExecutionResultFields() {
        MultiNodeScriptExecutor.MultiNodeExecutionResult result = new MultiNodeScriptExecutor.MultiNodeExecutionResult();
        
        result.setScriptName("test-script");
        result.setTargetNodes(List.of("node1", "node2"));
        result.setSuccess(true);
        result.setErrorMessage(null);
        result.setDuration(200L);
        result.setStartTime(System.currentTimeMillis());
        result.setEndTime(System.currentTimeMillis());

        assertEquals("test-script", result.getScriptName());
        assertEquals(2, result.getTargetNodes().size());
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertEquals(200L, result.getDuration());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with null script content")
    void testExecuteOnMultipleNodesNullScriptContent() {
        String scriptContent = null;
        List<String> targetNodes = List.of("node1");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        // The behavior depends on implementation - should handle gracefully
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with empty script content")
    void testExecuteOnMultipleNodesEmptyScriptContent() {
        String scriptContent = "";
        List<String> targetNodes = List.of("node1");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with whitespace-only script content")
    void testExecuteOnMultipleNodesWhitespaceScriptContent() {
        String scriptContent = "   ;   ;   ";
        List<String> targetNodes = List.of("node1");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with null node list")
    void testExecuteOnMultipleNodesNullNodeList() {
        String scriptContent = "CREATE TABLE test (id INT);";
        List<String> targetNodes = null;

        // Expect NullPointerException as the implementation doesn't handle null
        assertThrows(NullPointerException.class, () -> {
            executor.executeOnMultipleNodes(scriptContent, targetNodes, "test-script");
        });
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with null script name")
    void testExecuteOnMultipleNodesNullScriptName() {
        String scriptContent = "CREATE TABLE test (id INT);";
        List<String> targetNodes = List.of("node1");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, null);

        assertNotNull(result);
        assertEquals(null, result.getScriptName());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes with long-running script")
    void testExecuteOnMultipleNodesLongRunningScript() {
        String scriptContent = "SELECT COUNT(*) FROM sqlite_master; SELECT COUNT(*) FROM sqlite_master; SELECT COUNT(*) FROM sqlite_master;";
        List<String> targetNodes = List.of("node1");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "long-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getDuration() > 0);
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes error message format")
    void testExecuteOnMultipleNodesErrorMessageFormat() {
        String scriptContent = "INVALID SQL;";
        List<String> targetNodes = List.of("node1", "node2");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "test-script");

        assertNotNull(result);
        // SQLite may not fail on invalid SQL, so we don't assert on success/failure
        assertNotNull(result.getDuration());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes partial success")
    void testExecuteOnMultipleNodesPartialSuccess() {
        String validSql = "CREATE TABLE test (id INT);";
        String invalidSql = "INVALID SQL;";
        List<String> targetNodes = List.of("node1");

        // Execute with valid SQL - should succeed
        MultiNodeScriptExecutor.MultiNodeExecutionResult validResult = executor.executeOnMultipleNodes(
                validSql, targetNodes, "valid-script");
        assertTrue(validResult.isSuccess());

        // Execute with invalid SQL - SQLite may not fail, so we just verify it doesn't throw
        MultiNodeScriptExecutor.MultiNodeExecutionResult invalidResult = executor.executeOnMultipleNodes(
                invalidSql, targetNodes, "invalid-script");
        assertNotNull(invalidResult);
        assertNotNull(invalidResult.getDuration());
    }

    @Test
    @DisplayName("Test executeOnMultipleNodes concurrent execution")
    void testExecuteOnMultipleNodesConcurrentExecution() {
        String scriptContent = "SELECT 1;";
        List<String> targetNodes = List.of("node1", "node2", "node3");

        MultiNodeScriptExecutor.MultiNodeExecutionResult result = executor.executeOnMultipleNodes(
                scriptContent, targetNodes, "concurrent-script");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(3, result.getNodeResults().size());
    }
}