package org.jerish.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for retrieving database deployment status information.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseStatusService {

    private final AuditRepository auditRepository;

    /**
     * Get comprehensive database deployment status
     *
     * @return DatabaseStatus object with detailed status information
     */
    public DatabaseStatus getComprehensiveStatus() {
        DatabaseStatus status = new DatabaseStatus();
        // Basic connection info
        status.setDatabaseConnected(true);

        // Query deployment state using views
        populateDeploymentState(status);

        // Query script status using views
        populateScriptStatus(status);

        // Query deployment lock status
        populateLockStatus(status);

        // Query database health
        populateDatabaseHealth(status);

        // Query configuration status
        populateConfigurationStatus(status);

        // Query recent deployment history
        populateDeploymentHistory(status);
        return status;
    }

    private void populateDeploymentState(DatabaseStatus status) {
        try {
            status.setDatabaseConnected(true);

            DatabaseStatus.DeploymentStateInfo stateInfo = auditRepository.getCurrentDeploymentState();

            if (stateInfo != null) {
                // Set total rolled back scripts count across all tags
                stateInfo.setRolledBackScripts(auditRepository.getTotalRolledBackScripts());
                status.setDeploymentState(stateInfo);
            } else {
                // Create empty deployment state
                DatabaseStatus.DeploymentStateInfo emptyState = new DatabaseStatus.DeploymentStateInfo();
                emptyState.setCurrentTag("");
                emptyState.setTotalScripts(0);
                emptyState.setSuccessfulScripts(0);
                emptyState.setFailedScripts(0);
                emptyState.setRolledBackScripts(auditRepository.getTotalRolledBackScripts());
                status.setDeploymentState(emptyState);
            }
        } catch (Exception e) {
            log.error("Error populating deployment state", e);
        }
    }

    private void populateScriptStatus(DatabaseStatus status) {
        try {
            List<DatabaseStatus.ScriptExecutionInfo> scriptHistory = auditRepository.getScriptExecutionHistory();

            List<String> executedScripts = new ArrayList<>();
            List<String> failedScripts = new ArrayList<>();
            List<String> rolledBackScripts = new ArrayList<>();

            for (DatabaseStatus.ScriptExecutionInfo info : scriptHistory) {
                switch (info.getExecutionStatus()) {
                    case "SUCCESS" -> executedScripts.add(info.getScriptName());
                    case "FAILED" -> failedScripts.add(info.getScriptName());
                    case "ROLLED_BACK" -> rolledBackScripts.add(info.getScriptName());
                }
            }

            // Create script status info
            DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
            scriptStatus.setExecutedScriptNames(executedScripts);
            scriptStatus.setFailedScriptNames(failedScripts);
            scriptStatus.setRolledBackScriptNames(rolledBackScripts);
            scriptStatus.setPendingScriptNames(new ArrayList<>());
            scriptStatus.setScriptHistory(scriptHistory);
            
            // Get detailed failure information if needed
            if (!failedScripts.isEmpty()) {
                List<DatabaseStatus.FailedScriptInfo> failedScriptDetails = auditRepository.getFailedScripts();
                scriptStatus.setFailedScriptDetails(failedScriptDetails);
            } else {
                scriptStatus.setFailedScriptDetails(new ArrayList<>());
            }
            
            // Set script counts
            scriptStatus.setExecutedScripts(executedScripts.size());
            scriptStatus.setFailedScripts(failedScripts.size());
            scriptStatus.setRolledBackScripts(rolledBackScripts.size());
            scriptStatus.setPendingScripts(0);

            status.setScriptStatus(scriptStatus);

        } catch (Exception e) {
            log.error("Error populating script status", e);
            DatabaseStatus.ScriptStatusInfo emptyScriptStatus = new DatabaseStatus.ScriptStatusInfo();
            emptyScriptStatus.setExecutedScriptNames(new ArrayList<>());
            emptyScriptStatus.setFailedScriptNames(new ArrayList<>());
            emptyScriptStatus.setRolledBackScriptNames(new ArrayList<>());
            emptyScriptStatus.setPendingScriptNames(new ArrayList<>());
            emptyScriptStatus.setScriptHistory(new ArrayList<>());
            emptyScriptStatus.setFailedScriptDetails(new ArrayList<>());
            status.setScriptStatus(emptyScriptStatus);
        }
    }

    private void populateLockStatus(DatabaseStatus status) {
        try {
            DatabaseStatus.LockInfo lockInfo = auditRepository.getCurrentLockStatus();
            
            if (lockInfo != null && lockInfo.isActive()) {
                status.setLockInfo(lockInfo);
            } else {
                // Create empty lock info
                DatabaseStatus.LockInfo emptyLockInfo = new DatabaseStatus.LockInfo();
                emptyLockInfo.setActive(false);
                emptyLockInfo.setLockOwner("");
                status.setLockInfo(emptyLockInfo);
            }
        } catch (Exception e) {
            log.error("Error populating lock status", e);
            DatabaseStatus.LockInfo emptyLockInfo = new DatabaseStatus.LockInfo();
            emptyLockInfo.setActive(false);
            emptyLockInfo.setLockOwner("");
            status.setLockInfo(emptyLockInfo);
        }
    }

    private void populateDatabaseHealth(DatabaseStatus status) {
        try {
            // Basic health check - query database version and response time
            long startTime = System.currentTimeMillis();

            DatabaseStatus.DatabaseHealthInfo healthInfo = auditRepository.getDatabaseHealthInfo();
            
            // Add response time to health info (only if healthInfo is not null)
            if (healthInfo != null) {
                long responseTime = System.currentTimeMillis() - startTime;
                healthInfo.setResponseTime(responseTime);
            }
            
            status.setHealthInfo(healthInfo);

        } catch (Exception e) {
            log.error("Error populating database health", e);
            DatabaseStatus.DatabaseHealthInfo errorHealthInfo = new DatabaseStatus.DatabaseHealthInfo();
            errorHealthInfo.setHealthy(false);
            errorHealthInfo.setHealthMessage("Health check error: " + e.getMessage());
            errorHealthInfo.setResponseTime(-1);
            status.setHealthInfo(errorHealthInfo);
        }
    }

    private void populateConfigurationStatus(DatabaseStatus status) {
        try {
            DatabaseStatus.ConfigurationInfo configInfo = auditRepository.getConfigurationInfo();
            status.setConfigurationInfo(configInfo);

        } catch (Exception e) {
            log.error("Error populating configuration status", e);
            DatabaseStatus.ConfigurationInfo errorConfigInfo = new DatabaseStatus.ConfigurationInfo();
            errorConfigInfo.setValid(false);
            errorConfigInfo.setMessage("Configuration check failed");
            status.setConfigurationInfo(errorConfigInfo);
        }
    }

    private void populateDeploymentHistory(DatabaseStatus status) {
        try {
            List<DatabaseStatus.DeploymentHistoryEntry> history = auditRepository.getRecentDeploymentHistory();
            status.setRecentDeployments(history);

        } catch (Exception e) {
            log.error("Error populating deployment history", e);
            status.setRecentDeployments(new ArrayList<>());
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // Try standard ISO format first
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e1) {
            try {
                // Try SQLite datetime format: "YYYY-MM-DD HH:MM:SS"
                DateTimeFormatter sqliteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(dateTimeStr, sqliteFormatter);
            } catch (Exception e2) {
                log.debug("Failed to parse datetime: {}", dateTimeStr, e2);
                return null;
            }
        } catch (Exception e) {
            log.debug("Failed to parse datetime: {}", dateTimeStr, e);
            return null;
        }
    }
}