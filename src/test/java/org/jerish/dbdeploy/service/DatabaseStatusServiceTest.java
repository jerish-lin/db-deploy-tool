package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatabaseStatusService.
 */
@ExtendWith(MockitoExtension.class)
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
        when(auditRepository.getCurrentDeploymentState()).thenReturn(new DatabaseStatus.DeploymentStateInfo());
        when(auditRepository.getScriptExecutionHistory()).thenReturn(new ArrayList<>());
        when(auditRepository.getCurrentLockStatus()).thenReturn(new DatabaseStatus.LockInfo());
        when(auditRepository.getDatabaseHealthInfo()).thenReturn(new DatabaseStatus.DatabaseHealthInfo());
        when(auditRepository.getConfigurationInfo()).thenReturn(new DatabaseStatus.ConfigurationInfo());
        when(auditRepository.getRecentDeploymentHistory()).thenReturn(new ArrayList<>());
        when(auditRepository.getTotalRolledBackScripts()).thenReturn(0);

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        assertTrue(status.isDatabaseConnected());
        assertNotNull(status.getDeploymentState());
        assertNotNull(status.getScriptStatus());
        assertNotNull(status.getLockInfo());
        assertNotNull(status.getHealthInfo());
        assertNotNull(status.getConfigurationInfo());
        assertNotNull(status.getRecentDeployments());

        verify(auditRepository).getCurrentDeploymentState();
        verify(auditRepository).getScriptExecutionHistory();
        verify(auditRepository).getCurrentLockStatus();
        verify(auditRepository).getDatabaseHealthInfo();
        verify(auditRepository).getConfigurationInfo();
        verify(auditRepository).getRecentDeploymentHistory();
        verify(auditRepository).getTotalRolledBackScripts();
    }

    @Test
    @DisplayName("Test getComprehensiveStatus handles audit repository exceptions")
    void testGetComprehensiveStatusHandlesExceptions() {
        when(auditRepository.getCurrentDeploymentState()).thenThrow(new RuntimeException("Database error"));

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        // Should still return a status object even when there are exceptions
        assertNotNull(status.getScriptStatus());
        assertNotNull(status.getLockInfo());
        assertNotNull(status.getHealthInfo());
        assertNotNull(status.getConfigurationInfo());
        assertNotNull(status.getRecentDeployments());
    }

    @Test
    @DisplayName("Test getComprehensiveStatus with failed scripts")
    void testGetComprehensiveStatusWithFailedScripts() {
        // Setup mock responses with failed scripts
        when(auditRepository.getCurrentDeploymentState()).thenReturn(new DatabaseStatus.DeploymentStateInfo());
        
        DatabaseStatus.ScriptExecutionInfo failedScript = new DatabaseStatus.ScriptExecutionInfo();
        failedScript.setScriptName("failed-script.sql");
        failedScript.setExecutionStatus("FAILED");
        when(auditRepository.getScriptExecutionHistory()).thenReturn(new ArrayList<>(java.util.List.of(failedScript)));
        
        when(auditRepository.getFailedScripts()).thenReturn(new ArrayList<>(java.util.List.of(new DatabaseStatus.FailedScriptInfo())));
        when(auditRepository.getCurrentLockStatus()).thenReturn(new DatabaseStatus.LockInfo());
        when(auditRepository.getDatabaseHealthInfo()).thenReturn(new DatabaseStatus.DatabaseHealthInfo());
        when(auditRepository.getConfigurationInfo()).thenReturn(new DatabaseStatus.ConfigurationInfo());
        when(auditRepository.getRecentDeploymentHistory()).thenReturn(new ArrayList<>());
        when(auditRepository.getTotalRolledBackScripts()).thenReturn(0);

        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        assertEquals(1, status.getScriptStatus().getFailedScripts());
        assertFalse(status.getScriptStatus().getFailedScriptNames().isEmpty());
        assertTrue(status.getScriptStatus().getFailedScriptNames().contains("failed-script.sql"));
    }
}