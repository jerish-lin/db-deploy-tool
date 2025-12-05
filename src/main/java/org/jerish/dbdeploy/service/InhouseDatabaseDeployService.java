package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.config.ChangeLogConfig;
import org.jerish.dbdeploy.dao.AuditDao;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.script.ScriptExecutor;
import org.jerish.dbdeploy.script.ScriptFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

public class InhouseDatabaseDeployService implements DatabaseDeployService {
    private static final Logger logger = LoggerFactory.getLogger(InhouseDatabaseDeployService.class);

    private final DatabaseConnectionManager connectionManager;
    private final AuditDao auditDao;
    private final ScriptExecutor scriptExecutor;
    private final ScriptFileManager scriptFileManager;

    public InhouseDatabaseDeployService(DatabaseConnectionManager connectionManager,
                                        AuditDao auditDao,
                                        String scriptBasePath) {
        this.connectionManager = connectionManager;
        this.auditDao = auditDao;
        this.scriptExecutor = new ScriptExecutor(connectionManager);
        this.scriptFileManager = new ScriptFileManager(scriptBasePath);
    }

    @Override
    public void deploy(ChangeLogConfig changeLogConfig, String tagName, String buildVersion,
                       String environment, boolean dryRun) throws Exception {

        logger.info("Starting deployment with tag: {}", tagName);

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
                try (Connection conn = connectionManager.getConnection()) {
                    String dbUrl = conn.getMetaData().getURL();
                    logger.info("Database URL: {}", dbUrl);
                }

                List<DeploymentTag> existingTags = auditDao.getDeploymentTags();
                logger.info("Found {} existing tags", existingTags.size());
                for (DeploymentTag tag : existingTags) {
                    logger.info("Existing tag: {}", tag.getTagName());
                }
                if (existingTags.isEmpty()) {
                    isFirstDeployment = true;
                    logger.info("First deployment detected, creating initial tag");
                    createInitialTag();
                }
            }

            // Load all scripts from changelog
            List<ScriptFileManager.ScriptFile> scripts = scriptFileManager.loadScripts(changeLogConfig.getScripts());

            for (ScriptFileManager.ScriptFile script : scripts) {
                if (dryRun) {
                    logger.info("[DRY RUN] Would execute script: {}", script.getId());
                    continue;
                }

                // Check if script was already executed
                if (auditDao.isScriptExecuted(script.getId())) {
                    logger.info("Skipping already executed script: {}", script.getId());
                    continue;
                }

                logger.info("Executing script: {}", script.getId());
                executeScriptWithAudit(script, tagName);
            }

            // Create deployment tag
            if (!dryRun) {
                createDeploymentTag(tagName, buildVersion, environment);
            }

