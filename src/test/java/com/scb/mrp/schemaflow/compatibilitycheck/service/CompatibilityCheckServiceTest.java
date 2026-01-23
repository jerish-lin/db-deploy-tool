package com.scb.mrp.schemaflow.compatibilitycheck.service;

import com.scb.mrp.schemaflow.dbdeploy.changelog.ChangeLogManager;
import com.scb.mrp.schemaflow.dbdeploy.changelog.ConfigLoader;
import com.scb.mrp.schemaflow.dbdeploy.config.ChangeLogPathConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptFileContent;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusService;
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
public class CompatibilityCheckServiceTest {

    @Mock
    private DatabaseStatusService databaseStatusService;

    @Mock
    private ChangeLogManager changeLogManager;

    @Mock
    private ChangeLogPathConfig configuration;

    @Mock
    private SchemaInitializationManager schemaInitializationManager;

    private CompatibilityCheckService service;

    @BeforeEach
    void setUp() {
        service = new CompatibilityCheckService(databaseStatusService, changeLogManager, configuration, schemaInitializationManager);

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
            assertDoesNotThrow(() -> service.performCompatibilityCheck());

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
            service.performCompatibilityCheck();
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
        scriptStatus1.setId(1L);
        scriptStatus1.setScriptName("script1");
        scriptStatus1.setScriptChecksum("abc123");
        scriptStatus1.setRollbackScriptContent("DROP TABLE test1;");
        scriptStatus1.setRollbackVerifyScriptContent("SELECT COUNT(*) FROM test1;");
        scriptStatus1.setCreatedAt(java.time.LocalDateTime.now());
        scriptStatus1.setLatestStatus("FAILED");

        DatabaseStatus.ScriptStatus scriptStatus2 = new DatabaseStatus.ScriptStatus();
        scriptStatus2.setId(2L);
        scriptStatus2.setScriptName("script2");
        scriptStatus2.setScriptChecksum("def456");
        scriptStatus2.setRollbackScriptContent("DROP TABLE test2;");
        scriptStatus2.setRollbackVerifyScriptContent("SELECT COUNT(*) FROM test2;");
        scriptStatus2.setCreatedAt(java.time.LocalDateTime.now());
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
                service.performCompatibilityCheck();
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
                service.performCompatibilityCheck();
            });

            assertTrue(exception.getMessage().contains("2 pending changelog script(s)"));
            assertTrue(exception.getMessage().contains("pending-script-1"));
            assertTrue(exception.getMessage().contains("pending-script-2"));
        }
    }

    @Test
    @DisplayName("Test pre-check fails when database status service throws exception")
    void testPreCheckFailsWhenDatabaseStatusServiceThrowsException() throws Exception {
        // Simulate an exception from databaseStatusService
        when(databaseStatusService.getComprehensiveStatus())
                .thenThrow(new RuntimeException("Database connection failed"));

        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig changeLogConfig = new ChangeLogConfig();
            changeLogConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(changeLogConfig);

            when(changeLogManager.determinePendingScripts(any(), any())).thenReturn(List.of());

            // The service now throws IllegalStateException on all failures
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                service.performCompatibilityCheck();
            });

            assertTrue(exception.getMessage().contains("Database deployment status pre-check failed"));
            assertTrue(exception.getCause().getMessage().contains("Database connection failed"));

            // Should only call getComprehensiveStatus once
            verify(databaseStatusService, times(1)).getComprehensiveStatus();
        }
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

            assertDoesNotThrow(() -> service.performCompatibilityCheck());
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

            assertDoesNotThrow(() -> service.performCompatibilityCheck());
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

            assertDoesNotThrow(() -> service.performCompatibilityCheck());
        }
    }

    @Test
    @DisplayName("Test pre-check fails when schema is not initialized")
    void testPreCheckFailsWhenSchemaNotInitialized() {
        // Mock schema as not initialized
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);

        // Should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.performCompatibilityCheck();
        });

        assertTrue(exception.getMessage().contains("Database schema is not initialized"));

        // Verify that database status service was not called since schema is not initialized
        verifyNoInteractions(databaseStatusService);
        verifyNoInteractions(changeLogManager);
    }

    @Test
    @DisplayName("Test pre-check fails when schema initialization check throws exception")
    void testPreCheckFailsWhenSchemaInitializationCheckThrowsException() {
        // Mock schema initialization check to throw exception
        when(schemaInitializationManager.isSchemaInitialized())
                .thenThrow(new RuntimeException("Failed to check schema initialization"));

        // Should throw RuntimeException (thrown before being wrapped in IllegalStateException)
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.performCompatibilityCheck();
        });

        assertTrue(exception.getMessage().contains("Failed to check schema initialization"));

        // Verify that database status service was not called since schema check failed
        verifyNoInteractions(databaseStatusService);
        verifyNoInteractions(changeLogManager);
    }
}