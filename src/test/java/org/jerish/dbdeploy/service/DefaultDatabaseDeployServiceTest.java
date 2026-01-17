package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.script.MultiNodeScriptExecutor;
import org.jerish.dbdeploy.script.ScriptExecutor;
import org.jerish.dbdeploy.script.ScriptFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultDatabaseDeployService.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("DefaultDatabaseDeployService Tests")
public class DefaultDatabaseDeployServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private ScriptExecutor scriptExecutor;

    @Mock
    private ScriptFileManager scriptFileManager;

    @Mock
    private MultiNodeScriptExecutor multiNodeScriptExecutor;

    @Mock
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    private DefaultDatabaseDeployService service;
    private ChangeLogConfig changeLogConfig;
    private List<ScriptFileManager.ScriptFile> scripts;
    private ScriptFileManager.ScriptFile scriptFile;

    @BeforeEach
    void setUp() throws Exception {
        service = new DefaultDatabaseDeployService(
                jdbcTemplate,
                auditRepository,
                scriptExecutor,
                scriptFileManager,
                multiNodeScriptExecutor,
                nodeJdbcTemplateMap
        );

        // Setup lock acquisition mock
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);

        // Setup change log config
        changeLogConfig = new ChangeLogConfig();
        changeLogConfig.setChangelogFilePath("src/test/resources/test-changelog.yml");
        
        ScriptConfig scriptConfig = new ScriptConfig("test-script", null);
        changeLogConfig.setScripts(List.of(scriptConfig));

        // Setup script file
        scriptFile = mock(ScriptFileManager.ScriptFile.class);
        when(scriptFile.getName()).thenReturn("test-script");
        when(scriptFile.getApplyContent()).thenReturn("CREATE TABLE test (id INT);");
        when(scriptFile.getApplyPath()).thenReturn("test-script.apply.sql");
        when(scriptFile.getRollbackContent()).thenReturn("DROP TABLE test;");
        when(scriptFile.getRollbackVerifyContent()).thenReturn(null);

        scripts = List.of(scriptFile);
        when(scriptFileManager.loadScripts(anyString(), anyList())).thenReturn(scripts);

        // Setup script executor mock with proper result
        ScriptExecutor.ScriptExecutionResult successResult = new ScriptExecutor.ScriptExecutionResult();
        successResult.setSuccess(true);
        successResult.setScriptId("test-script");
        successResult.setScriptPath("test-script.apply.sql");
        successResult.setDuration(100L);
        successResult.setErrorMessage(null);
        when(scriptExecutor.executeScriptWithVerificationInTransaction(anyString(), anyString(), any()))
                .thenReturn(successResult);
    }

    @Test
    @DisplayName("Test deploy without parameters delegates to deploy with null parameters")
    void testDeployWithoutParameters() throws Exception {
        when(auditRepository.isScriptExecuted(anyString())).thenReturn(false);
        ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor.ScriptExecutionResult();
        result.setSuccess(true);
        when(scriptExecutor.executeScriptWithVerificationInTransaction(anyString(), anyString(), isNull()))
                .thenReturn(result);

        service.deploy(changeLogConfig, false);

        verify(scriptFileManager).loadScripts(anyString(), anyList());
        verify(auditRepository).isScriptExecuted("test-script");
    }

    @Test
    @DisplayName("Test deploy with parameters")
    void testDeployWithParameters() throws Exception {
        when(auditRepository.isScriptExecuted(anyString())).thenReturn(false);
        ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor.ScriptExecutionResult();
        result.setSuccess(true);
        when(scriptExecutor.executeScriptWithVerificationInTransaction(anyString(), anyString(), anyMap()))
                .thenReturn(result);

        Map<String, String> params = Map.of("param1", "value1");
        service.deploy(changeLogConfig, false, params);

        verify(scriptFileManager).loadScripts(anyString(), anyList());
        verify(auditRepository).isScriptExecuted("test-script");
    }

    @Test
    @DisplayName("Test deploy skips already executed scripts")
    void testDeploySkipsExecutedScripts() throws Exception {
        when(auditRepository.isScriptExecuted(anyString())).thenReturn(true);

        service.deploy(changeLogConfig, false);

        verify(scriptFileManager).loadScripts(anyString(), anyList());
        verify(auditRepository).isScriptExecuted("test-script");
        verify(scriptExecutor, never()).executeScriptWithVerificationInTransaction(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Test deploy in dry-run mode logs but doesn't execute")
    void testDeployDryRun() throws Exception {
        service.deploy(changeLogConfig, true);

        verify(scriptFileManager).loadScripts(anyString(), anyList());
        verify(scriptExecutor, never()).executeScriptWithVerificationInTransaction(anyString(), anyString(), anyMap());
        verify(auditRepository, never()).recordScriptExecution(any(ChangeLogEntry.class));
    }

    @Test
    @DisplayName("Test deploy acquires lock when not in dry-run mode")
    void testDeployAcquiresLock() throws Exception {
        when(auditRepository.isScriptExecuted(anyString())).thenReturn(false);
        ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor.ScriptExecutionResult();
        result.setSuccess(true);
        when(scriptExecutor.executeScriptWithVerificationInTransaction(anyString(), anyString(), isNull()))
                .thenReturn(result);

        service.deploy(changeLogConfig, false);

        verify(auditRepository).acquireLock(anyString(), anyString(), eq(30));
    }

    @Test
    @DisplayName("Test deploy throws exception when lock acquisition fails")
    void testDeployLockAcquisitionFailure() throws Exception {
        reset(auditRepository);
        when(auditRepository.acquireLock(eq("db_deploy_tool"), anyString(), eq(30))).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            service.deploy(changeLogConfig, false);
        });

        verify(auditRepository).acquireLock(eq("db_deploy_tool"), anyString(), eq(30));
    }

    @Test
    @DisplayName("Test deploy releases lock after execution")
    void testDeployReleasesLock() throws Exception {
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(auditRepository.isScriptExecuted(anyString())).thenReturn(false);
        ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor.ScriptExecutionResult();
        result.setSuccess(true);
        when(scriptExecutor.executeScriptWithVerificationInTransaction(anyString(), anyString(), isNull()))
                .thenReturn(result);

        service.deploy(changeLogConfig, false);

        verify(auditRepository).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("Test deploy releases lock even on exception")
    void testDeployReleasesLockOnException() throws Exception {
        when(auditRepository.acquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(auditRepository.isScriptExecuted(anyString())).thenReturn(false);
        when(scriptExecutor.executeScriptWithVerificationInTransaction(anyString(), anyString(), isNull()))
                .thenThrow(new RuntimeException("Script execution failed"));

        assertThrows(RuntimeException.class, () -> {
            service.deploy(changeLogConfig, false);
        });

        verify(auditRepository).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("Test rollback without parameters delegates to rollback with null parameters")
    void testRollbackWithoutParameters() throws Exception {
        ChangeLogEntry entry = mock(ChangeLogEntry.class);
        when(entry.getScriptName()).thenReturn("old-script");
        when(entry.getRollbackScriptContent()).thenReturn("DROP TABLE old;");
        when(entry.getTargetNodes()).thenReturn(null);
        
        when(auditRepository.getScriptsToRollback(anyList())).thenReturn(List.of(entry));

        service.rollback(changeLogConfig, false);

        verify(auditRepository).getScriptsToRollback(anyList());
    }

    @Test
    @DisplayName("Test rollback with parameters")
    void testRollbackWithParameters() throws Exception {
        ChangeLogEntry entry = mock(ChangeLogEntry.class);
        when(entry.getScriptName()).thenReturn("old-script");
        when(entry.getRollbackScriptContent()).thenReturn("DROP TABLE old;");
        when(entry.getTargetNodes()).thenReturn(null);
        
        when(auditRepository.getScriptsToRollback(anyList())).thenReturn(List.of(entry));

        Map<String, String> params = Map.of("param1", "value1");
        service.rollback(changeLogConfig, false, params);

        verify(auditRepository).getScriptsToRollback(anyList());
    }

    @Test
    @DisplayName("Test rollback in dry-run mode logs but doesn't execute")
    void testRollbackDryRun() throws Exception {
        ChangeLogEntry entry = mock(ChangeLogEntry.class);
        when(entry.getScriptName()).thenReturn("old-script");
        when(entry.getRollbackScriptContent()).thenReturn("DROP TABLE old;");
        when(entry.getTargetNodes()).thenReturn(null);
        
        when(auditRepository.getScriptsToRollback(anyList())).thenReturn(List.of(entry));

        service.rollback(changeLogConfig, true);

        verify(auditRepository).getScriptsToRollback(anyList());
        verify(scriptExecutor, never()).executeScriptContent(anyString());
        verify(auditRepository, never()).recordRollbackScriptExecution(any(ChangeLogEntry.class), anyLong());
    }

    @Test
    @DisplayName("Test rollback does nothing when no scripts to rollback")
    void testRollbackNoScriptsToRollback() throws Exception {
        when(auditRepository.getScriptsToRollback(anyList())).thenReturn(new ArrayList<>());

        service.rollback(changeLogConfig, false);

        verify(auditRepository).getScriptsToRollback(anyList());
        verify(scriptExecutor, never()).executeScriptContent(anyString());
    }

    @Test
    @DisplayName("Test getComprehensiveStatus returns database status")
    void testGetComprehensiveStatus() {
        DatabaseStatus status = service.getComprehensiveStatus();

        assertNotNull(status);
        assertTrue(status.isDatabaseConnected());
    }
}