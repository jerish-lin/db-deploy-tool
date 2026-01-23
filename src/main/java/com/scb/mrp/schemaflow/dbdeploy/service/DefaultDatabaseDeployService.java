package com.scb.mrp.schemaflow.dbdeploy.service;

import com.scb.mrp.schemaflow.dbdeploy.changelog.ChangeLogManager;
import com.scb.mrp.schemaflow.dbdeploy.entity.*;
import com.scb.mrp.schemaflow.dbdeploy.repository.AuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.script.ScriptExecutionManager;
import com.scb.mrp.schemaflow.dbdeploy.script.ScriptExecutor;
import lombok.extern.slf4j.Slf4j;
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
    private final ScriptExecutor scriptExecutor;

    @Autowired
    @Qualifier("nodeJdbcTemplateMap")
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    @Autowired
    public DefaultDatabaseDeployService(
            JdbcTemplate jdbcTemplate,
            AuditRepository auditRepository,
            ChangeLogManager changeLogManager,
            ScriptExecutionManager scriptExecutionManager,
            ScriptExecutor scriptExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditRepository = auditRepository;
        this.changeLogManager = changeLogManager;
        this.scriptExecutionManager = scriptExecutionManager;
        this.scriptExecutor = scriptExecutor;
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
        // Create script metadata
        ChangeLogScript metadata = new ChangeLogScript();
        metadata.setScriptName(script.getName());
        metadata.setRollbackScriptContent(script.getRollbackContent());
        metadata.setRollbackVerifyScriptContent(script.getRollbackVerificationContent());
        metadata.setCreatedAt(LocalDateTime.now());

        // Calculate checksum from the script content
        String checksum = scriptExecutor.calculateChecksum(script.getApplyContent());
        metadata.setScriptChecksum(checksum);

        // Store target nodes if multi-node
        if (scriptConfig != null && scriptConfig.isMultiNode()) {
            metadata.setTargetNodes(scriptConfig.getNodes());
        }

        // Create audit entry
        ChangeLogAuditEntry auditEntry = new ChangeLogAuditEntry();
        auditEntry.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        auditEntry.setExecutionTime(LocalDateTime.now());
        auditEntry.setCreatedAt(LocalDateTime.now());

        ScriptExecutor.ScriptExecutionResult result = null;
        ScriptExecutionManager.MultiNodeExecutionResult multiNodeResult = null;
        boolean success = false;
        String errorMessage = null;

        try {
            // Check if this is a multi-node script
            if (scriptConfig != null && scriptConfig.isMultiNode()) {
                // Resolve target nodes for execution
                List<String> executionTargetNodes = determineTargetNodes(scriptConfig);

                // Execute on multiple nodes in parallel
                multiNodeResult = scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                        script.getApplyContent(),
                        script.getApplyVerificationContent(),
                        executionTargetNodes,
                        script.getName());

                if (multiNodeResult.isSuccess()) {
                    auditEntry.setExecutionDurationMs(multiNodeResult.getDuration());
                    auditEntry.setNodeExecutionDetails(
                            serializeNodeResults(multiNodeResult.getNodeResults(), executionTargetNodes));
                    success = true;
                    log.info("Multi-node script {} executed successfully on {} nodes",
                            script.getName(), executionTargetNodes.size());
                } else {
                    errorMessage = multiNodeResult.getErrorMessage();
                    auditEntry.setNodeExecutionDetails(
                            serializeNodeResults(multiNodeResult.getNodeResults(), executionTargetNodes));
                }
            } else {
                // Single-node execution
                result = scriptExecutionManager.executeAndVerify(
                        script.getApplyContent(),
                        script.getApplyVerificationContent(),
                        script.getName());

                if (result.isSuccess()) {
                    auditEntry.setExecutionDurationMs(result.getDuration());
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
            auditEntry.setExecutionStatus(ScriptExecutionStatus.FAILED);
            auditEntry.setErrorMessage(errorMessage);
            recordScriptExecutionWithNewTransaction(metadata, auditEntry);
            throw new RuntimeException("Script execution failed: " + errorMessage);
        } else {
            recordScriptExecutionWithNewTransaction(metadata, auditEntry);
        }
    }

    /**
     * Record script execution in a new transaction to ensure audit records are committed
     * even when the script execution transaction is rolled back.
     * Uses createScriptMetadata and createScriptAuditEntry directly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void recordScriptExecutionWithNewTransaction(ChangeLogScript metadata, ChangeLogAuditEntry auditEntry) {
        Long scriptId = auditRepository.createScriptMetadata(metadata);
        auditEntry.setScriptId(scriptId);
        auditRepository.createScriptAuditEntry(auditEntry);
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
            List<ChangeLogScript> scriptsToRollback = changeLogManager.determineRollbackScripts(changeLogConfig);

            if (scriptsToRollback.isEmpty()) {
                log.info("No scripts to rollback. Database is already at the target state");
                return;
            }

            log.info("Found {} scripts to rollback", scriptsToRollback.size());

            // Execute rollback scripts in reverse order (already handled by ChangeLogManager)
            for (ChangeLogScript metadata : scriptsToRollback) {
                if (dryRun) {
                    log.info("[DRY RUN] Would rollback script: {}", metadata.getScriptName());
                    continue;
                }

                log.info("Rolling back script: {}", metadata.getScriptName());

                // Execute rollback script if available
                if (metadata.getRollbackScriptContent() != null && !metadata.getRollbackScriptContent().isEmpty()) {
                    // Check if this was a multi-node script (targetNodes is not null)
                    if (metadata.getTargetNodes() != null && !metadata.getTargetNodes().isEmpty()) {
                        // Execute rollback on all target nodes
                        List<String> targetNodes = metadata.getTargetNodes();
                        if (targetNodes.contains("ALL")) {
                            // Execute on all configured nodes
                            targetNodes = new ArrayList<>(nodeJdbcTemplateMap.keySet());
                        }

                        log.info("Executing multi-node rollback on {} nodes: {}", targetNodes.size(), targetNodes);
                        ScriptExecutionManager.MultiNodeExecutionResult rollbackResult =
                                scriptExecutionManager.executeAndVerifyOnMultipleNodes(
                                        metadata.getRollbackScriptContent(),
                                        metadata.getRollbackVerifyScriptContent(),
                                        targetNodes,
                                        metadata.getScriptName());

                        if (!rollbackResult.isSuccess()) {
                            throw new RuntimeException("Multi-node rollback failed: " + rollbackResult.getErrorMessage());
                        }
                    } else {
                        // Single-node rollback
                        ScriptExecutor.ScriptExecutionResult result =
                                scriptExecutionManager.executeAndVerify(
                                        metadata.getRollbackScriptContent(),
                                        metadata.getRollbackVerifyScriptContent(),
                                        metadata.getScriptName());

                        if (!result.isSuccess()) {
                            throw new RuntimeException("Rollback execution failed: " + result.getErrorMessage());
                        }
                    }
                } else {
                    log.warn("No rollback script available for: {}", metadata.getScriptName());
                }

                // Create audit entry
                ChangeLogAuditEntry changeLogAuditEntry = new ChangeLogAuditEntry();
                changeLogAuditEntry.setScriptId(metadata.getId());
                changeLogAuditEntry.setExecutionStatus(ScriptExecutionStatus.ROLLED_BACK);
                changeLogAuditEntry.setExecutionTime(LocalDateTime.now());
                changeLogAuditEntry.setExecutionDurationMs(0L);
                changeLogAuditEntry.setNodeExecutionDetails(null);
                changeLogAuditEntry.setCreatedAt(LocalDateTime.now());

                auditRepository.createScriptAuditEntry(changeLogAuditEntry);

                log.info("Script {} rolled back successfully", metadata.getScriptName());
            }

            log.info("Rollback completed successfully");

        } finally {
            if (!dryRun) {
                auditRepository.releaseLock("db_deploy_tool", lockOwner);
            }
        }
    }
}