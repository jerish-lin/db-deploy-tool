package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.entity.AuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptMetadata;
import com.scb.mrp.schemaflow.dbdeploy.model.ChangeLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract base implementation of AuditRepository with common logic.
 * Database-specific implementations extend this class and override methods as needed.
 */
@Slf4j
public abstract class AbstractAuditRepository implements AuditRepository {

    protected final JdbcTemplate dbDeployJdbcTemplate;

    public AbstractAuditRepository(JdbcTemplate dbDeployJdbcTemplate) {
        this.dbDeployJdbcTemplate = dbDeployJdbcTemplate;
    }

    /**
     * RowMapper for ScriptMetadata
     */
    protected final RowMapper<ScriptMetadata> scriptMetadataRowMapper = (ResultSet rs, int rowNum) -> {
        ScriptMetadata metadata = new ScriptMetadata();
        metadata.setId(rs.getLong("id"));
        metadata.setScriptName(rs.getString("script_name"));
        metadata.setScriptChecksum(rs.getString("script_checksum"));
        metadata.setRollbackScriptContent(rs.getString("rollback_script_content"));
        metadata.setRollbackVerifyScriptContent(rs.getString("rollback_verify_script_content"));

        // Handle target_nodes field
        metadata.setTargetNodes(parseTargetNodes(rs.getObject("target_nodes")));

        // Handle created_at timestamp
        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            try {
                metadata.setCreatedAt(LocalDateTime.parse(createdAtStr));
            } catch (Exception e) {
                metadata.setCreatedAt(LocalDateTime.now());
            }
        } else {
            metadata.setCreatedAt(LocalDateTime.now());
        }

