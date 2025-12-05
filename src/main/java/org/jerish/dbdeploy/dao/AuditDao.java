package org.jerish.dbdeploy.dao;

import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.model.DeploymentTag;
import org.jerish.dbdeploy.model.ScriptExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditDao {
    private static final Logger logger = LoggerFactory.getLogger(AuditDao.class);

    private final DatabaseConnectionManager connectionManager;

    public AuditDao(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void initializeSchema() throws SQLException {
        // Check if it's SQLite by checking the URL
        try (Connection connection = connectionManager.getConnection()) {
            String url = connection.getMetaData().getURL();
            boolean isSQLite = url.toLowerCase().contains("sqlite");

            String createChangeLogTable;
            String createDeploymentTagsTable;
            String createDatabaseLockTable;

            if (isSQLite) {
                createChangeLogTable = """
                        CREATE TABLE IF NOT EXISTS db_change_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            script_id TEXT NOT NULL,
                            script_name TEXT NOT NULL,
                            script_path TEXT NOT NULL,
                            script_checksum TEXT NOT NULL,
                            execution_status TEXT NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
                            execution_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            execution_duration_ms INTEGER,
                            error_message TEXT,
                            rollback_script_path TEXT,
                            rollback_script_content TEXT,
                            tag_name TEXT,
                            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """;

                createDeploymentTagsTable = """
                        CREATE TABLE IF NOT EXISTS deployment_tags (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            tag_name TEXT NOT NULL UNIQUE,
                            description TEXT,
                            deployment_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            build_version TEXT,
                            created_by TEXT,
                            environment TEXT,
                            is_active INTEGER DEFAULT 1
                        )
                        """;

                createDatabaseLockTable = """
                        CREATE TABLE IF NOT EXISTS database_lock (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            lock_key TEXT NOT NULL UNIQUE,
                            lock_owner TEXT,
                            lock_acquired_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            lock_expires_at TEXT,
                            is_active INTEGER DEFAULT 1
                        )
                        """;
            } else {
                createChangeLogTable = """
                        CREATE TABLE IF NOT EXISTS db_change_log (
                            id BIGSERIAL PRIMARY KEY,
                            script_id VARCHAR(255) NOT NULL,
                            script_name VARCHAR(500) NOT NULL,
                            script_path VARCHAR(1000) NOT NULL,
                            script_checksum VARCHAR(64) NOT NULL,
                            execution_status VARCHAR(20) NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
                            execution_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            execution_duration_ms BIGINT,
                            error_message TEXT,
                            rollback_script_path VARCHAR(1000),
                            rollback_script_content TEXT,
                            tag_name VARCHAR(100),
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """;

                createDeploymentTagsTable = """
                        CREATE TABLE IF NOT EXISTS deployment_tags (
                            id BIGSERIAL PRIMARY KEY,
                            tag_name VARCHAR(100) NOT NULL UNIQUE,
                            description TEXT,
                            deployment_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            build_version VARCHAR(100),
                            created_by VARCHAR(100),
                            environment VARCHAR(50),
                            is_active BOOLEAN DEFAULT TRUE
                        )
                        """;

                createDatabaseLockTable = """
                        CREATE TABLE IF NOT EXISTS database_lock (
                            id BIGSERIAL PRIMARY KEY,
                            lock_key VARCHAR(100) NOT NULL UNIQUE,
                            lock_owner VARCHAR(255),
                            lock_acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            lock_expires_at TIMESTAMP,
                            is_active BOOLEAN DEFAULT TRUE
                        )
                        """;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute(createChangeLogTable);
                statement.execute(createDeploymentTagsTable);
                statement.execute(createDatabaseLockTable);

                createIndexes(connection);

                logger.info("Database schema initialized successfully");
            }
        }
    }

    private void createIndexes(Connection connection) throws SQLException {
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_script_id ON db_change_log(script_id)",
                "CREATE INDEX IF NOT EXISTS idx_execution_status ON db_change_log(execution_status)",
                "CREATE INDEX IF NOT EXISTS idx_execution_time ON db_change_log(execution_time)",
                "CREATE INDEX IF NOT EXISTS idx_tag_name ON db_change_log(tag_name)",
                "CREATE INDEX IF NOT EXISTS idx_deployment_tags_tag_name ON deployment_tags(tag_name)",
                "CREATE INDEX IF NOT EXISTS idx_deployment_time ON deployment_tags(deployment_time)",
                "CREATE INDEX IF NOT EXISTS idx_lock_key ON database_lock(lock_key)",
                "CREATE INDEX IF NOT EXISTS idx_lock_expires_at ON database_lock(lock_expires_at)"
        };

        try (Statement statement = connection.createStatement()) {
            for (String index : indexes) {
                statement.execute(index);
            }
        }
    }

    public boolean isScriptExecuted(String scriptId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM db_change_log WHERE script_id = ? AND execution_status = 'SUCCESS'";

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, scriptId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    public void recordScriptExecution(ChangeLogEntry entry) throws SQLException {
        String sql = """
                INSERT INTO db_change_log (
                    script_id, script_name, script_path, script_checksum, execution_status,
                    execution_time, execution_duration_ms, error_message, rollback_script_path,
                    rollback_script_content, tag_name, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, entry.getScriptId());
            statement.setString(2, entry.getScriptName());
            statement.setString(3, entry.getScriptPath());
            statement.setString(4, entry.getScriptChecksum());
            statement.setString(5, entry.getExecutionStatus().getValue());

            // Check if it's SQLite by checking the URL
            String url = connection.getMetaData().getURL();
            boolean isSQLite = url.toLowerCase().contains("sqlite");

            if (isSQLite) {
                statement.setString(6, entry.getExecutionTime().toString());
            } else {
                statement.setTimestamp(6, Timestamp.valueOf(entry.getExecutionTime()));
            }

            statement.setObject(7, entry.getExecutionDurationMs());
            statement.setString(8, entry.getErrorMessage());
            statement.setString(9, entry.getRollbackScriptPath());
            statement.setString(10, entry.getRollbackScriptContent());
            statement.setString(11, entry.getTagName());

            if (isSQLite) {
                statement.setString(12, entry.getCreatedAt().toString());
                statement.setString(13, entry.getUpdatedAt().toString());
            } else {
                statement.setTimestamp(12, Timestamp.valueOf(entry.getCreatedAt()));
                statement.setTimestamp(13, Timestamp.valueOf(entry.getUpdatedAt()));
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
        String sql = """
                SELECT * FROM db_change_log 
                WHERE tag_name > ? OR tag_name IS NULL
                ORDER BY execution_time DESC
                """;

        List<ChangeLogEntry> entries = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tagName);

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
                INSERT INTO deployment_tags (tag_name, description, deployment_time, build_version, created_by, environment, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
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

            statement.setString(4, tag.getBuildVersion());
            statement.setString(5, tag.getCreatedBy());
            statement.setString(6, tag.getEnvironment());

            if (isSQLite) {
                statement.setInt(7, tag.getIsActive() ? 1 : 0);
            } else {
                statement.setBoolean(7, tag.getIsActive());
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
        entry.setScriptId(resultSet.getString("script_id"));
        entry.setScriptName(resultSet.getString("script_name"));
        entry.setScriptPath(resultSet.getString("script_path"));
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
        entry.setRollbackScriptPath(resultSet.getString("rollback_script_path"));
        entry.setRollbackScriptContent(resultSet.getString("rollback_script_content"));
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

        tag.setBuildVersion(resultSet.getString("build_version"));
        tag.setCreatedBy(resultSet.getString("created_by"));
        tag.setEnvironment(resultSet.getString("environment"));
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
                logger.info("Deactivated deployment tag: {}", tagName);
            } else {
                logger.warn("No deployment tag found to deactivate: {}", tagName);
            }
        }
    }
}