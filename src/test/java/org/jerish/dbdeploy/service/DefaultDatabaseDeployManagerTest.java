package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.config.DeploymentConfig;
import org.jerish.dbdeploy.changelog.ConfigLoader;
import org.jerish.dbdeploy.changelog.ChangeLogManager;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.model.ChangeLogEntry;
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
    private DatabaseStatusService databaseStatusService;

    @Mock
    private SchemaInitializationManager schemaInitializationManager;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private DeploymentConfig deploymentConfig;

    @Mock
    private ChangeLogManager changeLogManager;

    @Mock
    private ChangeLogConfig changeLogConfig;

    @Mock
    private ChangeLogEntry changeLogEntry;

    private DefaultDatabaseDeployManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = new DefaultDatabaseDeployManager(
                deployService,
                databaseStatusService,
                schemaInitializationManager,
                auditRepository,
                deploymentConfig,
                changeLogManager
        );

        // Setup default mock behaviors with lenient stubbing
        lenient().when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        lenient().when(deploymentConfig.isEnableAutoRollback()).thenReturn(true);
        lenient().when(changeLogConfig.getScripts()).thenReturn(List.of());
        lenient().when(changeLogConfig.getBasePath()).thenReturn("src/test/resources");
    }

    @Test
    @DisplayName("Test deploy without parameters calls deployService")
    void testDeployWithoutParameters() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            manager.deploy("classpath:test-changelog.yml", false);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test deploy with parameters calls deployService")
    void testDeployWithParameters() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            Map<String, String> params = Map.of("param1", "value1");

            manager.deploy("classpath:test-changelog.yml", false, params);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), eq(params));
        }
    }

    @Test
    @DisplayName("Test deploy with dryRun=true calls deployService with dryRun")
    void testDeployWithDryRun() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            manager.deploy("classpath:test-changelog.yml", true);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).deploy(any(ChangeLogConfig.class), eq(true), isNull());
        }
    }

    @Test
    @DisplayName("Test deploy initializes schema before deployment")
    void testDeployInitializesSchema() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);

            manager.deploy("classpath:test-changelog.yml", false);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).deploy(any(ChangeLogConfig.class), anyBoolean(), isNull());
        }
    }

    @Test
    @DisplayName("Test rollback without parameters calls deployService")
    void testRollbackWithoutParameters() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            manager.rollback("classpath:test-changelog.yml", false);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test rollback with parameters calls deployService")
    void testRollbackWithParameters() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            Map<String, String> params = Map.of("param1", "value1");

            manager.rollback("classpath:test-changelog.yml", false, params);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), eq(params));
        }
    }

    @Test
    @DisplayName("Test rollback with dryRun=true calls deployService with dryRun")
    void testRollbackWithDryRun() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            manager.rollback("classpath:test-changelog.yml", true);

            verify(schemaInitializationManager).initializeSchemaIfNeeded();
            verify(deployService).rollback(any(ChangeLogConfig.class), eq(true), isNull());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback performs deploy when scripts pending")
    void testDeployOrRollback_DeployNeeded() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(changeLogManager.determineDeploymentAction(any(ChangeLogConfig.class)))
                    .thenReturn(ChangeLogManager.DeploymentAction.DEPLOY);

            manager.deployOrRollback("classpath:test-changelog.yml", false);

            verify(deployService).deploy(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback performs rollback when scripts to rollback")
    void testDeployOrRollback_RollbackNeeded() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(changeLogManager.determineDeploymentAction(any(ChangeLogConfig.class)))
                    .thenReturn(ChangeLogManager.DeploymentAction.ROLLBACK);

            manager.deployOrRollback("classpath:test-changelog.yml", false);

            verify(deployService).rollback(any(ChangeLogConfig.class), eq(false), isNull());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback does nothing when database already at target state")
    void testDeployOrRollback_NoActionNeeded() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(changeLogManager.determineDeploymentAction(any(ChangeLogConfig.class)))
                    .thenReturn(ChangeLogManager.DeploymentAction.NONE);

            assertDoesNotThrow(() -> manager.deployOrRollback("classpath:test-changelog.yml", false));

            verify(deployService, never()).deploy(any(), anyBoolean(), any());
            verify(deployService, never()).rollback(any(), anyBoolean(), any());
        }
    }

    @Test
    @DisplayName("Test deployOrRollback throws exception when rollback needed but auto-rollback disabled")
    void testDeployOrRollback_AutoRollbackDisabled() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(deploymentConfig.isEnableAutoRollback()).thenReturn(false);
            when(changeLogManager.determineDeploymentAction(any(ChangeLogConfig.class)))
                    .thenReturn(ChangeLogManager.DeploymentAction.ROLLBACK);

            assertThrows(RuntimeException.class, () -> {
                manager.deployOrRollback("classpath:test-changelog.yml", false);
            });
        }
    }

    @Test
    @DisplayName("Test status returns comprehensive status when schema initialized")
    void testStatus_SchemaInitialized() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        DatabaseStatus mockStatus = new DatabaseStatus();
        mockStatus.setDatabaseConnected(true);
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(mockStatus);

        DatabaseStatus result = manager.status();

        assertNotNull(result);
        verify(databaseStatusService).getComprehensiveStatus();
    }

    @Test
    @DisplayName("Test status returns empty status when schema not initialized")
    void testStatus_SchemaNotInitialized() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);

        DatabaseStatus result = manager.status();

        assertNotNull(result);
        assertTrue(result.isDatabaseConnected());
        assertNotNull(result.getScriptSummary());
        assertNotNull(result.getLockInfo());
        verify(databaseStatusService, never()).getComprehensiveStatus();
    }

    @Test
    @DisplayName("Test status returns error status on exception")
    void testStatus_Exception() {
        when(schemaInitializationManager.isSchemaInitialized()).thenThrow(new RuntimeException("Connection failed"));

        DatabaseStatus result = manager.status();

        assertNotNull(result);
        assertFalse(result.isDatabaseConnected());
    }

    @Test
    @DisplayName("Test deployOrRollback with dryRun=true")
    void testDeployOrRollbackWithDryRun() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(changeLogManager.determineDeploymentAction(any(ChangeLogConfig.class)))
                    .thenReturn(ChangeLogManager.DeploymentAction.DEPLOY);

            manager.deployOrRollback("classpath:test-changelog.yml", true);

            verify(deployService).deploy(any(ChangeLogConfig.class), eq(true), isNull());
        }
    }

    @Test
    @DisplayName("Test deploy handles exception from deployService")
    void testDeployHandlesException() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            doThrow(new RuntimeException("Deployment failed"))
                    .when(deployService).deploy(any(), anyBoolean(), any());

            assertThrows(RuntimeException.class, () -> {
                manager.deploy("test-changelog.yml", false);
            });
        }
    }

    @Test
    @DisplayName("Test rollback handles exception from deployService")
    void testRollbackHandlesException() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            doThrow(new RuntimeException("Rollback failed"))
                    .when(deployService).rollback(any(), anyBoolean(), any());

            assertThrows(RuntimeException.class, () -> {
                manager.rollback("test-changelog.yml", false);
            });
        }
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
    @DisplayName("Test deployOrRollback handles exception from ChangeLogManager")
    void testDeployOrRollbackHandlesChangeLogManagerException() throws Exception {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class)) {
            ChangeLogConfig mockConfig = new ChangeLogConfig();
            mockConfig.setScripts(List.of());
            mockedConfigLoader.when(() -> ConfigLoader.loadChangeLogConfig(anyString())).thenReturn(mockConfig);

            when(changeLogManager.determineDeploymentAction(any(ChangeLogConfig.class)))
                    .thenThrow(new RuntimeException("ChangeLogManager failed"));

            assertThrows(RuntimeException.class, () -> {
                manager.deployOrRollback("classpath:/test-changelog.yml", false);
            });
        }
    }
}