        return metadata;
    };

    /**
     * RowMapper for AuditEntry
     */
    protected final RowMapper<AuditEntry> auditEntryRowMapper = (ResultSet rs, int rowNum) -> {
        AuditEntry entry = new AuditEntry();
        entry.setId(rs.getLong("id"));
        entry.setScriptId(rs.getLong("script_id"));
        entry.setExecutionStatus(ScriptExecutionStatus.fromValue(rs.getString("execution_status")));

        // Handle execution_time timestamp
        String executionTimeStr = rs.getString("execution_time");
        if (executionTimeStr != null) {
            try {
                entry.setExecutionTime(LocalDateTime.parse(executionTimeStr));
            } catch (Exception e) {
                entry.setExecutionTime(LocalDateTime.now());
            }
        } else {
            entry.setExecutionTime(LocalDateTime.now());
        }

        try {
            entry.setExecutionDurationMs(rs.getObject("execution_duration_ms", Long.class));
        } catch (SQLException e) {
            entry.setExecutionDurationMs(null);
        }
        entry.setErrorMessage(rs.getString("error_message"));

        // Handle multi-node fields - to be implemented by subclasses
        handleMultiNodeFields(rs, entry);

        // Handle created_at timestamp
        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            try {
                entry.setCreatedAt(LocalDateTime.parse(createdAtStr));
            } catch (Exception e) {
                entry.setCreatedAt(LocalDateTime.now());
            }
        } else {
            entry.setCreatedAt(LocalDateTime.now());
        }

        return entry;
    };

    /**
     * RowMapper for ChangeLogEntry (combined script metadata and audit entry)
     */
    protected final RowMapper<ChangeLogEntry> changeLogEntryRowMapper = (ResultSet rs, int rowNum) -> {
        ChangeLogEntry entry = new ChangeLogEntry();

        // Audit entry fields
        entry.setId(rs.getLong("audit_id"));
        entry.setScriptId(rs.getLong("script_id"));
        entry.setExecutionStatus(ScriptExecutionStatus.fromValue(rs.getString("execution_status")));

        String executionTimeStr = rs.getString("execution_time");
        if (executionTimeStr != null) {
            try {
                entry.setExecutionTime(LocalDateTime.parse(executionTimeStr));
            } catch (Exception e) {
                entry.setExecutionTime(LocalDateTime.now());
            }
        } else {
            entry.setExecutionTime(LocalDateTime.now());
        }

        try {
            entry.setExecutionDurationMs(rs.getObject("execution_duration_ms", Long.class));
        } catch (SQLException e) {
            entry.setExecutionDurationMs(null);
        }
        entry.setErrorMessage(rs.getString("error_message"));

        // Handle multi-node fields - to be implemented by subclasses
        handleMultiNodeFieldsForChangeLog(rs, entry);

        String createdAtStr = rs.getString("audit_created_at");
        if (createdAtStr != null) {
            try {
                entry.setCreatedAt(LocalDateTime.parse(createdAtStr));
            } catch (Exception e) {
                entry.setCreatedAt(LocalDateTime.now());
            }
        } else {
            entry.setCreatedAt(LocalDateTime.now());
        }

        // Script metadata fields
        entry.setScriptName(rs.getString("script_name"));
        entry.setScriptChecksum(rs.getString("script_checksum"));
        entry.setRollbackScriptContent(rs.getString("rollback_script_content"));
        entry.setRollbackVerifyScriptContent(rs.getString("rollback_verify_script_content"));

        return entry;
    };

    /**
     * Handle multi-node fields for AuditEntry - to be implemented by subclasses
     */
    protected abstract void handleMultiNodeFields(ResultSet rs, AuditEntry entry) throws SQLException;

    /**
     * Handle multi-node fields for ChangeLogEntry - to be implemented by subclasses
     */
    protected abstract void handleMultiNodeFieldsForChangeLog(ResultSet rs, ChangeLogEntry entry) throws SQLException;

    /**
     * Parse timestamp from Object to LocalDateTime
     */
    protected LocalDateTime parseTimestamp(Object timestampObj) {
        if (timestampObj == null) {
            return LocalDateTime.now();
        }
        String timestampStr = timestampObj.toString();
        if (timestampStr != null) {
            try {
                return LocalDateTime.parse(timestampStr);
            } catch (Exception e) {
                return LocalDateTime.now();
            }
        }
        return LocalDateTime.now();
    }

    /**
     * Parse target nodes from database result.
     * Can be Array (PostgreSQL), comma-separated string (SQLite/ClickHouse), or null.
     *
     * @param targetNodesObj The target nodes object from database
     * @return List of target node names, or null if not applicable
     */
    protected List<String> parseTargetNodes(Object targetNodesObj) {
        if (targetNodesObj == null) {
            return null;
        }

        // PostgreSQL returns Array
        if (targetNodesObj instanceof java.sql.Array) {
            try {
                java.sql.Array array = (java.sql.Array) targetNodesObj;
                Object[] arrayData = (Object[]) array.getArray();
                if (arrayData == null || arrayData.length == 0) {
                    return null;
                }
                List<String> nodes = new ArrayList<>();
                for (Object item : arrayData) {
                    if (item != null) {
                        nodes.add(item.toString());
                    }
                }
                return nodes.isEmpty() ? null : nodes;
            } catch (SQLException e) {
                log.warn("Failed to parse target nodes array", e);
                return null;
            }
        }

        // For SQLite and ClickHouse, target nodes might be stored as comma-separated string
        String targetNodesStr = targetNodesObj.toString();
        if (targetNodesStr == null || targetNodesStr.trim().isEmpty()) {
            return null;
        }

        // Try to parse as comma-separated string
        try {
            String[] nodes = targetNodesStr.split(",");
            if (nodes == null || nodes.length == 0) {
                return null;
            }
            List<String> result = new ArrayList<>();
            for (String node : nodes) {
                String trimmed = node.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("Failed to parse target nodes string: {}", targetNodesStr, e);
            return null;
        }
    }

    // ==================== Script Metadata Operations ====================

    public ScriptMetadata getScriptMetadata(String scriptName) {
        String sql = "SELECT * FROM schemaflow_changelog_script WHERE script_name = ?";
        List<ScriptMetadata> results = dbDeployJdbcTemplate.query(sql, scriptMetadataRowMapper, scriptName);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Long saveScriptMetadata(ScriptMetadata metadata) {
        // Check if script already exists
        ScriptMetadata existing = getScriptMetadata(metadata.getScriptName());
        if (existing != null) {
            return existing.getId();
        }

        String sql = getSaveScriptMetadataSql();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        setSaveScriptMetadataParameters(metadata, sql, keyHolder);

        return extractGeneratedKeyId(keyHolder);
    }

    /**
     * Get SQL for saving script metadata - to be implemented by subclasses
     */
    protected abstract String getSaveScriptMetadataSql();

    /**
     * Set parameters for saving script metadata - to be implemented by subclasses
     */
    protected abstract void setSaveScriptMetadataParameters(ScriptMetadata metadata, String sql, KeyHolder keyHolder);

    // ==================== Audit Entry Operations ====================

    @Override
    public Long recordAuditEntry(AuditEntry entry) {
        String sql = getRecordAuditEntrySql();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        setRecordAuditEntryParameters(entry, sql, keyHolder);

        return extractGeneratedKeyId(keyHolder);
    }

    /**
     * Get SQL for recording audit entry - to be implemented by subclasses
     */
    protected abstract String getRecordAuditEntrySql();

    /**
     * Set parameters for recording audit entry - to be implemented by subclasses
     */
    protected abstract void setRecordAuditEntryParameters(AuditEntry entry, String sql, KeyHolder keyHolder);

    @Override
    public void recordScriptExecution(ChangeLogEntry entry) {
        // First, save or get script metadata
        ScriptMetadata metadata = new ScriptMetadata();
        metadata.setScriptName(entry.getScriptName());
        metadata.setScriptChecksum(entry.getScriptChecksum());
        metadata.setRollbackScriptContent(entry.getRollbackScriptContent());
        metadata.setRollbackVerifyScriptContent(entry.getRollbackVerifyScriptContent());
        metadata.setTargetNodes(entry.getTargetNodes());
        metadata.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());

        Long scriptId = saveScriptMetadata(metadata);
        entry.setScriptId(scriptId);

        // Then, create audit entry
        AuditEntry auditEntry = new AuditEntry();
        auditEntry.setScriptId(scriptId);
        auditEntry.setExecutionStatus(entry.getExecutionStatus());
        auditEntry.setExecutionTime(entry.getExecutionTime());
        auditEntry.setExecutionDurationMs(entry.getExecutionDurationMs());
        auditEntry.setErrorMessage(entry.getErrorMessage());
        auditEntry.setNodeExecutionDetails(entry.getNodeExecutionDetails());
        auditEntry.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());

        Long auditId = recordAuditEntry(auditEntry);
        entry.setId(auditId);
    }

    @Override
    public void recordRollbackScriptExecution(ChangeLogEntry entry) {
        // Get or create script metadata
        ScriptMetadata metadata = getScriptMetadata(entry.getScriptName());
        if (metadata == null) {
            metadata = new ScriptMetadata();
            metadata.setScriptName(entry.getScriptName());
            metadata.setScriptChecksum(entry.getScriptChecksum());
            metadata.setRollbackScriptContent(entry.getRollbackScriptContent());
            metadata.setRollbackVerifyScriptContent(entry.getRollbackVerifyScriptContent());
            metadata.setTargetNodes(entry.getTargetNodes());
            metadata.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());
        }

        Long scriptId = saveScriptMetadata(metadata);
        entry.setScriptId(scriptId);

        // Create audit entry with parent reference
        AuditEntry auditEntry = new AuditEntry();
        auditEntry.setScriptId(scriptId);
        auditEntry.setExecutionStatus(entry.getExecutionStatus());
        auditEntry.setExecutionTime(entry.getExecutionTime());
        auditEntry.setExecutionDurationMs(entry.getExecutionDurationMs());
        auditEntry.setErrorMessage(entry.getErrorMessage());
        auditEntry.setNodeExecutionDetails(entry.getNodeExecutionDetails());
        auditEntry.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());

        Long auditId = recordAuditEntry(auditEntry);
        entry.setId(auditId);
    }

    // ==================== Lock Operations ====================

    @Override
    public boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes) {
        String sql = getAcquireLockSql(timeoutMinutes);
        return dbDeployJdbcTemplate.update(sql, lockKey, lockOwner) > 0;
    }

    @Override
    public void releaseLock(String lockKey, String lockOwner) {
        String sql = "DELETE FROM schemaflow_deploy_lock WHERE lock_key = ? AND lock_owner = ?";
        dbDeployJdbcTemplate.update(sql, lockKey, lockOwner);
    }

    /**
     * Get SQL for acquiring lock - to be implemented by subclasses
     */
    protected abstract String getAcquireLockSql(int timeoutMinutes);

    // ==================== Status and Reporting Operations ====================

    @Override
    public DatabaseStatus.LockInfo getCurrentLockStatus() {
        String sql = """
                SELECT lock_owner, lock_acquired_at, lock_expires_at, is_active
                FROM schemaflow_deploy_lock
                WHERE is_active = 1
                ORDER BY lock_acquired_at DESC
                LIMIT 1
                """;

        try {
            List<Map<String, Object>> lockResults = dbDeployJdbcTemplate.queryForList(sql);
            if (!lockResults.isEmpty()) {
                Map<String, Object> lockResult = lockResults.get(0);
                DatabaseStatus.LockInfo info = new DatabaseStatus.LockInfo();
                info.setLockOwner((String) lockResult.get("lock_owner"));
                info.setLockAcquiredAt((String) lockResult.get("lock_acquired_at"));
                info.setLockExpiresAt((String) lockResult.get("lock_expires_at"));
                info.setActive(((Number) lockResult.get("is_active")).intValue() == 1);
                return info;
            }
        } catch (Exception e) {
            log.debug("No active deployment lock found", e);
        }
        return null;
    }

    @Override
    public DatabaseStatus.ScriptSummary getScriptSummary() {
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        List<DatabaseStatus.ScriptStatus> scripts = new ArrayList<>();

        String sql = """
                SELECT
                    cs.id,
                    cs.script_name,
                    cs.script_checksum,
                    cs.rollback_script_content,
                    cs.rollback_verify_script_content,
                    cs.target_nodes,
                    cs.created_at,
                    ca.execution_status as latest_status
                FROM schemaflow_changelog_script cs
                LEFT JOIN (
                    SELECT
                        script_id,
                        execution_status,
                        ROW_NUMBER() OVER (PARTITION BY script_id ORDER BY execution_time DESC) as rn
                    FROM schemaflow_changelog_audit
                ) ca ON cs.id = ca.script_id AND ca.rn = 1
                ORDER BY cs.id
                """;

        try {
            List<Map<String, Object>> results = dbDeployJdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : results) {
                DatabaseStatus.ScriptStatus status = new DatabaseStatus.ScriptStatus();
                status.setId(((Number) row.get("id")).longValue());
                status.setScriptName((String) row.get("script_name"));
                status.setScriptChecksum((String) row.get("script_checksum"));
                status.setRollbackScriptContent((String) row.get("rollback_script_content"));
                status.setRollbackVerifyScriptContent((String) row.get("rollback_verify_script_content"));
                status.setTargetNodes(parseTargetNodes(row.get("target_nodes")));
                status.setCreatedAt(parseTimestamp(row.get("created_at")));
                status.setLatestStatus(ScriptExecutionStatus.fromValue((String) row.get("latest_status")));
                scripts.add(status);
            }

            summary.setScripts(scripts);

            // Calculate summary counts
            summary.setTotalScripts(scripts.size());
            summary.setExecutedScripts((int) scripts.stream()
                    .filter(s -> ScriptExecutionStatus.SUCCESS.equals(s.getLatestStatus())).count());
            summary.setFailedScripts((int) scripts.stream()
                    .filter(s -> ScriptExecutionStatus.FAILED.equals(s.getLatestStatus())).count());
            summary.setRolledBackScripts((int) scripts.stream()
                    .filter(s -> ScriptExecutionStatus.ROLLED_BACK.equals(s.getLatestStatus())).count());

        } catch (Exception e) {
            log.error("Error getting script summary", e);
            summary.setScripts(new ArrayList<>());
            summary.setTotalScripts(0);
            summary.setExecutedScripts(0);
            summary.setFailedScripts(0);
            summary.setRolledBackScripts(0);
        }

        return summary;
    }

    @Override
    public List<DatabaseStatus.AuditHistoryEntry> getRecentAuditHistory() {
        List<DatabaseStatus.AuditHistoryEntry> history = new ArrayList<>();

        String sql = """
                SELECT
                    ca.id as audit_id,
                    cs.script_name,
                    cs.script_checksum,
                    cs.target_nodes,
                    ca.execution_status,
                    ca.execution_time,
                    ca.execution_duration_ms,
                    ca.error_message,
                    ca.node_execution_details
                FROM schemaflow_changelog_audit ca
                INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
                ORDER BY ca.execution_time DESC
                LIMIT 10
                """;

        try {
            List<Map<String, Object>> results = dbDeployJdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : results) {
                DatabaseStatus.AuditHistoryEntry entry = new DatabaseStatus.AuditHistoryEntry();
                entry.setAuditId(((Number) row.get("audit_id")).longValue());
                entry.setScriptName((String) row.get("script_name"));
                entry.setScriptChecksum((String) row.get("script_checksum"));
                entry.setExecutionStatus(ScriptExecutionStatus.fromValue((String) row.get("execution_status")));
                entry.setExecutionTime((String) row.get("execution_time"));

                Object durationMs = row.get("execution_duration_ms");
                if (durationMs != null) {
                    entry.setExecutionDurationMs(((Number) durationMs).longValue());
                }

                entry.setErrorMessage((String) row.get("error_message"));

                // Handle target_nodes from script table - to be implemented by subclasses
                handleTargetNodesForAuditHistory(row, entry);

                entry.setNodeExecutionDetails((String) row.get("node_execution_details"));

                history.add(entry);
            }
        } catch (Exception e) {
            log.error("Error getting recent audit history", e);
        }

        return history;
    }

    /**
     * Handle target_nodes for audit history (from script table) - to be implemented by subclasses
     */
    protected abstract void handleTargetNodesForAuditHistory(Map<String, Object> row, DatabaseStatus.AuditHistoryEntry entry);

    /**
     * Extract generated key ID from KeyHolder
     */
    protected Long extractGeneratedKeyId(KeyHolder keyHolder) {
        // Try getKey() first (works for SQLite and some PostgreSQL configurations)
        try {
            Number generatedId = keyHolder.getKey();
            if (generatedId != null) {
                return generatedId.longValue();
            }
        } catch (InvalidDataAccessApiUsageException e) {
            // PostgreSQL may throw this when multiple keys are returned, fall through to getKeys()
        }

        // Fallback to getKeys() for PostgreSQL which returns multiple keys
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && keys.get("id") != null) {
            return ((Number) keys.get("id")).longValue();
        }

        throw new RuntimeException("Creating record failed, no ID obtained.");
    }
}