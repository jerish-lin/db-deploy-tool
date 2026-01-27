package com.scb.mrp.schemaflow.dbdeploy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseStatus and its nested classes.
 * Tests status reporting and script tracking functionality.
 */
@DisplayName("DatabaseStatus Tests")
public class DatabaseStatusTest {

    @Test
    @DisplayName("Test DatabaseStatus creation with all fields")
    void testDatabaseStatusCreation() {
        // Arrange & Act
        DatabaseStatus status = new DatabaseStatus();
        status.setLockInfo(new DatabaseStatus.LockInfo());
        status.setScriptSummary(new DatabaseStatus.ScriptSummary());
        status.setRecentAuditHistory(List.of());

        // Assert
        assertNotNull(status);
        assertNotNull(status.getLockInfo());
        assertNotNull(status.getScriptSummary());
        assertNotNull(status.getRecentAuditHistory());
    }

    @Test
    @DisplayName("Test LockInfo creation and properties")
    void testLockInfo() {
        // Arrange & Act
        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setLockOwner("test-owner");
        lockInfo.setLockAcquiredAt("2024-01-27 10:00:00");
        lockInfo.setLockExpiresAt("2024-01-27 10:30:00");
        lockInfo.setActive(true);

        // Assert
        assertEquals("test-owner", lockInfo.getLockOwner());
        assertEquals("2024-01-27 10:00:00", lockInfo.getLockAcquiredAt());
        assertEquals("2024-01-27 10:30:00", lockInfo.getLockExpiresAt());
        assertTrue(lockInfo.isActive());
    }

    @Test
    @DisplayName("Test LockInfo with inactive lock")
    void testLockInfoInactive() {
        // Arrange & Act
        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setLockOwner("test-owner");
        lockInfo.setActive(false);

        // Assert
        assertFalse(lockInfo.isActive());
    }

    @Test
    @DisplayName("Test ScriptSummary creation with scripts")
    void testScriptSummaryCreation() {
        // Arrange
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setTotalScripts(10);
        summary.setExecutedScripts(8);
        summary.setFailedScripts(1);
        summary.setRolledBackScripts(1);

        // Act
        summary.setScripts(List.of());

        // Assert
        assertEquals(10, summary.getTotalScripts());
        assertEquals(8, summary.getExecutedScripts());
        assertEquals(1, summary.getFailedScripts());
        assertEquals(1, summary.getRolledBackScripts());
        assertNotNull(summary.getScripts());
    }

    @Test
    @DisplayName("Test ScriptSummary getSuccessScripts filters correctly")
    void testScriptSummaryGetSuccessScripts() {
        // Arrange
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        DatabaseStatus.ChangeLogScriptStatus script1 = new DatabaseStatus.ChangeLogScriptStatus();
        script1.setScriptName("script1.sql");
        script1.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        DatabaseStatus.ChangeLogScriptStatus script2 = new DatabaseStatus.ChangeLogScriptStatus();
        script2.setScriptName("script2.sql");
        script2.setLatestStatus(ScriptExecutionStatus.FAILED);

        DatabaseStatus.ChangeLogScriptStatus script3 = new DatabaseStatus.ChangeLogScriptStatus();
        script3.setScriptName("script3.sql");
        script3.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        summary.setScripts(List.of(script1, script2, script3));

        // Act
        List<DatabaseStatus.ChangeLogScriptStatus> successScripts = summary.getSuccessScripts();

        // Assert
        assertEquals(2, successScripts.size());
        assertTrue(successScripts.stream().allMatch(s -> ScriptExecutionStatus.SUCCESS.equals(s.getLatestStatus())));
        assertEquals("script1.sql", successScripts.get(0).getScriptName());
        assertEquals("script3.sql", successScripts.get(1).getScriptName());
    }

    @Test
    @DisplayName("Test ScriptSummary getSuccessScripts with null scripts")
    void testScriptSummaryGetSuccessScriptsWithNull() {
        // Arrange
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setScripts(null);

        // Act
        List<DatabaseStatus.ChangeLogScriptStatus> successScripts = summary.getSuccessScripts();

        // Assert
        assertNotNull(successScripts);
        assertTrue(successScripts.isEmpty());
    }

