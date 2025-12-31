package org.jerish.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.script.ScriptExecutor;
import org.jerish.dbdeploy.script.ScriptFileManager;
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
    private final AuditRepository auditDao;
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
            boolean lockAcquired = auditDao.acquireLock("db_deploy_tool", lockOwner, 30);
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

                List<DeploymentTag> existingTags = auditDao.getDeploymentTags();
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
                if (auditDao.isScriptExecuted(scriptNameForDb)) {
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
                auditDao.releaseLock("db_deploy_tool", lockOwner);
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
                auditDao.recordScriptExecution(entry);
                log.info("Script {} executed and verified successfully", script.getName());
            } else {
                throw new RuntimeException(result.getErrorMessage());
            }
        } catch (Exception e) {
            entry.setExecutionStatus(ScriptExecutionStatus.FAILED);
            entry.setErrorMessage(e.getMessage());
            auditDao.recordScriptExecution(entry);
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

        auditDao.createDeploymentTag(tag);
    }

    private void createInitialTag() throws Exception {
        DeploymentTag tag = new DeploymentTag();
        tag.setTagName("initial");
        tag.setDescription("Initial state - before any changesets applied");


        tag.setDeploymentTime(LocalDateTime.now());
        tag.setCreatedBy("db-deploy-tool");
        tag.setIsActive(true);

        auditDao.createDeploymentTag(tag);

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

        auditDao.recordScriptExecution(initialEntry);

        log.info("Created initial tag and changelog entry for rollback capability");
    }

    @Override
    public void rollback(String targetTagName, boolean dryRun) throws Exception {
        log.info("Starting rollback to tag: {}", targetTagName);

        String lockOwner = "rollback-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditDao.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
            // Get the target deployment tag
            DeploymentTag targetTag = auditDao.getDeploymentTag(targetTagName);
            if (targetTag == null) {
                throw new RuntimeException("Target tag '" + targetTagName + "' not found");
            }

            // Get all scripts executed after the target tag
            List<ChangeLogEntry> scriptsToRollback = auditDao.getScriptsExecutedAfter(targetTagName);

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
                        ScriptExecutor.VerificationResult verificationResult = scriptExecutor.executeVerification(
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
                auditDao.updateScriptExecutionStatus(entry.getId(), ScriptExecutionStatus.ROLLED_BACK, null);

                log.info("Script {} rolled back successfully", entry.getScriptName());
            }

            // Deactivate all deployment tags that were rolled back
            if (!dryRun) {
                // Get all tags and deactivate those that were rolled back
                List<DeploymentTag> allTags = auditDao.getDeploymentTags();

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
                        auditDao.deactivateDeploymentTag(tag.getTagName());
                        log.info("Deactivated deployment tag: {}", tag.getTagName());
                    }
                }
            }

            log.info("Rollback to tag '{}' completed successfully", targetTagName);

        } finally {
            if (!dryRun) {
                auditDao.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    @Override
    public void showStatus() throws Exception {
        log.info("=== Database Deployment Status ===");

        // Get the latest deployment tag from active deployment tags
        List<DeploymentTag> activeTags = auditDao.getDeploymentTags();
        String currentTag = null;

        // Find the first active tag (tags are ordered by deployment_time DESC)
        for (DeploymentTag tag : activeTags) {
            if (tag.getIsActive()) {
                currentTag = tag.getTagName();
                break;
            }
        }

        if (currentTag != null) {
            log.info("Current deployment tag: {}", currentTag);
        } else {
            log.info("No deployment tag found");
        }

        // Get all script executions
        List<ChangeLogEntry> recentExecutions = auditDao.getScriptsExecutedAfter("");

        if (recentExecutions.isEmpty()) {
            log.info("No scripts have been executed yet");
            return;
        }

        // Count executions by status
        long successCount = 0;
        long failedCount = 0;
        long rolledBackCount = 0;

        for (ChangeLogEntry entry : recentExecutions) {
            if (entry.getExecutionStatus() == ScriptExecutionStatus.SUCCESS) {
                successCount++;
            } else if (entry.getExecutionStatus() == ScriptExecutionStatus.FAILED) {
                failedCount++;
            } else if (entry.getExecutionStatus() == ScriptExecutionStatus.ROLLED_BACK) {
                rolledBackCount++;
            }
        }

        log.info("Total scripts executed: {}", recentExecutions.size());
        log.info("Successful: {}", successCount);
        log.info("Failed: {}", failedCount);
        log.info("Rolled back: {}", rolledBackCount);
    }

    private DeploymentTag getCurrentDeploymentTag() throws Exception {
        return null;
    }

    private List<ChangeLogEntry> getRecentExecutions(int limit) throws Exception {
        return List.of();
    }
}