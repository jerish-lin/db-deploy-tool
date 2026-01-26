package com.scb.mrp.schemaflow.statusinfo.autoconfigure;

import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusInfoHealthIndicatorTest {

    @Mock
    private DatabaseStatusService databaseStatusService;

    @Mock
    private SchemaInitializationManager schemaInitializationManager;

    private StatusInfoHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new StatusInfoHealthIndicator(
                databaseStatusService,
                schemaInitializationManager,
                true // includeDetails
        );
    }

    @Test
    @DisplayName("Health should be UP when schema is not initialized")
    void healthUpWhenSchemaNotInitialized() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(false);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("schema_not_initialized", health.getDetails().get("status"));
    }

    @Test
    @DisplayName("Health should be UP when all scripts executed successfully")
    void healthUpWhenAllScriptsSuccessful() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);

        DatabaseStatus status = createHealthyStatus();
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("healthy", health.getDetails().get("status"));
        assertEquals(3, health.getDetails().get("totalScripts"));
        assertEquals(3, health.getDetails().get("executedScripts"));
        assertEquals(0, health.getDetails().get("failedScripts"));
        assertEquals("NONE", health.getDetails().get("deploymentLock"));
    }

    @Test
    @DisplayName("Health should be DOWN when there are failed scripts")
    void healthDownWhenFailedScripts() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);

        DatabaseStatus status = createStatusWithFailedScripts();
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("failed_scripts", health.getDetails().get("status"));
        assertTrue(health.getDetails().get("message").toString().contains("failed"));
    }

    @Test
    @DisplayName("Health should be OUT_OF_SERVICE when deployment lock is active")
    void healthOutOfServiceWhenLockActive() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);

        DatabaseStatus status = createStatusWithActiveLock();
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        Health health = healthIndicator.health();

        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals("deployment_in_progress", health.getDetails().get("status"));
        assertEquals("ACTIVE", health.getDetails().get("deploymentLock"));
    }

    @Test
    @DisplayName("Health should be DOWN when exception occurs")
    void healthDownWhenException() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        when(databaseStatusService.getComprehensiveStatus())
                .thenThrow(new RuntimeException("Database connection failed"));

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("error", health.getDetails().get("status"));
        assertTrue(health.getDetails().containsKey("error"));
    }

    @Test
    @DisplayName("Health should include script details when includeDetails is true")
    void healthIncludesScriptDetails() {
        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);

        DatabaseStatus status = createHealthyStatus();
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        Health health = healthIndicator.health();

        assertTrue(health.getDetails().containsKey("scripts"));
        assertNotNull(health.getDetails().get("scripts"));
    }

    @Test
    @DisplayName("Health should not include script details when includeDetails is false")
    void healthExcludesScriptDetails() {
        StatusInfoHealthIndicator indicator = new StatusInfoHealthIndicator(
                databaseStatusService,
                schemaInitializationManager,
                false // excludeDetails
        );

        when(schemaInitializationManager.isSchemaInitialized()).thenReturn(true);
        DatabaseStatus status = createHealthyStatus();
        when(databaseStatusService.getComprehensiveStatus()).thenReturn(status);

        Health health = indicator.health();

        assertFalse(health.getDetails().containsKey("scripts"));
    }

    private DatabaseStatus createHealthyStatus() {
        DatabaseStatus status = new DatabaseStatus();

        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setTotalScripts(3);
        summary.setExecutedScripts(3);
        summary.setFailedScripts(0);
        summary.setRolledBackScripts(0);

        DatabaseStatus.ChangeLogScriptStatus script1 = new DatabaseStatus.ChangeLogScriptStatus();
        script1.setScriptName("create-users-table");
        script1.setScriptChecksum("abc123");
        script1.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        summary.setScripts(Collections.singletonList(script1));
        status.setScriptSummary(summary);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        return status;
    }

    private DatabaseStatus createStatusWithFailedScripts() {
        DatabaseStatus status = new DatabaseStatus();

        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setTotalScripts(3);
        summary.setExecutedScripts(2);
        summary.setFailedScripts(1);
        summary.setRolledBackScripts(0);

        status.setScriptSummary(summary);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(false);
        status.setLockInfo(lockInfo);

        return status;
    }

    private DatabaseStatus createStatusWithActiveLock() {
        DatabaseStatus status = new DatabaseStatus();

        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setTotalScripts(3);
        summary.setExecutedScripts(3);
        summary.setFailedScripts(0);
        summary.setRolledBackScripts(0);

        status.setScriptSummary(summary);

        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setActive(true);
        lockInfo.setLockOwner("deploy-user");
        lockInfo.setLockAcquiredAt("2024-01-21T11:00:00");
        status.setLockInfo(lockInfo);

        return status;
    }
}