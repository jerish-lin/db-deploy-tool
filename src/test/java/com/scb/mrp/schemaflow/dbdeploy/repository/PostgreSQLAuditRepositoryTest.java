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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PostgreSQLAuditRepository.
 * Tests PostgreSQL-specific behavior including ON CONFLICT handling and timestamp management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PostgreSQLAuditRepository Tests")
public class PostgreSQLAuditRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PostgreSQLAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgreSQLAuditRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Test getAcquireLockSql generates correct PostgreSQL ON CONFLICT statement")
    void testGetAcquireLockSql() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        boolean result = repository.acquireLock("test-key", "test-owner", 30);

        // Assert
        assertTrue(result);
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '30 minutes', TRUE) ON CONFLICT (lock_key) DO NOTHING"),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test getAcquireLockSql with different timeout values")
    void testGetAcquireLockSqlWithDifferentTimeouts() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("key1"), eq("owner1")))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq("key2"), eq("owner2")))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq("key3"), eq("owner3")))
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
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '15 minutes', TRUE) ON CONFLICT (lock_key) DO NOTHING"),
                eq("key1"),
                eq("owner1")
        );
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '60 minutes', TRUE) ON CONFLICT (lock_key) DO NOTHING"),
                eq("key2"),
                eq("owner2")
        );
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '120 minutes', TRUE) ON CONFLICT (lock_key) DO NOTHING"),
                eq("key3"),
                eq("owner3")
        );
    }

    @Test
    @DisplayName("Test acquireLock fails when lock already exists (ON CONFLICT DO NOTHING)")
    void testAcquireLockFailsWhenLockExists() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(0); // No rows inserted due to ON CONFLICT

        // Act
        boolean result = repository.acquireLock("test-key", "test-owner", 30);

        // Assert
        assertFalse(result);
        verify(jdbcTemplate).update(
                contains("ON CONFLICT (lock_key) DO NOTHING"),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test prepareTimestamp converts LocalDateTime to Timestamp")
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
                eq(Timestamp.valueOf(testTime))
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
                eq(Timestamp.valueOf(testTime))
        );
    }

    @Test
    @DisplayName("Test prepareTimestamp with null LocalDateTime returns current Timestamp")
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
                any(Timestamp.class)  // Should be current timestamp
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
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test parseTimestamp converts Timestamp to LocalDateTime")
    void testParseTimestamp() {
        // Arrange
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 27, 10, 30, 45);
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", Timestamp.valueOf(testTime));
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
        assertEquals(testTime, summary.getScripts().get(0).getCreatedAt());
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
    @DisplayName("Test parseTimestamp with various Timestamp values")
    void testParseTimestampWithVariousTimestamps() {
        // Test different timestamp scenarios
        LocalDateTime[] testTimes = {
                LocalDateTime.of(2024, 1, 27, 10, 30, 45),
                LocalDateTime.of(2024, 12, 31, 23, 59, 59),
                LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                LocalDateTime.of(2024, 6, 15, 12, 30, 0)
        };

        for (LocalDateTime testTime : testTimes) {
            Map<String, Object> scriptRow = new HashMap<>();
            scriptRow.put("id", 1L);
            scriptRow.put("script_name", "test.sql");
            scriptRow.put("script_checksum", "abc123");
            scriptRow.put("rollback_script_content", "DROP TABLE test;");
            scriptRow.put("rollback_verify_script_content", "SELECT 1;");
            scriptRow.put("target_nodes", null);
            scriptRow.put("created_at", Timestamp.valueOf(testTime));
            scriptRow.put("latest_status", "SUCCESS");

            when(jdbcTemplate.queryForList(anyString()))
                    .thenReturn(List.of(scriptRow));

            DatabaseStatus.ScriptSummary summary =
                    repository.getScriptSummary();

            assertEquals(testTime, summary.getScripts().get(0).getCreatedAt());
        }
    }

    @Test
    @DisplayName("Test createScriptAuditEntry uses Timestamp for timestamps")
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
                eq(Timestamp.valueOf(LocalDateTime.of(2024, 1, 27, 10, 30, 45))),
                eq(1234L),
                isNull(),
                isNull(),
                eq(Timestamp.valueOf(LocalDateTime.of(2024, 1, 27, 10, 30, 45)))
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
    @DisplayName("Test getCurrentLockStatus with PostgreSQL timestamp format")
    void testGetCurrentLockStatusWithPostgreSQLTimestamp() {
        // Arrange
        Map<String, Object> lockRow = new HashMap<>();
        lockRow.put("lock_owner", "test-owner");
        lockRow.put("lock_acquired_at", Timestamp.valueOf(LocalDateTime.of(2024, 1, 27, 10, 0, 0)).toString());
        lockRow.put("lock_expires_at", Timestamp.valueOf(LocalDateTime.of(2024, 1, 27, 10, 30, 0)).toString());
        lockRow.put("is_active", 1);

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(lockRow));

        // Act
        DatabaseStatus.LockInfo lockInfo =
                repository.getCurrentLockStatus();

        // Assert
        assertNotNull(lockInfo);
        assertEquals("test-owner", lockInfo.getLockOwner());
        assertNotNull(lockInfo.getLockAcquiredAt());
        assertNotNull(lockInfo.getLockExpiresAt());
        assertTrue(lockInfo.isActive());
    }

    @Test
    @DisplayName("Test getRecentAuditHistory with PostgreSQL timestamp format")
    void testGetRecentAuditHistoryWithPostgreSQLTimestamp() {
        // Arrange
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 27, 10, 30, 45);
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("audit_id", 1L);
        auditRow.put("script_name", "test-script.sql");
        auditRow.put("script_checksum", "abc123");
        auditRow.put("target_nodes", null);
        auditRow.put("execution_status", "SUCCESS");
        auditRow.put("execution_time", Timestamp.valueOf(testTime));
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
        assertEquals(testTime, history.get(0).getExecutionTime());
    }

    @Test
    @DisplayName("Test PostgreSQL-specific ON CONFLICT behavior with concurrent lock attempts")
    void testConcurrentLockAcquisition() {
        // Arrange - Simulate concurrent lock acquisition
        when(jdbcTemplate.update(anyString(), eq("shared-key"), eq("owner1")))
                .thenReturn(1); // First attempt succeeds
        when(jdbcTemplate.update(anyString(), eq("shared-key"), eq("owner2")))
                .thenReturn(0); // Second attempt fails due to ON CONFLICT

        // Act
        boolean result1 = repository.acquireLock("shared-key", "owner1", 30);
        boolean result2 = repository.acquireLock("shared-key", "owner2", 30);

        // Assert
        assertTrue(result1);
        assertFalse(result2);

        verify(jdbcTemplate, times(2)).update(
                contains("ON CONFLICT (lock_key) DO NOTHING"),
                anyString(),
                anyString()
        );
    }

    @Test
    @DisplayName("Test PostgreSQL INTERVAL syntax in lock expiration")
    void testPostgreSQLIntervalSyntax() {
        // Test that INTERVAL syntax is correctly formatted for various timeout values
        int[] timeouts = {1, 5, 15, 30, 60, 120, 1440}; // 1 minute to 1 day

        for (int timeout : timeouts) {
            when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                    .thenReturn(1);

            repository.acquireLock("test-key", "test-owner", timeout);

            verify(jdbcTemplate).update(
                    eq(String.format(
                            "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) ON CONFLICT (lock_key) DO NOTHING",
                            timeout
                    )),
                    eq("test-key"),
                    eq("test-owner")
            );
        }
    }

    @Test
    @DisplayName("Test PostgreSQL RETURNING clause in createScriptMetadata")
    void testCreateScriptMetadataWithReturningClause() {
        // Arrange
        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(LocalDateTime.now());

        Long expectedId = 100L;
        when(jdbcTemplate.queryForObject(
                contains("RETURNING id"),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                any(Timestamp.class)
        )).thenReturn(expectedId);

        // Act
        Long resultId = repository.createScriptMetadata(metadata);

        // Assert
        assertEquals(expectedId, resultId);
        verify(jdbcTemplate).queryForObject(
                contains("RETURNING id"),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test PostgreSQL timestamp precision handling")
    void testPostgreSQLTimestampPrecision() {
        // Test that PostgreSQL handles microsecond precision correctly
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 27, 10, 30, 45, 123456789);

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(Timestamp.class)
        )).thenReturn(1L);

        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(testTime);

        repository.createScriptMetadata(metadata);

        verify(jdbcTemplate).queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(Timestamp.class)
        );
    }
}
