package org.jerish.dbdeploy.entity;

import lombok.Data;

import java.time.LocalDateTime;
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
        private LocalDateTime deploymentTime;
        private String status;
        private int scriptCount;
        private String deployedBy;
    }
}
