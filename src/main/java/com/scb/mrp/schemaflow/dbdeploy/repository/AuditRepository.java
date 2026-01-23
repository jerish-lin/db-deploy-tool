package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogAuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogScript;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;

import java.util.List;

/**
 * Repository interface for audit operations.
 * Provides methods for managing script metadata, audit entries, locks, and status reporting.
 */
public interface AuditRepository {
    // ==================== Lock Operations ====================

    /**
     * Acquire a deployment lock
     */
    boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes);

    /**
     * Release a deployment lock
     */
    void releaseLock(String lockKey, String lockOwner);

    // ==================== Audit Entry Operations ====================

    /**
     * Insert or update script metadata
     * Returns the script ID
     */
    Long createScriptMetadata(ChangeLogScript metadata);

    /**
     * Record script execution as a new audit entry
     */
    Long createScriptAuditEntry(ChangeLogAuditEntry entry);

    /**
     * Get current lock status
     */
    DatabaseStatus.LockInfo getCurrentLockStatus();

    /**
     * Get script summary with latest status for all scripts
     */
    DatabaseStatus.ScriptSummary getScriptSummary();

    /**
     * Get recent audit history entries (last 10)
     */
    List<DatabaseStatus.AuditHistoryEntry> getRecentAuditHistory();
}