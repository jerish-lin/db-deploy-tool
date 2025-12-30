package org.jerish.dbdeploy.dao;

import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.jerish.dbdeploy.schema.SchemaInitializationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class AuditDao {

    private final DatabaseConnectionManager connectionManager;
    private final SchemaInitializationManager schemaInitializationManager;

    @Autowired
    public AuditDao(DatabaseConnectionManager connectionManager,
                    SchemaInitializationManager schemaInitializationManager) {
        this.connectionManager = connectionManager;
        this.schemaInitializationManager = schemaInitializationManager;
    }

    public void initializeSchema() throws SQLException {
        log.info("Initializing database schema using strategy pattern");
        schemaInitializationManager.initializeSchemaIfNeeded();
    }


    public boolean isScriptExecuted(String scriptName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM db_change_log WHERE script_name = ? AND execution_status = 'SUCCESS'";

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, scriptName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    public void recordScriptExecution(ChangeLogEntry entry) throws SQLException {
        String sql = """
                INSERT INTO db_change_log (
                    script_name, script_checksum, execution_status,
                    execution_time, execution_duration_ms, error_message,
                    rollback_script_content, rollback_verify_script_content, tag_name, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, entry.getScriptName());
            statement.setString(2, entry.getScriptChecksum());
            statement.setString(3, entry.getExecutionStatus().getValue());

            // Check if it's SQLite by checking the URL
            String url = connection.getMetaData().getURL();
            boolean isSQLite = url.toLowerCase().contains("sqlite");

            if (isSQLite) {
                statement.setString(4, entry.getExecutionTime().toString());
            } else {
                statement.setTimestamp(4, Timestamp.valueOf(entry.getExecutionTime()));
            }

            statement.setObject(5, entry.getExecutionDurationMs());
            statement.setString(6, entry.getErrorMessage());
            statement.setString(7, entry.getRollbackScriptContent());
            statement.setString(8, entry.getRollbackVerifyScriptContent());
            statement.setString(9, entry.getTagName());

            if (isSQLite) {
                statement.setString(10, entry.getCreatedAt().toString());
                statement.setString(11, entry.getUpdatedAt().toString());
            } else {
                statement.setTimestamp(10, Timestamp.valueOf(entry.getCreatedAt()));
                statement.setTimestamp(11, Timestamp.valueOf(entry.getUpdatedAt()));
            }

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating script execution record failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    entry.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Creating script execution record failed, no ID obtained.");
                }
            }
        }
    }

    public void updateScriptExecutionStatus(Long id, ScriptExecutionStatus status, String errorMessage) throws SQLException {
        String sql = """
                UPDATE db_change_log 
                SET execution_status = ?, error_message = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status.getValue());
            statement.setString(2, errorMessage);

            // Check if it's SQLite by checking the URL
            String url = connection.getMetaData().getURL();
            boolean isSQLite = url.toLowerCase().contains("sqlite");

            if (isSQLite) {
                statement.setString(3, LocalDateTime.now().toString());
            } else {
                statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            }

            statement.setLong(4, id);

            statement.executeUpdate();
        }
    }

    public List<ChangeLogEntry> getScriptsExecutedAfter(String tagName) throws SQLException {
        // Special case: empty tagName means rollback to initial state - return all changelogs
        if (tagName == null || tagName.isEmpty()) {
            String sql = """
                    SELECT * FROM db_change_log 
                    WHERE tag_name IS NOT NULL
                    ORDER BY execution_time DESC
                    """;

            List<ChangeLogEntry> entries = new ArrayList<>();

            try (Connection connection = connectionManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(mapResultSetToChangeLogEntry(resultSet));
                    }
                }
            }

            return entries;
        }

        // Normal case: get scripts after specific tag
        DeploymentTag targetTag = getDeploymentTag(tagName);
        if (targetTag == null) {
            throw new SQLException("Deployment tag not found: " + tagName);
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

        List<ChangeLogEntry> entries = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, targetTag.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(mapResultSetToChangeLogEntry(resultSet));
                }
            }
        }

        return entries;
    }

    public void createDeploymentTag(DeploymentTag tag) throws Exception {
        String sql = """
                INSERT INTO deployment_tags (tag_name, description, deployment_time, created_by, is_active)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, tag.getTagName());
            statement.setString(2, tag.getDescription());

            // Check if it's SQLite by checking the URL
            String url = connection.getMetaData().getURL();
            boolean isSQLite = url.toLowerCase().contains("sqlite");

            if (isSQLite) {
                statement.setString(3, tag.getDeploymentTime().toString());
            } else {
                statement.setTimestamp(3, Timestamp.valueOf(tag.getDeploymentTime()));
            }

            statement.setString(4, tag.getCreatedBy());

            if (isSQLite) {
                statement.setInt(5, tag.getIsActive() ? 1 : 0);
            } else {
                statement.setBoolean(5, tag.getIsActive());
            }

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating deployment tag failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    tag.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Creating deployment tag failed, no ID obtained.");
                }
            }
        }
    }

    public DeploymentTag getDeploymentTag(String tagName) throws SQLException {
        String sql = "SELECT * FROM deployment_tags WHERE tag_name = ? AND is_active = TRUE";

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tagName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToDeploymentTag(resultSet);
                }
            }
        }

        return null;
    }

    public boolean acquireLock(String lockKey, String lockOwner, int timeoutMinutes) throws SQLException {
        String sql;

        try (Connection connection = connectionManager.getConnection()) {
            // Check if it's SQLite by checking the URL
            String url = connection.getMetaData().getURL();
            boolean isSQLite = url.toLowerCase().contains("sqlite");

            if (isSQLite) {
                // SQLite doesn't support INTERVAL, use datetime function
                sql = "INSERT OR IGNORE INTO database_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                        "VALUES (?, ?, datetime('now', '+' || ? || ' minutes'), 1)";
            } else {
                // PostgreSQL syntax
                sql = String.format(
                        "INSERT INTO database_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                                "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) " +
                                "ON CONFLICT (lock_key) DO NOTHING", timeoutMinutes);
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, lockKey);
                statement.setString(2, lockOwner);
                if (isSQLite) {
                    statement.setInt(3, timeoutMinutes);
                }

                return statement.executeUpdate() > 0;
            }
        }
    }

    public void releaseLock(String lockKey, String lockOwner) throws SQLException {
        String sql = "DELETE FROM database_lock WHERE lock_key = ? AND lock_owner = ?";

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, lockKey);
            statement.setString(2, lockOwner);

            statement.executeUpdate();
        }
    }

    public List<DeploymentTag> getDeploymentTags() throws SQLException {
        String sql = "SELECT * FROM deployment_tags ORDER BY deployment_time DESC";
        List<DeploymentTag> tags = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                tags.add(mapResultSetToDeploymentTag(resultSet));
            }
        }

        return tags;
    }

    private ChangeLogEntry mapResultSetToChangeLogEntry(ResultSet resultSet) throws SQLException {
        ChangeLogEntry entry = new ChangeLogEntry();
        entry.setId(resultSet.getLong("id"));
        entry.setScriptName(resultSet.getString("script_name"));
        entry.setScriptChecksum(resultSet.getString("script_checksum"));
        entry.setExecutionStatus(ScriptExecutionStatus.fromValue(resultSet.getString("execution_status")));

        // Handle SQLite timestamp parsing
        String executionTimeStr = resultSet.getString("execution_time");
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
            entry.setExecutionDurationMs(resultSet.getObject("execution_duration_ms", Long.class));
        } catch (SQLException e) {
            // Handle case where execution_duration_ms might be NULL or invalid type
            entry.setExecutionDurationMs(null);
        }
        entry.setErrorMessage(resultSet.getString("error_message"));
        entry.setRollbackScriptContent(resultSet.getString("rollback_script_content"));
        entry.setRollbackVerifyScriptContent(resultSet.getString("rollback_verify_script_content"));
        entry.setTagName(resultSet.getString("tag_name"));

        // Handle created_at timestamp
        String createdAtStr = resultSet.getString("created_at");
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
        String updatedAtStr = resultSet.getString("updated_at");
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
    }

    private DeploymentTag mapResultSetToDeploymentTag(ResultSet resultSet) throws SQLException {
        DeploymentTag tag = new DeploymentTag();
        tag.setId(resultSet.getLong("id"));
        tag.setTagName(resultSet.getString("tag_name"));
        tag.setDescription(resultSet.getString("description"));

        // Handle SQLite timestamp parsing
        String deploymentTimeStr = resultSet.getString("deployment_time");
        if (deploymentTimeStr != null) {
            try {
                tag.setDeploymentTime(LocalDateTime.parse(deploymentTimeStr));
            } catch (Exception e) {
                // If parsing fails, use current time
                tag.setDeploymentTime(LocalDateTime.now());
            }
        } else {
            tag.setDeploymentTime(LocalDateTime.now());
        }

        tag.setCreatedBy(resultSet.getString("created_by"));
        tag.setIsActive(resultSet.getBoolean("is_active"));
        return tag;
    }

    public void deactivateDeploymentTag(String tagName) throws SQLException {
        String sql = "UPDATE deployment_tags SET is_active = FALSE WHERE tag_name = ?";

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, tagName);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                log.info("Deactivated deployment tag: {}", tagName);
            } else {
                log.warn("No deployment tag found to deactivate: {}", tagName);
            }
        }
    }
}