package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing audit entry stored in changelog_audit table.
 * This table stores execution history for scripts with a reference to the script metadata.
 */
@Data
@NoArgsConstructor
public class AuditEntry {
    private Long id;
    private Long scriptId;
    private ScriptExecutionStatus executionStatus;
    private LocalDateTime executionTime;
    private Long executionDurationMs;
    private String errorMessage;
    private LocalDateTime createdAt;

    /**
     * JSON string containing per-node execution details.
     * Format: [{"nodeName":"node1","success":true,"durationMs":123,"errorMessage":null},...]
     * Null for single-node execution.
     */
    private String nodeExecutionDetails;

    /**
     * Convenience constructor for creating a new audit entry
     */
    public AuditEntry(Long scriptId, ScriptExecutionStatus executionStatus) {
        this.scriptId = scriptId;
        this.executionStatus = executionStatus;
        this.executionTime = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
}