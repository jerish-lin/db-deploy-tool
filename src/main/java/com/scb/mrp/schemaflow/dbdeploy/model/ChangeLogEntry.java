package com.scb.mrp.schemaflow.dbdeploy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Combined model representing both script metadata and audit entry.
 * This is used for backward compatibility with existing code that expects
 * a single object containing both script and audit information.
 */
@Data
@NoArgsConstructor
public class ChangeLogEntry {
    // Audit entry fields
    private Long id;
    private Long scriptId;
    private ScriptExecutionStatus executionStatus;
    private LocalDateTime executionTime;
    private Long executionDurationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Script metadata fields
    private String scriptName;
    private String scriptChecksum;
    private String rollbackScriptContent;
    private String rollbackVerifyScriptContent;

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