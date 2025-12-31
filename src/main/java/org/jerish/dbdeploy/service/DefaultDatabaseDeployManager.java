package org.jerish.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.configloader.ConfigLoader;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.springframework.stereotype.Service;

/**
 * Implementation of DatabaseDeployManager that handles the core business logic
 * for database deployment operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDatabaseDeployManager implements DatabaseDeployManager {
    private final DatabaseDeployService deployService;
    private final AuditRepository auditDao;

    @Override
    public void deploy(String changeLogConfigPath, String tagName, boolean dryRun) throws Exception {
        log.info("Starting deployment with tag: {} (dry-run: {})", tagName, dryRun);

        // Initialize schema
        auditDao.initializeSchema();

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
        auditDao.initializeSchema();

        // Delegate to the deploy service
        deployService.rollback(targetTagName, dryRun);

        log.info("Rollback completed successfully to tag: {}", targetTagName);
    }

    @Override
    public void deployOrRollback(String changeLogConfigPath, String tagName, boolean dryRun) {
        log.info("Starting deployOrRollback with tag: {} (dry-run: {})", tagName, dryRun);

        try {
            // Initialize schema
            auditDao.initializeSchema();

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
        DatabaseStatus status = new DatabaseStatus();

        try {
            // Initialize schema if needed
            auditDao.initializeSchema();

            // Get current deployment information from audit tables
            // This is a simplified implementation - you'll need to implement the actual logic
            // based on your audit table structure

            status.setDatabaseConnected(true);
            status.setCurrentTag(getCurrentDeploymentTag());
            status.setExecutedScripts(getExecutedScriptCount());
            status.setFailedScripts(getFailedScriptCount());
            status.setRolledBackScripts(getRolledBackScriptCount());
            status.setLastDeploymentTime(getLastDeploymentTime());
            status.setExecutedScriptNames(getExecutedScriptNames());
            status.setTotalScripts(getTotalScriptCount());

        } catch (Exception e) {
            log.warn("Failed to get database status", e);
            status.setDatabaseConnected(false);
        }

        return status;
    }

    // Helper methods - implemented using AuditDao methods
    private String getCurrentDeploymentTag() {
        try {
            // Get the most recent active deployment tag
            var tags = auditDao.getDeploymentTags();
            return tags.stream()
                    .filter(tag -> tag.getIsActive())
                    .findFirst()
                    .map(tag -> tag.getTagName())
                    .orElse("");
        } catch (Exception e) {
            log.debug("Failed to get current deployment tag", e);
            return "";
        }
    }

    private int getExecutedScriptCount() {
        try {
            // Count successful script executions
            var tags = auditDao.getDeploymentTags();
            return tags.stream()
                    .filter(tag -> tag.getIsActive())
                    .mapToInt(tag -> {
                        try {
                            return auditDao.getScriptsExecutedAfter("").size();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            log.debug("Failed to get executed script count", e);
            return 0;
        }
    }

    private int getFailedScriptCount() {
        try {
            // For simplicity, return 0 since failed scripts are rolled back
            return 0;
        } catch (Exception e) {
            log.debug("Failed to get failed script count", e);
            return 0;
        }
    }

    private int getRolledBackScriptCount() {
        try {
            // This would require additional audit logic - simplified for now
            return 0;
        } catch (Exception e) {
            log.debug("Failed to get rolled back script count", e);
            return 0;
        }
    }

    private java.time.LocalDateTime getLastDeploymentTime() {
        try {
            var tags = auditDao.getDeploymentTags();
            return tags.stream()
                    .filter(tag -> tag.getIsActive())
                    .findFirst()
                    .map(tag -> tag.getDeploymentTime())
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Failed to get last deployment time", e);
            return null;
        }
    }

    private java.util.List<String> getExecutedScriptNames() {
        try {
            java.util.List<String> scriptNames = new java.util.ArrayList<>();
            var tags = auditDao.getDeploymentTags();
            for (var tag : tags) {
                if (tag.getIsActive()) {
                    var scripts = auditDao.getScriptsExecutedAfter("");
                    scriptNames.addAll(scripts.stream()
                            .map(script -> script.getScriptName())
                            .toList());
                }
            }
            return scriptNames;
        } catch (Exception e) {
            log.debug("Failed to get executed script names", e);
            return java.util.Collections.emptyList();
        }
    }

    private int getTotalScriptCount() {
        try {
            // This would require loading the changelog config - simplified for now
            return 0;
        } catch (Exception e) {
            log.debug("Failed to get total script count", e);
            return 0;
        }
    }
}