    @Test
    @DisplayName("Test ScriptSummary getSuccessScripts with empty scripts")
    void testScriptSummaryGetSuccessScriptsWithEmpty() {
        // Arrange
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setScripts(List.of());

        // Act
        List<DatabaseStatus.ChangeLogScriptStatus> successScripts = summary.getSuccessScripts();

        // Assert
        assertNotNull(successScripts);
        assertTrue(successScripts.isEmpty());
    }

    @Test
    @DisplayName("Test ScriptSummary getSuccessAndFailedScripts filters correctly")
    void testScriptSummaryGetSuccessAndFailedScripts() {
        // Arrange
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        DatabaseStatus.ChangeLogScriptStatus script1 = new DatabaseStatus.ChangeLogScriptStatus();
        script1.setScriptName("script1.sql");
        script1.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        DatabaseStatus.ChangeLogScriptStatus script2 = new DatabaseStatus.ChangeLogScriptStatus();
        script2.setScriptName("script2.sql");
        script2.setLatestStatus(ScriptExecutionStatus.FAILED);

        DatabaseStatus.ChangeLogScriptStatus script3 = new DatabaseStatus.ChangeLogScriptStatus();
        script3.setScriptName("script3.sql");
        script3.setLatestStatus(ScriptExecutionStatus.ROLLED_BACK);

        summary.setScripts(List.of(script1, script2, script3));

        // Act
        List<DatabaseStatus.ChangeLogScriptStatus> successAndFailed = summary.getSuccessAndFailedScripts();

        // Assert
        assertEquals(2, successAndFailed.size());
        assertTrue(successAndFailed.stream().noneMatch(s -> ScriptExecutionStatus.ROLLED_BACK.equals(s.getLatestStatus())));
        assertEquals("script1.sql", successAndFailed.get(0).getScriptName());
        assertEquals("script2.sql", successAndFailed.get(1).getScriptName());
    }

    @Test
    @DisplayName("Test ChangeLogScriptStatus creation with constructor")
    void testChangeLogScriptStatusConstructor() {
        // Arrange & Act
        DatabaseStatus.ChangeLogScriptStatus status = new DatabaseStatus.ChangeLogScriptStatus(
                "test-script.sql",
                "abc123",
                ScriptExecutionStatus.SUCCESS
        );

        // Assert
        assertEquals("test-script.sql", status.getScriptName());
        assertEquals("abc123", status.getScriptChecksum());
        assertEquals(ScriptExecutionStatus.SUCCESS, status.getLatestStatus());
    }

    @Test
    @DisplayName("Test ChangeLogScriptStatus extends ChangeLogScript")
    void testChangeLogScriptStatusInheritance() {
        // Arrange & Act
        DatabaseStatus.ChangeLogScriptStatus status = new DatabaseStatus.ChangeLogScriptStatus();
        status.setId(1L);
        status.setScriptName("test-script.sql");
        status.setScriptChecksum("abc123");
        status.setRollbackScriptContent("DROP TABLE test;");
        status.setRollbackVerifyScriptContent("SELECT 1;");
        status.setCreatedAt(LocalDateTime.now());
        status.setTargetNodes(List.of("node1", "node2"));
        status.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        // Assert
        assertEquals(1L, status.getId());
        assertEquals("test-script.sql", status.getScriptName());
        assertEquals("abc123", status.getScriptChecksum());
        assertEquals("DROP TABLE test;", status.getRollbackScriptContent());
        assertEquals("SELECT 1;", status.getRollbackVerifyScriptContent());
        assertNotNull(status.getCreatedAt());
        assertEquals(List.of("node1", "node2"), status.getTargetNodes());
        assertEquals(ScriptExecutionStatus.SUCCESS, status.getLatestStatus());
    }

    @Test
    @DisplayName("Test ChangeLogScriptStatus toScriptMetadata conversion")
    void testChangeLogScriptStatusToScriptMetadata() {
        // Arrange
        DatabaseStatus.ChangeLogScriptStatus status = new DatabaseStatus.ChangeLogScriptStatus();
        status.setId(1L);
        status.setScriptName("test-script.sql");
        status.setScriptChecksum("abc123");
        status.setRollbackScriptContent("DROP TABLE test;");
        status.setRollbackVerifyScriptContent("SELECT 1;");
        status.setCreatedAt(LocalDateTime.of(2024, 1, 27, 10, 30, 0));
        status.setTargetNodes(List.of("node1", "node2"));
        status.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        // Act
        ChangeLogScript metadata = status.toScriptMetadata();

        // Assert
        assertNotNull(metadata);
        assertEquals(status.getId(), metadata.getId());
        assertEquals(status.getScriptName(), metadata.getScriptName());
        assertEquals(status.getScriptChecksum(), metadata.getScriptChecksum());
        assertEquals(status.getRollbackScriptContent(), metadata.getRollbackScriptContent());
        assertEquals(status.getRollbackVerifyScriptContent(), metadata.getRollbackVerifyScriptContent());
        assertEquals(status.getCreatedAt(), metadata.getCreatedAt());
        assertEquals(status.getTargetNodes(), metadata.getTargetNodes());
    }

