package org.jerish.dbdeploy.dao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@Slf4j
@RequiredArgsConstructor
public class AuditDao {

    private final JdbcTemplate jdbcTemplate;
    private final SchemaInitializationManager schemaInitializationManager;

    public void initializeSchema() {
        log.info("Initializing database schema using strategy pattern");
        schemaInitializationManager.initializeSchemaIfNeeded();
    }

    /**
     * Helper method to check if the database is SQLite
     */
    private boolean isSQLiteDatabase() {
        try {
            return jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class) != null;
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

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, scriptName);
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
            jdbcTemplate.update(connection -> {
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
            jdbcTemplate.update(connection -> {
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
            jdbcTemplate.update(sql, status.getValue(), errorMessage, LocalDateTime.now().toString(), id);
        } else {
            jdbcTemplate.update(sql, status.getValue(), errorMessage, Timestamp.valueOf(LocalDateTime.now()), id);
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
            return jdbcTemplate.query(sql, changeLogEntryRowMapper);
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

        return jdbcTemplate.query(sql, changeLogEntryRowMapper, targetTag.getId());
    }

    public void createDeploymentTag(DeploymentTag tag) {
        String sql = """
                INSERT INTO deployment_tags (tag_name, description, deployment_time, created_by, is_active)
                VALUES (?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        if (isSQLiteDatabase()) {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, tag.getTagName());
                ps.setString(2, tag.getDescription());
                ps.setString(3, tag.getDeploymentTime().toString());
                ps.setString(4, tag.getCreatedBy());
                ps.setInt(5, tag.getIsActive() ? 1 : 0);
                return ps;
            }, keyHolder);
        } else {
            jdbcTemplate.update(connection -> {
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
            return jdbcTemplate.queryForObject(sql, deploymentTagRowMapper, tagName);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes) {
        if (isSQLiteDatabase()) {
            // SQLite doesn't support INTERVAL, use datetime function
            String sql = "INSERT OR IGNORE INTO database_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                    "VALUES (?, ?, datetime('now', '+' || ? || ' minutes'), 1)";
            return jdbcTemplate.update(sql, lockKey, lockOwner, timeoutMinutes) > 0;
        } else {
            // PostgreSQL syntax
            String sql = String.format(
                    "INSERT INTO database_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                            "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) " +
                            "ON CONFLICT (lock_key) DO NOTHING", timeoutMinutes);
            return jdbcTemplate.update(sql, lockKey, lockOwner) > 0;
        }
    }

    public void releaseLock(String lockKey, String lockOwner) {
        String sql = "DELETE FROM database_lock WHERE lock_key = ? AND lock_owner = ?";
        jdbcTemplate.update(sql, lockKey, lockOwner);
    }

    public List<DeploymentTag> getDeploymentTags() {
        String sql = "SELECT * FROM deployment_tags ORDER BY deployment_time DESC";
        return jdbcTemplate.query(sql, deploymentTagRowMapper);
    }


    public void deactivateDeploymentTag(String tagName) {
        String sql = "UPDATE deployment_tags SET is_active = FALSE WHERE tag_name = ?";

        int rowsUpdated = jdbcTemplate.update(sql, tagName);

        if (rowsUpdated > 0) {
            log.info("Deactivated deployment tag: {}", tagName);
        } else {
            log.warn("No deployment tag found to deactivate: {}", tagName);
        }
    }
}