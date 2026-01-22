package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
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
import static org.mockito.Mockito.*;

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

        DatabaseStatus.ScriptStatus failedScript = new DatabaseStatus.ScriptStatus();
        failedScript.setScriptName("failed-script.sql");
        failedScript.setLatestStatus("FAILED");
        summary.setScripts(new ArrayList<>(java.util.List.of(failedScript)));

        when(auditRepository.getScriptSummary()).thenReturn(summary);
        when(auditRepository.getCurrentLockStatus()).thenReturn(new DatabaseStatus.LockInfo());
        when(auditRepository.getRecentAuditHistory()).thenReturn(new ArrayList<>());

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        assertEquals(1, status.getScriptSummary().getFailedScripts());
        assertFalse(status.getScriptSummary().getScripts().isEmpty());
        assertTrue(status.getScriptSummary().getScripts().stream()
                .anyMatch(s -> "FAILED".equals(s.getLatestStatus()) && "failed-script.sql".equals(s.getScriptName())));
    }
}