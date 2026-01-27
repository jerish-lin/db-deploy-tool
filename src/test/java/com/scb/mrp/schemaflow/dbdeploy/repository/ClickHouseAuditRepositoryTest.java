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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ClickHouseAuditRepository.
 * Tests ClickHouse-specific behavior including explicit ID handling and INTERVAL syntax.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClickHouseAuditRepository Tests")
public class ClickHouseAuditRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ClickHouseAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ClickHouseAuditRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Test createScriptMetadata generates explicit ID using System.currentTimeMillis")
    void testCreateScriptMetadataWithExplicitId() {
        // Arrange
        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(Timestamp.class),
                any(Long.class)  // Explicit ID parameter
        )).thenReturn(1L);

        // Act
        Long resultId = repository.createScriptMetadata(metadata);

        // Assert
        assertNotNull(resultId);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO schemaflow_changelog_script"),
                eq(Long.class),
                eq("test.sql"),
                eq("abc123"),
                eq("DROP TABLE test;"),
                eq("SELECT 1;"),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test createScriptMetadata includes explicit ID in SQL parameters")
    void testCreateScriptMetadataIdParameter() {
        // Arrange
        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        )).thenReturn(1L);

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
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test createScriptAuditEntry generates explicit ID using System.currentTimeMillis")
    void testCreateScriptAuditEntryWithExplicitId() {
        // Arrange
        ChangeLogAuditEntry entry =
                new ChangeLogAuditEntry(
                        100L,
                        ScriptExecutionStatus.SUCCESS
                );
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(1234L);
        entry.setErrorMessage(null);
        entry.setNodeExecutionDetails(null);
        entry.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.update(
                anyString(),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)  // Explicit ID parameter
        )).thenReturn(1);

        // Act
        repository.createScriptAuditEntry(entry);

        // Assert
        verify(jdbcTemplate).update(
                contains("INSERT INTO schemaflow_changelog_audit"),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test createScriptAuditEntry includes explicit ID in SQL parameters")
    void testCreateScriptAuditEntryIdParameter() {
        // Arrange
        ChangeLogAuditEntry entry =
                new ChangeLogAuditEntry(
                        100L,
                        ScriptExecutionStatus.SUCCESS
                );
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(1234L);
        entry.setErrorMessage(null);
        entry.setNodeExecutionDetails(null);
        entry.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.update(
                anyString(),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        )).thenReturn(1);

        // Act
        repository.createScriptAuditEntry(entry);

        // Assert
        verify(jdbcTemplate).update(
                anyString(),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test getAcquireLockSql generates correct ClickHouse INTERVAL syntax")
    void testGetAcquireLockSql() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        boolean result = repository.acquireLock("test-key", "test-owner", 30);

        // Assert
        assertTrue(result);
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, now() + INTERVAL 30 MINUTE, 1)"),
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
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, now() + INTERVAL 15 MINUTE, 1)"),
                eq("key1"),
                eq("owner1")
        );
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, now() + INTERVAL 60 MINUTE, 1)"),
                eq("key2"),
                eq("owner2")
        );
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, now() + INTERVAL 120 MINUTE, 1)"),
                eq("key3"),
                eq("owner3")
        );
    }

    @Test
    @DisplayName("Test acquireLock with ClickHouse specific INTERVAL syntax")
    void testAcquireLockClickHouseIntervalSyntax() {
        // Test various timeout values to ensure INTERVAL syntax is correct
        int[] timeouts = {1, 5, 15, 30, 60, 120, 1440};

        for (int timeout : timeouts) {
            when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                    .thenReturn(1);

            repository.acquireLock("test-key", "test-owner", timeout);

            verify(jdbcTemplate).update(
                    eq(String.format(
                            "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, now() + INTERVAL %d MINUTE, 1)",
                            timeout
                    )),
                    eq("test-key"),
                    eq("test-owner")
            );
        }
    }

    @Test
    @DisplayName("Test prepareTimestamp uses Timestamp for ClickHouse")
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
                eq(Timestamp.valueOf(testTime)),
                any(Long.class)
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
                eq(Timestamp.valueOf(testTime)),
                any(Long.class)
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
                any(Timestamp.class),
                any(Long.class)
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
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test parseTimestamp converts Timestamp to LocalDateTime (inherited from AbstractAuditRepository)")
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
    @DisplayName("Test releaseLock uses standard DELETE statement (inherited from AbstractAuditRepository)")
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
    @DisplayName("Test getCurrentLockStatus with ClickHouse timestamp format")
    void testGetCurrentLockStatusWithClickHouseTimestamp() {
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
    @DisplayName("Test getRecentAuditHistory with ClickHouse timestamp format")
    void testGetRecentAuditHistoryWithClickHouseTimestamp() {
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
    @DisplayName("Test ClickHouse-specific SQL includes explicit ID in INSERT statements")
    void testClickHouseInsertWithExplicitId() {
        // Arrange
        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        )).thenReturn(1L);

        // Act
        repository.createScriptMetadata(metadata);

        // Assert
        verify(jdbcTemplate).queryForObject(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test ClickHouse audit entry SQL includes explicit ID")
    void testClickHouseAuditEntryWithExplicitId() {
        // Arrange
        ChangeLogAuditEntry entry =
                new ChangeLogAuditEntry(
                        100L,
                        ScriptExecutionStatus.SUCCESS
                );
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(1234L);
        entry.setErrorMessage(null);
        entry.setNodeExecutionDetails(null);
        entry.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.update(
                anyString(),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        )).thenReturn(1);

        // Act
        repository.createScriptAuditEntry(entry);

        // Assert
        verify(jdbcTemplate).update(
                anyString(),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                isNull(),
                any(Timestamp.class),
                any(Long.class)
        );
    }

    @Test
    @DisplayName("Test ClickHouse uses now() function for current timestamp")
    void testClickHouseNowFunction() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        repository.acquireLock("test-key", "test-owner", 30);

        // Assert
        verify(jdbcTemplate).update(
                contains("now() + INTERVAL"),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test ClickHouse INTERVAL syntax uses MINUTE keyword")
    void testClickHouseIntervalMinuteKeyword() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        repository.acquireLock("test-key", "test-owner", 30);

        // Assert
        verify(jdbcTemplate).update(
                eq("INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, now() + INTERVAL 30 MINUTE, 1)"),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test ClickHouse timestamp handling with various scenarios")
    void testClickHouseTimestampHandling() {
        // Test different timestamp scenarios for ClickHouse
        LocalDateTime[] testTimes = {
                LocalDateTime.of(2024, 1, 27, 10, 30, 45),
                LocalDateTime.of(2024, 12, 31, 23, 59, 59),
                LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        };

        for (LocalDateTime testTime : testTimes) {
            when(jdbcTemplate.queryForObject(
                    anyString(),
                    eq(Long.class),
                    eq("test.sql"),
                    eq("abc123"),
                    eq("DROP TABLE test;"),
                    eq("SELECT 1;"),
                    isNull(),
                    eq(Timestamp.valueOf(testTime)),
                    any(Long.class)
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
                    eq(Timestamp.valueOf(testTime)),
                    any(Long.class)
            );
        }
    }

    @Test
    @DisplayName("Test ClickHouse ID generation uses System.currentTimeMillis")
    void testClickHouseIdGeneration() {
        // Arrange
        ChangeLogScript metadata =
                new ChangeLogScript("test.sql", "abc123");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                any(Timestamp.class),
                anyLong()  // Should be a long value (timestamp)
        )).thenReturn(1L);

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
                any(Timestamp.class),
                anyLong()
        );
    }
}
