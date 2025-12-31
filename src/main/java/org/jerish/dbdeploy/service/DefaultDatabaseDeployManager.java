package org.jerish.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.configloader.ConfigLoader;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Implementation of DatabaseDeployManager that handles the core business logic
 * for database deployment operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDatabaseDeployManager implements DatabaseDeployManager {
    private final DatabaseDeployService deployService;
    private final SchemaInitializationManager schemaInitializationManager;

    @Override
    public void deploy(String changeLogConfigPath, String tagName, boolean dryRun) throws Exception {
        log.info("Starting deployment with tag: {} (dry-run: {})", tagName, dryRun);

        // Initialize schema
        schemaInitializationManager.initializeSchemaIfNeeded();

        // Load changelog config
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(changeLogConfigPath);

        // Delegate to the deploy service
        deployService.deploy(changeLogConfig, tagName, dryRun);

        log.info("Deployment completed successfully for tag: {}", tagName);
    }

    @Override
    public void rollback(String targetTagName, boolean dryRun) throws Exception {
        log.info("Starting rollback to tag: {} (dry-run: {})", targetTagName, dryRun);

        // Initialize schema
        schemaInitializationManager.initializeSchemaIfNeeded();

        // Delegate to the deploy service
        deployService.rollback(targetTagName, dryRun);

        log.info("Rollback completed successfully to tag: {}", targetTagName);
    }

    @Override
    public void deployOrRollback(String changeLogConfigPath, String tagName, boolean dryRun) {
        log.info("Starting deployOrRollback with tag: {} (dry-run: {})", tagName, dryRun);

        try {
            // Initialize schema
            schemaInitializationManager.initializeSchemaIfNeeded();

            // Check current deployment status to decide whether to deploy or rollback
            DatabaseStatus currentStatus = status();

            if (currentStatus.getCurrentTag() == null || currentStatus.getCurrentTag().isEmpty()) {
                // No current deployment, perform initial deployment
                log.info("No current deployment found, performing initial deployment");
                deploy(changeLogConfigPath, tagName, dryRun);
            } else if (currentStatus.getCurrentTag().equals(tagName)) {
                // Already at target tag, no action needed
                log.info("Database is already at target tag: {}", tagName);
            } else {
                // Check if we need to deploy forward or rollback
                // This is a simplified logic - you may want to implement version comparison
                log.info("Current tag: {}, Target tag: {}", currentStatus.getCurrentTag(), tagName);

                // For now, we'll assume if the target tag doesn't exist in deployment history, we deploy
                // Otherwise, we rollback
                try {
                    deploy(changeLogConfigPath, tagName, dryRun);
                } catch (Exception e) {
                    log.info("Deployment failed, attempting rollback to tag: {}", tagName);
                    try {
                        rollback(tagName, dryRun);
                    } catch (Exception rollbackException) {
                        log.error("Both deployment and rollback failed", rollbackException);
                        throw new RuntimeException("Neither deployment nor rollback succeeded", rollbackException);
                    }
                }
            }
        } catch (Exception e) {
            log.error("deployOrRollback operation failed", e);
            throw new RuntimeException("deployOrRollback operation failed", e);
        }

        log.info("deployOrRollback completed successfully for tag: {}", tagName);
    }

    @Override
    public DatabaseStatus status() {
        try {
            // Check if schema is initialized without initializing it
            if (!schemaInitializationManager.isSchemaInitialized()) {
                log.info("Database schema not initialized - returning empty status");
                DatabaseStatus status = new DatabaseStatus();
                status.setDatabaseConnected(true);
                status.setDatabaseHealthy(true);
                status.setDatabaseHealthMessage("Database connected but schema not initialized");
                status.setCurrentTag("");
                status.setConfigurationValid(true);
                status.setConfigurationMessage("Ready for initial deployment");

                // Initialize lists to prevent null pointer exceptions
                status.setExecutedScriptNames(new ArrayList<>());
                status.setFailedScriptNames(new ArrayList<>());
                status.setRolledBackScriptNames(new ArrayList<>());
                status.setPendingScriptNames(new ArrayList<>());
                status.setRecentDeployments(new ArrayList<>());
                return status;
            }

            return deployService.getComprehensiveStatus();

        } catch (Exception e) {
            log.warn("Failed to get database status", e);
            DatabaseStatus status = new DatabaseStatus();
            status.setDatabaseConnected(false);
            status.setDatabaseHealthy(false);
            status.setDatabaseHealthMessage("Database connection failed: " + e.getMessage());
            return status;
        }
    }
}