package org.jerish.dbdeploy.script;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manager for script execution with verification support.
 * Supports single-node and multi-node execution scenarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptExecutionManager {

    private final ScriptExecutor scriptExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("nodeJdbcTemplateMap")
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    /**
     * Execute SQL content and optionally run verification SQL.
     *
     * @param sqlContent The SQL content to execute
     * @param sqlVerificationContent The verification SQL content (can be null)
     * @param sqlName The name of the SQL for logging
     * @return ScriptExecutionResult containing execution details
     */
    public ScriptExecutor.ScriptExecutionResult executeAndVerify(
            String sqlContent,
            String sqlVerificationContent,
            String sqlName){
        return executeAndVerify(jdbcTemplate, sqlContent, sqlVerificationContent, sqlName);
    }


    public ScriptExecutor.ScriptExecutionResult executeAndVerify(
            JdbcTemplate jdbcTemplate,
            String sqlContent,
            String sqlVerificationContent,
            String sqlName) {

        log.info("Executing SQL: {}", sqlName);

        // Execute the main SQL
        ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(jdbcTemplate, sqlContent);

        if (!result.isSuccess()) {
            log.error("SQL execution failed for: {}", sqlName);
            return result;
        }

        // Execute verification SQL if provided
        if (sqlVerificationContent != null && !sqlVerificationContent.trim().isEmpty()) {
            log.info("Executing verification SQL for: {}", sqlName);
            ScriptExecutor.ScriptExecutionResult verificationResult =
                    scriptExecutor.executeVerificationSql(jdbcTemplate, sqlVerificationContent);

            if (!verificationResult.isSuccess()) {
                log.error("Verification SQL execution failed for: {}", sqlName);
                result.setSuccess(false);
                result.setErrorMessage("Verification failed: " + verificationResult.getErrorMessage());
                return result;
            }

            result.setOutput(verificationResult.getOutput());
            log.info("Verification SQL executed successfully for: {}", sqlName);
        }
        return result;
    }

    /**
     * Execute SQL and verification on multiple nodes in parallel.
     *
     * @param sqlContent The SQL content to execute
     * @param sqlVerificationContent The verification SQL content (can be null)
     * @param targetNodes List of target node names to execute on
     * @param sqlName The name of the SQL for logging
     * @return MultiNodeExecutionResult containing per-node execution details
     */
    public MultiNodeExecutionResult executeAndVerifyOnMultipleNodes(
            String sqlContent,
            String sqlVerificationContent,
            List<String> targetNodes,
            String sqlName) {

        long startTime = System.currentTimeMillis();
        MultiNodeExecutionResult result = new MultiNodeExecutionResult();
        result.setScriptName(sqlName);
        result.setTargetNodes(targetNodes);
        result.setStartTime(LocalDateTime.now());

        log.info("Executing SQL '{}' on {} node(s) in parallel", sqlName, targetNodes.size());

        // Execute on each node in parallel
        List<CompletableFuture<ScriptExecutor.ScriptExecutionResult>> futures = targetNodes.stream()
                .map(nodeName -> CompletableFuture.supplyAsync(() -> 
                        executeAndVerifyOnNode(sqlContent, sqlVerificationContent, nodeName, sqlName)))
                .collect(Collectors.toList());

        // Wait for all executions to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allOf.join(); // Wait for all to complete

            // Collect results
            List<ScriptExecutor.ScriptExecutionResult> nodeResults = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            result.setNodeResults(nodeResults);

            // Determine overall success
            boolean allSucceeded = nodeResults.stream().allMatch(ScriptExecutor.ScriptExecutionResult::isSuccess);
            result.setSuccess(allSucceeded);

            // Build error message if any node failed
            if (!allSucceeded) {
                List<ScriptExecutor.ScriptExecutionResult> failedNodes = nodeResults.stream()
                        .filter(r -> !r.isSuccess())
                        .collect(Collectors.toList());

                StringBuilder errorMsg = new StringBuilder("SQL execution failed on ");
                errorMsg.append(failedNodes.size()).append(" node(s): ");
                for (ScriptExecutor.ScriptExecutionResult failed : failedNodes) {
                    errorMsg.append("[").append(getNodeNameFromResult(failed))
                            .append(": ").append(failed.getErrorMessage()).append("] ");
                }
                result.setErrorMessage(errorMsg.toString());
            }

            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.info("SQL '{}' execution completed on {} nodes. Success: {}, Duration: {}ms",
                    sqlName, targetNodes.size(), allSucceeded, result.getDuration());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Parallel execution failed: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);
            log.error("SQL '{}' parallel execution failed", sqlName, e);
        }

        return result;
    }

    /**
     * Execute SQL and verification on a single node.
     * Reuses the executeAndVerify method.
     */
    private ScriptExecutor.ScriptExecutionResult executeAndVerifyOnNode(
            String sqlContent,
            String sqlVerificationContent,
            String nodeName,
            String sqlName) {

        JdbcTemplate jdbcTemplate = nodeJdbcTemplateMap.get(nodeName);
        if (jdbcTemplate == null) {
            ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor.ScriptExecutionResult();
            result.setSuccess(false);
            result.setErrorMessage("JdbcTemplate not found for node: " + nodeName);
            result.setStartTime(LocalDateTime.now());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(0);
            log.error("JdbcTemplate not found for node: {}", nodeName);
            return result;
        }

        log.debug("Executing SQL '{}' on node: {}", sqlName, nodeName);

        return executeAndVerify(jdbcTemplate, sqlContent, sqlVerificationContent, sqlName);
    }

    /**
     * Extract node name from execution result error message.
     * This is a helper to identify which node failed.
     */
    private String getNodeNameFromResult(ScriptExecutor.ScriptExecutionResult result) {
        if (result.getErrorMessage() != null) {
            String errorMsg = result.getErrorMessage();
            if (errorMsg.contains("JdbcTemplate not found for node: ")) {
                return errorMsg.substring(errorMsg.indexOf("node: ") + 6);
            }
        }
        return "unknown";
    }

    /**
     * Result of executing a script on multiple nodes
     */
    @Data
    public static class MultiNodeExecutionResult {
        private String scriptName;
        private List<String> targetNodes;
        private List<ScriptExecutor.ScriptExecutionResult> nodeResults;
        private boolean success;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
    }
}