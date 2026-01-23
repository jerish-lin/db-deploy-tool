package com.scb.mrp.schemaflow.compatibilitycheck.service;

import com.scb.mrp.schemaflow.dbdeploy.changelog.ChangeLogManager;
import com.scb.mrp.schemaflow.dbdeploy.changelog.ConfigLoader;
import com.scb.mrp.schemaflow.dbdeploy.config.ChangeLogPathConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptFileContent;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that performs pre-check on database deployment status during application startup.
 * <p>
 * The check will fail if:
 * 1. A deployment is currently in progress (lock acquired by others)
 * 2. The last deployment status is FAILED
 * 3. There are pending changelog scripts not yet applied
 * <p>
 * Note: If the database schema is not initialized, the pre-check will be skipped
 * and a warning will be logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityCheckService {

    private final DatabaseStatusService databaseStatusService;
    private final ChangeLogManager changeLogManager;
    private final ChangeLogPathConfig changeLogPathConfig;
    private final SchemaInitializationManager schemaInitializationManager;

    /**
     * Performs the database status pre-check after bean construction.
     * This method is automatically called by Spring after dependency injection.
     *
     * @throws IllegalStateException if any of the pre-check conditions fail
     */
    @PostConstruct
    public void performCompatibilityCheck() {
        log.info("Starting database deployment status pre-check...");

        // Check if database schema is initialized
        if (!isSchemaInitialized()) {
            String error = "Database schema is not initialized. Skipping deployment status pre-check. " +
                    "Please run the deployment tool to initialize the schema.";
            log.error(error);
            throw new IllegalStateException(error);
        }

        try {

            // Get comprehensive status once and reuse it for multiple checks
            DatabaseStatus status = getDatabaseStatus();

            // Check 1: Deployment in progress
            checkDeploymentInProgress(status);

            // Check 2: Last deployment status
            checkLastDeploymentStatus(status);

            // Check 3: Pending changelog scripts
            checkPendingChangelogScripts();

            log.info("Database deployment status pre-check completed successfully.");
        } catch (Exception e) {
            log.error("Database deployment status pre-check failed: {}", e.getMessage());
            throw new IllegalStateException("Database deployment status pre-check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the database schema is initialized.
     *
     * @return true if schema is initialized, false otherwise
     */
    private boolean isSchemaInitialized() {
        try {
            return schemaInitializationManager.isSchemaInitialized();
        } catch (Exception e) {
            log.error("Failed to check schema initialization status: {}");
            throw e;
        }
    }

    /**
     * Get database status with error handling.
     * Returns null if unable to retrieve status.
     */
    private DatabaseStatus getDatabaseStatus() {
        try {
            return databaseStatusService.getComprehensiveStatus();
        } catch (Exception e) {
            log.error("Failed to get database status: {}");
            throw e;
        }
    }

    /**
     * Check if a deployment is currently in progress by checking the lock status.
     */
    private void checkDeploymentInProgress(DatabaseStatus status) {
        if (status == null) {
            log.debug("Skipping deployment lock check - unable to get database status");
            return;
        }

        if (status.getLockInfo() != null && status.getLockInfo().isActive()) {
            String message = String.format(
                    "Database deployment is currently in progress (lock is held by: %s). " +
                            "Application cannot start while a deployment is in progress.",
                    status.getLockInfo().getLockOwner()
            );
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.debug("Deployment lock check passed - no deployment in progress.");
    }

    /**
     * Check if the last deployment status is FAILED.
     */
    private void checkLastDeploymentStatus(DatabaseStatus status) {
        if (status == null) {
            log.debug("Skipping last deployment status check - unable to get database status");
            return;
        }

        if (status.getScriptSummary() != null) {
            int failedScripts = status.getScriptSummary().getFailedScripts();

            if (failedScripts > 0) {
                List<String> failedScriptNames = status.getScriptSummary().getScripts().stream()
                                    .filter(s -> ScriptExecutionStatus.FAILED.equals(s.getLatestStatus()))                        .map(DatabaseStatus.ScriptStatus::getScriptName)
                        .toList();

                String message = String.format(
                        "Last deployment has %d failed script(s): %s. " +
                                "Please resolve the failures before starting the application.",
                        failedScripts,
                        String.join(", ", failedScriptNames)
                );
                log.error(message);
                throw new IllegalStateException(message);
            }

            log.debug("Last deployment status check passed - no failed scripts.");
        }
    }

    /**
     * Check if there are pending changelog scripts that haven't been applied yet.
     */
    private void checkPendingChangelogScripts() throws Exception {
        // Load the changelog configuration
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(changeLogPathConfig.getPath());

        // Use ChangeLogManager to determine pending scripts
        List<ScriptFileContent> pendingScripts = changeLogManager.determinePendingScripts(
                changeLogConfig,
                java.util.Map.of()
        );

        if (!pendingScripts.isEmpty()) {
            List<String> pendingScriptNames = pendingScripts.stream()
                    .map(ScriptFileContent::getName)
                    .collect(Collectors.toList());

            String message = String.format(
                    "There are %d pending changelog script(s) not yet applied: %s. " +
                            "Please run the deployment before starting the application.",
                    pendingScripts.size(),
                    String.join(", ", pendingScriptNames)
            );
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.debug("Pending changelog check passed - all scripts are up to date.");
    }
}