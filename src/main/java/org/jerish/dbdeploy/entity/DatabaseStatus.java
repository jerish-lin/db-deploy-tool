package org.jerish.dbdeploy.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Data
public class DatabaseStatus {
    // Basic connection info
    private boolean databaseConnected;
    private String databaseName;
    private String databaseVersion;
    private String databaseUrl;
    
    // Deployment state
    private String currentTag;
    private String currentTagDescription;
    private LocalDateTime lastDeploymentTime;
    private String lastDeploymentUser;
    
    // Script counts
    private int totalScripts;
    private int executedScripts;
    private int failedScripts;
    private int rolledBackScripts;
    private int pendingScripts;
    
    // Script details
    private List<String> executedScriptNames;
    private List<String> failedScriptNames;
    private List<String> rolledBackScriptNames;
    private List<String> pendingScriptNames;
    
    // Deployment lock status
    private boolean deploymentInProgress;
    private String deploymentLockOwner;
    private LocalDateTime deploymentLockAcquiredAt;
    private LocalDateTime deploymentLockExpiresAt;
    
    // Database health
    private boolean databaseHealthy;
    private String databaseHealthMessage;
    private long connectionResponseTime;
    
    // Configuration status
    private boolean configurationValid;
    private String configurationMessage;
    
    // Recent deployment history (last 10 deployments)
    private List<DeploymentHistoryEntry> recentDeployments;
    
    // Additional metrics
    private Map<String, Object> additionalMetrics;
    
    @Data
    public static class DeploymentHistoryEntry {
        private String tagName;
        private String description;
        private String deploymentTime; // String to support parsing from database
        private String status;
        private int scriptCount;
        private String deployedBy;
        
        // Helper method to get parsed LocalDateTime
        public LocalDateTime getParsedDeploymentTime() {
            if (deploymentTime == null || deploymentTime.isEmpty()) {
                return null;
            }
            try {
                // Try standard ISO format first
                return LocalDateTime.parse(deploymentTime);
            } catch (DateTimeParseException e1) {
                try {
                    // Try SQLite datetime format: "YYYY-MM-DD HH:MM:SS"
                    DateTimeFormatter sqliteFormatter =
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDateTime.parse(deploymentTime, sqliteFormatter);
                } catch (Exception e2) {
                    return null;
                }
            }
        }
    }
    
    @Data
    public static class DeploymentStateInfo {
        private String currentTag;
        private String description;
        private String deploymentTime;
        private String createdBy;
        private int totalScripts;
        private int successfulScripts;
        private int failedScripts;
        private int rolledBackScripts;
    }
    
    @Data
    public static class ScriptExecutionInfo {
        private String scriptName;
        private String executionStatus;
    }
    
    @Data
    public static class FailedScriptInfo {
        private String scriptName;
        private String errorMessage;
        private String executionTime;
    }
    
    @Data
    public static class LockInfo {
        private String lockOwner;
        private String lockAcquiredAt;
        private String lockExpiresAt;
        private boolean active;
    }
    
    @Data
    public static class DatabaseHealthInfo {
        private String version;
        private boolean healthy;
        private String healthMessage;
    }
    
    @Data
    public static class ConfigurationInfo {
        private boolean valid;
        private String message;
    }
}
