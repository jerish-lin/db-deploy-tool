package org.jerish.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.script.ScriptExecutor;
import org.jerish.dbdeploy.script.ScriptFileManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDatabaseDeployService implements DatabaseDeployService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditRepository auditRepository;
    private final ScriptExecutor scriptExecutor;
    private final ScriptFileManager scriptFileManager;

    private String deriveScriptBasePath(String changelogPath) {
        if (changelogPath == null) {
            return ".";
        }
        // Get the parent directory of the changelog file and append "scripts"
        Path changelogFile = Paths.get(changelogPath);
        Path scriptsDir = changelogFile.getParent() != null ? changelogFile.getParent().resolve("scripts") : Paths.get("scripts");
        return scriptsDir.toString();
    }

    @Override
    public void deploy(ChangeLogConfig changeLogConfig, String tagName, boolean dryRun) throws Exception {

        log.info("Starting deployment with tag: {}", tagName);

        String lockOwner = "deploy-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
            // Check if this is the first deployment (no tags exist)
            boolean isFirstDeployment = false;
            if (!dryRun) {
                // Debug: Check database URL
                try {
                    String dbUrl = jdbcTemplate.getDataSource().getConnection().getMetaData().getURL();
                    log.info("Database URL: {}", dbUrl);
                } catch (Exception e) {
                    log.debug("Could not retrieve database URL", e);
                }

                List<DeploymentTag> existingTags = auditRepository.getDeploymentTags();
                log.info("Found {} existing tags", existingTags.size());
                for (DeploymentTag tag : existingTags) {
                    log.info("Existing tag: {}", tag.getTagName());
                }
                if (existingTags.isEmpty()) {
                    isFirstDeployment = true;
                    log.info("First deployment detected, creating initial tag");
                    createInitialTag();
                }
            }

            // Load all scripts from changelog
            String scriptBasePath = deriveScriptBasePath(changeLogConfig.getChangelogFilePath());
            List<ScriptFileManager.ScriptFile> scripts = scriptFileManager.loadScripts(scriptBasePath, changeLogConfig.getScripts());

            for (ScriptFileManager.ScriptFile script : scripts) {
                if (dryRun) {
                    log.info("[DRY RUN] Would execute script: {}", script.getName());
                    continue;
                }

                // Check if script was already executed
                String scriptNameForDb = script.getName();
                if (auditRepository.isScriptExecuted(scriptNameForDb)) {
                    log.info("Skipping already executed script: {}", script.getName());
                    continue;
                }

                log.info("Executing script: {}", script.getName());
                executeScriptWithAudit(script, tagName);
            }

            // Create deployment tag
            if (!dryRun) {
                createDeploymentTag(tagName);
            }

            log.info("Deployment completed successfully with tag: {}", tagName);

        } finally {
            if (!dryRun) {
                auditRepository.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    private void executeScriptWithAudit(ScriptFileManager.ScriptFile script, String tagName) throws Exception {
        ChangeLogEntry entry = new ChangeLogEntry();
        // Use the full script name including folder structure
        String scriptNameForDb = script.getName();
        entry.setScriptName(scriptNameForDb);
        entry.setRollbackScriptContent(script.getRollbackContent());
        entry.setRollbackVerifyScriptContent(script.getRollbackVerifyContent());
        entry.setTagName(tagName);
        entry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        // Calculate checksum from the script content that's already loaded
        String checksum = scriptExecutor.calculateChecksum(script.getApplyContent());
        entry.setScriptChecksum(checksum);

        try {
            // Execute script with verification in the same transaction
            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScriptWithVerificationInTransaction(script.getApplyPath(), script.getName());

            if (result.isSuccess()) {
                entry.setExecutionDurationMs(result.getDuration());
                auditRepository.recordScriptExecution(entry);
                log.info("Script {} executed and verified successfully", script.getName());
            } else {
                throw new RuntimeException(result.getErrorMessage());
            }
        } catch (Exception e) {
            entry.setExecutionStatus(ScriptExecutionStatus.FAILED);
            entry.setErrorMessage(e.getMessage());
            auditRepository.recordScriptExecution(entry);
            throw e;
        }
    }

    private void createDeploymentTag(String tagName) throws Exception {
        DeploymentTag tag = new DeploymentTag();
        tag.setTagName(tagName);
        tag.setDescription("Deployment tag created by db-deploy-tool");
        tag.setDeploymentTime(LocalDateTime.now());
        tag.setCreatedBy("db-deploy-tool");
        tag.setIsActive(true);

        auditRepository.createDeploymentTag(tag);
    }

    private void createInitialTag() throws Exception {
        DeploymentTag tag = new DeploymentTag();
        tag.setTagName("initial");
        tag.setDescription("Initial state - before any changesets applied");


        tag.setDeploymentTime(LocalDateTime.now());
        tag.setCreatedBy("db-deploy-tool");
        tag.setIsActive(true);

        auditRepository.createDeploymentTag(tag);

        // Create a fake changelog entry for the initial state
        ChangeLogEntry initialEntry = new ChangeLogEntry();
        initialEntry.setScriptName("Initial Database State");
        initialEntry.setScriptChecksum("initial");
        initialEntry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        initialEntry.setExecutionTime(LocalDateTime.now());
        initialEntry.setExecutionDurationMs(0L);
        initialEntry.setRollbackScriptContent("-- Initial state - no rollback needed");
        initialEntry.setTagName("initial");
        initialEntry.setCreatedAt(LocalDateTime.now());
        initialEntry.setUpdatedAt(LocalDateTime.now());

        auditRepository.recordScriptExecution(initialEntry);

        log.info("Created initial tag and changelog entry for rollback capability");
    }

    @Override
    public void rollback(String targetTagName, boolean dryRun) throws Exception {
        log.info("Starting rollback to tag: {}", targetTagName);

        String lockOwner = "rollback-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
            // Get the target deployment tag
            DeploymentTag targetTag = auditRepository.getDeploymentTag(targetTagName);
            if (targetTag == null) {
                throw new RuntimeException("Target tag '" + targetTagName + "' not found");
            }

            // Get all scripts executed after the target tag
            List<ChangeLogEntry> scriptsToRollback = auditRepository.getScriptsExecutedAfter(targetTagName);

            if (scriptsToRollback.isEmpty()) {
                log.info("No scripts to rollback. Database is already at tag: {}", targetTagName);
                return;
            }

            log.info("Found {} scripts to rollback", scriptsToRollback.size());

            // Execute rollback scripts in reverse order
            for (int i = 0; i < scriptsToRollback.size(); i++) {
                ChangeLogEntry entry = scriptsToRollback.get(i);

                if (dryRun) {
                    log.info("[DRY RUN] Would rollback script: {}", entry.getScriptName());
                    continue;
                }

                log.info("Rolling back script: {}", entry.getScriptName());

                // Execute rollback script if available
                if (entry.getRollbackScriptContent() != null && !entry.getRollbackScriptContent().isEmpty()) {
                    scriptExecutor.executeScriptContent(entry.getRollbackScriptContent());
                } else {
                    log.warn("No rollback script available for: {}", entry.getScriptName());
                }

                // Execute rollback verification if available
                if (entry.getRollbackVerifyScriptContent() != null && !entry.getRollbackVerifyScriptContent().isEmpty()) {
                    try {
                        ScriptExecutor.VerificationResult verificationResult = scriptExecutor.executeVerificationContent(entry.getRollbackVerifyScriptContent());

                        // Log verification results
                        log.info("\n=== ROLLBACK VERIFICATION RESULTS ===");
                        log.info("Script: {}", entry.getScriptName());
                        log.info("Verification Status: {}", verificationResult.isSuccess() ? "SUCCESS" : "FAILED");
                        log.info("Duration: {}ms", verificationResult.getDuration());
                        log.info("Output:");
                        log.info("{}", verificationResult.getOutput());

                        if (!verificationResult.isSuccess()) {
                            log.error("Rollback verification error: {}", verificationResult.getErrorMessage());
                        }
                        log.info("=== END ROLLBACK VERIFICATION ===\n");

                        // If verification fails, log warning but continue
                        if (!verificationResult.isSuccess()) {
                            log.warn("Rollback verification failed for script: {}", entry.getScriptName());
                        }
                    } catch (Exception e) {
                        log.warn("Could not execute rollback verification for script {}: {}", entry.getScriptName(), e.getMessage());
                    }
                }

                // Update the execution status to ROLLED_BACK
                auditRepository.updateScriptExecutionStatus(entry.getId(), ScriptExecutionStatus.ROLLED_BACK, null);

                log.info("Script {} rolled back successfully", entry.getScriptName());
            }

            // Deactivate all deployment tags that were rolled back
            if (!dryRun) {
                // Get all tags and deactivate those that were rolled back
                List<DeploymentTag> allTags = auditRepository.getDeploymentTags();

                // Since tags are ordered by deployment_time DESC (newest first),
                // we need to find tags that were deployed after the target tag
                boolean foundTargetTag = false;

                for (DeploymentTag tag : allTags) {
                    if (tag.getTagName().equals(targetTagName)) {
                        foundTargetTag = true;
                        // Continue to next iteration - don't deactivate the target tag
                        continue;
                    }

                    // If we haven't found the target tag yet, we're still looking at tags
                    // that were deployed after the target tag (because of DESC order)
                    if (!foundTargetTag && tag.getIsActive()) {
                        auditRepository.deactivateDeploymentTag(tag.getTagName());
                        log.info("Deactivated deployment tag: {}", tag.getTagName());
                    }
                }
            }

            log.info("Rollback to tag '{}' completed successfully", targetTagName);

        } finally {
            if (!dryRun) {
                auditRepository.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    /**
     * Get comprehensive database deployment status
     *
     * @return DatabaseStatus object with detailed status information
     */
    @Override
    public DatabaseStatus getComprehensiveStatus() {
        DatabaseStatus status = new DatabaseStatus();
        // Basic connection info
        status.setDatabaseConnected(true);
        status.setDatabaseHealthy(true);
        status.setDatabaseHealthMessage("Database operational");

        // Query deployment state using views
        populateDeploymentState(status);

        // Query script status using views
        populateScriptStatus(status);

        // Query deployment lock status
        populateLockStatus(status);

        // Query database health
        populateDatabaseHealth(status);

        // Query configuration status
        populateConfigurationStatus(status);

        // Query recent deployment history
        populateDeploymentHistory(status);
        return status;
    }

    private void populateDeploymentState(DatabaseStatus status) {
        try {
            status.setDatabaseConnected(true);
            DatabaseStatus.DeploymentStateInfo stateInfo = auditRepository.getCurrentDeploymentState();
            if (stateInfo != null) {
                status.setCurrentTag(stateInfo.getCurrentTag());
                status.setCurrentTagDescription(stateInfo.getDescription());
                status.setLastDeploymentTime(parseDateTime(stateInfo.getDeploymentTime()));
                status.setLastDeploymentUser(stateInfo.getCreatedBy());
                status.setTotalScripts(stateInfo.getTotalScripts());
                status.setExecutedScripts(stateInfo.getSuccessfulScripts());
                status.setFailedScripts(stateInfo.getFailedScripts());
                status.setRolledBackScripts(stateInfo.getRolledBackScripts());
            } else {
                status.setCurrentTag("");
                status.setTotalScripts(0);
                status.setExecutedScripts(0);
                status.setFailedScripts(0);
            }
            // Always get total rolled back scripts count across all tags
            status.setRolledBackScripts(auditRepository.getTotalRolledBackScripts());
        } catch (Exception e) {
            log.error("Error populating deployment state", e);
        }

    }

    private void populateScriptStatus(DatabaseStatus status) {
        try {
            List<DatabaseStatus.ScriptExecutionInfo> scriptHistory = auditRepository.getScriptExecutionHistory();

            List<String> executedScripts = new ArrayList<>();
            List<String> failedScripts = new ArrayList<>();
            List<String> rolledBackScripts = new ArrayList<>();

            for (var info : scriptHistory) {
                switch (info.getExecutionStatus()) {
                    case "SUCCESS" -> executedScripts.add(info.getScriptName());
                    case "FAILED" -> failedScripts.add(info.getScriptName());
                    case "ROLLED_BACK" -> rolledBackScripts.add(info.getScriptName());
                }
            }

            status.setExecutedScriptNames(executedScripts);
            status.setFailedScriptNames(failedScripts);
            status.setRolledBackScriptNames(rolledBackScripts);

            // Get detailed failure information if needed
            if (!failedScripts.isEmpty()) {
                List<DatabaseStatus.FailedScriptInfo> failedScriptDetails = auditRepository.getFailedScripts();
                // Failed scripts are already populated above, details available if needed
            }

        } catch (Exception e) {
            log.error("Error populating script status", e);
            status.setExecutedScriptNames(new ArrayList<>());
            status.setFailedScriptNames(new ArrayList<>());
            status.setRolledBackScriptNames(new ArrayList<>());
        }
    }

    private void populateLockStatus(DatabaseStatus status) {
        try {
            DatabaseStatus.LockInfo lockInfo = auditRepository.getCurrentLockStatus();

            if (lockInfo != null && lockInfo.isActive()) {
                status.setDeploymentInProgress(true);
                status.setDeploymentLockOwner(lockInfo.getLockOwner());
                status.setDeploymentLockAcquiredAt(parseDateTime(lockInfo.getLockAcquiredAt()));
                status.setDeploymentLockExpiresAt(parseDateTime(lockInfo.getLockExpiresAt()));
            } else {
                status.setDeploymentInProgress(false);
                status.setDeploymentLockOwner("");
            }
        } catch (Exception e) {
            log.error("Error populating lock status", e);
            status.setDeploymentInProgress(false);
            status.setDeploymentLockOwner("");
        }
    }

    private void populateDatabaseHealth(DatabaseStatus status) {
        try {
            // Basic health check - query database version and response time
            long startTime = System.currentTimeMillis();

            DatabaseStatus.DatabaseHealthInfo healthInfo = auditRepository.getDatabaseHealthInfo();

            status.setDatabaseVersion(healthInfo.getVersion());
            status.setDatabaseHealthy(healthInfo.isHealthy());
            if (!healthInfo.isHealthy()) {
                status.setDatabaseHealthMessage(healthInfo.getHealthMessage());
            }

            long responseTime = System.currentTimeMillis() - startTime;
            status.setConnectionResponseTime(responseTime);

        } catch (Exception e) {
            log.error("Error populating database health", e);
            status.setDatabaseHealthy(false);
            status.setDatabaseHealthMessage("Health check error: " + e.getMessage());
        }
    }

    private void populateConfigurationStatus(DatabaseStatus status) {
        try {
            DatabaseStatus.ConfigurationInfo configInfo = auditRepository.getConfigurationInfo();

            status.setConfigurationValid(configInfo.isValid());
            status.setConfigurationMessage(configInfo.getMessage());

        } catch (Exception e) {
            log.error("Error populating configuration status", e);
            status.setConfigurationValid(false);
            status.setConfigurationMessage("Configuration check failed");
        }
    }

    private void populateDeploymentHistory(DatabaseStatus status) {
        try {
            List<DatabaseStatus.DeploymentHistoryEntry> history = auditRepository.getRecentDeploymentHistory();
            status.setRecentDeployments(history);

        } catch (Exception e) {
            log.error("Error populating deployment history", e);
            status.setRecentDeployments(new ArrayList<>());
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // Try standard ISO format first
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e1) {
            try {
                // Try SQLite datetime format: "YYYY-MM-DD HH:MM:SS"
                DateTimeFormatter sqliteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(dateTimeStr, sqliteFormatter);
            } catch (Exception e2) {
                log.debug("Failed to parse datetime: {}", dateTimeStr, e2);
                return null;
            }
        } catch (Exception e) {
            log.debug("Failed to parse datetime: {}", dateTimeStr, e);
            return null;
        }
    }
}