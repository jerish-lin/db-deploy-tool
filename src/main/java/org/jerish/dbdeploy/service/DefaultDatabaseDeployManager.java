package org.jerish.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.configloader.ConfigLoader;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private final SchemaInitializationManager schemaInitializationManager;
    private final AuditRepository auditRepository;

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

            // Get all successfully executed scripts from database
            List<ChangeLogEntry> executedScripts = auditRepository.getAllExecutedScripts();

            // Get script names from changelog
            List<String> changelogScriptNames = new ArrayList<>();
            for (org.jerish.dbdeploy.entity.ScriptConfig scriptConfig : changeLogConfig.getScripts()) {
                changelogScriptNames.add(scriptConfig.getName());
            }

            // Get executed script names
            List<String> executedScriptNames = new ArrayList<>();
            for (ChangeLogEntry entry : executedScripts) {
                executedScriptNames.add(entry.getScriptName());
            }

            // Determine if we need to deploy or rollback
            // If there are scripts in changelog that are not executed, we need to deploy
            // If there are scripts executed that are not in changelog, we need to rollback
            boolean needDeploy = false;
            boolean needRollback = false;

            for (String scriptName : changelogScriptNames) {
                if (!executedScriptNames.contains(scriptName)) {
                    needDeploy = true;
                    break;
                }
            }

            for (String scriptName : executedScriptNames) {
                if (!changelogScriptNames.contains(scriptName)) {
                    needRollback = true;
                    break;
                }
            }

            if (needDeploy && needRollback) {
                throw new RuntimeException("Cannot determine whether to deploy or rollback. Both deployment and rollback are needed. Please use explicit deploy or rollback command.");
            } else if (needDeploy) {
                log.info("Detected pending scripts, performing deployment");
                deploy(changeLogConfigPath, dryRun);
            } else if (needRollback) {
                log.info("Detected scripts to rollback, performing rollback");
                rollback(changeLogConfigPath, dryRun);
            } else {
                log.info("Database is already at the target state. No action needed.");
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
                DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
                deploymentState.setCurrentTag("");
                status.setDeploymentState(deploymentState);
                
                DatabaseStatus.DatabaseHealthInfo healthInfo = new DatabaseStatus.DatabaseHealthInfo();
                healthInfo.setHealthy(true);
                healthInfo.setHealthMessage("Database connected but schema not initialized");
                status.setHealthInfo(healthInfo);
                
                DatabaseStatus.ConfigurationInfo configInfo = new DatabaseStatus.ConfigurationInfo();
                configInfo.setValid(true);
                configInfo.setMessage("Ready for initial deployment");
                status.setConfigurationInfo(configInfo);
                
                DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
                scriptStatus.setExecutedScriptNames(new ArrayList<>());
                scriptStatus.setFailedScriptNames(new ArrayList<>());
                scriptStatus.setRolledBackScriptNames(new ArrayList<>());
                scriptStatus.setPendingScriptNames(new ArrayList<>());
                scriptStatus.setScriptHistory(new ArrayList<>());
                scriptStatus.setFailedScriptDetails(new ArrayList<>());
                status.setScriptStatus(scriptStatus);
                
                DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
                lockInfo.setActive(false);
                status.setLockInfo(lockInfo);
                
                status.setRecentDeployments(new ArrayList<>());
                return status;
            }

            return deployService.getComprehensiveStatus();

        } catch (Exception e) {
            log.warn("Failed to get database status", e);
            DatabaseStatus status = new DatabaseStatus();
            status.setDatabaseConnected(false);
            
            DatabaseStatus.DatabaseHealthInfo errorHealthInfo = new DatabaseStatus.DatabaseHealthInfo();
            errorHealthInfo.setHealthy(false);
            errorHealthInfo.setHealthMessage("Database connection failed: " + e.getMessage());
            status.setHealthInfo(errorHealthInfo);
            
            return status;
        }
    }
}