package org.jerish.dbdeploy.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.config.DeploymentConfig;
import org.jerish.dbdeploy.changelog.ConfigLoader;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.entity.ScriptExecutionStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that performs pre-check on database deployment status during application startup.
 * This service is activated via the {@link org.jerish.dbdeploy.annotation.EnableDbDeployCheck} annotation.
 * 
 * The check will fail if:
 * 1. A deployment is currently in progress (lock acquired by others)
 * 2. The last deployment status is FAILED
 * 3. There are pending changelog scripts not yet applied
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "db.deploy.check", name = "enabled", havingValue = "true", matchIfMissing = false)
public class
DBStatusPreCheckService {

    private final DatabaseStatusService databaseStatusService;
    private final AuditRepository auditRepository;
    private final DeploymentConfig deploymentConfig;
    
    @Value("${db.deploy.check.fail-on-deployment-in-progress:true}")
    private boolean failOnDeploymentInProgress;
    
    @Value("${db.deploy.check.fail-on-last-deployment-failed:true}")
    private boolean failOnLastDeploymentFailed;
    
    @Value("${db.deploy.check.fail-on-pending-changelog:true}")
    private boolean failOnPendingChangelog;
    
    @Value("${db.deploy.check.changelog-path:classpath:db-changelog.yml}")
    private String changelogPath;

    /**
     * Performs the database status pre-check after bean construction.
     * This method is automatically called by Spring after dependency injection.
     * 
     * @throws IllegalStateException if any of the pre-check conditions fail
     */
    @PostConstruct
    public void performPreCheck() {
        log.info("Starting database deployment status pre-check...");
        
        try {
            // Check 1: Deployment in progress
            checkDeploymentInProgress();
            
            // Check 2: Last deployment status
            checkLastDeploymentStatus();
            
            // Check 3: Pending changelog scripts
            checkPendingChangelogScripts();
            
            log.info("Database deployment status pre-check completed successfully.");
        } catch (Exception e) {
            log.error("Database deployment status pre-check failed: {}", e.getMessage());
            throw new IllegalStateException("Database deployment status pre-check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a deployment is currently in progress by checking the lock status.
     */
    private void checkDeploymentInProgress() {
        if (!failOnDeploymentInProgress) {
            log.debug("Deployment in progress check is disabled.");
            return;
        }
        
        try {
            String lockOwner = "precheck-" + System.currentTimeMillis();
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 1);
            
            if (!lockAcquired) {
                String message = "Database deployment is currently in progress (lock is held by another process). " +
                               "Application cannot start while a deployment is in progress.";
                log.error(message);
                throw new IllegalStateException(message);
            }
            
            // Release the lock immediately after checking
            auditRepository.releaseLock("db_deploy_tool", lockOwner);
            log.debug("Deployment lock check passed - no deployment in progress.");
            
        } catch (IllegalStateException e) {
            throw e; // Re-throw our own exception
        } catch (Exception e) {
            log.warn("Failed to check deployment lock status: {}", e.getMessage());
            // Don't fail the startup if we can't check the lock, just log a warning
        }
    }

    /**
     * Check if the last deployment status is FAILED.
     */
    private void checkLastDeploymentStatus() {
        if (!failOnLastDeploymentFailed) {
            log.debug("Last deployment status check is disabled.");
            return;
        }
        
        try {
            DatabaseStatus status = databaseStatusService.getComprehensiveStatus();
            
            if (status.getScriptStatus() != null) {
                List<String> failedScripts = status.getScriptStatus().getFailedScriptNames();
                
                if (failedScripts != null && !failedScripts.isEmpty()) {
                    String message = String.format(
                        "Last deployment has %d failed script(s): %s. " +
                        "Please resolve the failures before starting the application.",
                        failedScripts.size(),
                        String.join(", ", failedScripts)
                    );
                    log.error(message);
                    throw new IllegalStateException(message);
                }
                
                log.debug("Last deployment status check passed - no failed scripts.");
            }
            
        } catch (IllegalStateException e) {
            throw e; // Re-throw our own exception
        } catch (Exception e) {
            log.warn("Failed to check last deployment status: {}", e.getMessage());
            // Don't fail the startup if we can't check the status, just log a warning
        }
    }

    /**
     * Check if there are pending changelog scripts that haven't been applied yet.
     */
    private void checkPendingChangelogScripts() {
        if (!failOnPendingChangelog) {
            log.debug("Pending changelog check is disabled.");
            return;
        }
        
        try {
            // Load the changelog configuration
            ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(changelogPath);
            
            // Get all scripts from changelog
            List<ScriptConfig> changelogScripts = changeLogConfig.getScripts();
            if (changelogScripts == null || changelogScripts.isEmpty()) {
                log.debug("No scripts found in changelog, skipping pending check.");
                return;
            }
            
            // Get all successfully executed scripts from database
            List<ChangeLogEntry> executedScripts = auditRepository.getAllExecutedScripts();
            Set<String> executedScriptNames = executedScripts.stream()
                .filter(entry -> entry.getExecutionStatus() == ScriptExecutionStatus.SUCCESS)
                .map(ChangeLogEntry::getScriptName)
                .collect(Collectors.toSet());
            
            // Find pending scripts (scripts in changelog that haven't been executed)
            List<String> pendingScripts = new ArrayList<>();
            for (ScriptConfig scriptConfig : changelogScripts) {
                if (!executedScriptNames.contains(scriptConfig.getName())) {
                    pendingScripts.add(scriptConfig.getName());
                }
            }
            
            if (!pendingScripts.isEmpty()) {
                String message = String.format(
                    "There are %d pending changelog script(s) not yet applied: %s. " +
                    "Please run the deployment before starting the application.",
                    pendingScripts.size(),
                    String.join(", ", pendingScripts)
                );
                log.error(message);
                throw new IllegalStateException(message);
            }
            
            log.debug("Pending changelog check passed - all scripts are up to date.");
            
        } catch (IllegalStateException e) {
            throw e; // Re-throw our own exception
        } catch (Exception e) {
            log.warn("Failed to check pending changelog scripts: {}", e.getMessage());
            // Don't fail the startup if we can't check the changelog, just log a warning
        }
    }
}