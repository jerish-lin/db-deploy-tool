package com.scb.mrp.schemaflow.statusinfo.autoconfigure;

import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Actuator health indicator for database deployment status.
 * Reports the health of the database deployment system including:
 * - Schema initialization status
 * - Script execution summary
 * - Deployment lock status
 * - Overall health status based on failures and active deployments
 */
@Slf4j
public class StatusInfoHealthIndicator implements HealthIndicator {

    private final DatabaseStatusService databaseStatusService;
    private final SchemaInitializationManager schemaInitializationManager;
    private final boolean includeDetails;

    public StatusInfoHealthIndicator(DatabaseStatusService databaseStatusService,
                                     SchemaInitializationManager schemaInitializationManager,
                                     boolean includeDetails) {
        this.databaseStatusService = databaseStatusService;
        this.schemaInitializationManager = schemaInitializationManager;
        this.includeDetails = includeDetails;
    }

    @Override
    public Health health() {
        try {
            // Check if schema is initialized
            if (!schemaInitializationManager.isSchemaInitialized()) {
                return Health.up()
                        .withDetail("status", "schema_not_initialized")
                        .withDetail("message", "Database schema not initialized - deployment tool not yet used")
                        .build();
            }

            // Get comprehensive status
            DatabaseStatus status = databaseStatusService.getComprehensiveStatus();

            // Determine overall health based on status
            Health.Builder builder = determineHealthBuilder(status);

            // Add summary details
            if (status.getScriptSummary() != null) {
                builder.withDetail("totalScripts", status.getScriptSummary().getTotalScripts())
                        .withDetail("executedScripts", status.getScriptSummary().getExecutedScripts())
                        .withDetail("failedScripts", status.getScriptSummary().getFailedScripts())
                        .withDetail("rolledBackScripts", status.getScriptSummary().getRolledBackScripts());
            }

            // Add lock status
            if (status.getLockInfo() != null) {
                String lockStatus = status.getLockInfo().isActive() ? "ACTIVE" : "NONE";
                builder.withDetail("deploymentLock", lockStatus);
                if (status.getLockInfo().isActive()) {
                    builder.withDetail("lockOwner", status.getLockInfo().getLockOwner())
                            .withDetail("lockAcquiredAt", status.getLockInfo().getLockAcquiredAt());
                }
            }

            // Add detailed information if enabled
            if (includeDetails && status.getScriptSummary() != null) {
                addScriptDetails(builder, status);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to get database status for health check", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "error")
                    .build();
        }
    }

    /**
     * Determine the health status based on the database deployment state.
     * - DOWN: if there are failed scripts
     * - OUT_OF_SERVICE: if a deployment is in progress (lock active)
     * - UP: otherwise
     */
    private Health.Builder determineHealthBuilder(DatabaseStatus status) {
        if (status.getScriptSummary() != null && status.getScriptSummary().getFailedScripts() > 0) {
            return Health.down()
                    .withDetail("status", "failed_scripts")
                    .withDetail("message", String.format("There are %d failed script(s)", status.getScriptSummary().getFailedScripts()));
        }

        if (status.getLockInfo() != null && status.getLockInfo().isActive()) {
            return Health.outOfService()
                    .withDetail("status", "deployment_in_progress")
                    .withDetail("message", "Database deployment is currently in progress");
        }

        return Health.up()
                .withDetail("status", "healthy")
                .withDetail("message", "Database deployment system is healthy");
    }

    /**
     * Add detailed script information to the health response.
     * Includes script names, checksums, and latest execution status.
     */
    private void addScriptDetails(Health.Builder builder, DatabaseStatus status) {
        if (status.getScriptSummary() == null || status.getScriptSummary().getScripts() == null) {
            return;
        }

        Map<String, Object> scripts = new HashMap<>();
        for (DatabaseStatus.ChangeLogScriptStatus script : status.getScriptSummary().getScripts()) {
            Map<String, Object> scriptInfo = new HashMap<>();
            scriptInfo.put("checksum", script.getScriptChecksum());
            scriptInfo.put("status", script.getLatestStatus().getValue());
            scriptInfo.put("createdAt", script.getCreatedAt());

            if (script.getTargetNodes() != null && !script.getTargetNodes().isEmpty()) {
                scriptInfo.put("targetNodes", script.getTargetNodes());
            }

            scripts.put(script.getScriptName(), scriptInfo);
        }

        builder.withDetail("scripts", scripts);
    }
}