    @Test
    @DisplayName("Test ChangeLogScriptStatus toScriptMetadata with null fields")
    void testChangeLogScriptStatusToScriptMetadataWithNulls() {
        // Arrange
        DatabaseStatus.ChangeLogScriptStatus status = new DatabaseStatus.ChangeLogScriptStatus();
        status.setScriptName("test-script.sql");
        status.setScriptChecksum("abc123");

        // Act
        ChangeLogScript metadata = status.toScriptMetadata();

        // Assert
        assertNotNull(metadata);
        assertEquals("test-script.sql", metadata.getScriptName());
        assertEquals("abc123", metadata.getScriptChecksum());
        assertNull(metadata.getId());
        assertNull(metadata.getRollbackScriptContent());
        assertNull(metadata.getRollbackVerifyScriptContent());
        assertNull(metadata.getCreatedAt());
        assertNull(metadata.getTargetNodes());
    }

    @Test
    @DisplayName("Test AuditHistoryEntry creation and properties")
    void testAuditHistoryEntry() {
        // Arrange & Act
        DatabaseStatus.AuditHistoryEntry entry = new DatabaseStatus.AuditHistoryEntry();
        entry.setScriptName("test-script.sql");
        entry.setScriptChecksum("abc123");
        entry.setAuditId(1L);
        entry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        entry.setExecutionTime(LocalDateTime.of(2024, 1, 27, 10, 30, 0));
        entry.setExecutionDurationMs(1234L);
        entry.setErrorMessage(null);
        entry.setTargetNodes(List.of("node1", "node2"));
        entry.setNodeExecutionDetails("[{\"node\":\"node1\",\"success\":true}]");

        // Assert
        assertEquals("test-script.sql", entry.getScriptName());
        assertEquals("abc123", entry.getScriptChecksum());
        assertEquals(1L, entry.getAuditId());
        assertEquals(ScriptExecutionStatus.SUCCESS, entry.getExecutionStatus());
        assertEquals(LocalDateTime.of(2024, 1, 27, 10, 30, 0), entry.getExecutionTime());
        assertEquals(1234L, entry.getExecutionDurationMs());
        assertNull(entry.getErrorMessage());
        assertEquals(List.of("node1", "node2"), entry.getTargetNodes());
        assertEquals("[{\"node\":\"node1\",\"success\":true}]", entry.getNodeExecutionDetails());
    }

    @Test
    @DisplayName("Test AuditHistoryEntry with failed execution")
    void testAuditHistoryEntryWithFailure() {
        // Arrange & Act
        DatabaseStatus.AuditHistoryEntry entry = new DatabaseStatus.AuditHistoryEntry();
        entry.setScriptName("failed-script.sql");
        entry.setScriptChecksum("def456");
        entry.setAuditId(2L);
        entry.setExecutionStatus(ScriptExecutionStatus.FAILED);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(567L);
        entry.setErrorMessage("Table already exists");
        entry.setTargetNodes(null);
        entry.setNodeExecutionDetails(null);

        // Assert
        assertEquals("failed-script.sql", entry.getScriptName());
        assertEquals(ScriptExecutionStatus.FAILED, entry.getExecutionStatus());
        assertEquals("Table already exists", entry.getErrorMessage());
        assertNull(entry.getTargetNodes());
        assertNull(entry.getNodeExecutionDetails());
    }

    @Test
    @DisplayName("Test AuditHistoryEntry with rolled back execution")
    void testAuditHistoryEntryWithRolledBack() {
        // Arrange & Act
        DatabaseStatus.AuditHistoryEntry entry = new DatabaseStatus.AuditHistoryEntry();
        entry.setScriptName("rolled-back-script.sql");
        entry.setScriptChecksum("ghi789");
        entry.setAuditId(3L);
        entry.setExecutionStatus(ScriptExecutionStatus.ROLLED_BACK);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setExecutionDurationMs(890L);

        // Assert
        assertEquals("rolled-back-script.sql", entry.getScriptName());
        assertEquals(ScriptExecutionStatus.ROLLED_BACK, entry.getExecutionStatus());
        assertEquals(890L, entry.getExecutionDurationMs());
    }

