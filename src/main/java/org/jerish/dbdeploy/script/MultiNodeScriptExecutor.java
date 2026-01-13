package org.jerish.dbdeploy.script;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Script executor for multi-node deployment scenarios.
 * Executes scripts in parallel across multiple database nodes and aggregates results.
 */
@Service
@Slf4j
public class MultiNodeScriptExecutor {

    @Autowired
    @Qualifier("nodeJdbcTemplateMap")
    private Map<String, JdbcTemplate> nodeJdbcTemplateMap;

    /**
     * Execute a script on multiple nodes in parallel
     *
     * @param scriptContent The SQL script content to execute
     * @param targetNodes List of target node names to execute on
     * @param scriptName The name of the script for logging purposes
     * @return MultiNodeExecutionResult containing per-node execution details
     */
    public MultiNodeExecutionResult executeOnMultipleNodes(String scriptContent, List<String> targetNodes, String scriptName) {
        long startTime = System.currentTimeMillis();
        MultiNodeExecutionResult result = new MultiNodeExecutionResult();
        result.setScriptName(scriptName);
        result.setTargetNodes(targetNodes);
        result.setStartTime(System.currentTimeMillis());

        log.info("Executing script '{}' on {} node(s) in parallel", scriptName, targetNodes.size());

        // Execute on each node in parallel
        List<CompletableFuture<NodeExecutionResult>> futures = targetNodes.stream()
                .map(nodeName -> CompletableFuture.supplyAsync(() -> executeOnNode(scriptContent, nodeName, scriptName)))
                .collect(Collectors.toList());

        // Wait for all executions to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allOf.join(); // Wait for all to complete

            // Collect results
            List<NodeExecutionResult> nodeResults = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            result.setNodeResults(nodeResults);

            // Determine overall success
            boolean allSucceeded = nodeResults.stream().allMatch(NodeExecutionResult::isSuccess);
            result.setSuccess(allSucceeded);

            // Build error message if any node failed
            if (!allSucceeded) {
                List<NodeExecutionResult> failedNodes = nodeResults.stream()
                        .filter(r -> !r.isSuccess())
                        .collect(Collectors.toList());

                StringBuilder errorMsg = new StringBuilder("Script execution failed on ");
                errorMsg.append(failedNodes.size()).append(" node(s): ");
                for (NodeExecutionResult failed : failedNodes) {
                    errorMsg.append("[").append(failed.getNodeName())
                            .append(": ").append(failed.getErrorMessage()).append("] ");
                }
                result.setErrorMessage(errorMsg.toString());
            }

            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());

            log.info("Script '{}' execution completed on {} nodes. Success: {}, Duration: {}ms",
                    scriptName, targetNodes.size(), allSucceeded, result.getDuration());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Parallel execution failed: " + e.getMessage());
            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());
            log.error("Script '{}' parallel execution failed", scriptName, e);
        }

        return result;
    }

    /**
     * Execute a script on a single node
     */
    private NodeExecutionResult executeOnNode(String scriptContent, String nodeName, String scriptName) {
        long startTime = System.currentTimeMillis();
        NodeExecutionResult result = new NodeExecutionResult();
        result.setNodeName(nodeName);
        result.setStartTime(startTime);

        JdbcTemplate jdbcTemplate = nodeJdbcTemplateMap.get(nodeName);
        if (jdbcTemplate == null) {
            result.setSuccess(false);
            result.setErrorMessage("JdbcTemplate not found for node: " + nodeName);
            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());
            log.error("JdbcTemplate not found for node: {}", nodeName);
            return result;
        }

        try {
            log.debug("Executing script '{}' on node: {}", scriptName, nodeName);

            // Split and execute SQL statements
            String[] sqlStatements = scriptContent.split(";");

            for (String sql : sqlStatements) {
                if (!sql.trim().isEmpty()) {
                    jdbcTemplate.execute(sql.trim());
                }
            }

            result.setSuccess(true);
            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());

            log.debug("Script '{}' executed successfully on node: {} ({}ms)",
                    scriptName, nodeName, result.getDuration());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());

            log.error("Script '{}' execution failed on node: {}", scriptName, nodeName, e);
        }

        return result;
    }

    /**
     * Convert node execution results to JSON string for storage
     */
    public String nodeResultsToJson(List<NodeExecutionResult> nodeResults) {
        if (nodeResults == null || nodeResults.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < nodeResults.size(); i++) {
            NodeExecutionResult result = nodeResults.get(i);
            json.append("{");
            json.append("\"nodeName\":\"").append(escapeJson(result.getNodeName())).append("\",");
            json.append("\"success\":").append(result.isSuccess()).append(",");
            json.append("\"errorMessage\":\"").append(escapeJson(result.getErrorMessage() != null ? result.getErrorMessage() : "")).append("\",");
            json.append("\"startTime\":").append(result.getStartTime()).append(",");
            json.append("\"endTime\":").append(result.getEndTime()).append(",");
            json.append("\"duration\":").append(result.getDuration());
            json.append("}");
            if (i < nodeResults.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Parse JSON string to node execution results
     */
    public List<NodeExecutionResult> jsonToNodeResults(String json) {
        List<NodeExecutionResult> results = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return results;
        }

        try {
            // Remove outer brackets
            String content = json.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }

            if (content.isEmpty()) {
                return results;
            }

            // Split by "},{" to get individual objects
            String[] objects = content.split("\\},\\{");

            for (String obj : objects) {
                // Clean up the object string
                obj = obj.trim();
                if (obj.startsWith("{")) {
                    obj = obj.substring(1);
                }
                if (obj.endsWith("}")) {
                    obj = obj.substring(0, obj.length() - 1);
                }

                NodeExecutionResult result = new NodeExecutionResult();
                String[] pairs = obj.split("\",\"");

                for (String pair : pairs) {
                    String[] keyValue = pair.split("\":\"", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = unescapeJson(keyValue[1].trim());

                        switch (key) {
                            case "nodeName":
                                result.setNodeName(value);
                                break;
                            case "errorMessage":
                                result.setErrorMessage(value);
                                break;
                            case "success":
                                result.setSuccess(Boolean.parseBoolean(value));
                                break;
                            case "startTime":
                                result.setStartTime(Long.parseLong(value));
                                break;
                            case "endTime":
                                result.setEndTime(Long.parseLong(value));
                                break;
                            case "duration":
                                result.setDuration(Long.parseLong(value));
                                break;
                        }
                    }
                }
                results.add(result);
            }
        } catch (Exception e) {
            log.error("Failed to parse node results from JSON: {}", json, e);
        }

        return results;
    }

    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Unescape special characters from JSON
     */
    private String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    /**
     * Result of executing a script on a single node
     */
    @Data
    public static class NodeExecutionResult {
        private String nodeName;
        private boolean success;
        private String errorMessage;
        private long startTime;
        private long endTime;
        private long duration;
    }

    /**
     * Result of executing a script on multiple nodes
     */
    @Data
    public static class MultiNodeExecutionResult {
        private String scriptName;
        private List<String> targetNodes;
        private List<NodeExecutionResult> nodeResults;
        private boolean success;
        private String errorMessage;
        private long startTime;
        private long endTime;
        private long duration;
    }
}