package com.scb.mrp.schemaflow.dbdeploy.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScriptExecutionManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScriptExecutionManager Tests")
public class ScriptExecutionManagerTest {

    @Mock
    private ScriptExecutor scriptExecutorV2;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    @Qualifier("nodeJdbcTemplateMap")
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    @InjectMocks
    private ScriptExecutionManager scriptExecutionManager;

    private ScriptExecutor.ScriptExecutionResult successResult;
    private ScriptExecutor.ScriptExecutionResult failureResult;

    @BeforeEach
    public void setUp() {
        successResult = new ScriptExecutor.ScriptExecutionResult();
        successResult.setSuccess(true);
        successResult.setStartTime(LocalDateTime.now());
        successResult.setEndTime(LocalDateTime.now());
        successResult.setDuration(100);

        failureResult = new ScriptExecutor.ScriptExecutionResult();
        failureResult.setSuccess(false);
        failureResult.setErrorMessage("Test error");
        failureResult.setStartTime(LocalDateTime.now());
        failureResult.setEndTime(LocalDateTime.now());
        failureResult.setDuration(50);

        ReflectionTestUtils.setField(scriptExecutionManager, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(scriptExecutionManager, "nodeJdbcTemplateMap", nodeJdbcTemplateMap);
    }

    @Test
    @DisplayName("Execute and verify single SQL successfully")
    public void testExecuteAndVerifySuccess() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "SELECT COUNT(*) FROM test_table;";
        String sqlName = "test_script";

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(successResult);
        when(scriptExecutorV2.executeVerificationSql(any(JdbcTemplate.class), anyString())).thenReturn(successResult);

        ScriptExecutor.ScriptExecutionResult result = scriptExecutionManager.executeAndVerify(
                sqlContent, sqlVerificationContent, sqlName);

        assertTrue(result.isSuccess(), "Execution should succeed");
        verify(scriptExecutorV2, times(1)).execute(eq(jdbcTemplate), eq(sqlContent));
        verify(scriptExecutorV2, times(1)).executeVerificationSql(eq(jdbcTemplate), eq(sqlVerificationContent));
    }

    @Test
    @DisplayName("Execute and verify single SQL without verification")
    public void testExecuteAndVerifyWithoutVerification() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlName = "test_script";

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(successResult);

        ScriptExecutor.ScriptExecutionResult result = scriptExecutionManager.executeAndVerify(
                sqlContent, null, sqlName);

