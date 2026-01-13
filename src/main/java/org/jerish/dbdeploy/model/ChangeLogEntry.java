package org.jerish.dbdeploy.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class ChangeLogEntry {
    private Long id;
    private String scriptName;
    private String scriptChecksum;
    private ScriptExecutionStatus executionStatus;
    private LocalDateTime executionTime;
    private Long executionDurationMs;
    private String errorMessage;
    private String rollbackScriptContent;
    private String rollbackVerifyScriptContent;
    private Long parentAuditId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * List of target node names for multi-node execution.
     * Null for single-node execution.
     * Contains "ALL" if script was executed on all configured nodes.
     */
    private List<String> targetNodes;

    /**
     * JSON string containing per-node execution details.
     * Format: [{"nodeName":"node1","success":true,"durationMs":123,"errorMessage":null},...]
     * Null for single-node execution.
     */
    private String nodeExecutionDetails;
}