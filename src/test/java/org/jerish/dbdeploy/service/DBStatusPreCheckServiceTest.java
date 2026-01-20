package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.config.DeploymentConfig;
import org.jerish.dbdeploy.changelog.ConfigLoader;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.entity.ScriptExecutionStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Unit tests for DBStatusPreCheckService")
public class DBStatusPreCheckServiceTest {

    @Mock
    private DatabaseStatusService databaseStatusService;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private DeploymentConfig deploymentConfig;

    private DBStatusPreCheckService service;

    @BeforeEach
    void setUp() {
        service = new DBStatusPreCheckService(databaseStatusService, auditRepository, deploymentConfig);
        
        // Set default values for configuration
        ReflectionTestUtils.setField(service, "failOnDeploymentInProgress", true);
        ReflectionTestUtils.setField(service, "failOnLastDeploymentFailed", true);
        ReflectionTestUtils.setField(service, "failOnPendingChangelog", true);
        ReflectionTestUtils.setField(service, "changelogPath", "classpath:db-changelog.yml");
    }

    @Test
    @DisplayName("Test pre-check passes when all checks are successful")
    void testPreCheckPasses() throws Exception {
        // Setup mocks
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of());
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            // This should not throw any exception
            assertDoesNotThrow(() -> service.performPreCheck());
            
            verify(auditRepository).acquireLock(anyString(), anyString(), eq(1));
            verify(auditRepository).releaseLock(anyString(), anyString());
            verify(databaseStatusService).getComprehensiveStatus();
        }
    }

    @Test
    @DisplayName("Test pre-check fails when deployment is in progress")
    void testPreCheckFailsWhenDeploymentInProgress() {
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(false);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.performPreCheck();
        });
        
        assertTrue(exception.getMessage().contains("deployment is currently in progress"));
        verify(auditRepository).acquireLock(anyString(), anyString(), eq(1));
        verify(auditRepository, never()).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("Test pre-check fails when last deployment has failed scripts")
    void testPreCheckFailsWhenLastDeploymentFailed() throws Exception {
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of("script1", "script2"));
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                service.performPreCheck();
            });
            
            assertTrue(exception.getMessage().contains("failed script"));
            assertTrue(exception.getMessage().contains("script1"));
            assertTrue(exception.getMessage().contains("script2"));
        }
    }

    @Test
    @DisplayName("Test pre-check fails when there are pending changelog scripts")
    void testPreCheckFailsWhenPendingChangelogExists() throws Exception {
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of());
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ScriptConfig script2 = new ScriptConfig("script2", null);
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of(script1, script2));
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            // Only script1 is executed, script2 is pending
            ChangeLogEntry entry1 = mock(ChangeLogEntry.class);
            when(entry1.getScriptName()).thenReturn("script1");
            when(entry1.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(entry1));
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                service.performPreCheck();
            });
            
            assertTrue(exception.getMessage().contains("pending changelog"));
            assertTrue(exception.getMessage().contains("script2"));
        }
    }

    @Test
    @DisplayName("Test pre-check skips deployment in progress check when disabled")
    void testPreCheckSkipsDeploymentInProgressCheckWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(service, "failOnDeploymentInProgress", false);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of());
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            // This should not throw any exception even though lock is not acquired
            assertDoesNotThrow(() -> service.performPreCheck());
            
            verify(auditRepository, never()).acquireLock(anyString(), anyString(), anyInt());
        }
    }

    @Test
    @DisplayName("Test pre-check skips last deployment status check when disabled")
    void testPreCheckSkipsLastDeploymentStatusCheckWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(service, "failOnLastDeploymentFailed", false);
        
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of("script1", "script2")); // Failed scripts
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            // This should not throw any exception even though there are failed scripts
            assertDoesNotThrow(() -> service.performPreCheck());
            
            verify(databaseStatusService, never()).getComprehensiveStatus();
        }
    }

    @Test
    @DisplayName("Test pre-check skips pending changelog check when disabled")
    void testPreCheckSkipsPendingChangelogCheckWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(service, "failOnPendingChangelog", false);
        
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of());
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ScriptConfig script2 = new ScriptConfig("script2", null);
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of(script1, script2));
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            // Only script1 is executed, script2 is pending
            ChangeLogEntry entry1 = mock(ChangeLogEntry.class);
            when(entry1.getScriptName()).thenReturn("script1");
            when(entry1.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(entry1));
            
            // This should not throw any exception even though there are pending scripts
            assertDoesNotThrow(() -> service.performPreCheck());
            
            mockedConfigLoader.verify(() -> ConfigLoader.loadChangeLogConfig(anyString()), never());
        }
    }

    @Test
    @DisplayName("Test pre-check handles exceptions gracefully")
    void testPreCheckHandlesExceptionsGracefully() throws Exception {
        // Mock a database connection error during lock check
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of());
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            // The service should handle the exception gracefully and not fail
            // It logs a warning but continues with other checks
            assertDoesNotThrow(() -> service.performPreCheck());
            
            // The lock acquisition should have been attempted
            verify(auditRepository).acquireLock(anyString(), anyString(), eq(1));
            
            // Other checks should still run
            verify(databaseStatusService).getComprehensiveStatus();
        }
    }

    @Test
    @DisplayName("Test pre-check releases lock after successful check")
    void testPreCheckReleasesLockAfterSuccessfulCheck() throws Exception {
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);
        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
        scriptStatus.setFailedScriptNames(List.of());
        status.setScriptStatus(scriptStatus);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);
        
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            assertDoesNotThrow(() -> service.performPreCheck());
            
            verify(auditRepository).releaseLock(anyString(), anyString());
        }
    }
}
