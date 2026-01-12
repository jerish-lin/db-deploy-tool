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
    public void deploy(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception {

        log.info("Starting deployment");

        String lockOwner = "deploy-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
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
                executeScriptWithAudit(script);
            }

            log.info("Deployment completed successfully");

        } finally {
            if (!dryRun) {
                auditRepository.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    private void executeScriptWithAudit(ScriptFileManager.ScriptFile script) throws Exception {
        ChangeLogEntry entry = new ChangeLogEntry();
        // Use the full script name including folder structure
        String scriptNameForDb = script.getName();
        entry.setScriptName(scriptNameForDb);
        entry.setRollbackScriptContent(script.getRollbackContent());
        entry.setRollbackVerifyScriptContent(script.getRollbackVerifyContent());
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

    @Override
    public void rollback(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception {
        log.info("Starting rollback");

        String lockOwner = "rollback-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
            // Extract script names from the target changelog
            List<String> targetScriptNames = new ArrayList<>();
            for (org.jerish.dbdeploy.entity.ScriptConfig scriptConfig : changeLogConfig.getScripts()) {
                targetScriptNames.add(scriptConfig.getName());
            }

            // Get scripts that need to be rolled back (scripts in DB but not in target changelog)
            List<ChangeLogEntry> scriptsToRollback = auditRepository.getScriptsToRollback(targetScriptNames);

            if (scriptsToRollback.isEmpty()) {
                log.info("No scripts to rollback. Database is already at the target state");
                return;
            }

            log.info("Found {} scripts to rollback", scriptsToRollback.size());

            // Execute rollback scripts in reverse order
            for (ChangeLogEntry entry : scriptsToRollback) {
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

                // Record the rollback as a new audit entry (instead of updating the existing one)
                ChangeLogEntry rollbackEntry = new ChangeLogEntry();
                rollbackEntry.setScriptName(entry.getScriptName());
                rollbackEntry.setScriptChecksum(entry.getScriptChecksum());
                rollbackEntry.setExecutionStatus(ScriptExecutionStatus.ROLLED_BACK);
                rollbackEntry.setExecutionTime(LocalDateTime.now());
                rollbackEntry.setExecutionDurationMs(0L); // Rollback duration is tracked separately
                rollbackEntry.setRollbackScriptContent(entry.getRollbackScriptContent());
                rollbackEntry.setRollbackVerifyScriptContent(entry.getRollbackVerifyScriptContent());
                rollbackEntry.setParentAuditId(entry.getId());
                rollbackEntry.setCreatedAt(LocalDateTime.now());
                rollbackEntry.setUpdatedAt(LocalDateTime.now());

                auditRepository.recordRollbackScriptExecution(rollbackEntry, entry.getId());

                log.info("Script {} rolled back successfully", entry.getScriptName());
            }

            log.info("Rollback completed successfully");

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

                    // Set total rolled back scripts count across all tags

                    stateInfo.setRolledBackScripts(auditRepository.getTotalRolledBackScripts());

                    status.setDeploymentState(stateInfo);

                } else {

                    // Create empty deployment state

                    DatabaseStatus.DeploymentStateInfo emptyState = new DatabaseStatus.DeploymentStateInfo();

                    emptyState.setCurrentTag("");

                    emptyState.setTotalScripts(0);

                    emptyState.setSuccessfulScripts(0);

                    emptyState.setFailedScripts(0);

                    emptyState.setRolledBackScripts(auditRepository.getTotalRolledBackScripts());

                    status.setDeploymentState(emptyState);

                }

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

            for (DatabaseStatus.ScriptExecutionInfo info : scriptHistory) {
                switch (info.getExecutionStatus()) {
                    case "SUCCESS" -> executedScripts.add(info.getScriptName());
                    case "FAILED" -> failedScripts.add(info.getScriptName());
                    case "ROLLED_BACK" -> rolledBackScripts.add(info.getScriptName());
                }
            }

            // Create script status info
            DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
            scriptStatus.setExecutedScriptNames(executedScripts);
            scriptStatus.setFailedScriptNames(failedScripts);
            scriptStatus.setRolledBackScriptNames(rolledBackScripts);
            scriptStatus.setPendingScriptNames(new ArrayList<>());
            scriptStatus.setScriptHistory(scriptHistory);
            
            // Get detailed failure information if needed
            if (!failedScripts.isEmpty()) {
                List<DatabaseStatus.FailedScriptInfo> failedScriptDetails = auditRepository.getFailedScripts();
                scriptStatus.setFailedScriptDetails(failedScriptDetails);
            } else {
                scriptStatus.setFailedScriptDetails(new ArrayList<>());
            }
            
            // Set script counts
            scriptStatus.setExecutedScripts(executedScripts.size());
            scriptStatus.setFailedScripts(failedScripts.size());
            scriptStatus.setRolledBackScripts(rolledBackScripts.size());
            scriptStatus.setPendingScripts(0);

            status.setScriptStatus(scriptStatus);

        } catch (Exception e) {
            log.error("Error populating script status", e);
            DatabaseStatus.ScriptStatusInfo emptyScriptStatus = new DatabaseStatus.ScriptStatusInfo();
            emptyScriptStatus.setExecutedScriptNames(new ArrayList<>());
            emptyScriptStatus.setFailedScriptNames(new ArrayList<>());
            emptyScriptStatus.setRolledBackScriptNames(new ArrayList<>());
            emptyScriptStatus.setPendingScriptNames(new ArrayList<>());
            emptyScriptStatus.setScriptHistory(new ArrayList<>());
            emptyScriptStatus.setFailedScriptDetails(new ArrayList<>());
            status.setScriptStatus(emptyScriptStatus);
        }
    }

    private void populateLockStatus(DatabaseStatus status) {
            try {
                DatabaseStatus.LockInfo lockInfo = auditRepository.getCurrentLockStatus();
                
                if (lockInfo != null && lockInfo.isActive()) {
                    status.setLockInfo(lockInfo);
                } else {
                    // Create empty lock info
                    DatabaseStatus.LockInfo emptyLockInfo = new DatabaseStatus.LockInfo();
                    emptyLockInfo.setActive(false);
                    emptyLockInfo.setLockOwner("");
                    status.setLockInfo(emptyLockInfo);
                }
            } catch (Exception e) {
                log.error("Error populating lock status", e);
                DatabaseStatus.LockInfo emptyLockInfo = new DatabaseStatus.LockInfo();
                emptyLockInfo.setActive(false);
                emptyLockInfo.setLockOwner("");
                status.setLockInfo(emptyLockInfo);
            }
        }
    private void populateDatabaseHealth(DatabaseStatus status) {
            try {
                // Basic health check - query database version and response time
                long startTime = System.currentTimeMillis();
    
                DatabaseStatus.DatabaseHealthInfo healthInfo = auditRepository.getDatabaseHealthInfo();
                
                // Add response time to health info
                long responseTime = System.currentTimeMillis() - startTime;
                healthInfo.setResponseTime(responseTime);
                
                status.setHealthInfo(healthInfo);
    
            } catch (Exception e) {
                log.error("Error populating database health", e);
                DatabaseStatus.DatabaseHealthInfo errorHealthInfo = new DatabaseStatus.DatabaseHealthInfo();
                errorHealthInfo.setHealthy(false);
                errorHealthInfo.setHealthMessage("Health check error: " + e.getMessage());
                errorHealthInfo.setResponseTime(-1);
                status.setHealthInfo(errorHealthInfo);
            }
        }
    private void populateConfigurationStatus(DatabaseStatus status) {
        try {
            DatabaseStatus.ConfigurationInfo configInfo = auditRepository.getConfigurationInfo();
            status.setConfigurationInfo(configInfo);

        } catch (Exception e) {
            log.error("Error populating configuration status", e);
            DatabaseStatus.ConfigurationInfo errorConfigInfo = new DatabaseStatus.ConfigurationInfo();
            errorConfigInfo.setValid(false);
            errorConfigInfo.setMessage("Configuration check failed");
            status.setConfigurationInfo(errorConfigInfo);
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