package com.scb.mrp.schemaflow.dbdeploy.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.scb.mrp.schemaflow.dbdeploy.entity.AuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptMetadata;
import com.scb.mrp.schemaflow.dbdeploy.model.ChangeLogEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
@RequiredArgsConstructor
public class AuditRepository {

    private final JdbcTemplate dbDeployJdbcTemplate;

    /**
     * Get the JdbcTemplate for direct database operations
     */
    public JdbcTemplate getJdbcTemplate() {
        return dbDeployJdbcTemplate;
    }

    /**
     * Helper method to check if the database is SQLite
     */
    private boolean isSQLiteDatabase() {
        try {
            return dbDeployJdbcTemplate.queryForObject("SELECT sqlite_version()", String.class) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * RowMapper for ScriptMetadata
     */
    private final RowMapper<ScriptMetadata> scriptMetadataRowMapper = (ResultSet rs, int rowNum) -> {
        ScriptMetadata metadata = new ScriptMetadata();
        metadata.setId(rs.getLong("id"));
        metadata.setScriptName(rs.getString("script_name"));
        metadata.setScriptChecksum(rs.getString("script_checksum"));
        metadata.setApplyScriptContent(rs.getString("apply_script_content"));
        metadata.setRollbackScriptContent(rs.getString("rollback_script_content"));
        metadata.setApplyVerifyScriptContent(rs.getString("apply_verify_script_content"));
        metadata.setRollbackVerifyScriptContent(rs.getString("rollback_verify_script_content"));

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
    private final RowMapper<AuditEntry> auditEntryRowMapper = (ResultSet rs, int rowNum) -> {
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

        // Handle multi-node fields
        try {
            String targetNodesStr = rs.getString("target_nodes");
            if (targetNodesStr != null && !targetNodesStr.isEmpty()) {
                // SQLite stores as comma-separated string
                entry.setTargetNodes(List.of(targetNodesStr.split(",")));
            } else {
                entry.setTargetNodes(null);
            }
        } catch (SQLException e) {
            entry.setTargetNodes(null);
        }

        entry.setNodeExecutionDetails(rs.getString("node_execution_details"));

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
    private final RowMapper<ChangeLogEntry> changeLogEntryRowMapper = (ResultSet rs, int rowNum) -> {
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

        try {
            String targetNodesStr = rs.getString("target_nodes");
            if (targetNodesStr != null && !targetNodesStr.isEmpty()) {
                entry.setTargetNodes(List.of(targetNodesStr.split(",")));
            } else {
                entry.setTargetNodes(null);
            }
        } catch (SQLException e) {
            entry.setTargetNodes(null);
        }

        entry.setNodeExecutionDetails(rs.getString("node_execution_details"));

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

    // ==================== Script Metadata Operations ====================

    /**
     * Get script metadata by script name
     */
    public ScriptMetadata getScriptMetadata(String scriptName) {
        String sql = "SELECT * FROM schemaflow_changelog_script WHERE script_name = ?";
        List<ScriptMetadata> results = dbDeployJdbcTemplate.query(sql, scriptMetadataRowMapper, scriptName);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Get script metadata by ID
     */
    public ScriptMetadata getScriptMetadataById(Long scriptId) {
        String sql = "SELECT * FROM schemaflow_changelog_script WHERE id = ?";
        List<ScriptMetadata> results = dbDeployJdbcTemplate.query(sql, scriptMetadataRowMapper, scriptId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Insert or update script metadata
     * Returns the script ID
     */
    public Long saveScriptMetadata(ScriptMetadata metadata) {
        // Check if script already exists
        ScriptMetadata existing = getScriptMetadata(metadata.getScriptName());
        if (existing != null) {
            return existing.getId();
        }

        String sql = """
                INSERT INTO schemaflow_changelog_script (
                    script_name, script_checksum, apply_script_content,
                    rollback_script_content, apply_verify_script_content,
                    rollback_verify_script_content, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        if (isSQLiteDatabase()) {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, metadata.getScriptName());
                ps.setString(2, metadata.getScriptChecksum());
                ps.setString(3, metadata.getApplyScriptContent());
                ps.setString(4, metadata.getRollbackScriptContent());
                ps.setString(5, metadata.getApplyVerifyScriptContent());
                ps.setString(6, metadata.getRollbackVerifyScriptContent());
                ps.setString(7, metadata.getCreatedAt() != null ? metadata.getCreatedAt().toString() : LocalDateTime.now().toString());
                return ps;
            }, keyHolder);
        } else {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, metadata.getScriptName());
                ps.setString(2, metadata.getScriptChecksum());
                ps.setString(3, metadata.getApplyScriptContent());
                ps.setString(4, metadata.getRollbackScriptContent());
                ps.setString(5, metadata.getApplyVerifyScriptContent());
                ps.setString(6, metadata.getRollbackVerifyScriptContent());
                ps.setTimestamp(7, Timestamp.valueOf(metadata.getCreatedAt() != null ? metadata.getCreatedAt() : LocalDateTime.now()));
                return ps;
            }, keyHolder);
        }

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

        throw new RuntimeException("Creating script metadata failed, no ID obtained.");
    }

    // ==================== Audit Entry Operations ====================

    /**
     * Record script execution as a new audit entry
     */
    public Long recordAuditEntry(AuditEntry entry) {
        String sql = """
                INSERT INTO schemaflow_changelog_audit (
                    script_id, execution_status, execution_time,
                    execution_duration_ms, error_message,
                    target_nodes, node_execution_details, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        if (isSQLiteDatabase()) {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, entry.getScriptId());
                ps.setString(2, entry.getExecutionStatus().getValue());
                ps.setString(3, entry.getExecutionTime().toString());
                ps.setObject(4, entry.getExecutionDurationMs());
                ps.setString(5, entry.getErrorMessage());
                ps.setString(6, entry.getTargetNodes() != null ? String.join(",", entry.getTargetNodes()) : null);
                ps.setString(7, entry.getNodeExecutionDetails());
                ps.setString(8, entry.getCreatedAt().toString());
                return ps;
            }, keyHolder);
        } else {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, entry.getScriptId());
                ps.setString(2, entry.getExecutionStatus().getValue());
                ps.setTimestamp(3, Timestamp.valueOf(entry.getExecutionTime()));
                ps.setObject(4, entry.getExecutionDurationMs());
                ps.setString(5, entry.getErrorMessage());
                ps.setArray(6, connection.createArrayOf("TEXT", entry.getTargetNodes() != null ? entry.getTargetNodes().toArray() : null));
                ps.setString(7, entry.getNodeExecutionDetails());
                ps.setTimestamp(8, Timestamp.valueOf(entry.getCreatedAt()));
                return ps;
            }, keyHolder);
        }

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

        throw new RuntimeException("Creating audit entry failed, no ID obtained.");
    }

    /**
     * Check if a script has been successfully executed
     */
    public boolean isScriptExecuted(String scriptName) {
        String sql = """
                SELECT COUNT(*) FROM schemaflow_changelog_audit ca
                INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
                WHERE cs.script_name = ?
                AND ca.id = (
                    SELECT MAX(ca2.id)
                    FROM schemaflow_changelog_audit ca2
                    WHERE ca2.script_id = cs.id
                )
                AND ca.execution_status = 'SUCCESS'
                """;

        Integer count = dbDeployJdbcTemplate.queryForObject(sql, Integer.class, scriptName);
        return count != null && count > 0;
    }

    /**
     * Get the latest audit entry for a specific script
     */
    public AuditEntry getLatestAuditEntry(String scriptName) {
        String sql = """
                SELECT ca.* FROM schemaflow_changelog_audit ca
                INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
                WHERE cs.script_name = ?
                ORDER BY ca.execution_time DESC
                LIMIT 1
                """;
        List<AuditEntry> results = dbDeployJdbcTemplate.query(sql, auditEntryRowMapper, scriptName);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Get the full audit history for a specific script
     */
    public List<AuditEntry> getAuditHistory(String scriptName) {
        String sql = """
                SELECT ca.* FROM schemaflow_changelog_audit ca
                INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
                WHERE cs.script_name = ?
                ORDER BY ca.execution_time DESC
                """;
        return dbDeployJdbcTemplate.query(sql, auditEntryRowMapper, scriptName);
    }

    /**
     * Get all successfully executed scripts (latest status only)
     */
    public List<ChangeLogEntry> getAllExecutedScripts() {
        String sql = """
                SELECT
                    ca.id AS audit_id,
                    ca.script_id,
                    ca.execution_status,
                    ca.execution_time,
                    ca.execution_duration_ms,
                    ca.error_message,
                    ca.target_nodes,
                    ca.node_execution_details,
                    ca.created_at AS audit_created_at,
                    cs.script_name,
                    cs.script_checksum,
                    cs.rollback_script_content,
                    cs.rollback_verify_script_content
                FROM schemaflow_changelog_audit ca
                INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
                WHERE ca.id = (
                    SELECT MAX(ca2.id)
                    FROM schemaflow_changelog_audit ca2
                    WHERE ca2.script_id = cs.id
                )
                AND ca.execution_status IN ('SUCCESS', 'FAILED')
                ORDER BY ca.execution_time ASC
                """;
        return dbDeployJdbcTemplate.query(sql, changeLogEntryRowMapper);
    }

    /**
     * Get scripts that need to be rolled back based on the target changelog
     */
    public List<ChangeLogEntry> getScriptsToRollback(List<String> targetScriptNames) {
        List<ChangeLogEntry> allExecuted = getAllExecutedScripts();
        List<ChangeLogEntry> scriptsToRollback = new ArrayList<>();

        for (ChangeLogEntry entry : allExecuted) {
            if (!targetScriptNames.contains(entry.getScriptName())) {
                scriptsToRollback.add(entry);
            }
        }

        // Return in reverse order (most recent first)
        List<ChangeLogEntry> reversed = new ArrayList<>(scriptsToRollback);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    // ==================== Combined Operations for Backward Compatibility ====================

    /**
     * Record a complete script execution (metadata + audit entry)
     * This is used for backward compatibility with existing code
     */
    public void recordScriptExecution(ChangeLogEntry entry) {
        // First, save or get script metadata
        ScriptMetadata metadata = new ScriptMetadata();
        metadata.setScriptName(entry.getScriptName());
        metadata.setScriptChecksum(entry.getScriptChecksum());
        metadata.setRollbackScriptContent(entry.getRollbackScriptContent());
        metadata.setRollbackVerifyScriptContent(entry.getRollbackVerifyScriptContent());
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
        auditEntry.setTargetNodes(entry.getTargetNodes());
        auditEntry.setNodeExecutionDetails(entry.getNodeExecutionDetails());
        auditEntry.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());

        Long auditId = recordAuditEntry(auditEntry);
        entry.setId(auditId);
    }

    /**
     * Record a rollback script execution as a new audit entry
     */
    public void recordRollbackScriptExecution(ChangeLogEntry entry) {
        // Get or create script metadata
        ScriptMetadata metadata = getScriptMetadata(entry.getScriptName());
        if (metadata == null) {
            metadata = new ScriptMetadata();
            metadata.setScriptName(entry.getScriptName());
            metadata.setScriptChecksum(entry.getScriptChecksum());
            metadata.setRollbackScriptContent(entry.getRollbackScriptContent());
            metadata.setRollbackVerifyScriptContent(entry.getRollbackVerifyScriptContent());
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
        auditEntry.setTargetNodes(entry.getTargetNodes());
        auditEntry.setNodeExecutionDetails(entry.getNodeExecutionDetails());
        auditEntry.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());

        Long auditId = recordAuditEntry(auditEntry);
        entry.setId(auditId);
    }

    // ==================== Lock Operations ====================

    public boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes) {
        if (isSQLiteDatabase()) {
            String sql = "INSERT OR IGNORE INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                    "VALUES (?, ?, datetime('now', '+' || ? || ' minutes'), 1)";
            return dbDeployJdbcTemplate.update(sql, lockKey, lockOwner, timeoutMinutes) > 0;
        } else {
            String sql = String.format(
                    "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                            "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) " +
                            "ON CONFLICT (lock_key) DO NOTHING", timeoutMinutes);
            return dbDeployJdbcTemplate.update(sql, lockKey, lockOwner) > 0;
        }
    }

    public void releaseLock(String lockKey, String lockOwner) {
        String sql = "DELETE FROM schemaflow_deploy_lock WHERE lock_key = ? AND lock_owner = ?";
        dbDeployJdbcTemplate.update(sql, lockKey, lockOwner);
    }

    // ==================== Status and Reporting Operations ====================

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

    /**
     * Get script summary with latest status for all scripts
     * Returns list of scripts with their latest execution status
     */
    public DatabaseStatus.ScriptSummary getScriptSummary() {
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        List<DatabaseStatus.ScriptStatus> scripts = new ArrayList<>();

        String sql = """
                SELECT
                    cs.script_name,
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
                status.setScriptName((String) row.get("script_name"));
                status.setLatestStatus((String) row.get("latest_status"));
                scripts.add(status);
            }

            summary.setScripts(scripts);

            // Calculate summary counts
            summary.setTotalScripts(scripts.size());
            summary.setExecutedScripts((int) scripts.stream()
                    .filter(s -> "SUCCESS".equals(s.getLatestStatus())).count());
            summary.setFailedScripts((int) scripts.stream()
                    .filter(s -> "FAILED".equals(s.getLatestStatus())).count());
            summary.setRolledBackScripts((int) scripts.stream()
                    .filter(s -> "ROLLED_BACK".equals(s.getLatestStatus())).count());

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

    /**
     * Get recent audit history entries (last 10)
     */
    public List<DatabaseStatus.AuditHistoryEntry> getRecentAuditHistory() {
        List<DatabaseStatus.AuditHistoryEntry> history = new ArrayList<>();

        String sql = """
                SELECT
                    ca.id as audit_id,
                    cs.script_name,
                    cs.script_checksum,
                    ca.execution_status,
                    ca.execution_time,
                    ca.execution_duration_ms,
                    ca.error_message,
                    ca.target_nodes,
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
                entry.setExecutionStatus((String) row.get("execution_status"));
                entry.setExecutionTime((String) row.get("execution_time"));

                Object durationMs = row.get("execution_duration_ms");
                if (durationMs != null) {
                    entry.setExecutionDurationMs(((Number) durationMs).longValue());
                }

                entry.setErrorMessage((String) row.get("error_message"));

                // Handle target_nodes (stored as JSON string)
                String targetNodesStr = (String) row.get("target_nodes");
                if (targetNodesStr != null && !targetNodesStr.isEmpty()) {
                    // Parse JSON array - for now, just store as string
                    // In production, you might want to use a JSON parser
                    entry.setTargetNodes(List.of(targetNodesStr.split(",")));
                }

                entry.setNodeExecutionDetails((String) row.get("node_execution_details"));

                history.add(entry);
            }
        } catch (Exception e) {
            log.error("Error getting recent audit history", e);
        }

        return history;
    }
}
