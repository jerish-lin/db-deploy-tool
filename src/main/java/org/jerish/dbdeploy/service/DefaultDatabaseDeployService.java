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
        Path scriptsDir = changelogFile.getParent() != null ?
                changelogFile.getParent().resolve("scripts") :
                Paths.get("scripts");
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
                        ScriptExecutor.VerificationResult verificationResult = scriptExecutor.executeVerificationContent(
                                entry.getRollbackVerifyScriptContent());

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
                        log.warn("Could not execute rollback verification for script {}: {}",
                                entry.getScriptName(), e.getMessage());
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
            // Query current_deployment_state view
            String sql = """
                    SELECT tag_name, description, deployment_time, created_by,
                           total_scripts, successful_scripts, failed_scripts, rolled_back_scripts
                    FROM current_deployment_state
                    LIMIT 1
                    """;

            status.setDatabaseConnected(true);

            try {
                var results = auditRepository.getJdbcTemplate().queryForList(sql);
                if (!results.isEmpty()) {
                    var result = results.get(0);
                    status.setCurrentTag((String) result.get("tag_name"));
                    status.setCurrentTagDescription((String) result.get("description"));
                    status.setLastDeploymentTime(parseDateTime((String) result.get("deployment_time")));
                    status.setLastDeploymentUser((String) result.get("created_by"));
                    status.setTotalScripts(((Number) result.get("total_scripts")).intValue());
                    status.setExecutedScripts(((Number) result.get("successful_scripts")).intValue());
                    status.setFailedScripts(((Number) result.get("failed_scripts")).intValue());
                    status.setRolledBackScripts(((Number) result.get("rolled_back_scripts")).intValue());

                    // Get total rolled back scripts count across all tags
                    String totalRolledBackSql = """
                            SELECT COUNT(*) as total_rolled_back
                            FROM db_change_log
                            WHERE execution_status = 'ROLLED_BACK'
                            """;
                    try {
                        Integer totalRolledBack = auditRepository.getJdbcTemplate().queryForObject(totalRolledBackSql, Integer.class);
                        status.setRolledBackScripts(totalRolledBack != null ? totalRolledBack : 0);
                    } catch (Exception rbException) {
                        log.debug("Error getting total rolled back count", rbException);
                    }
                } else {
                    status.setCurrentTag("");
                    status.setTotalScripts(0);
                    status.setExecutedScripts(0);
                    status.setFailedScripts(0);

                    // Get total rolled back scripts count across all tags
                                    String totalRolledBackSql = """
                                            SELECT COUNT(*) as total_rolled_back
                                            FROM db_change_log
                                            WHERE execution_status = 'ROLLED_BACK'
                                            """;
                                    try {
                                        Integer totalRolledBack = auditRepository.getJdbcTemplate().queryForObject(totalRolledBackSql, Integer.class);
                                        status.setRolledBackScripts(totalRolledBack != null ? totalRolledBack : 0);
                                    } catch (Exception rbException) {
                                        log.debug("Error getting total rolled back count", rbException);
                                    }                }
            } catch (Exception e) {
                log.debug("No current deployment state found", e);
                status.setCurrentTag("");
                status.setTotalScripts(0);
                status.setExecutedScripts(0);
                status.setFailedScripts(0);

                // Get total rolled back scripts count across all tags
                String totalRolledBackSql = """
                        SELECT COUNT(*) as total_rolled_back
                        FROM db_change_log
                        WHERE execution_status = 'ROLLED_BACK'
                        """;
                try {
                    Integer totalRolledBack = auditRepository.getJdbcTemplate().queryForObject(totalRolledBackSql, Integer.class);
                    status.setRolledBackScripts(totalRolledBack != null ? totalRolledBack : 0);
                } catch (Exception rbException) {
                    log.debug("Error getting total rolled back count", rbException);
                }
            }
        } catch (Exception e) {
            log.error("Error populating deployment state", e);
        }
    }

    private void populateScriptStatus(DatabaseStatus status) {
        try {
            // Query script execution history view for script names
            String scriptHistorySql = """
                    SELECT script_name, execution_status
                    FROM script_execution_history
                    ORDER BY execution_time DESC
                    LIMIT 50
                    """;

            var scriptResults = auditRepository.getJdbcTemplate().queryForList(scriptHistorySql);

            java.util.List<String> executedScripts = new java.util.ArrayList<>();
            java.util.List<String> failedScripts = new java.util.ArrayList<>();
            java.util.List<String> rolledBackScripts = new java.util.ArrayList<>();

            for (var row : scriptResults) {
                String scriptName = (String) row.get("script_name");
                String execStatus = (String) row.get("execution_status");

                switch (execStatus) {
                    case "SUCCESS" -> executedScripts.add(scriptName);
                    case "FAILED" -> failedScripts.add(scriptName);
                    case "ROLLED_BACK" -> rolledBackScripts.add(scriptName);
                }
            }

            status.setExecutedScriptNames(executedScripts);
            status.setFailedScriptNames(failedScripts);
            status.setRolledBackScriptNames(rolledBackScripts);

            // Query failed scripts view for detailed failure information
            if (!failedScripts.isEmpty()) {
                String failedSql = """
                        SELECT script_name, error_message, execution_time
                        FROM failed_scripts
                        ORDER BY execution_time DESC
                        """;

                var failedResults = auditRepository.getJdbcTemplate().queryForList(failedSql);
                // Failed scripts are already populated above
            }

        } catch (Exception e) {
            log.error("Error populating script status", e);
            status.setExecutedScriptNames(new java.util.ArrayList<>());
            status.setFailedScriptNames(new java.util.ArrayList<>());
            status.setRolledBackScriptNames(new java.util.ArrayList<>());
        }
    }

    private void populateLockStatus(DatabaseStatus status) {
        try {
            // Check database lock table
            String lockSql = """
                    SELECT lock_owner, lock_acquired_at, lock_expires_at, is_active
                    FROM database_lock
                    WHERE is_active = 1
                    ORDER BY lock_acquired_at DESC
                    LIMIT 1
                    """;

            try {
                var lockResults = auditRepository.getJdbcTemplate().queryForList(lockSql);
                if (!lockResults.isEmpty()) {
                    var lockResult = lockResults.get(0);
                    status.setDeploymentInProgress(true);
                    status.setDeploymentLockOwner((String) lockResult.get("lock_owner"));
                    status.setDeploymentLockAcquiredAt(parseDateTime((String) lockResult.get("lock_acquired_at")));
                    status.setDeploymentLockExpiresAt(parseDateTime((String) lockResult.get("lock_expires_at")));
                } else {
                    status.setDeploymentInProgress(false);
                    status.setDeploymentLockOwner("");
                }
            } catch (Exception e) {
                log.debug("No active deployment lock found", e);
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

            try {
                String versionSql = auditRepository.getJdbcTemplate().queryForObject(
                        "SELECT sqlite_version()", String.class);
                status.setDatabaseVersion(versionSql);
                status.setDatabaseHealthy(true);
            } catch (Exception e) {
                // For non-SQLite databases, try a generic query
                try {
                    auditRepository.getJdbcTemplate().queryForObject("SELECT 1", Integer.class);
                    status.setDatabaseVersion("Unknown");
                    status.setDatabaseHealthy(true);
                } catch (Exception ex) {
                    status.setDatabaseHealthy(false);
                    status.setDatabaseHealthMessage("Database health check failed");
                }
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
            // Check if audit tables exist and are accessible
            String tableCheckSql = """
                    SELECT COUNT(*) as table_count
                    FROM sqlite_master
                    WHERE type='table' AND name IN ('db_change_log', 'deployment_tags', 'database_lock')
                    """;

            try {
                Integer tableCount = auditRepository.getJdbcTemplate().queryForObject(tableCheckSql, Integer.class);
                status.setConfigurationValid(tableCount != null && tableCount >= 3);
                status.setConfigurationMessage(tableCount != null && tableCount >= 3 ?
                        "Audit tables present" : "Missing audit tables");
            } catch (Exception e) {
                // For non-SQLite databases
                try {
                    auditRepository.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM db_change_log", Integer.class);
                    status.setConfigurationValid(true);
                    status.setConfigurationMessage("Audit tables accessible");
                } catch (Exception ex) {
                    status.setConfigurationValid(false);
                    status.setConfigurationMessage("Audit tables not accessible");
                }
            }
        } catch (Exception e) {
            log.error("Error populating configuration status", e);
            status.setConfigurationValid(false);
            status.setConfigurationMessage("Configuration check failed");
        }
    }

    private void populateDeploymentHistory(DatabaseStatus status) {
        try {
            // Query recent deployment history from views
            String historySql = """
                    SELECT dt.tag_name, dt.description, dt.deployment_time, dt.created_by,
                           COUNT(dcl.id) as script_count,
                           CASE 
                               WHEN COUNT(CASE WHEN dcl.execution_status = 'FAILED' THEN 1 END) > 0 THEN 'FAILED'
                               WHEN COUNT(CASE WHEN dcl.execution_status = 'ROLLED_BACK' THEN 1 END) > 0 THEN 'ROLLED_BACK'
                               ELSE 'SUCCESS'
                           END as status
                    FROM deployment_tags dt
                    LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
                    GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
                    ORDER BY dt.deployment_time DESC
                    LIMIT 10
                    """;

            var historyResults = auditRepository.getJdbcTemplate().queryForList(historySql);
            java.util.List<DatabaseStatus.DeploymentHistoryEntry> history = new java.util.ArrayList<>();

            for (var row : historyResults) {
                DatabaseStatus.DeploymentHistoryEntry entry = new DatabaseStatus.DeploymentHistoryEntry();
                entry.setTagName((String) row.get("tag_name"));
                entry.setDescription((String) row.get("description"));
                entry.setDeploymentTime(parseDateTime((String) row.get("deployment_time")));
                entry.setDeployedBy((String) row.get("created_by"));
                entry.setScriptCount(((Number) row.get("script_count")).intValue());
                entry.setStatus((String) row.get("status"));
                history.add(entry);
            }

            status.setRecentDeployments(history);

        } catch (Exception e) {
            log.error("Error populating deployment history", e);
            status.setRecentDeployments(new java.util.ArrayList<>());
        }
    }

    private java.time.LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // Try standard ISO format first
            return java.time.LocalDateTime.parse(dateTimeStr);
        } catch (java.time.format.DateTimeParseException e1) {
            try {
                // Try SQLite datetime format: "YYYY-MM-DD HH:MM:SS"
                java.time.format.DateTimeFormatter sqliteFormatter =
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return java.time.LocalDateTime.parse(dateTimeStr, sqliteFormatter);
            } catch (Exception e2) {
                log.debug("Failed to parse datetime: {}", dateTimeStr, e2);
                return null;
            }
        } catch (Exception e) {
            log.debug("Failed to parse datetime: {}", dateTimeStr, e);
            return null;
        }
    }

    private DeploymentTag getCurrentDeploymentTag() throws Exception {
        return null;
    }

    private List<ChangeLogEntry> getRecentExecutions(int limit) throws Exception {
        return List.of();
    }
}