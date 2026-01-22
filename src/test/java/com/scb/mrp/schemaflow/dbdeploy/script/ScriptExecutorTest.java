package com.scb.mrp.schemaflow.dbdeploy.script;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScriptExecutor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScriptExecutor Tests")
public class ScriptExecutorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ScriptExecutor scriptExecutor;

    @Test
    @DisplayName("Execute simple SQL statement successfully")
    public void testExecuteSimpleSqlSuccess() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(100));";

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(jdbcTemplate, sqlContent);

        assertTrue(result.isSuccess(), "Execution should succeed");
        assertNull(result.getErrorMessage(), "Error message should be null");
        assertNotNull(result.getStartTime(), "Start time should be set");
        assertNotNull(result.getEndTime(), "End time should be set");
        assertTrue(result.getDuration() >= 0, "Duration should be non-negative");
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("Execute multiple SQL statements successfully")
    public void testExecuteMultipleSqlStatementsSuccess() {
        String sqlContent = "CREATE TABLE test_table1 (id INT PRIMARY KEY);" +
                "CREATE TABLE test_table2 (id INT PRIMARY KEY);" +
                "INSERT INTO test_table1 VALUES (1);";

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(jdbcTemplate, sqlContent);

        assertTrue(result.isSuccess(), "Execution should succeed");
        verify(jdbcTemplate, times(3)).execute(anyString());
    }

    @Test
    @DisplayName("Execute SQL with empty statements")
    public void testExecuteSqlWithEmptyStatements() {
        String sqlContent = "CREATE TABLE test_table (id INT PRIMARY KEY);  ;  ;";

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(jdbcTemplate, sqlContent);

        assertTrue(result.isSuccess(), "Execution should succeed");
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("Execute SQL with failure")
    public void testExecuteSqlFailure() {
        String sqlContent = "CREATE INVALID TABLE test_table;";
        doThrow(new RuntimeException("SQL syntax error")).when(jdbcTemplate).execute(anyString());

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(jdbcTemplate, sqlContent);

        assertFalse(result.isSuccess(), "Execution should fail");
        assertNotNull(result.getErrorMessage(), "Error message should be set");
        assertTrue(result.getErrorMessage().contains("SQL syntax error"), "Error message should contain the error");
        assertNotNull(result.getStartTime(), "Start time should be set");
        assertNotNull(result.getEndTime(), "End time should be set");
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("Execute verification SQL successfully")
    public void testExecuteVerificationSqlSuccess() {
        String sqlContent = "SELECT 'SUCCESS' AS result;";

        // Mock the query method to invoke the callback handler
        doAnswer(invocation -> {
            org.springframework.jdbc.core.RowCallbackHandler handler = invocation.getArgument(1);
            // Create a mock ResultSet
            java.sql.ResultSet mockResultSet = mock(java.sql.ResultSet.class);
            when(mockResultSet.getString(1)).thenReturn("SUCCESS");
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeVerificationSql(jdbcTemplate, sqlContent);

        assertTrue(result.isSuccess(), "Verification should succeed");
        assertNull(result.getErrorMessage(), "Error message should be null");
        verify(jdbcTemplate, times(1)).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));
    }

    @Test
    @DisplayName("Execute verification SQL with FAILED output")
    public void testExecuteVerificationSqlWithFailedOutput() {
        String sqlContent = "SELECT 'FAILED - Test failed' AS result;";

        // Mock the query method to invoke the callback handler
        doAnswer(invocation -> {
            org.springframework.jdbc.core.RowCallbackHandler handler = invocation.getArgument(1);
            // Create a mock ResultSet
            java.sql.ResultSet mockResultSet = mock(java.sql.ResultSet.class);
            when(mockResultSet.getString(1)).thenReturn("FAILED - Test failed");
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeVerificationSql(jdbcTemplate, sqlContent);

        assertFalse(result.isSuccess(), "Verification should fail when output contains FAILED");
        assertNotNull(result.getErrorMessage(), "Error message should be set");
        assertTrue(result.getErrorMessage().contains("FAILED"), "Error message should contain FAILED");
    }

    @Test
    @DisplayName("Execute verification SQL with FAIL output")
    public void testExecuteVerificationSqlWithFailOutput() {
        String sqlContent = "SELECT 'FAIL - Test failed' AS result;";

        // Mock the query method to invoke the callback handler
        doAnswer(invocation -> {
            org.springframework.jdbc.core.RowCallbackHandler handler = invocation.getArgument(1);
            // Create a mock ResultSet
            java.sql.ResultSet mockResultSet = mock(java.sql.ResultSet.class);
            when(mockResultSet.getString(1)).thenReturn("FAIL - Test failed");
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeVerificationSql(jdbcTemplate, sqlContent);

        assertFalse(result.isSuccess(), "Verification should fail when output contains FAIL");
        assertNotNull(result.getErrorMessage(), "Error message should be set");
        assertTrue(result.getErrorMessage().contains("FAIL"), "Error message should contain FAIL");
    }

    @Test
    @DisplayName("Execute verification SQL with failure")
    public void testExecuteVerificationSqlFailure() {
        String sqlContent = "SELECT * FROM invalid_table;";
        doThrow(new RuntimeException("Table not found")).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));

        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeVerificationSql(jdbcTemplate, sqlContent);

        assertFalse(result.isSuccess(), "Verification should fail");
        assertNotNull(result.getErrorMessage(), "Error message should be set");
        assertTrue(result.getErrorMessage().contains("Table not found"), "Error message should contain the error");
        verify(jdbcTemplate, times(1)).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));
    }

    @Test
    @DisplayName("Calculate checksum for content")
    public void testCalculateChecksum() {
        String content = "SELECT * FROM test_table;";

        String checksum = scriptExecutor.calculateChecksum(content);

        assertNotNull(checksum, "Checksum should not be null");
        assertEquals(64, checksum.length(), "SHA-256 checksum should be 64 characters");
        assertFalse(checksum.isEmpty(), "Checksum should not be empty");
    }

    @Test
    @DisplayName("Calculate checksum is consistent for same content")
    public void testCalculateChecksumConsistency() {
        String content = "SELECT * FROM test_table;";

        String checksum1 = scriptExecutor.calculateChecksum(content);
        String checksum2 = scriptExecutor.calculateChecksum(content);

        assertEquals(checksum1, checksum2, "Checksum should be consistent for same content");
    }

    @Test
    @DisplayName("Calculate checksum differs for different content")
    public void testCalculateChecksumDifferentContent() {
        String content1 = "SELECT * FROM test_table;";
        String content2 = "SELECT * FROM other_table;";

        String checksum1 = scriptExecutor.calculateChecksum(content1);
        String checksum2 = scriptExecutor.calculateChecksum(content2);

        assertNotEquals(checksum1, checksum2, "Checksum should differ for different content");
    }

    @Test
    @DisplayName("Calculate checksum for empty string")
    public void testCalculateChecksumEmptyString() {
        String content = "";

        String checksum = scriptExecutor.calculateChecksum(content);

        assertNotNull(checksum, "Checksum should not be null for empty string");
        assertEquals(64, checksum.length(), "SHA-256 checksum should be 64 characters");
    }

    @Test
    @DisplayName("ScriptExecutionResult data class holds all fields")
    public void testScriptExecutionResultFields() {
        ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor.ScriptExecutionResult();

        result.setSuccess(true);
        result.setErrorMessage("Test error");
        result.setOutput("Test output");
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(100);

        assertTrue(result.isSuccess());
        assertEquals("Test error", result.getErrorMessage());
        assertEquals("Test output", result.getOutput());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertEquals(100, result.getDuration());
    }
}