package org.jerish.dbdeploy.service;

import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.entity.ScriptExecutionStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.changelog.ChangeLogManager;
import org.jerish.dbdeploy.script.ScriptExecutionManager;
import org.jerish.dbdeploy.entity.ScriptFileContent;
import org.jerish.dbdeploy.script.ScriptExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DefaultDatabaseDeployService implements DatabaseDeployService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditRepository auditRepository;
    private final ChangeLogManager changeLogManager;
    private final ScriptExecutionManager scriptExecutionManager;
    private final ScriptExecutor scriptExecutorV2;
    
    @Autowired
    @Qualifier("nodeJdbcTemplateMap")
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;
    
    @Autowired
    public DefaultDatabaseDeployService(
            JdbcTemplate jdbcTemplate,
            AuditRepository auditRepository,
            ChangeLogManager changeLogManager,
            ScriptExecutionManager scriptExecutionManager,
            ScriptExecutor scriptExecutorV2) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditRepository = auditRepository;
        this.changeLogManager = changeLogManager;
        this.scriptExecutionManager = scriptExecutionManager;
        this.scriptExecutorV2 = scriptExecutorV2;
    }

    @Override
    public void deploy(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception {
        deploy(changeLogConfig, dryRun, null);
    }

    @Override
    public void deploy(ChangeLogConfig changeLogConfig, boolean dryRun, Map<String, String> parameters) throws Exception {
        log.info("Starting deployment");

        String lockOwner = "deploy-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
            // Get pending scripts using ChangeLogManager
            List<ScriptFileContent> pendingScripts = changeLogManager.determinePendingScripts(
                    changeLogConfig, parameters != null ? parameters : Map.of());

            if (pendingScripts.isEmpty()) {
                log.info("No pending scripts to deploy. Database is already up to date.");
                return;
            }

            log.info("Found {} pending scripts to deploy", pendingScripts.size());

            // Create a map of script name to ScriptConfig for easy lookup
            Map<String, ScriptConfig> scriptConfigMap = changeLogConfig.getScripts().stream()
                    .collect(Collectors.toMap(ScriptConfig::getName, config -> config));

            // Execute each pending script
            for (ScriptFileContent script : pendingScripts) {
                if (dryRun) {
                    log.info("[DRY RUN] Would execute script: {}", script.getName());
                    continue;
                }

                log.info("Executing script: {}", script.getName());
                ScriptConfig scriptConfig = scriptConfigMap.get(script.getName());
                executeScriptWithAudit(script, scriptConfig);
            }

            log.info("Deployment completed successfully");

        } finally {
            if (!dryRun) {
                auditRepository.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }

    private void executeScriptWithAudit(ScriptFileContent script, ScriptConfig scriptConfig) throws Exception {
        ChangeLogEntry entry = new ChangeLogEntry();
        entry.setScriptName(script.getName());
        entry.setRollbackScriptContent(script.getRollbackContent());
        entry.setRollbackVerifyScriptContent(script.getRollbackVerificationContent());
        entry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        entry.setExecutionTime(LocalDateTime.now());
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        // Calculate checksum from the script content
        String checksum = scriptExecutorV2.calculateChecksum(script.getApplyContent());
        entry.setScriptChecksum(checksum);

        ScriptExecutor.ScriptExecutionResult result = null;
        ScriptExecutionManager.MultiNodeExecutionResult multiNodeResult = null;
        boolean success = false;
        String errorMessage = null;

        try {
            // Check if this is a multi-node script
            if (scriptConfig != null && scriptConfig.isMultiNode()) {
                // Store the original target nodes from the script config
                entry.setTargetNodes(scriptConfig.getNodes());

                // Resolve target nodes for execution
                List<String> executionTargetNodes = determineTargetNodes(scriptConfig);

                // Execute on multiple nodes in parallel
                multiNodeResult = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                        script.getApplyContent(),
                        script.getApplyVerificationContent(),
                        executionTargetNodes,
                        script.getName());

                if (multiNodeResult.isSuccess()) {
                    entry.setExecutionDurationMs(multiNodeResult.getDuration());
                    entry.setNodeExecutionDetails(
                            serializeNodeResults(multiNodeResult.getNodeResults(), executionTargetNodes));
                    success = true;
                    log.info("Multi-node script {} executed successfully on {} nodes",
                            script.getName(), executionTargetNodes.size());
                } else {
                    errorMessage = multiNodeResult.getErrorMessage();
                    entry.setNodeExecutionDetails(
                            serializeNodeResults(multiNodeResult.getNodeResults(), executionTargetNodes));
                }
            } else {
                // Single-node execution
                result = scriptExecutionManager.executeAndVerify(
                        script.getApplyContent(),
                        script.getApplyVerificationContent(),
                        script.getName());

                if (result.isSuccess()) {
                    entry.setExecutionDurationMs(result.getDuration());
                    success = true;
                    log.info("Script {} executed and verified successfully", script.getName());
                } else {
                    errorMessage = result.getErrorMessage();
                }
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        // Record audit result in a separate transaction
        if (!success) {
            entry.setExecutionStatus(ScriptExecutionStatus.FAILED);
            entry.setErrorMessage(errorMessage);
            recordScriptExecutionWithNewTransaction(entry);
            throw new RuntimeException("Script execution failed: " + errorMessage);
        } else {
            recordScriptExecutionWithNewTransaction(entry);
        }
    }

    /**
     * Record script execution in a new transaction to ensure audit records are committed
     * even when the script execution transaction is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void recordScriptExecutionWithNewTransaction(ChangeLogEntry entry) {
        auditRepository.recordScriptExecution(entry);
    }

    /**
     * Determine the target nodes for a script based on its configuration
     */
    private List<String> determineTargetNodes(ScriptConfig scriptConfig) {
        if (scriptConfig.isAllNodes()) {
            // Return all configured nodes
            log.debug("All node keys in map: {}", nodeJdbcTemplateMap.keySet());
            List<String> allNodes = new ArrayList<>(nodeJdbcTemplateMap.keySet());
            log.debug("Determined target nodes for ALL: {}", allNodes);
            return allNodes;
        } else {
            // Return specific nodes
            log.debug("Determined target nodes for specific: {}", scriptConfig.getNodes());
            return scriptConfig.getNodes();
        }
    }

    /**
     * Serialize node execution results to JSON format
     */
    private String serializeNodeResults(List<ScriptExecutor.ScriptExecutionResult> nodeResults, List<String> nodeNames) {
        if (nodeResults == null || nodeResults.isEmpty() || nodeNames == null || nodeNames.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(nodeResults.size(), nodeNames.size()); i++) {
            ScriptExecutor.ScriptExecutionResult result = nodeResults.get(i);
            String nodeName = nodeNames.get(i);
            sb.append("{\"nodeName\":\"").append(nodeName).append("\"")
                    .append(",\"success\":").append(result.isSuccess())
                    .append(",\"durationMs\":").append(result.getDuration())
                    .append(",\"errorMessage\":\"").append(result.getErrorMessage() != null ? result.getErrorMessage().replace("\"", "'") : "")
                    .append("\"}");
            if (i < Math.min(nodeResults.size(), nodeNames.size()) - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void rollback(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception {
        rollback(changeLogConfig, dryRun, null);
    }

    @Override
    public void rollback(ChangeLogConfig changeLogConfig, boolean dryRun, Map<String, String> parameters) throws Exception {
        log.info("Starting rollback");

        String lockOwner = "rollback-" + System.currentTimeMillis();

        if (!dryRun) {
            boolean lockAcquired = auditRepository.acquireLock("db_deploy_tool", lockOwner, 30);
            if (!lockAcquired) {
                throw new RuntimeException("Failed to acquire deployment lock. Another deployment may be in progress.");
            }
        }

        try {
            // Use ChangeLogManager to determine which scripts need to be rolled back
            // The rollback content is already saved in the database with parameters replaced,
            // so no need to replace parameters again
            List<ChangeLogEntry> scriptsToRollback = changeLogManager.determineRollbackScripts(changeLogConfig);

            if (scriptsToRollback.isEmpty()) {
                log.info("No scripts to rollback. Database is already at the target state");
                return;
            }

            log.info("Found {} scripts to rollback", scriptsToRollback.size());

            // Execute rollback scripts in reverse order (already handled by ChangeLogManager)
            for (ChangeLogEntry entry : scriptsToRollback) {
                if (dryRun) {
                    log.info("[DRY RUN] Would rollback script: {}", entry.getScriptName());
                    continue;
                }

                log.info("Rolling back script: {}", entry.getScriptName());

                // Execute rollback script if available
                if (entry.getRollbackScriptContent() != null && !entry.getRollbackScriptContent().isEmpty()) {
                    // Check if this was a multi-node script (targetNodes is not null)
                    if (entry.getTargetNodes() != null && !entry.getTargetNodes().isEmpty()) {
                        // Execute rollback on all target nodes
                        List<String> targetNodes = entry.getTargetNodes();
                        if (targetNodes.contains("ALL")) {
                            // Execute on all configured nodes
                            targetNodes = new ArrayList<>(nodeJdbcTemplateMap.keySet());
                        }

                        log.info("Executing multi-node rollback on {} nodes: {}", targetNodes.size(), targetNodes);
                        ScriptExecutionManager.MultiNodeExecutionResult rollbackResult =
                                scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                                        entry.getRollbackScriptContent(),
                                        entry.getRollbackVerifyScriptContent(),
                                        targetNodes,
                                        entry.getScriptName());

                        if (!rollbackResult.isSuccess()) {
                            throw new RuntimeException("Multi-node rollback failed: " + rollbackResult.getErrorMessage());
                        }
                    } else {
                        // Single-node rollback
                        ScriptExecutor.ScriptExecutionResult result =
                                scriptExecutionManager.executeAndVerify(
                                        entry.getRollbackScriptContent(),
                                        entry.getRollbackVerifyScriptContent(),
                                        entry.getScriptName());

                        if (!result.isSuccess()) {
                            throw new RuntimeException("Rollback execution failed: " + result.getErrorMessage());
                        }
                    }
                } else {
                    log.warn("No rollback script available for: {}", entry.getScriptName());
                }

                // Record the rollback as a new audit entry
                ChangeLogEntry rollbackEntry = new ChangeLogEntry();
                rollbackEntry.setScriptName(entry.getScriptName());
                rollbackEntry.setScriptChecksum(entry.getScriptChecksum());
                rollbackEntry.setExecutionStatus(ScriptExecutionStatus.ROLLED_BACK);
                rollbackEntry.setExecutionTime(LocalDateTime.now());
                rollbackEntry.setExecutionDurationMs(0L);
                rollbackEntry.setRollbackScriptContent(entry.getRollbackScriptContent());
                rollbackEntry.setRollbackVerifyScriptContent(entry.getRollbackVerifyScriptContent());
                rollbackEntry.setParentAuditId(entry.getId());
                rollbackEntry.setCreatedAt(LocalDateTime.now());
                rollbackEntry.setUpdatedAt(LocalDateTime.now());

                // Copy multi-node fields if applicable
                rollbackEntry.setTargetNodes(entry.getTargetNodes());

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
}