            logger.info("Deployment completed successfully with tag: {}", tagName);

        } finally {
            if (!dryRun) {
                auditDao.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    private void executeScriptWithAudit(ScriptFileManager.ScriptFile script, String tagName) throws Exception {
        ChangeLogEntry entry = new ChangeLogEntry();
        entry.setScriptId(script.getId());
        entry.setScriptName(script.getScriptName());
        entry.setScriptPath(script.getApplyPath());
        entry.setRollbackScriptPath(script.getRollbackPath());
        entry.setRollbackScriptContent(script.getRollbackContent());
        entry.setTagName(tagName);
        entry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        // Calculate checksum from the script content that's already loaded
        String checksum = scriptExecutor.calculateChecksum(script.getApplyContent());
        entry.setScriptChecksum(checksum);

        try {
            // Execute script with verification
            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScript(script.getApplyPath(), script.getId());

            if (result.isSuccess()) {
                entry.setExecutionDurationMs(result.getDuration());
                
                // Run verification after successful execution
                String verificationPath = script.getApplyPath().replace(".apply.sql", ".apply.verify.sql");
                try {
                    if (Files.exists(Paths.get(verificationPath))) {
                        ScriptExecutor.VerificationResult verificationResult = scriptExecutor.executeVerification(verificationPath);
                        
                        // Log verification results
                        logger.info(" === VERIFICATION RESULTS ===");
                        logger.info("Script: {}", script.getId());
                        logger.info("Verification Status: {}", verificationResult.isSuccess() ? "SUCCESS" : "FAILED");
                        logger.info("Duration: {}ms", verificationResult.getDuration());
                        logger.info("Output:");
                        logger.info("{}", verificationResult.getOutput());
                        
                        if (!verificationResult.isSuccess()) {
                            logger.error("Error: {}", verificationResult.getErrorMessage());
                        }
                        logger.info("=== END VERIFICATION ===");
                        
                        // If verification fails, mark as failed
                        if (!verificationResult.isSuccess()) {
                            throw new RuntimeException("Verification failed for script: " + script.getId());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not check for verification script: {}", e.getMessage());
                }
                
                auditDao.recordScriptExecution(entry);
                logger.info("Script {} executed and verified successfully", script.getId());
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

    private void createDeploymentTag(String tagName, String buildVersion, String environment) throws Exception {
        DeploymentTag tag = new DeploymentTag();
        tag.setTagName(tagName);
        tag.setDescription("Deployment tag created by db-deploy-tool");
        tag.setBuildVersion(buildVersion);
        tag.setEnvironment(environment);
        tag.setDeploymentTime(LocalDateTime.now());
        tag.setCreatedBy("db-deploy-tool");
        tag.setIsActive(true);

        auditDao.createDeploymentTag(tag);
    }

    private void createInitialTag() throws Exception {
        DeploymentTag tag = new DeploymentTag();
        tag.setTagName("initial");
        tag.setDescription("Initial state - before any changesets applied");
        tag.setBuildVersion("0.0.0");
        tag.setEnvironment("initial");
        tag.setDeploymentTime(LocalDateTime.now());
        tag.setCreatedBy("db-deploy-tool");
        tag.setIsActive(true);

        auditDao.createDeploymentTag(tag);

        // Create a fake changelog entry for the initial state
        ChangeLogEntry initialEntry = new ChangeLogEntry();
        initialEntry.setScriptId("initial-state");
        initialEntry.setScriptName("Initial Database State");
        initialEntry.setScriptPath("N/A");
        initialEntry.setScriptChecksum("initial");
        initialEntry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        initialEntry.setExecutionTime(LocalDateTime.now());
        initialEntry.setExecutionDurationMs(0L);
        initialEntry.setRollbackScriptPath("N/A");
        initialEntry.setRollbackScriptContent("-- Initial state - no rollback needed");
        initialEntry.setTagName("initial");
        initialEntry.setCreatedAt(LocalDateTime.now());
        initialEntry.setUpdatedAt(LocalDateTime.now());

        auditDao.recordScriptExecution(initialEntry);

        logger.info("Created initial tag and changelog entry for rollback capability");
    }

    @Override
    public void rollback(String targetTagName, boolean dryRun) throws Exception {
        logger.info("Starting rollback to tag: {}", targetTagName);

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
                logger.info("No scripts to rollback. Database is already at tag: {}", targetTagName);
                return;
            }

            logger.info("Found {} scripts to rollback", scriptsToRollback.size());

            // Execute rollback scripts in reverse order
            for (int i = 0; i < scriptsToRollback.size(); i++) {
                ChangeLogEntry entry = scriptsToRollback.get(i);

                if (dryRun) {
                    logger.info("[DRY RUN] Would rollback script: {}", entry.getScriptId());
                    continue;
                }

                logger.info("Rolling back script: {}", entry.getScriptId());

                // Execute rollback script if available
                if (entry.getRollbackScriptContent() != null && !entry.getRollbackScriptContent().isEmpty()) {
                    scriptExecutor.executeScriptContent(entry.getRollbackScriptContent());
                } else if (entry.getRollbackScriptPath() != null && !entry.getRollbackScriptPath().isEmpty()) {
                    String rollbackScriptPath = entry.getRollbackScriptPath();
                    if (!rollbackScriptPath.startsWith("/") && !rollbackScriptPath.contains(":")) {
                        // Relative path, combine with script base path
                        rollbackScriptPath = scriptFileManager.getScriptBasePath() + "/" + rollbackScriptPath;
                    }
                    scriptExecutor.executeScript(rollbackScriptPath);
                } else {
                    System.out.println("WARNING: No rollback script available for: " + entry.getScriptId());
                }

                // Update the execution status to ROLLED_BACK
                auditDao.updateScriptExecutionStatus(entry.getId(), ScriptExecutionStatus.ROLLED_BACK, null);

                logger.info("Script {} rolled back successfully", entry.getScriptId());
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
                        logger.info("Deactivated deployment tag: {}", tag.getTagName());
                    }
                }
            }

            logger.info("Rollback to tag '{}' completed successfully", targetTagName);

        } finally {
            if (!dryRun) {
                auditDao.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    @Override
    public void showStatus() throws Exception {
        logger.info("=== Database Deployment Status ===");

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
            logger.info("Current deployment tag: {}", currentTag);
        } else {
            logger.info("No deployment tag found");
        }

        // Get all script executions
        List<ChangeLogEntry> recentExecutions = auditDao.getScriptsExecutedAfter("");

        if (recentExecutions.isEmpty()) {
            logger.info("No scripts have been executed yet");
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

        logger.info("Total scripts executed: {}", recentExecutions.size());
        logger.info("Successful: {}", successCount);
        logger.info("Failed: {}", failedCount);
        logger.info("Rolled back: {}", rolledBackCount);
    }

    private DeploymentTag getCurrentDeploymentTag() throws Exception {
        return null;
    }

    private List<ChangeLogEntry> getRecentExecutions(int limit) throws Exception {
        return List.of();
    }
}