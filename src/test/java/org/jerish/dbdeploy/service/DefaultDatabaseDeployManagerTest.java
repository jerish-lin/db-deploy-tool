package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.config.DeploymentConfig;
import org.jerish.dbdeploy.configloader.ConfigLoader;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Unit tests for DefaultDatabaseDeployManager")
public class DefaultDatabaseDeployManagerTest {

    @Mock
    private DatabaseDeployService deployService;

    @Mock
    private SchemaInitializationManager schemaInitializationManager;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private DeploymentConfig deploymentConfig;

    @Mock
    private ChangeLogConfig changeLogConfig;

    @Mock
    private ChangeLogEntry changeLogEntry;

    private DefaultDatabaseDeployManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = new DefaultDatabaseDeployManager(
                deployService,
                schemaInitializationManager,
                auditRepository,
                deploymentConfig
        );

        // Setup default mock behaviors with lenient stubbing
        lenient().when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        lenient().when(deploymentConfig.isEnableAutoRollback()).thenReturn(true);
        lenient().when(changeLogConfig.getScripts()).thenReturn(List.of());
        lenient().when(changeLogConfig.getChangelogFilePath()).thenReturn("test-changelog.yml");
    }

    @Test
    @DisplayName("Test deploy without parameters calls deployService")
    void testDeployWithoutParameters() throws Exception {
        manager.deploy("classpath:test-changelog.yml", false);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), isNull());
    }

    @Test
    @DisplayName("Test deploy with parameters calls deployService")
    void testDeployWithParameters() throws Exception {
        Map<String, String> params = Map.of("param1", "value1");
        
        manager.deploy("classpath:test-changelog.yml", false, params);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), eq(params));
    }

    @Test
    @DisplayName("Test deploy with dryRun=true calls deployService with dryRun")
    void testDeployWithDryRun() throws Exception {
        manager.deploy("classpath:test-changelog.yml", true);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).deploy(any(ChangeLogConfig.class), eq(true), isNull());
    }

    @Test
    @DisplayName("Test deploy initializes schema before deployment")
    void testDeployInitializesSchema() throws Exception {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);
        
        manager.deploy("classpath:test-changelog.yml", false);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).deploy(any(ChangeLogConfig.class), anyBoolean(), isNull());
    }

    @Test
    @DisplayName("Test rollback without parameters calls deployService")
    void testRollbackWithoutParameters() throws Exception {
        manager.rollback("classpath:test-changelog.yml", false);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), isNull());
    }

    @Test
    @DisplayName("Test rollback with parameters calls deployService")
    void testRollbackWithParameters() throws Exception {
        Map<String, String> params = Map.of("param1", "value1");
        
        manager.rollback("classpath:test-changelog.yml", false, params);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), eq(params));
    }

    @Test
    @DisplayName("Test rollback with dryRun=true calls deployService with dryRun")
    void testRollbackWithDryRun() throws Exception {
        manager.rollback("classpath:test-changelog.yml", true);

        verify(schemaInitializationManager).initializeSchemaIfNeeded();
        verify(deployService).rollback(any(ChangeLogConfig.class), eq(true), isNull());
    }

    @Test
    @DisplayName("Test deployOrRollback performs deploy when scripts pending")
    void testDeployOrRollback_DeployNeeded() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with script1 (not executed, so deployment is needed)
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of(script1));
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(changeLogEntry.getScriptName()).thenReturn("script1");
            when(changeLogEntry.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            manager.deployOrRollback("classpath:test-changelog.yml", false);

            verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback performs rollback when scripts to rollback")
    void testDeployOrRollback_RollbackNeeded() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with no scripts (script3 is not in changelog, so it needs rollback)
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(changeLogEntry.getScriptName()).thenReturn("script3");
            when(changeLogEntry.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(changeLogEntry));
            
            manager.deployOrRollback("classpath:test-changelog.yml", false);

            verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback throws exception when both deploy and rollback needed")
    void testDeployOrRollback_AmbiguousState() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with script1 and script2 (script3 is not in changelog, so it needs rollback)
            // but script1 and script2 are not executed, so they need deployment too
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ScriptConfig script2 = new ScriptConfig("script2", null);
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of(script1, script2));
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(changeLogEntry.getScriptName()).thenReturn("script3");
            when(changeLogEntry.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(changeLogEntry));
            
            assertThrows(RuntimeException.class, () -> {
                manager.deployOrRollback("classpath:test-changelog.yml", false);
            });
        }
    }

    @Test
    @DisplayName("Test deployOrRollback throws exception when rollback needed but auto-rollback disabled")
    void testDeployOrRollback_AutoRollbackDisabled() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with only script1 (script3 is not in changelog, so it needs rollback)
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of(script1));
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(deploymentConfig.isEnableAutoRollback()).thenReturn(false);
            when(changeLogEntry.getScriptName()).thenReturn("script3");
            when(changeLogEntry.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(changeLogEntry));
            
            assertThrows(RuntimeException.class, () -> {
                manager.deployOrRollback("classpath:test-changelog.yml", false);
            });
        }
    }

    @Test
    @DisplayName("Test deployOrRollback does nothing when database already at target state")
    void testDeployOrRollback_NoActionNeeded() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with script1 and script2 (both are executed, so no action needed)
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ScriptConfig script2 = new ScriptConfig("script2", null);
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of(script1, script2));
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(changeLogEntry.getScriptName()).thenReturn("script1");
            when(changeLogEntry.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            ChangeLogEntry entry2 = mock(ChangeLogEntry.class);
            when(entry2.getScriptName()).thenReturn("script2");
            when(entry2.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(changeLogEntry, entry2));
            
            assertDoesNotThrow(() -> manager.deployOrRollback("classpath:test-changelog.yml", false));
            
            verify(deployService, never()).deploy(any(), anyBoolean(), any());
            verify(deployService, never()).rollback(any(), anyBoolean(), any());
        }
    }

    @Test
    @DisplayName("Test status returns comprehensive status when schema initialized")
    void testStatus_SchemaInitialized() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        DatabaseStatus mockStatus = new DatabaseStatus();
        mockStatus.setDatabaseConnected(true);
        when(deployService.getComprehensiveStatus()).thenReturn(mockStatus);
        
        DatabaseStatus result = manager.status();
        
        assertNotNull(result);
        verify(deployService).getComprehensiveStatus();
    }

    @Test
    @DisplayName("Test status returns empty status when schema not initialized")
    void testStatus_SchemaNotInitialized() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);
        
        DatabaseStatus result = manager.status();
        
        assertNotNull(result);
        assertTrue(result.isDatabaseConnected());
        assertNotNull(result.getDeploymentState());
        assertNotNull(result.getHealthInfo());
        assertNotNull(result.getConfigurationInfo());
        assertNotNull(result.getScriptStatus());
        assertNotNull(result.getLockInfo());
        verify(deployService, never()).getComprehensiveStatus();
    }

    @Test
    @DisplayName("Test status returns error status on exception")
    void testStatus_Exception() {
        when(schemaInitializationManager.isSchemaInitialized()).thenThrow(new RuntimeException("Connection failed"));
        
        DatabaseStatus result = manager.status();
        
        assertNotNull(result);
        assertFalse(result.isDatabaseConnected());
        assertNotNull(result.getHealthInfo());
        assertFalse(result.getHealthInfo().isHealthy());
    }

    @Test
    @DisplayName("Test deployOrRollback with dryRun=true")
    void testDeployOrRollbackWithDryRun() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with script1 (not executed, so deployment is needed)
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of(script1));
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            manager.deployOrRollback("classpath:test-changelog.yml", true);

            verify(deployService).deploy(any(ChangeLogConfig.class), eq(true), isNull());
        }
    }

    @Test
    @DisplayName("Test deploy handles exception from deployService")
    void testDeployHandlesException() throws Exception {
        doThrow(new RuntimeException("Deployment failed"))
                .when(deployService).deploy(any(), anyBoolean(), any());
        
        assertThrows(RuntimeException.class, () -> {
            manager.deploy("test-changelog.yml", false);
        });
    }

    @Test
    @DisplayName("Test rollback handles exception from deployService")
    void testRollbackHandlesException() throws Exception {
        doThrow(new RuntimeException("Rollback failed"))
                .when(deployService).rollback(any(), anyBoolean(), any());
        
        assertThrows(RuntimeException.class, () -> {
            manager.rollback("test-changelog.yml", false);
        });
    }

    @Test
    @DisplayName("Test deployOrRollback handles exception from schema initialization")
    void testDeployOrRollbackHandlesSchemaException() throws Exception {
        doThrow(new RuntimeException("Schema initialization failed"))
                .when(schemaInitializationManager).initializeSchemaIfNeeded();
        
        assertThrows(RuntimeException.class, () -> {
            manager.deployOrRollback("test-changelog.yml", false);
        });
    }

    @Test
    @DisplayName("Test deployOrRollback handles exception from audit repository")
    void testDeployOrRollbackHandlesAuditException() throws Exception {
        ScriptConfig scriptConfig = new ScriptConfig("script1", null);
        when(changeLogConfig.getScripts()).thenReturn(List.of(scriptConfig));
        
        when(auditRepository.getAllExecutedScripts())
                .thenThrow(new RuntimeException("Audit query failed"));
        
        assertThrows(RuntimeException.class, () -> {
            manager.deployOrRollback("classpath:/test-changelog.yml", false);
        });
    }

    @Test
    @DisplayName("Test status with null health info from service")
    void testStatusWithNullHealthInfo() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        DatabaseStatus mockStatus = new DatabaseStatus();
        mockStatus.setDatabaseConnected(true);
        mockStatus.setHealthInfo(null);
        when(deployService.getComprehensiveStatus()).thenReturn(mockStatus);
        
        DatabaseStatus result = manager.status();
        
        assertNotNull(result);
        assertNull(result.getHealthInfo());
    }

    @Test
    @DisplayName("Test deployOrRollback with multiple pending scripts")
    void testDeployOrRollback_MultiplePendingScripts() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with script1 and script2 (not executed, so deployment is needed)
            ScriptConfig script1 = new ScriptConfig("script1", null);
            ScriptConfig script2 = new ScriptConfig("script2", null);
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of(script1, script2));
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of());
            
            manager.deployOrRollback("classpath:test-changelog.yml", false);

            verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback with multiple scripts to rollback")
    void testDeployOrRollback_MultipleScriptsToRollback() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            // Mock config with no scripts (script1 and script2 are executed but not in changelog, so they need rollback)
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockConfig.setChangelogFilePath("test-changelog.yml");
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);
            
            ChangeLogEntry entry1 = mock(ChangeLogEntry.class);
            ChangeLogEntry entry2 = mock(ChangeLogEntry.class);
            when(entry1.getScriptName()).thenReturn("script1");
            when(entry1.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            when(entry2.getScriptName()).thenReturn("script2");
            when(entry2.getExecutionStatus()).thenReturn(ScriptExecutionStatus.SUCCESS);
            
            when(auditRepository.getAllExecutedScripts()).thenReturn(List.of(entry1, entry2));
            
            manager.deployOrRollback("classpath:test-changelog.yml", false);

            verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }
}