package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogAuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogScript;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
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

    @Override
    public Long createScriptMetadata(ChangeLogScript metadata) {
        String sql = getCreateScriptMetadataSql();
        return dbDeployJdbcTemplate.queryForObject(sql, Long.class,
                metadata.getScriptName(),
                metadata.getScriptChecksum(),
                metadata.getRollbackScriptContent(),
                metadata.getRollbackVerifyScriptContent(),
                metadata.getTargetNodes() != null ? String.join(",", metadata.getTargetNodes()) : null,
                prepareTimestamp(metadata.getCreatedAt()));

    }

    private String getCreateScriptMetadataSql() {
        return """
                INSERT INTO schemaflow_changelog_script (
                    script_name, script_checksum,
                    rollback_script_content, rollback_verify_script_content, target_nodes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """;
    }

    @Override
    public void createScriptAuditEntry(ChangeLogAuditEntry entry) {
        String sql = getRecordAuditEntrySql();
        dbDeployJdbcTemplate.update(sql,
                entry.getScriptId(),
                entry.getExecutionStatus().getValue(),
                prepareTimestamp(entry.getExecutionTime()),
                entry.getExecutionDurationMs(),
                entry.getErrorMessage(),
                entry.getNodeExecutionDetails(),
                prepareTimestamp(entry.getCreatedAt()));
    }

    protected Object prepareTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return Timestamp.valueOf(LocalDateTime.now());
        }
        return Timestamp.valueOf(dateTime);
    }

    protected String getRecordAuditEntrySql() {
        return """
                INSERT INTO schemaflow_changelog_audit (
                    script_id, execution_status, execution_time,
                    execution_duration_ms, error_message,
                    node_execution_details, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
    }

    // ==================== Lock Operations ====================
    @Override
    public boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes) {
        String sql = getAcquireLockSql(timeoutMinutes);
        return dbDeployJdbcTemplate.update(sql, lockKey, lockOwner) > 0;
    }

    protected abstract String getAcquireLockSql(int timeoutMinutes);

    @Override
    public void releaseLock(String lockKey, String lockOwner) {
        String sql = "DELETE FROM schemaflow_deploy_lock WHERE lock_key = ? AND lock_owner = ?";
        dbDeployJdbcTemplate.update(sql, lockKey, lockOwner);
    }

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
        List<DatabaseStatus.ChangeLogScriptStatus> scripts = new ArrayList<>();

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
                ORDER BY cs.id ASC
                """;

        try {
            List<Map<String, Object>> results = dbDeployJdbcTemplate.queryForList(sql);

            // Track seen script names to only show the latest entry for each script
            java.util.Set<String> seenScriptNames = new java.util.HashSet<>();

            for (Map<String, Object> row : results) {
                String scriptName = (String) row.get("script_name");
                
                // Skip if we've already seen this script name (show only the latest)
                if (seenScriptNames.contains(scriptName)) {
                    continue;
                }
                seenScriptNames.add(scriptName);

                DatabaseStatus.ChangeLogScriptStatus status = new DatabaseStatus.ChangeLogScriptStatus();
                status.setId(((Number) row.get("id")).longValue());
                status.setScriptName(scriptName);
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

    private List<String> parseTargetNodes(Object targetNodesObj) {
        if (targetNodesObj == null) {
            return null;
        }

        String targetNodesStr = targetNodesObj.toString();
        if (targetNodesStr == null || targetNodesStr.trim().isEmpty()) {
            return null;
        }

        return List.of(targetNodesStr.split(","));
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
                ORDER BY ca.id DESC
                LIMIT 100
                """;

        try {
            List<Map<String, Object>> results = dbDeployJdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : results) {
                DatabaseStatus.AuditHistoryEntry entry = new DatabaseStatus.AuditHistoryEntry();
                entry.setAuditId(((Number) row.get("audit_id")).longValue());
                entry.setScriptName((String) row.get("script_name"));
                entry.setScriptChecksum((String) row.get("script_checksum"));
                entry.setExecutionStatus(ScriptExecutionStatus.fromValue((String) row.get("execution_status")));
                entry.setExecutionTime(parseTimestamp(row.get("execution_time")));

                Object durationMs = row.get("execution_duration_ms");
                if (durationMs != null) {
                    entry.setExecutionDurationMs(((Number) durationMs).longValue());
                }

                entry.setErrorMessage((String) row.get("error_message"));

                // Handle target_nodes from script table (stored as comma-separated string)
                entry.setTargetNodes(parseTargetNodes(row.get("target_nodes")));

                entry.setNodeExecutionDetails((String) row.get("node_execution_details"));

                history.add(entry);
            }
        } catch (Exception e) {
            log.error("Error getting recent audit history", e);
        }

        return history;
    }

    protected LocalDateTime parseTimestamp(Object timestampObj) {
        // PostgreSQL: Parse from Timestamp
        if (timestampObj == null) {
            return LocalDateTime.now();
        }
        if (timestampObj instanceof Timestamp) {
            return parseLocalDateTime((Timestamp) timestampObj);
        }
        // Fallback: try to parse as string
        try {
            return LocalDateTime.parse(timestampObj.toString());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private LocalDateTime parseLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        return timestamp.toLocalDateTime();
    }
}