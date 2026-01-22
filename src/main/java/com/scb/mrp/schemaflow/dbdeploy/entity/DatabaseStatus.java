package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.Data;

import java.util.List;

@Data
public class DatabaseStatus {
    // Basic connection info
    private boolean databaseConnected;

    // Script summary - simple summary of all scripts with latest status
    private ScriptSummary scriptSummary;

    // Recent execution history (last 10 audit entries from changelog_audit)
    private List<AuditHistoryEntry> recentAuditHistory;

    // Deployment lock information
    private LockInfo lockInfo;

    @Data
    public static class ScriptSummary {
        // List of all unique scripts with their latest status
        private List<ScriptStatus> scripts;

        // Summary counts
        private int totalScripts;
        private int executedScripts;   // Scripts with SUCCESS status
        private int failedScripts;      // Scripts with FAILED status
        private int rolledBackScripts;  // Scripts with ROLLED_BACK status
    }

    @Data
    public static class ScriptStatus {
        private String scriptName;
        private String latestStatus;  // SUCCESS / FAILED / ROLLED_BACK
    }

    @Data
    public static class AuditHistoryEntry {
        // Script information
        private String scriptName;
        private String scriptChecksum;

        // Execution audit details
        private Long auditId;
        private String executionStatus;  // SUCCESS / FAILED / ROLLED_BACK
        private String executionTime;
        private Long executionDurationMs;
        private String errorMessage;

        // Multi-node execution details (if applicable)
        private List<String> targetNodes;
        private String nodeExecutionDetails;
    }

    @Data
    public static class LockInfo {
        private String lockOwner;
        private String lockAcquiredAt;
        private String lockExpiresAt;
        private boolean active;
    }
}