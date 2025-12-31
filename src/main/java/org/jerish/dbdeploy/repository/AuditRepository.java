package org.jerish.dbdeploy.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
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
        entry.setTagName(rs.getString("tag_name"));

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

    /**
     * RowMapper for DeploymentTag
     */
    private final RowMapper<DeploymentTag> deploymentTagRowMapper = (ResultSet rs, int rowNum) -> {
        DeploymentTag tag = new DeploymentTag();
        tag.setId(rs.getLong("id"));
        tag.setTagName(rs.getString("tag_name"));
        tag.setDescription(rs.getString("description"));

        // Handle SQLite timestamp parsing
        String deploymentTimeStr = rs.getString("deployment_time");
        if (deploymentTimeStr != null) {
            try {
                tag.setDeploymentTime(LocalDateTime.parse(deploymentTimeStr));
            } catch (Exception e) {
                tag.setDeploymentTime(LocalDateTime.now());
            }
        } else {
            tag.setDeploymentTime(LocalDateTime.now());
        }

        tag.setCreatedBy(rs.getString("created_by"));
        tag.setIsActive(rs.getBoolean("is_active"));
        return tag;
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
                    rollback_script_content, rollback_verify_script_content, tag_name, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.setString(9, entry.getTagName());
                ps.setString(10, entry.getCreatedAt().toString());
                ps.setString(11, entry.getUpdatedAt().toString());
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
                ps.setString(9, entry.getTagName());
                ps.setTimestamp(10, Timestamp.valueOf(entry.getCreatedAt()));
                ps.setTimestamp(11, Timestamp.valueOf(entry.getUpdatedAt()));
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

    public List<ChangeLogEntry> getScriptsExecutedAfter(String tagName) {
        // Special case: empty tagName means rollback to initial state - return all changelogs
        if (tagName == null || tagName.isEmpty()) {
            String sql = """
                    SELECT * FROM db_change_log 
                    WHERE tag_name IS NOT NULL
                    ORDER BY execution_time DESC
                    """;
            return dbDeployJdbcTemplate.query(sql, changeLogEntryRowMapper);
        }

        // Normal case: get scripts after specific tag
        DeploymentTag targetTag = getDeploymentTag(tagName);
        if (targetTag == null) {
            throw new RuntimeException("Deployment tag not found: " + tagName);
        }

        String sql = """
                SELECT * FROM db_change_log 
                WHERE tag_name IS NOT NULL 
                AND tag_name NOT IN (
                    SELECT tag_name FROM deployment_tags 
                    WHERE id <= ?
                )
                ORDER BY execution_time DESC
                """;

        return dbDeployJdbcTemplate.query(sql, changeLogEntryRowMapper, targetTag.getId());
    }

    public void createDeploymentTag(DeploymentTag tag) {
        String sql = """
                INSERT INTO deployment_tags (tag_name, description, deployment_time, created_by, is_active)
                VALUES (?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        if (isSQLiteDatabase()) {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, tag.getTagName());
                ps.setString(2, tag.getDescription());
                ps.setString(3, tag.getDeploymentTime().toString());
                ps.setString(4, tag.getCreatedBy());
                ps.setInt(5, tag.getIsActive() ? 1 : 0);
                return ps;
            }, keyHolder);
        } else {
            dbDeployJdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, tag.getTagName());
                ps.setString(2, tag.getDescription());
                ps.setTimestamp(3, Timestamp.valueOf(tag.getDeploymentTime()));
                ps.setString(4, tag.getCreatedBy());
                ps.setBoolean(5, tag.getIsActive());
                return ps;
            }, keyHolder);
        }

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            tag.setId(generatedId.longValue());
        } else {
            throw new RuntimeException("Creating deployment tag failed, no ID obtained.");
        }
    }

    public DeploymentTag getDeploymentTag(String tagName) {
        String sql = "SELECT * FROM deployment_tags WHERE tag_name = ? AND is_active = TRUE";

        try {
            return dbDeployJdbcTemplate.queryForObject(sql, deploymentTagRowMapper, tagName);
        } catch (Exception e) {
            return null;
        }
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

    public List<DeploymentTag> getDeploymentTags() {
        String sql = "SELECT * FROM deployment_tags ORDER BY deployment_time DESC";
        return dbDeployJdbcTemplate.query(sql, deploymentTagRowMapper);
    }


    public void deactivateDeploymentTag(String tagName) {
        String sql = "UPDATE deployment_tags SET is_active = FALSE WHERE tag_name = ?";

        int rowsUpdated = dbDeployJdbcTemplate.update(sql, tagName);

        if (rowsUpdated > 0) {
            log.info("Deactivated deployment tag: {}", tagName);
        } else {
            log.warn("No deployment tag found to deactivate: {}", tagName);
        }
    }

    // Status-related methods moved from DefaultDatabaseDeployService

    /**
     * Get current deployment state from database
     */
    public DatabaseStatus.DeploymentStateInfo getCurrentDeploymentState() {
        String sql = """
                SELECT tag_name, description, deployment_time, created_by,
                       total_scripts, successful_scripts, failed_scripts, rolled_back_scripts
                FROM current_deployment_state
                LIMIT 1
                """;

        try {
            List<Map<String, Object>> results = dbDeployJdbcTemplate.queryForList(sql);
            if (!results.isEmpty()) {
                Map<String, Object> result = results.get(0);
                DatabaseStatus.DeploymentStateInfo info = new DatabaseStatus.DeploymentStateInfo();
                info.setCurrentTag((String) result.get("tag_name"));
                info.setDescription((String) result.get("description"));
                info.setDeploymentTime((String) result.get("deployment_time"));
                info.setCreatedBy((String) result.get("created_by"));
                info.setTotalScripts(((Number) result.get("total_scripts")).intValue());
                info.setSuccessfulScripts(((Number) result.get("successful_scripts")).intValue());
                info.setFailedScripts(((Number) result.get("failed_scripts")).intValue());
                info.setRolledBackScripts(((Number) result.get("rolled_back_scripts")).intValue());
                return info;
            }
        } catch (Exception e) {
            log.debug("No current deployment state found", e);
        }
        return null;
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
                    WHERE type='table' AND name IN ('db_change_log', 'deployment_tags', 'database_lock')
                    """;

            try {
                Integer tableCount = dbDeployJdbcTemplate.queryForObject(tableCheckSql, Integer.class);
                info.setValid(tableCount != null && tableCount >= 3);
                info.setMessage(tableCount != null && tableCount >= 3 ?
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
        String sql = """
                SELECT dt.tag_name, dt.description, dt.deployment_time, dt.created_by,
                       COUNT(dcl.id) as script_count,
                       CASE 
                           WHEN COUNT(CASE WHEN dcl.execution_status = 'FAILED' THEN 1 END) > 0 THEN 'FAILED'
                           WHEN COUNT(CASE WHEN dcl.execution_status = 'ROLLED_BACK' THEN 1 END) > 0 THEN 'ROLLED_BACK'
                           ELSE 'SUCCESS'
                       END as status
                FROM deployment_tags dt
                LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
                GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
                ORDER BY dt.deployment_time DESC
                LIMIT 10
                """;

        try {
            List<Map<String, Object>> historyResults = dbDeployJdbcTemplate.queryForList(sql);
            List<DatabaseStatus.DeploymentHistoryEntry> history = new ArrayList<>();

            for (Map<String, Object> row : historyResults) {
                DatabaseStatus.DeploymentHistoryEntry entry = new DatabaseStatus.DeploymentHistoryEntry();
                entry.setTagName((String) row.get("tag_name"));
                entry.setDescription((String) row.get("description"));
                entry.setDeploymentTime((String) row.get("deployment_time"));
                entry.setDeployedBy((String) row.get("created_by"));
                entry.setScriptCount(((Number) row.get("script_count")).intValue());
                entry.setStatus((String) row.get("status"));
                history.add(entry);
            }
            return history;
        } catch (Exception e) {
            log.error("Error getting deployment history", e);
            return new ArrayList<>();
        }
    }
}