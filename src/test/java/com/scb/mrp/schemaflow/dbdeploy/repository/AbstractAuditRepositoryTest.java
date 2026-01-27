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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbstractAuditRepository.
 * Tests the common functionality shared across all database-specific implementations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AbstractAuditRepository Tests")
public class AbstractAuditRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TestAuditRepository testRepository;

    /**
     * Test implementation of AbstractAuditRepository for testing purposes.
     */
    private static class TestAuditRepository extends AbstractAuditRepository {
        public TestAuditRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        protected String getAcquireLockSql(int timeoutMinutes) {
            return "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) VALUES (?, ?, ?, 1)";
        }
    }

    @BeforeEach
    void setUp() {
        testRepository = new TestAuditRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Test createScriptMetadata successfully creates script metadata")
    void testCreateScriptMetadata() {
        // Arrange
        ChangeLogScript metadata = new ChangeLogScript("test-script.sql", "abc123def456");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT COUNT(*) FROM test;");
        metadata.setTargetNodes(List.of("node1", "node2"));
        metadata.setCreatedAt(LocalDateTime.now());

        Long expectedId = 100L;
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedId);

        // Act
        Long resultId = testRepository.createScriptMetadata(metadata);

        // Assert
        assertEquals(expectedId, resultId);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO schemaflow_changelog_script"),
                eq(Long.class),
                eq("test-script.sql"),
                eq("abc123def456"),
                eq("DROP TABLE test;"),
                eq("SELECT COUNT(*) FROM test;"),
                eq("node1,node2"),
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test createScriptMetadata with null target nodes")
    void testCreateScriptMetadataWithNullTargetNodes() {
        // Arrange
        ChangeLogScript metadata = new ChangeLogScript("test-script.sql", "abc123def456");
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT COUNT(*) FROM test;");
        metadata.setTargetNodes(null);
        metadata.setCreatedAt(LocalDateTime.now());

        Long expectedId = 100L;
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), isNull(), any()))
                .thenReturn(expectedId);

        // Act
        Long resultId = testRepository.createScriptMetadata(metadata);

        // Assert
        assertEquals(expectedId, resultId);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO schemaflow_changelog_script"),
                eq(Long.class),
                eq("test-script.sql"),
                eq("abc123def456"),
                eq("DROP TABLE test;"),
                eq("SELECT COUNT(*) FROM test;"),
                isNull(),
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test createScriptAuditEntry successfully creates audit entry")
    void testCreateScriptAuditEntry() {
        // Arrange
        ChangeLogAuditEntry entry = new ChangeLogAuditEntry(100L, ScriptExecutionStatus.SUCCESS);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(1234L);
        entry.setErrorMessage(null);
        entry.setNodeExecutionDetails("[{\"nodeName\":\"node1\",\"success\":true}]");
        entry.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        // Act
        testRepository.createScriptAuditEntry(entry);

        // Assert
        verify(jdbcTemplate).update(
                contains("INSERT INTO schemaflow_changelog_audit"),
                eq(100L),
                eq("SUCCESS"),
                any(Timestamp.class),
                eq(1234L),
                isNull(),
                eq("[{\"nodeName\":\"node1\",\"success\":true}]"),
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test createScriptAuditEntry with failed status and error message")
    void testCreateScriptAuditEntryWithFailure() {
        // Arrange
        ChangeLogAuditEntry entry = new ChangeLogAuditEntry(100L, ScriptExecutionStatus.FAILED);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(567L);
        entry.setErrorMessage("Table already exists");
        entry.setNodeExecutionDetails(null);
        entry.setCreatedAt(LocalDateTime.now());

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        // Act
        testRepository.createScriptAuditEntry(entry);

        // Assert
        verify(jdbcTemplate).update(
                contains("INSERT INTO schemaflow_changelog_audit"),
                eq(100L),
                eq("FAILED"),
                any(Timestamp.class),
                eq(567L),
                eq("Table already exists"),
                isNull(),
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test acquireLock successfully acquires lock")
    void testAcquireLock() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        boolean result = testRepository.acquireLock("test-key", "test-owner", 30);

        // Assert
        assertTrue(result);
        verify(jdbcTemplate).update(contains("INSERT INTO schemaflow_deploy_lock"), eq("test-key"), eq("test-owner"));
    }

    @Test
    @DisplayName("Test acquireLock fails when lock already exists")
    void testAcquireLockFails() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(0);

        // Act
        boolean result = testRepository.acquireLock("test-key", "test-owner", 30);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test releaseLock successfully releases lock")
    void testReleaseLock() {
        // Arrange
        when(jdbcTemplate.update(anyString(), eq("test-key"), eq("test-owner")))
                .thenReturn(1);

        // Act
        testRepository.releaseLock("test-key", "test-owner");

        // Assert
        verify(jdbcTemplate).update(
                eq("DELETE FROM schemaflow_deploy_lock WHERE lock_key = ? AND lock_owner = ?"),
                eq("test-key"),
                eq("test-owner")
        );
    }

    @Test
    @DisplayName("Test getCurrentLockStatus returns active lock")
    void testGetCurrentLockStatus() {
        // Arrange
        Map<String, Object> lockRow = new HashMap<>();
        lockRow.put("lock_owner", "test-owner");
        lockRow.put("lock_acquired_at", "2024-01-27 10:00:00");
        lockRow.put("lock_expires_at", "2024-01-27 10:30:00");
        lockRow.put("is_active", 1);

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_deploy_lock")))
                .thenReturn(List.of(lockRow));

        // Act
        DatabaseStatus.LockInfo lockInfo = testRepository.getCurrentLockStatus();

        // Assert
        assertNotNull(lockInfo);
        assertEquals("test-owner", lockInfo.getLockOwner());
        assertEquals("2024-01-27 10:00:00", lockInfo.getLockAcquiredAt());
        assertEquals("2024-01-27 10:30:00", lockInfo.getLockExpiresAt());
        assertTrue(lockInfo.isActive());
    }

    @Test
    @DisplayName("Test getCurrentLockStatus returns null when no active lock")
    void testGetCurrentLockStatusNoLock() {
        // Arrange
        when(jdbcTemplate.queryForList(contains("FROM schemaflow_deploy_lock")))
                .thenReturn(new ArrayList<>());

        // Act
        DatabaseStatus.LockInfo lockInfo = testRepository.getCurrentLockStatus();

        // Assert
        assertNull(lockInfo);
    }

    @Test
    @DisplayName("Test getCurrentLockStatus handles exception gracefully")
    void testGetCurrentLockStatusHandlesException() {
        // Arrange
        when(jdbcTemplate.queryForList(contains("FROM schemaflow_deploy_lock")))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        DatabaseStatus.LockInfo lockInfo = testRepository.getCurrentLockStatus();

        // Assert
        assertNull(lockInfo);
    }

    @Test
    @DisplayName("Test getScriptSummary returns correct summary")
    void testGetScriptSummary() {
        // Arrange
        Map<String, Object> scriptRow1 = new HashMap<>();
        scriptRow1.put("id", 1L);
        scriptRow1.put("script_name", "script1.sql");
        scriptRow1.put("script_checksum", "abc123");
        scriptRow1.put("rollback_script_content", "DROP TABLE test1;");
        scriptRow1.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow1.put("target_nodes", "node1,node2");
        scriptRow1.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        scriptRow1.put("latest_status", "SUCCESS");

        Map<String, Object> scriptRow2 = new HashMap<>();
        scriptRow2.put("id", 2L);
        scriptRow2.put("script_name", "script2.sql");
        scriptRow2.put("script_checksum", "def456");
        scriptRow2.put("rollback_script_content", "DROP TABLE test2;");
        scriptRow2.put("rollback_verify_script_content", "SELECT 2;");
        scriptRow2.put("target_nodes", null);
        scriptRow2.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        scriptRow2.put("latest_status", "FAILED");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenReturn(List.of(scriptRow1, scriptRow2));

        // Act
        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        // Assert
        assertNotNull(summary);
        assertEquals(2, summary.getTotalScripts());
        assertEquals(1, summary.getExecutedScripts());
        assertEquals(1, summary.getFailedScripts());
        assertEquals(0, summary.getRolledBackScripts());
        assertEquals(2, summary.getScripts().size());

        DatabaseStatus.ChangeLogScriptStatus script1 = summary.getScripts().get(0);
        assertEquals("script1.sql", script1.getScriptName());
        assertEquals("abc123", script1.getScriptChecksum());
        assertEquals(ScriptExecutionStatus.SUCCESS, script1.getLatestStatus());
        assertEquals(List.of("node1", "node2"), script1.getTargetNodes());

        DatabaseStatus.ChangeLogScriptStatus script2 = summary.getScripts().get(1);
        assertEquals("script2.sql", script2.getScriptName());
        assertEquals("def456", script2.getScriptChecksum());
        assertEquals(ScriptExecutionStatus.FAILED, script2.getLatestStatus());
        assertNull(script2.getTargetNodes());
    }

    @Test
    @DisplayName("Test getScriptSummary filters duplicate script names")
    void testGetScriptSummaryFiltersDuplicates() {
        // Arrange - Same script name appears twice (different versions)
        Map<String, Object> scriptRow1 = new HashMap<>();
        scriptRow1.put("id", 1L);
        scriptRow1.put("script_name", "script.sql");
        scriptRow1.put("script_checksum", "abc123");
        scriptRow1.put("rollback_script_content", "DROP TABLE test;");
        scriptRow1.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow1.put("target_nodes", null);
        scriptRow1.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        scriptRow1.put("latest_status", "SUCCESS");

        Map<String, Object> scriptRow2 = new HashMap<>();
        scriptRow2.put("id", 2L);
        scriptRow2.put("script_name", "script.sql");
        scriptRow2.put("script_checksum", "def456");
        scriptRow2.put("rollback_script_content", "DROP TABLE test;");
        scriptRow2.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow2.put("target_nodes", null);
        scriptRow2.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        scriptRow2.put("latest_status", "ROLLED_BACK");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenReturn(List.of(scriptRow1, scriptRow2));

        // Act
        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        // Assert - Should only show the first occurrence of each script name (ordered by id ASC)
        assertNotNull(summary);
        assertEquals(1, summary.getTotalScripts());
        assertEquals(1, summary.getScripts().size());
        assertEquals("script.sql", summary.getScripts().get(0).getScriptName());
        // First one (SUCCESS) is kept, second one (ROLLED_BACK) is filtered out
        assertEquals(ScriptExecutionStatus.SUCCESS, summary.getScripts().get(0).getLatestStatus());
    }

    @Test
    @DisplayName("Test getScriptSummary handles exception gracefully")
    void testGetScriptSummaryHandlesException() {
        // Arrange
        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        // Assert
        assertNotNull(summary);
        assertEquals(0, summary.getTotalScripts());
        assertEquals(0, summary.getExecutedScripts());
        assertEquals(0, summary.getFailedScripts());
        assertEquals(0, summary.getRolledBackScripts());
        assertTrue(summary.getScripts().isEmpty());
    }

    @Test
    @DisplayName("Test getRecentAuditHistory returns audit history")
    void testGetRecentAuditHistory() {
        // Arrange
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("audit_id", 1L);
        auditRow.put("script_name", "test-script.sql");
        auditRow.put("script_checksum", "abc123");
        auditRow.put("target_nodes", "node1,node2");
        auditRow.put("execution_status", "SUCCESS");
        auditRow.put("execution_time", Timestamp.valueOf(LocalDateTime.now()));
        auditRow.put("execution_duration_ms", 1234L);
        auditRow.put("error_message", null);
        auditRow.put("node_execution_details", "[{\"nodeName\":\"node1\",\"success\":true}]");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_audit")))
                .thenReturn(List.of(auditRow));

        // Act
        List<DatabaseStatus.AuditHistoryEntry> history = testRepository.getRecentAuditHistory();

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());

        DatabaseStatus.AuditHistoryEntry entry = history.get(0);
        assertEquals(1L, entry.getAuditId());
        assertEquals("test-script.sql", entry.getScriptName());
        assertEquals("abc123", entry.getScriptChecksum());
        assertEquals(ScriptExecutionStatus.SUCCESS, entry.getExecutionStatus());
        assertEquals(1234L, entry.getExecutionDurationMs());
        assertNull(entry.getErrorMessage());
        assertEquals(List.of("node1", "node2"), entry.getTargetNodes());
        assertEquals("[{\"nodeName\":\"node1\",\"success\":true}]", entry.getNodeExecutionDetails());
    }

    @Test
    @DisplayName("Test getRecentAuditHistory with failed execution")
    void testGetRecentAuditHistoryWithFailure() {
        // Arrange
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("audit_id", 2L);
        auditRow.put("script_name", "failed-script.sql");
        auditRow.put("script_checksum", "def456");
        auditRow.put("target_nodes", null);
        auditRow.put("execution_status", "FAILED");
        auditRow.put("execution_time", Timestamp.valueOf(LocalDateTime.now()));
        auditRow.put("execution_duration_ms", 567L);
        auditRow.put("error_message", "Table already exists");
        auditRow.put("node_execution_details", null);

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_audit")))
                .thenReturn(List.of(auditRow));

        // Act
        List<DatabaseStatus.AuditHistoryEntry> history = testRepository.getRecentAuditHistory();

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());

        DatabaseStatus.AuditHistoryEntry entry = history.get(0);
        assertEquals(2L, entry.getAuditId());
        assertEquals("failed-script.sql", entry.getScriptName());
        assertEquals(ScriptExecutionStatus.FAILED, entry.getExecutionStatus());
        assertEquals("Table already exists", entry.getErrorMessage());
        assertNull(entry.getTargetNodes());
        assertNull(entry.getNodeExecutionDetails());
    }

    @Test
    @DisplayName("Test getRecentAuditHistory handles exception gracefully")
    void testGetRecentAuditHistoryHandlesException() {
        // Arrange
        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_audit")))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        List<DatabaseStatus.AuditHistoryEntry> history = testRepository.getRecentAuditHistory();

        // Assert
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Test parseTargetNodes with comma-separated string")
    void testParseTargetNodes() {
        // This test indirectly tests parseTargetNodes through getScriptSummary
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", "node1,node2,node3");
        scriptRow.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenReturn(List.of(scriptRow));

        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        assertEquals(List.of("node1", "node2", "node3"), summary.getScripts().get(0).getTargetNodes());
    }

    @Test
    @DisplayName("Test parseTargetNodes with null value")
    void testParseTargetNodesWithNull() {
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", Timestamp.valueOf(LocalDateTime.now()));
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenReturn(List.of(scriptRow));

        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        assertNull(summary.getScripts().get(0).getTargetNodes());
    }

    @Test
    @DisplayName("Test prepareTimestamp with null value")
    void testPrepareTimestampWithNull() {
        // This is tested indirectly through createScriptMetadata
        ChangeLogScript metadata = new ChangeLogScript("test.sql", "abc123");
        metadata.setCreatedAt(null);
        metadata.setRollbackScriptContent("DROP TABLE test;");
        metadata.setRollbackVerifyScriptContent("SELECT 1;");
        metadata.setTargetNodes(null);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), isNull(), any(Timestamp.class)))
                .thenReturn(1L);

        testRepository.createScriptMetadata(metadata);

        verify(jdbcTemplate).queryForObject(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("Test parseTimestamp with Timestamp object")
    void testParseTimestampWithTimestamp() {
        // This is tested indirectly through getScriptSummary
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 27, 10, 30, 0);
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", Timestamp.valueOf(testTime));
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenReturn(List.of(scriptRow));

        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        assertEquals(testTime, summary.getScripts().get(0).getCreatedAt());
    }

    @Test
    @DisplayName("Test parseTimestamp with null value returns current time")
    void testParseTimestampWithNull() {
        Map<String, Object> scriptRow = new HashMap<>();
        scriptRow.put("id", 1L);
        scriptRow.put("script_name", "test.sql");
        scriptRow.put("script_checksum", "abc123");
        scriptRow.put("rollback_script_content", "DROP TABLE test;");
        scriptRow.put("rollback_verify_script_content", "SELECT 1;");
        scriptRow.put("target_nodes", null);
        scriptRow.put("created_at", null);
        scriptRow.put("latest_status", "SUCCESS");

        when(jdbcTemplate.queryForList(contains("FROM schemaflow_changelog_script")))
                .thenReturn(List.of(scriptRow));

        DatabaseStatus.ScriptSummary summary = testRepository.getScriptSummary();

        assertNotNull(summary.getScripts().get(0).getCreatedAt());
    }
}
