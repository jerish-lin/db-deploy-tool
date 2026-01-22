package com.scb.mrp.schemaflow.dbdeploy.service;

import com.scb.mrp.schemaflow.dbdeploy.changelog.ChangeLogManager;
import com.scb.mrp.schemaflow.dbdeploy.changelog.ConfigLoader;
import com.scb.mrp.schemaflow.dbdeploy.config.DeploymentConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.repository.AuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * Implementation of DatabaseDeployManager that handles the core business logic
 * for database deployment operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDatabaseDeployManager implements DatabaseDeployManager {
    private final DatabaseDeployService deployService;
    private final DatabaseStatusService databaseStatusService;
    private final SchemaInitializationManager schemaInitializationManager;
    private final AuditRepository auditRepository;
    private final DeploymentConfig deploymentConfig;
    private final ChangeLogManager changeLogManager;

    @Override
    public void deploy(String changeLogConfigPath, boolean dryRun) throws Exception {
        deploy(changeLogConfigPath, dryRun, null);
    }

    @Override
    public void deploy(String changeLogConfigPath, boolean dryRun, Map<String, String> parameters) throws Exception {
        log.info("Starting deployment (dry-run: {})", dryRun);

        // Initialize schema
        schemaInitializationManager.initializeSchemaIfNeeded();

        // Load changelog config
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(changeLogConfigPath);

        // Delegate to the deploy service
        deployService.deploy(changeLogConfig, dryRun, parameters);

        log.info("Deployment completed successfully");
    }

    @Override
    public void rollback(String changeLogConfigPath, boolean dryRun) throws Exception {
        rollback(changeLogConfigPath, dryRun, null);
    }

    @Override
    public void rollback(String changeLogConfigPath, boolean dryRun, Map<String, String> parameters) throws Exception {
        log.info("Starting rollback (dry-run: {})", dryRun);

        // Initialize schema
        schemaInitializationManager.initializeSchemaIfNeeded();

        // Load changelog config
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(changeLogConfigPath);

        // Delegate to the deploy service
        deployService.rollback(changeLogConfig, dryRun, parameters);

        log.info("Rollback completed successfully");
    }

    @Override
    public void deployOrRollback(String changeLogConfigPath, boolean dryRun) {
        log.info("Starting deployOrRollback (dry-run: {})", dryRun);

        try {
            // Initialize schema
            schemaInitializationManager.initializeSchemaIfNeeded();

            // Load changelog config
            ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(changeLogConfigPath);

            // Use ChangeLogManager to determine what action is needed
            ChangeLogManager.DeploymentAction action =
                    changeLogManager.determineDeploymentAction(changeLogConfig);

            switch (action) {
                case DEPLOY:
                    log.info("Detected pending scripts, performing deployment");
                    deploy(changeLogConfigPath, dryRun);
                    break;
                case ROLLBACK:
                    if (!deploymentConfig.isEnableAutoRollback()) {
                        throw new RuntimeException("Rollback is required but enableAutoRollback is not enabled. Please enable enableAutoRollback in the configuration or use explicit rollback command.");
                    }
                    log.info("Detected scripts to rollback, performing rollback");
                    rollback(changeLogConfigPath, dryRun);
                    break;
                case NONE:
                    log.info("Database is already at the target state. No action needed.");
                    break;
            }

        } catch (Exception e) {
            log.error("deployOrRollback operation failed", e);
            throw new RuntimeException("deployOrRollback operation failed", e);
        }

        log.info("deployOrRollback completed successfully");
    }

    @Override
    public DatabaseStatus status() {
        try {
            // Check if schema is initialized without initializing it
            if (!schemaInitializationManager.isSchemaInitialized()) {
                log.info("Database schema not initialized - returning empty status");
                DatabaseStatus status = new DatabaseStatus();
                status.setDatabaseConnected(true);

                // Create empty sub-objects
                DatabaseStatus.ScriptSummary scriptSummary = new DatabaseStatus.ScriptSummary();
                scriptSummary.setScripts(new ArrayList<>());
                scriptSummary.setTotalScripts(0);
                scriptSummary.setExecutedScripts(0);
                scriptSummary.setFailedScripts(0);
                scriptSummary.setRolledBackScripts(0);
                status.setScriptSummary(scriptSummary);

                DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
                lockInfo.setActive(false);
                status.setLockInfo(lockInfo);

                status.setRecentAuditHistory(new ArrayList<>());
                return status;
            }

            return databaseStatusService.getComprehensiveStatus();

        } catch (Exception e) {
            log.warn("Failed to get database status", e);
            DatabaseStatus status = new DatabaseStatus();
            status.setDatabaseConnected(false);

            return status;
        }
    }
}