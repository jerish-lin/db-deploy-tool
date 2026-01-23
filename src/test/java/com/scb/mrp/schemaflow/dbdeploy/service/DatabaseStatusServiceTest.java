package com.scb.mrp.schemaflow.dbdeploy.service;

import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.repository.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DatabaseStatusService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DatabaseStatusService Tests")
public class DatabaseStatusServiceTest {

    @Mock
    private AuditRepository auditRepository;

    private DatabaseStatusService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseStatusService(auditRepository);
    }

    @Test
    @DisplayName("Test getComprehensiveStatus returns database status")
    void testGetComprehensiveStatus() {
        // Setup mock responses
        when(auditRepository.getScriptSummary()).thenReturn(new DatabaseStatus.ScriptSummary());
        when(auditRepository.getCurrentLockStatus()).thenReturn(new DatabaseStatus.LockInfo());
        when(auditRepository.getRecentAuditHistory()).thenReturn(new ArrayList<>());

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        assertTrue(status.isDatabaseConnected());
        assertNotNull(status.getScriptSummary());
        assertNotNull(status.getLockInfo());
        assertNotNull(status.getRecentAuditHistory());

        verify(auditRepository).getScriptSummary();
        verify(auditRepository).getCurrentLockStatus();
        verify(auditRepository).getRecentAuditHistory();
    }

    @Test
    @DisplayName("Test getComprehensiveStatus handles audit repository exceptions")
    void testGetComprehensiveStatusHandlesExceptions() {
        when(auditRepository.getScriptSummary()).thenThrow(new RuntimeException("Database error"));
        // Mock other methods to return empty objects
        when(auditRepository.getCurrentLockStatus()).thenReturn(new DatabaseStatus.LockInfo());
        when(auditRepository.getRecentAuditHistory()).thenReturn(new ArrayList<>());

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        // Should still return a status object even when there are exceptions
        assertNotNull(status.getScriptSummary());
        assertNotNull(status.getLockInfo());
        assertNotNull(status.getRecentAuditHistory());
    }

    @Test
    @DisplayName("Test getComprehensiveStatus with failed scripts")
    void testGetComprehensiveStatusWithFailedScripts() {
        // Setup mock responses with failed scripts
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setTotalScripts(3);
        summary.setExecutedScripts(2);
        summary.setFailedScripts(1);
        summary.setRolledBackScripts(0);

        DatabaseStatus.ChangeLogScriptStatus failedScript = new DatabaseStatus.ChangeLogScriptStatus();
        failedScript.setId(1L);
        failedScript.setScriptName("failed-script.sql");
        failedScript.setScriptChecksum("abc123");
        failedScript.setRollbackScriptContent("DROP TABLE test;");
        failedScript.setRollbackVerifyScriptContent("SELECT COUNT(*) FROM test;");
        failedScript.setCreatedAt(java.time.LocalDateTime.now());
        failedScript.setLatestStatus(ScriptExecutionStatus.FAILED);
        summary.setScripts(new ArrayList<>(java.util.List.of(failedScript)));

        when(auditRepository.getScriptSummary()).thenReturn(summary);
        when(auditRepository.getCurrentLockStatus()).thenReturn(new DatabaseStatus.LockInfo());
        when(auditRepository.getRecentAuditHistory()).thenReturn(new ArrayList<>());

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        assertEquals(1, status.getScriptSummary().getFailedScripts());
        assertFalse(status.getScriptSummary().getScripts().isEmpty());
        assertTrue(status.getScriptSummary().getScripts().stream()
                .anyMatch(s -> ScriptExecutionStatus.FAILED.equals(s.getLatestStatus()) && "failed-script.sql".equals(s.getScriptName())));
        // Verify ScriptMetadata fields are populated
        DatabaseStatus.ChangeLogScriptStatus scriptStatus = status.getScriptSummary().getScripts().get(0);
        assertEquals(1L, scriptStatus.getId());
        assertEquals("abc123", scriptStatus.getScriptChecksum());
        assertEquals("DROP TABLE test;", scriptStatus.getRollbackScriptContent());
        assertEquals("SELECT COUNT(*) FROM test;", scriptStatus.getRollbackVerifyScriptContent());
        assertNotNull(scriptStatus.getCreatedAt());
    }
}