        assertTrue(result.isSuccess(), "Execution should succeed");
        verify(scriptExecutorV2, times(1)).execute(eq(jdbcTemplate), eq(sqlContent));
        verify(scriptExecutorV2, never()).executeVerificationSql(any(JdbcTemplate.class), anyString());
    }

    @Test
    @DisplayName("Execute and verify single SQL with empty verification")
    public void testExecuteAndVerifyWithEmptyVerification() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "  ";
        String sqlName = "test_script";

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(successResult);

        ScriptExecutor.ScriptExecutionResult result = scriptExecutionManager.executeAndVerify(
                sqlContent, sqlVerificationContent, sqlName);

        assertTrue(result.isSuccess(), "Execution should succeed");
        verify(scriptExecutorV2, times(1)).execute(eq(jdbcTemplate), eq(sqlContent));
        verify(scriptExecutorV2, never()).executeVerificationSql(any(JdbcTemplate.class), anyString());
    }

    @Test
    @DisplayName("Execute and verify single SQL when main SQL fails")
    public void testExecuteAndVerifyMainSqlFails() {
        String sqlContent = "CREATE INVALID TABLE test_table;";
        String sqlVerificationContent = "SELECT COUNT(*) FROM test_table;";
        String sqlName = "test_script";

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(failureResult);

        ScriptExecutor.ScriptExecutionResult result = scriptExecutionManager.executeAndVerify(
                sqlContent, sqlVerificationContent, sqlName);

        assertFalse(result.isSuccess(), "Execution should fail");
        assertEquals("Test error", result.getErrorMessage(), "Error message should match");
        verify(scriptExecutorV2, times(1)).execute(eq(jdbcTemplate), eq(sqlContent));
        verify(scriptExecutorV2, never()).executeVerificationSql(any(JdbcTemplate.class), anyString());
    }

    @Test
    @DisplayName("Execute and verify single SQL when verification fails")
    public void testExecuteAndVerifyVerificationFails() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "SELECT 'FAILED' AS result;";
        String sqlName = "test_script";

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(successResult);
        when(scriptExecutorV2.executeVerificationSql(any(JdbcTemplate.class), anyString())).thenReturn(failureResult);

        ScriptExecutor.ScriptExecutionResult result = scriptExecutionManager.executeAndVerify(
                sqlContent, sqlVerificationContent, sqlName);

        assertFalse(result.isSuccess(), "Execution should fail");
        assertTrue(result.getErrorMessage().contains("Verification failed"), "Error message should contain verification failure");
        verify(scriptExecutorV2, times(1)).execute(eq(jdbcTemplate), eq(sqlContent));
        verify(scriptExecutorV2, times(1)).executeVerificationSql(eq(jdbcTemplate), eq(sqlVerificationContent));
    }

    @Test
    @DisplayName("Execute and verify on multiple nodes successfully")
    public void testExecuteAndVerifyOnMultipleNodesSuccess() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "SELECT COUNT(*) FROM test_table;";
        String sqlName = "test_script";
        List<String> targetNodes = List.of("node1", "node2", "node3");

        JdbcTemplate node1Template = mock(JdbcTemplate.class);
        JdbcTemplate node2Template = mock(JdbcTemplate.class);
        JdbcTemplate node3Template = mock(JdbcTemplate.class);

        Map<String, JdbcTemplate> nodeMap = new HashMap<>();
        nodeMap.put("node1", node1Template);
        nodeMap.put("node2", node2Template);
        nodeMap.put("node3", node3Template);

        ReflectionTestUtils.setField(scriptExecutionManager, "nodeJdbcTemplateMap", nodeMap);

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(successResult);
        when(scriptExecutorV2.executeVerificationSql(any(JdbcTemplate.class), anyString())).thenReturn(successResult);

        ScriptExecutionManager.MultiNodeExecutionResult result = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                sqlContent, sqlVerificationContent, targetNodes, sqlName);

        assertTrue(result.isSuccess(), "Multi-node execution should succeed");
        assertEquals(sqlName, result.getScriptName(), "Script name should match");
        assertEquals(targetNodes, result.getTargetNodes(), "Target nodes should match");
        assertEquals(3, result.getNodeResults().size(), "Should have 3 node results");
        assertNotNull(result.getStartTime(), "Start time should be set");
        assertNotNull(result.getEndTime(), "End time should be set");
        assertTrue(result.getDuration() >= 0, "Duration should be non-negative");
    }

    @Test
    @DisplayName("Execute and verify on multiple nodes when one node fails")
    public void testExecuteAndVerifyOnMultipleNodesOneNodeFails() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "SELECT COUNT(*) FROM test_table;";
        String sqlName = "test_script";
        List<String> targetNodes = List.of("node1", "node2");

        JdbcTemplate node1Template = mock(JdbcTemplate.class);
        JdbcTemplate node2Template = mock(JdbcTemplate.class);

        Map<String, JdbcTemplate> nodeMap = new HashMap<>();
        nodeMap.put("node1", node1Template);
        nodeMap.put("node2", node2Template);

        ReflectionTestUtils.setField(scriptExecutionManager, "nodeJdbcTemplateMap", nodeMap);

        when(scriptExecutorV2.execute(eq(node1Template), anyString())).thenReturn(successResult);
        when(scriptExecutorV2.execute(eq(node2Template), anyString())).thenReturn(failureResult);
        when(scriptExecutorV2.executeVerificationSql(any(JdbcTemplate.class), anyString())).thenReturn(successResult);

        ScriptExecutionManager.MultiNodeExecutionResult result = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                sqlContent, sqlVerificationContent, targetNodes, sqlName);

        assertFalse(result.isSuccess(), "Multi-node execution should fail when one node fails");
        assertEquals(sqlName, result.getScriptName(), "Script name should match");
        assertEquals(2, result.getNodeResults().size(), "Should have 2 node results");
        assertTrue(result.getErrorMessage().contains("SQL execution failed on"), "Error message should mention failed nodes");
    }

    @Test
    @DisplayName("Execute and verify on multiple nodes when JdbcTemplate not found")
    public void testExecuteAndVerifyOnMultipleNodesJdbcTemplateNotFound() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "SELECT COUNT(*) FROM test_table;";
        String sqlName = "test_script";
        List<String> targetNodes = List.of("node1", "node2");

        Map<String, JdbcTemplate> nodeMap = new HashMap<>();
        nodeMap.put("node1", mock(JdbcTemplate.class));
        // node2 is not in the map

        ReflectionTestUtils.setField(scriptExecutionManager, "nodeJdbcTemplateMap", nodeMap);

        ScriptExecutionManager.MultiNodeExecutionResult result = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                sqlContent, sqlVerificationContent, targetNodes, sqlName);

        assertFalse(result.isSuccess(), "Multi-node execution should fail when JdbcTemplate not found");
        // The error message might be "Parallel execution failed" or contain "JdbcTemplate not found for node"
        assertNotNull(result.getErrorMessage(), "Error message should be set");
    }

    @Test
    @DisplayName("Execute and verify on multiple nodes without verification")
    public void testExecuteAndVerifyOnMultipleNodesWithoutVerification() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlName = "test_script";
        List<String> targetNodes = List.of("node1");

        JdbcTemplate node1Template = mock(JdbcTemplate.class);

        Map<String, JdbcTemplate> nodeMap = new HashMap<>();
        nodeMap.put("node1", node1Template);

        ReflectionTestUtils.setField(scriptExecutionManager, "nodeJdbcTemplateMap", nodeMap);

        when(scriptExecutorV2.execute(any(JdbcTemplate.class), anyString())).thenReturn(successResult);

        ScriptExecutionManager.MultiNodeExecutionResult result = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                sqlContent, null, targetNodes, sqlName);

        assertTrue(result.isSuccess(), "Multi-node execution should succeed");
        verify(scriptExecutorV2, times(1)).execute(eq(node1Template), eq(sqlContent));
        verify(scriptExecutorV2, never()).executeVerificationSql(any(JdbcTemplate.class), anyString());
    }

    @Test
    @DisplayName("Execute and verify on empty node list")
    public void testExecuteAndVerifyOnMultipleNodesEmptyList() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);";
        String sqlVerificationContent = "SELECT COUNT(*) FROM test_table;";
        String sqlName = "test_script";
        List<String> targetNodes = new ArrayList<>();

        ScriptExecutionManager.MultiNodeExecutionResult result = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                sqlContent, sqlVerificationContent, targetNodes, sqlName);

        assertTrue(result.isSuccess(), "Multi-node execution should succeed with empty node list");
        assertEquals(0, result.getNodeResults().size(), "Should have 0 node results");
    }

    @Test
    @DisplayName("MultiNodeExecutionResult data class holds all fields")
    public void testMultiNodeExecutionResultFields() {
        ScriptExecutionManager.MultiNodeExecutionResult result = new ScriptExecutionManager.MultiNodeExecutionResult();
        
        result.setScriptName("test_script");
        result.setTargetNodes(List.of("node1", "node2"));
        result.setNodeResults(List.of(successResult, failureResult));
        result.setSuccess(true);
        result.setErrorMessage("Test error");
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(100);

        assertEquals("test_script", result.getScriptName());
        assertEquals(2, result.getTargetNodes().size());
        assertEquals(2, result.getNodeResults().size());
        assertTrue(result.isSuccess());
        assertEquals("Test error", result.getErrorMessage());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertEquals(100, result.getDuration());
    }
}