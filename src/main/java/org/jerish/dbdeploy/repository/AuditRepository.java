package org.jerish.dbdeploy.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.entity.DatabaseStatus;
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
     * RowMapper for ChangeLogEntry
     */
    private final RowMapper<ChangeLogEntry> changeLogEntryRowMapper = (ResultSet rs, int rowNum) -> {
        ChangeLogEntry entry = new ChangeLogEntry();
        entry.setId(rs.getLong("id"));
        entry.setScriptName(rs.getString("script_name"));
        entry.setScriptChecksum(rs.getString("script_checksum"));
        entry.setExecutionStatus(ScriptExecutionStatus.fromValue(rs.getString("execution_status")));

        // Handle SQLite timestamp parsing
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
        entry.setRollbackScriptContent(rs.getString("rollback_script_content"));
        entry.setRollbackVerifyScriptContent(rs.getString("rollback_verify_script_content"));

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

        // Handle updated_at timestamp
        String updatedAtStr = rs.getString("updated_at");
        if (updatedAtStr != null) {
            try {
                entry.setUpdatedAt(LocalDateTime.parse(updatedAtStr));
            } catch (Exception e) {
                entry.setUpdatedAt(LocalDateTime.now());
            }
        } else {
            entry.setUpdatedAt(LocalDateTime.now());
        }

        return entry;
    };

    public boolean isScriptExecuted(String scriptName) {
        String sql = "SELECT COUNT(*) FROM db_change_log WHERE script_name = ? AND execution_status = 'SUCCESS'";

        Integer count = dbDeployJdbcTemplate.queryForObject(sql, Integer.class, scriptName);
        return count != null && count > 0;
    }

    public void recordScriptExecution(ChangeLogEntry entry) {
        String sql = """
                INSERT INTO db_change_log (
                    script_name, script_checksum, execution_status,
                    execution_time, execution_duration_ms, error_message,
                    rollback_script_content, rollback_verify_script_content, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        if (isSQLiteDatabase()) {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, entry.getScriptName());
                ps.setString(2, entry.getScriptChecksum());
                ps.setString(3, entry.getExecutionStatus().getValue());
                ps.setString(4, entry.getExecutionTime().toString());
                ps.setObject(5, entry.getExecutionDurationMs());
                ps.setString(6, entry.getErrorMessage());
                ps.setString(7, entry.getRollbackScriptContent());
                ps.setString(8, entry.getRollbackVerifyScriptContent());
                ps.setString(9, entry.getCreatedAt().toString());
                ps.setString(10, entry.getUpdatedAt().toString());
                return ps;
            }, keyHolder);
        } else {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, entry.getScriptName());
                ps.setString(2, entry.getScriptChecksum());
                ps.setString(3, entry.getExecutionStatus().getValue());
                ps.setTimestamp(4, Timestamp.valueOf(entry.getExecutionTime()));
                ps.setObject(5, entry.getExecutionDurationMs());
                ps.setString(6, entry.getErrorMessage());
                ps.setString(7, entry.getRollbackScriptContent());
                ps.setString(8, entry.getRollbackVerifyScriptContent());
                ps.setTimestamp(9, Timestamp.valueOf(entry.getCreatedAt()));
                ps.setTimestamp(10, Timestamp.valueOf(entry.getUpdatedAt()));
                return ps;
            }, keyHolder);
        }

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            entry.setId(generatedId.longValue());
        } else {
            throw new RuntimeException("Creating script execution record failed, no ID obtained.");
        }
    }

    public void updateScriptExecutionStatus(Long id, ScriptExecutionStatus status, String errorMessage) {
        String sql = """
                UPDATE db_change_log 
                SET execution_status = ?, error_message = ?, updated_at = ?
                WHERE id = ?
                """;

        if (isSQLiteDatabase()) {
            dbDeployJdbcTemplate.update(sql, status.getValue(), errorMessage, LocalDateTime.now().toString(), id);
        } else {
            dbDeployJdbcTemplate.update(sql, status.getValue(), errorMessage, Timestamp.valueOf(LocalDateTime.now()), id);
        }
    }

    /**
     * Get all successfully executed scripts from the database
     */
    public List<ChangeLogEntry> getAllExecutedScripts() {
        String sql = """
                SELECT * FROM db_change_log 
                WHERE execution_status = 'SUCCESS'
                ORDER BY execution_time ASC
                """;
        return dbDeployJdbcTemplate.query(sql, changeLogEntryRowMapper);
    }

    /**
     * Get scripts that need to be rolled back based on the target changelog
     * Returns scripts that are in the database but NOT in the target changelog
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

    public boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes) {
        if (isSQLiteDatabase()) {
            // SQLite doesn't support INTERVAL, use datetime function
            String sql = "INSERT OR IGNORE INTO database_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                    "VALUES (?, ?, datetime('now', '+' || ? || ' minutes'), 1)";
            return dbDeployJdbcTemplate.update(sql, lockKey, lockOwner, timeoutMinutes) > 0;
        } else {
            // PostgreSQL syntax
            String sql = String.format(
                    "INSERT INTO database_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                            "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) " +
                            "ON CONFLICT (lock_key) DO NOTHING", timeoutMinutes);
            return dbDeployJdbcTemplate.update(sql, lockKey, lockOwner) > 0;
        }
    }

    public void releaseLock(String lockKey, String lockOwner) {
        String sql = "DELETE FROM database_lock WHERE lock_key = ? AND lock_owner = ?";
        dbDeployJdbcTemplate.update(sql, lockKey, lockOwner);
    }

    // Status-related methods moved from DefaultDatabaseDeployService

    /**
     * Get current deployment state from database
     */
    public DatabaseStatus.DeploymentStateInfo getCurrentDeploymentState() {
        DatabaseStatus.DeploymentStateInfo info = new DatabaseStatus.DeploymentStateInfo();
        info.setCurrentTag("");
        info.setDescription("");
        info.setDeploymentTime("");
        info.setCreatedBy("");

        // Count scripts by status
        String totalSql = "SELECT COUNT(*) FROM db_change_log";
        String successSql = "SELECT COUNT(*) FROM db_change_log WHERE execution_status = 'SUCCESS'";
        String failedSql = "SELECT COUNT(*) FROM db_change_log WHERE execution_status = 'FAILED'";
        String rolledBackSql = "SELECT COUNT(*) FROM db_change_log WHERE execution_status = 'ROLLED_BACK'";

        try {
            info.setTotalScripts(dbDeployJdbcTemplate.queryForObject(totalSql, Integer.class, 0));
            info.setSuccessfulScripts(dbDeployJdbcTemplate.queryForObject(successSql, Integer.class, 0));
            info.setFailedScripts(dbDeployJdbcTemplate.queryForObject(failedSql, Integer.class, 0));
            info.setRolledBackScripts(dbDeployJdbcTemplate.queryForObject(rolledBackSql, Integer.class, 0));
        } catch (Exception e) {
            log.debug("Error getting deployment state", e);
        }

        return info;
    }

    /**
     * Get total rolled back scripts count across all tags
     */
    public int getTotalRolledBackScripts() {
        String sql = """
                SELECT COUNT(*) as total_rolled_back
                FROM db_change_log
                WHERE execution_status = 'ROLLED_BACK'
                """;
        try {
            Integer totalRolledBack = dbDeployJdbcTemplate.queryForObject(sql, Integer.class);
            return totalRolledBack != null ? totalRolledBack : 0;
        } catch (Exception e) {
            log.debug("Error getting total rolled back count", e);
            return 0;
        }
    }

    /**
     * Get script execution history
     */
    public List<DatabaseStatus.ScriptExecutionInfo> getScriptExecutionHistory() {
        String sql = """
                SELECT script_name, execution_status
                FROM script_execution_history
                ORDER BY execution_time DESC
                LIMIT 50
                """;

        try {
            List<Map<String, Object>> scriptResults = dbDeployJdbcTemplate.queryForList(sql);
            List<DatabaseStatus.ScriptExecutionInfo> history = new ArrayList<>();

            for (Map<String, Object> row : scriptResults) {
                DatabaseStatus.ScriptExecutionInfo info = new DatabaseStatus.ScriptExecutionInfo();
                info.setScriptName((String) row.get("script_name"));
                info.setExecutionStatus((String) row.get("execution_status"));
                history.add(info);
            }
            return history;
        } catch (Exception e) {
            log.error("Error getting script execution history", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get failed scripts with detailed information
     */
    public List<DatabaseStatus.FailedScriptInfo> getFailedScripts() {
        String sql = """
                SELECT script_name, error_message, execution_time
                FROM failed_scripts
                ORDER BY execution_time DESC
                """;

        try {
            List<Map<String, Object>> failedResults = dbDeployJdbcTemplate.queryForList(sql);
            List<DatabaseStatus.FailedScriptInfo> failedScripts = new ArrayList<>();

            for (Map<String, Object> row : failedResults) {
                DatabaseStatus.FailedScriptInfo info = new DatabaseStatus.FailedScriptInfo();
                info.setScriptName((String) row.get("script_name"));
                info.setErrorMessage((String) row.get("error_message"));
                info.setExecutionTime((String) row.get("execution_time"));
                failedScripts.add(info);
            }
            return failedScripts;
        } catch (Exception e) {
            log.error("Error getting failed scripts", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get current deployment lock status
     */
    public DatabaseStatus.LockInfo getCurrentLockStatus() {
        String sql = """
                SELECT lock_owner, lock_acquired_at, lock_expires_at, is_active
                FROM database_lock
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
     * Get database version and health information
     */
    public DatabaseStatus.DatabaseHealthInfo getDatabaseHealthInfo() {
        DatabaseStatus.DatabaseHealthInfo info = new DatabaseStatus.DatabaseHealthInfo();
        
        try {
            // Try SQLite version first
            String versionSql = dbDeployJdbcTemplate.queryForObject(
                    "SELECT sqlite_version()", String.class);
            info.setVersion(versionSql);
            info.setHealthy(true);
            info.setHealthMessage("Database health check passed");
        } catch (Exception e) {
            // For non-SQLite databases, try a generic query
            try {
                dbDeployJdbcTemplate.queryForObject("SELECT 1", Integer.class);
                info.setVersion("Unknown");
                info.setHealthy(true);
            } catch (Exception ex) {
                info.setHealthy(false);
                info.setHealthMessage("Database health check failed");
            }
        }
        return info;
    }

    /**
     * Check configuration status (audit tables existence)
     */
    public DatabaseStatus.ConfigurationInfo getConfigurationInfo() {
        DatabaseStatus.ConfigurationInfo info = new DatabaseStatus.ConfigurationInfo();
        
        try {
            // Check if audit tables exist and are accessible (SQLite)
            String tableCheckSql = """
                    SELECT COUNT(*) as table_count
                    FROM sqlite_master
                    WHERE type='table' AND name IN ('db_change_log', 'database_lock')
                    """;

            try {
                Integer tableCount = dbDeployJdbcTemplate.queryForObject(tableCheckSql, Integer.class);
                info.setValid(tableCount != null && tableCount >= 2);
                info.setMessage(tableCount != null && tableCount >= 2 ?
                        "Audit tables present" : "Missing audit tables");
            } catch (Exception e) {
                // For non-SQLite databases
                try {
                    dbDeployJdbcTemplate.queryForObject("SELECT COUNT(*) FROM db_change_log", Integer.class);
                    info.setValid(true);
                    info.setMessage("Audit tables accessible");
                } catch (Exception ex) {
                    info.setValid(false);
                    info.setMessage("Audit tables not accessible");
                }
            }
        } catch (Exception e) {
            info.setValid(false);
            info.setMessage("Configuration check failed");
        }
        return info;
    }

    /**
     * Get recent deployment history
     */
    public List<DatabaseStatus.DeploymentHistoryEntry> getRecentDeploymentHistory() {
        // Return empty list since we no longer have deployment tags
        return new ArrayList<>();
    }
}