    @Test
    @DisplayName("Test AuditHistoryEntry with multi-node execution")
    void testAuditHistoryEntryWithMultiNode() {
        // Arrange & Act
        DatabaseStatus.AuditHistoryEntry entry = new DatabaseStatus.AuditHistoryEntry();
        entry.setScriptName("multi-node-script.sql");
        entry.setScriptChecksum("jkl012");
        entry.setAuditId(4L);
        entry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        entry.setTargetNodes(List.of("node1", "node2", "node3"));
        entry.setNodeExecutionDetails("[{\"node\":\"node1\",\"success\":true},{\"node\":\"node2\",\"success\":true},{\"node\":\"node3\",\"success\":true}]");

        // Assert
        assertEquals(3, entry.getTargetNodes().size());
        assertTrue(entry.getTargetNodes().contains("node1"));
        assertTrue(entry.getTargetNodes().contains("node2"));
        assertTrue(entry.getTargetNodes().contains("node3"));
        assertNotNull(entry.getNodeExecutionDetails());
    }

    @Test
    @DisplayName("Test ScriptSummary with all statuses")
    void testScriptSummaryWithAllStatuses() {
        // Arrange
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        DatabaseStatus.ChangeLogScriptStatus script1 = new DatabaseStatus.ChangeLogScriptStatus();
        script1.setScriptName("success.sql");
        script1.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        DatabaseStatus.ChangeLogScriptStatus script2 = new DatabaseStatus.ChangeLogScriptStatus();
        script2.setScriptName("failed.sql");
        script2.setLatestStatus(ScriptExecutionStatus.FAILED);

        DatabaseStatus.ChangeLogScriptStatus script3 = new DatabaseStatus.ChangeLogScriptStatus();
        script3.setScriptName("rolledback.sql");
        script3.setLatestStatus(ScriptExecutionStatus.ROLLED_BACK);

        summary.setScripts(List.of(script1, script2, script3));
        summary.setTotalScripts(3);
        summary.setExecutedScripts(1);
        summary.setFailedScripts(1);
        summary.setRolledBackScripts(1);

        // Act
        List<DatabaseStatus.ChangeLogScriptStatus> successScripts = summary.getSuccessScripts();
        List<DatabaseStatus.ChangeLogScriptStatus> successAndFailed = summary.getSuccessAndFailedScripts();

        // Assert
        assertEquals(3, summary.getTotalScripts());
        assertEquals(1, summary.getExecutedScripts());
        assertEquals(1, summary.getFailedScripts());
        assertEquals(1, summary.getRolledBackScripts());
        assertEquals(1, successScripts.size());
        assertEquals(2, successAndFailed.size());
    }

    @Test
    @DisplayName("Test DatabaseStatus with complete information")
    void testDatabaseStatusWithCompleteInformation() {
        // Arrange
        DatabaseStatus status = new DatabaseStatus();

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setLockOwner("test-owner");
        lockInfo.setActive(true);

        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        DatabaseStatus.ChangeLogScriptStatus script = new DatabaseStatus.ChangeLogScriptStatus();
        script.setScriptName("test.sql");
        script.setLatestStatus(ScriptExecutionStatus.SUCCESS);
        summary.setScripts(List.of(script));
        summary.setTotalScripts(1);
        summary.setExecutedScripts(1);
        summary.setFailedScripts(0);
        summary.setRolledBackScripts(0);

        DatabaseStatus.AuditHistoryEntry auditEntry = new DatabaseStatus.AuditHistoryEntry();
        auditEntry.setScriptName("test.sql");
        auditEntry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);

        // Act
        status.setLockInfo(lockInfo);
        status.setScriptSummary(summary);
        status.setRecentAuditHistory(List.of(auditEntry));

        // Assert
        assertNotNull(status.getLockInfo());
        assertNotNull(status.getScriptSummary());
        assertNotNull(status.getRecentAuditHistory());
        assertEquals(1, status.getRecentAuditHistory().size());
        assertTrue(status.getScriptSummary().getScripts().get(0).getLatestStatus() == ScriptExecutionStatus.SUCCESS);
    }
}
