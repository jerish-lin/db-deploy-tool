package org.jerish.precheck.service;

import org.jerish.dbdeploy.changelog.ChangeLogManager;
import org.jerish.dbdeploy.changelog.ConfigLoader;
import org.jerish.dbdeploy.config.ChangeLogPathConfig;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.entity.ScriptFileContent;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
import org.jerish.dbdeploy.service.DatabaseStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Unit tests for DBStatusPreCheckService")
public class DBStatusPreCheckServiceTest {

    @Mock
    private DatabaseStatusService databaseStatusService;

    @Mock
    private ChangeLogManager changeLogManager;

    @Mock
    private ChangeLogPathConfig configuration;

    @Mock
    private SchemaInitializationManager schemaInitializationManager;

    private DBStatusPreCheckService service;

    @BeforeEach
    void setUp() {
        service = new DBStatusPreCheckService(databaseStatusService, changeLogManager, configuration, schemaInitializationManager);

        // Set default values for configuration
        when(configuration.getPath()).thenReturn("classpath:db-changelog.yml");
        // Set default schema initialized to true
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
    }

    @Test
    @DisplayName("Test pre-check passes when all checks are successful")
    void testPreCheckPasses() throws Exception {
        // Setup mocks
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        DatabaseStatus.ScriptSummary scriptSummary = new DatabaseStatus.ScriptSummary();
        scriptSummary.setFailedScripts(0);
        scriptSummary.setScripts(List.of());
        status.setScriptSummary(scriptSummary);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            when(changeLogManager.determinePendingScripts(any(), any())).thenReturn(List.of());

            // This should not throw any exception
            assertDoesNotThrow(() -> service.performPreCheck());

            // Should only call getComprehensiveStatus once now
            verify(databaseStatusService, times(1)).getComprehensiveStatus();
            verify(changeLogManager).determinePendingScripts(any(), any());
        }
    }

    @Test
    @DisplayName("Test pre-check fails when deployment is in progress")
    void testPreCheckFailsWhenDeploymentInProgress() {
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(true);
        lockInfo.setLockOwner("test-owner");
        status.setLockInfo(lockInfo);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.performPreCheck();
        });

        assertTrue(exception.getMessage().contains("deployment is currently in progress"));
        // Should only call getComprehensiveStatus once
        verify(databaseStatusService, times(1)).getComprehensiveStatus();
    }

    @Test
    @DisplayName("Test pre-check fails when last deployment has failed scripts")
    void testPreCheckFailsWhenLastDeploymentFailed() throws Exception {
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        DatabaseStatus.ScriptSummary scriptSummary = new DatabaseStatus.ScriptSummary();
        scriptSummary.setFailedScripts(2);

        DatabaseStatus.ScriptStatus scriptStatus1 = new DatabaseStatus.ScriptStatus();
        scriptStatus1.setScriptName("script1");
        scriptStatus1.setLatestStatus("FAILED");

        DatabaseStatus.ScriptStatus scriptStatus2 = new DatabaseStatus.ScriptStatus();
        scriptStatus2.setScriptName("script2");
        scriptStatus2.setLatestStatus("FAILED");

        scriptSummary.setScripts(List.of(scriptStatus1, scriptStatus2));
        status.setScriptSummary(scriptSummary);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            when(changeLogManager.determinePendingScripts(any(), any())).thenReturn(List.of());

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                service.performPreCheck();
            });

            assertTrue(exception.getMessage().contains("Last deployment has 2 failed script(s)"));
            assertTrue(exception.getMessage().contains("script1"));
            assertTrue(exception.getMessage().contains("script2"));
        }
    }

    @Test
    @DisplayName("Test pre-check fails when there are pending changelog scripts")
    void testPreCheckFailsWhenPendingChangelogScripts() throws Exception {
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        DatabaseStatus.ScriptSummary scriptSummary = new DatabaseStatus.ScriptSummary();
        scriptSummary.setFailedScripts(0);
        scriptSummary.setScripts(List.of());
        status.setScriptSummary(scriptSummary);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            ScriptFileContent pendingScript1 = new ScriptFileContent();
            pendingScript1.setName("pending-script-1");

            ScriptFileContent pendingScript2 = new ScriptFileContent();
            pendingScript2.setName("pending-script-2");

            when(changeLogManager.determinePendingScripts(any(), any()))
                    .thenReturn(List.of(pendingScript1, pendingScript2));

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                service.performPreCheck();
            });

            assertTrue(exception.getMessage().contains("2 pending changelog script(s)"));
            assertTrue(exception.getMessage().contains("pending-script-1"));
            assertTrue(exception.getMessage().contains("pending-script-2"));
        }
    }

    @Test
    @DisplayName("Test pre-check handles exceptions gracefully")
    void testPreCheckHandlesExceptionsGracefully() {
        // Simulate an exception from databaseStatusService
        when(databaseStatusService.getComprehensiveStatus())
                .thenThrow(new RuntimeException("Database connection failed"));

        // The service should catch the exception and log a warning, not throw
        assertDoesNotThrow(() -> service.performPreCheck());

        // Should only call getComprehensiveStatus once
        verify(databaseStatusService, times(1)).getComprehensiveStatus();
    }

    @Test
    @DisplayName("Test pre-check passes when changelog is empty")
    void testPreCheckPassesWhenChangelogIsEmpty() throws Exception {
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        DatabaseStatus.ScriptSummary scriptSummary = new DatabaseStatus.ScriptSummary();
        scriptSummary.setFailedScripts(0);
        scriptSummary.setScripts(List.of());
        status.setScriptSummary(scriptSummary);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            when(changeLogManager.determinePendingScripts(any(), any())).thenReturn(List.of());

            assertDoesNotThrow(() -> service.performPreCheck());
        }
    }

    @Test
    @DisplayName("Test pre-check handles null script summary gracefully")
    void testPreCheckHandlesNullScriptSummary() throws Exception {
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        status.setScriptSummary(null);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            when(changeLogManager.determinePendingScripts(any(), any())).thenReturn(List.of());

            assertDoesNotThrow(() -> service.performPreCheck());
        }
    }

    @Test
    @DisplayName("Test pre-check handles null lock info gracefully")
    void testPreCheckHandlesNullLockInfo() throws Exception {
        DatabaseStatus status = new DatabaseStatus();
        status.setDatabaseConnected(true);

        status.setLockInfo(null);

        DatabaseStatus.ScriptSummary scriptSummary = new DatabaseStatus.ScriptSummary();
        scriptSummary.setFailedScripts(0);
        scriptSummary.setScripts(List.of());
        status.setScriptSummary(scriptSummary);

        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            when(changeLogManager.determinePendingScripts(any(), any())).thenReturn(List.of());

            assertDoesNotThrow(() -> service.performPreCheck());
        }
    }

    @Test
    @DisplayName("Test pre-check skips when schema is not initialized")
    void testPreCheckSkipsWhenSchemaNotInitialized() {
        // Mock schema as not initialized
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);

        // Should not throw any exception and should not call databaseStatusService
        assertDoesNotThrow(() -> service.performPreCheck());

        // Verify that database status service was not called since schema is not initialized
        verifyNoInteractions(databaseStatusService);
        verifyNoInteractions(changeLogManager);
    }

    @Test
    @DisplayName("Test pre-check skips when schema initialization check throws exception")
    void testPreCheckSkipsWhenSchemaInitializationCheckThrowsException() {
        // Mock schema initialization check to throw exception
        when(schemaInitializationManager.isSchemaInitialized())
                .thenThrow(new RuntimeException("Failed to check schema initialization"));

        // Should not throw any exception and should not call databaseStatusService
        assertDoesNotThrow(() -> service.performPreCheck());

        // Verify that database status service was not called since schema check failed
        verifyNoInteractions(databaseStatusService);
        verifyNoInteractions(changeLogManager);
    }
}