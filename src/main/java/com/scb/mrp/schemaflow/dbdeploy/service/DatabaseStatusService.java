package com.scb.mrp.schemaflow.dbdeploy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.repository.AuditRepository;
import org.springframework.stereotype.Service;

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

        // Query script summary
        populateScriptSummary(status);

        // Query recent audit history
        populateRecentAuditHistory(status);

        // Query deployment lock status
        populateLockStatus(status);

        return status;
    }

    private void populateScriptSummary(DatabaseStatus status) {
        try {
            DatabaseStatus.ScriptSummary scriptSummary = auditRepository.getScriptSummary();
            status.setScriptSummary(scriptSummary);
        } catch (Exception e) {
            log.error("Error populating script summary", e);
            DatabaseStatus.ScriptSummary emptySummary = new DatabaseStatus.ScriptSummary();
            emptySummary.setScripts(new java.util.ArrayList<>());
            emptySummary.setTotalScripts(0);
            emptySummary.setExecutedScripts(0);
            emptySummary.setFailedScripts(0);
            emptySummary.setRolledBackScripts(0);
            status.setScriptSummary(emptySummary);
        }
    }

    private void populateRecentAuditHistory(DatabaseStatus status) {
        try {
            status.setRecentAuditHistory(auditRepository.getRecentAuditHistory());
        } catch (Exception e) {
            log.error("Error populating recent audit history", e);
            status.setRecentAuditHistory(new java.util.ArrayList<>());
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
}