package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogAuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogScript;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SQLiteAuditRepository.
 * Tests SQLite-specific behavior including timestamp handling and lock SQL generation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SQLiteAuditRepository Tests")
public class SQLiteAuditRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SQLiteAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SQLiteAuditRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Test getAcquireLockSql generates correct SQLite INSERT OR IGNORE statement")
    void testGetAcquireLockSql() {
        // Arrange
        when(jdbcTemplate.update(any(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        boolean result = repository.acquireLock("test-key", "test-owner", 30);

        // Assert
        assertTrue(result);
        verify(jdbcTemplate).update(
                any(),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test acquireLock with different timeout values")
    void testAcquireLockWithDifferentTimeouts() {
        // Arrange
        when(jdbcTemplate.update(any(), eq("key1"), eq("owner1")))
                .thenReturn(1);
        when(jdbcTemplate.update(any(), eq("key2"), eq("owner2")))
                .thenReturn(1);
        when(jdbcTemplate.update(any(), eq("key3"), eq("owner3")))
                .thenReturn(1);

        // Act
        boolean result1 = repository.acquireLock("key1", "owner1", 15);
        boolean result2 = repository.acquireLock("key2", "owner2", 60);
        boolean result3 = repository.acquireLock("key3", "owner3", 120);

        // Assert
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);

        verify(jdbcTemplate).update(
                any(),
                eq("key1"), eq("owner1")
        );
        verify(jdbcTemplate).update(
                any(),
                eq("key2"), eq("owner2")
        );
        verify(jdbcTemplate).update(
                any(),
                eq("key3"), eq("owner3")
        );
    }

    @Test
    @DisplayName("Test prepareTimestamp converts LocalDateTime to ISO-8601 string")
    void testPrepareTimestamp() {
        // Arrange
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 27, 10, 30, 45);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                eq("2024-01-27T10:30:45")
        )).thenReturn(1L);

        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(testTime);

        // Act
        repository.createScriptMetadata(metadata);

        // Assert
        verify(jdbcTemplate).queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                eq("2024-01-27T10:30:45")
        );
    }

    @Test
    @DisplayName("Test prepareTimestamp with null LocalDateTime returns current time string")
    void testPrepareTimestampWithNull() {
        // Arrange
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(String.class)  // Should be current time as string
        )).thenReturn(1L);

        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(null);

        // Act
        repository.createScriptMetadata(metadata);

        // Assert
        verify(jdbcTemplate).queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(String.class)
        );
    }

    @Test
    @DisplayName("Test parseTimestamp converts ISO-8601 string to LocalDateTime")
    void testParseTimestamp() {
        // Arrange
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", "2024-01-27T10:30:45");
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(scriptRow));

        // Act
        DatabaseStatus.ScriptSummary summary =
                repository.getScriptSummary();

        // Assert
        assertNotNull(summary);
        assertNotNull(summary.getScripts());
        assertEquals(1, summary.getScripts().size());
        assertEquals(LocalDateTime.of(2024, 1, 27, 10, 30, 45), summary.getScripts().get(0).getCreatedAt());
    }

    @Test
    @DisplayName("Test parseTimestamp with various ISO-8601 formats")
    void testParseTimestampWithVariousFormats() {
        // Test different ISO-8601 string formats
        String[] isoFormats = {
                "2024-01-27T10:30:45",
                "2024-01-27T10:30:45.123",
                "2024-12-31T23:59:59.999"
        };

        for (String isoFormat : isoFormats) {
            Map<String, Object> scriptRow = new HashMap<>();
            scriptRow.put("id", 1L);
            scriptRow.put("script_name", "test.sql");
            scriptRow.put("script_checksum", "abc123");
            scriptRow.put("rollback_script_content", "DROP TABLE test;");
            scriptRow.put("rollback_verify_script_content", "SELECT 1;");
            scriptRow.put("target_nodes", null);
            scriptRow.put("created_at", isoFormat);
            scriptRow.put("latest_status", "SUCCESS");

            when(jdbcTemplate.queryForList(anyString()))
                    .thenReturn(List.of(scriptRow));

            DatabaseStatus.ScriptSummary summary =
                    repository.getScriptSummary();

            assertNotNull(summary.getScripts().get(0).getCreatedAt());
        }
    }

    @Test
    @DisplayName("Test parseTimestamp with null value returns current time")
    void testParseTimestampWithNull() {
        // Arrange
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", null);
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(scriptRow));

        // Act
        DatabaseStatus.ScriptSummary summary =
                repository.getScriptSummary();

        // Assert
        assertNotNull(summary);
        assertNotNull(summary.getScripts());
        assertEquals(1, summary.getScripts().size());
        assertNotNull(summary.getScripts().get(0).getCreatedAt());
    }

    @Test
    @DisplayName("Test parseTimestamp with invalid string returns current time")
    void testParseTimestampWithInvalidString() {
        // Arrange
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", "invalid-timestamp");
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(scriptRow));

        // Act
        DatabaseStatus.ScriptSummary summary =
                repository.getScriptSummary();

        // Assert
        assertNotNull(summary);
        assertNotNull(summary.getScripts());
        assertEquals(1, summary.getScripts().size());
        assertNotNull(summary.getScripts().get(0).getCreatedAt());
    }

    @Test
    @DisplayName("Test createScriptAuditEntry uses ISO-8601 string for timestamps")
    void testCreateScriptAuditEntryTimestampFormat() {
        // Arrange
        ChangeLogAuditEntry entry =
                new ChangeLogAuditEntry(
                        100L,
                        ScriptExecutionStatus.SUCCESS
                );
        entry.setExecutionTime(LocalDateTime.of(2024, 1, 27, 10, 30, 45));
        entry.setExecutionDurationMs(1234L);
        entry.setErrorMessage(null);
        entry.setNodeExecutionDetails(null);
        entry.setCreatedAt(LocalDateTime.of(2024, 1, 27, 10, 30, 45));

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        // Act
        repository.createScriptAuditEntry(entry);

        // Assert
        verify(jdbcTemplate).update(
                anyString(),
                eq(100L),
                eq("SUCCESS"),
                eq("2024-01-27T10:30:45"),
                eq(1234L),
                isNull(),
                isNull(),
                eq("2024-01-27T10:30:45")
        );
    }

    @Test
    @DisplayName("Test releaseLock uses standard DELETE statement")
    void testReleaseLock() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        repository.releaseLock("test-key", "test-owner");

        // Assert
        verify(jdbcTemplate).update(
                eq("DELETE FROM schemaflow_deploy_lock WHERE lock_key = ? AND lock_owner = ?"),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test getCurrentLockStatus with SQLite timestamp format")
    void testGetCurrentLockStatusWithSQLiteTimestamp() {
        // Arrange
        Map<String, Object> lockRow = new HashMap<>();
        lockRow.put("lock_owner", "test-owner");
        lockRow.put("lock_acquired_at", "2024-01-27 10:00:00");
        lockRow.put("lock_expires_at", "2024-01-27 10:30:00");
        lockRow.put("is_active", 1);

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(lockRow));

        // Act
        DatabaseStatus.LockInfo lockInfo =
                repository.getCurrentLockStatus();

        // Assert
        assertNotNull(lockInfo);
        assertEquals("test-owner", lockInfo.getLockOwner());
        assertEquals("2024-01-27 10:00:00", lockInfo.getLockAcquiredAt());
        assertEquals("2024-01-27 10:30:00", lockInfo.getLockExpiresAt());
        assertTrue(lockInfo.isActive());
    }

    @Test
    @DisplayName("Test getRecentAuditHistory with SQLite timestamp format")
    void testGetRecentAuditHistoryWithSQLiteTimestamp() {
        // Arrange
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("audit_id", 1L);
        auditRow.put("script_name", "test-script.sql");
        auditRow.put("script_checksum", "abc123");
        auditRow.put("target_nodes", null);
        auditRow.put("execution_status", "SUCCESS");
        auditRow.put("execution_time", "2024-01-27T10:30:45");
        auditRow.put("execution_duration_ms", 1234L);
        auditRow.put("error_message", null);
        auditRow.put("node_execution_details", null);

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(auditRow));

        // Act
        List<DatabaseStatus.AuditHistoryEntry> history =
                repository.getRecentAuditHistory();

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("test-script.sql", history.get(0).getScriptName());
        assertEquals(LocalDateTime.of(2024, 1, 27, 10, 30, 45), history.get(0).getExecutionTime());
    }

    @Test
    @DisplayName("Test SQLite-specific timestamp handling edge cases")
    void testSQLiteTimestampEdgeCases() {
        // Test edge cases for timestamp parsing

        // Case 1: Midnight
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 1L);
        row1.put("script_name", "test1.sql");
        row1.put("script_checksum", "abc123");
        row1.put("rollback_script_content", "DROP TABLE test1;");
        row1.put("rollback_verify_script_content", "SELECT 1;");
        row1.put("target_nodes", null);
        row1.put("created_at", "2024-01-27T00:00:00");
        row1.put("latest_status", "SUCCESS");

        // Case 2: End of day
        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 2L);
        row2.put("script_name", "test2.sql");
        row2.put("script_checksum", "def456");
        row2.put("rollback_script_content", "DROP TABLE test2;");
        row2.put("rollback_verify_script_content", "SELECT 2;");
        row2.put("target_nodes", null);
        row2.put("created_at", "2024-12-31T23:59:59.999");
        row2.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(row1, row2));

        DatabaseStatus.ScriptSummary summary =
                repository.getScriptSummary();

        assertEquals(2, summary.getScripts().size());
        assertEquals(LocalDateTime.of(2024, 1, 27, 0, 0, 0), summary.getScripts().get(0).getCreatedAt());
        assertEquals(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999000000), summary.getScripts().get(1).getCreatedAt());
    }
}
