package com.scb.mrp.schemaflow.dbdeploy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DatabaseStatus {
    // Deployment lock information
    private LockInfo lockInfo;

    // Script summary - simple summary of all scripts with latest status
    private ScriptSummary scriptSummary;

    // Recent execution history (last 10 audit entries from changelog_audit)
    private List<AuditHistoryEntry> recentAuditHistory;

    @Data
    public static class ScriptSummary {
        // List of all unique scripts with their latest status
        private List<ChangeLogScriptStatus> scripts;

        // Summary counts
        private int totalScripts;
        private int executedScripts;   // Scripts with SUCCESS status
        private int failedScripts;      // Scripts with FAILED status
        private int rolledBackScripts;  // Scripts with ROLLED_BACK status

        /**
         * Get all successfully executed script names
         */
        @JsonIgnore
        public List<ChangeLogScriptStatus> getSuccessScripts() {
            if (scripts == null) {
                return List.of();
            }
            return scripts.stream()
                    .filter(s -> ScriptExecutionStatus.SUCCESS.equals(s.getLatestStatus()))
                    .collect(Collectors.toList());
        }

        /**
         * Get all successfully executed script names
         */
        @JsonIgnore
        public List<ChangeLogScriptStatus> getSuccessAndFailedScripts() {
            if (scripts == null) {
                return List.of();
            }
            return scripts.stream()
                    .filter(s -> !ScriptExecutionStatus.ROLLED_BACK.equals(s.getLatestStatus()))
                    .collect(Collectors.toList());
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ChangeLogScriptStatus extends ChangeLogScript {
        private ScriptExecutionStatus latestStatus;  // SUCCESS / FAILED / ROLLED_BACK

        public ChangeLogScriptStatus() {
            super();
        }

        public ChangeLogScriptStatus(String scriptName, String scriptChecksum, ScriptExecutionStatus latestStatus) {
            super(scriptName, scriptChecksum);
            this.latestStatus = latestStatus;
        }

        /**
         * Convert this ScriptStatus to a ScriptMetadata object.
         * This copies all the inherited ScriptMetadata fields.
         *
         * @return ScriptMetadata object with the same field values
         */
        public ChangeLogScript toScriptMetadata() {
            ChangeLogScript metadata = new ChangeLogScript();
            metadata.setId(this.getId());
            metadata.setScriptName(this.getScriptName());
            metadata.setScriptChecksum(this.getScriptChecksum());
            metadata.setRollbackScriptContent(this.getRollbackScriptContent());
            metadata.setRollbackVerifyScriptContent(this.getRollbackVerifyScriptContent());
            metadata.setCreatedAt(this.getCreatedAt());
            metadata.setTargetNodes(this.getTargetNodes());
            return metadata;
        }
    }

    @Data
    public static class AuditHistoryEntry {
        // Script information
        private String scriptName;
        private String scriptChecksum;

        // Execution audit details
        private Long auditId;
        private ScriptExecutionStatus executionStatus;  // SUCCESS / FAILED / ROLLED_BACK
        private LocalDateTime executionTime;
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