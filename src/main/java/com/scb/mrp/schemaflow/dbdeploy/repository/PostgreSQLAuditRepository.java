package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.database.ConditionalOnDatabaseDriver;
import com.scb.mrp.schemaflow.dbdeploy.database.DatabaseType;
import com.scb.mrp.schemaflow.dbdeploy.entity.AuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptMetadata;
import com.scb.mrp.schemaflow.dbdeploy.model.ChangeLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL-specific implementation of AuditRepository.
 */
@Slf4j
@Repository
@ConditionalOnDatabaseDriver(DatabaseType.POSTGRESQL)
public class PostgreSQLAuditRepository extends AbstractAuditRepository {

    public PostgreSQLAuditRepository(JdbcTemplate dbDeployJdbcTemplate) {
        super(dbDeployJdbcTemplate);
    }

    @Override
    protected void handleMultiNodeFields(ResultSet rs, AuditEntry entry) throws SQLException {
        // targetNodes is no longer in AuditEntry, it's in ScriptMetadata
        // This method is kept for compatibility but does nothing
    }

    @Override
    protected void handleMultiNodeFieldsForChangeLog(ResultSet rs, ChangeLogEntry entry) throws SQLException {
        try {
            String targetNodesStr = rs.getString("target_nodes");
            if (targetNodesStr != null && !targetNodesStr.isEmpty()) {
                // PostgreSQL stores as array, need to convert from string representation
                // Array format: {node1,node2,node3}
                String cleaned = targetNodesStr.substring(1, targetNodesStr.length() - 1);
                entry.setTargetNodes(List.of(cleaned.split(",")));
            } else {
                entry.setTargetNodes(null);
            }
        } catch (SQLException e) {
            entry.setTargetNodes(null);
        }
    }

    @Override
    protected String getSaveScriptMetadataSql() {
        return """
                INSERT INTO schemaflow_changelog_script (
                    script_name, script_checksum,
                    rollback_script_content, rollback_verify_script_content, target_nodes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    protected void setSaveScriptMetadataParameters(ScriptMetadata metadata, String sql, KeyHolder keyHolder) {
        dbDeployJdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setString(1, metadata.getScriptName());
            ps.setString(2, metadata.getScriptChecksum());
            ps.setString(3, metadata.getRollbackScriptContent());
            ps.setString(4, metadata.getRollbackVerifyScriptContent());
            ps.setArray(5, connection.createArrayOf("TEXT", metadata.getTargetNodes() != null ? metadata.getTargetNodes().toArray() : null));
            ps.setTimestamp(6, Timestamp.valueOf(metadata.getCreatedAt() != null ? metadata.getCreatedAt() : LocalDateTime.now()));
            return ps;
        }, keyHolder);
    }

    @Override
    protected String getRecordAuditEntrySql() {
        return """
                INSERT INTO schemaflow_changelog_audit (
                    script_id, execution_status, execution_time,
                    execution_duration_ms, error_message,
                    node_execution_details, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    protected void setRecordAuditEntryParameters(AuditEntry entry, String sql, KeyHolder keyHolder) {
        dbDeployJdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setLong(1, entry.getScriptId());
            ps.setString(2, entry.getExecutionStatus().getValue());
            ps.setTimestamp(3, Timestamp.valueOf(entry.getExecutionTime()));
            ps.setObject(4, entry.getExecutionDurationMs());
            ps.setString(5, entry.getErrorMessage());
            ps.setString(6, entry.getNodeExecutionDetails());
            ps.setTimestamp(7, Timestamp.valueOf(entry.getCreatedAt()));
            return ps;
        }, keyHolder);
    }

    @Override
    protected String getAcquireLockSql(int timeoutMinutes) {
        return String.format(
                "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                        "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) " +
                        "ON CONFLICT (lock_key) DO NOTHING", timeoutMinutes);
    }

    @Override
    protected void handleTargetNodesForAuditHistory(Map<String, Object> row, DatabaseStatus.AuditHistoryEntry entry) {
        // Handle target_nodes from script table (stored as array)
        Object targetNodesObj = row.get("target_nodes");
        if (targetNodesObj != null && targetNodesObj instanceof String) {
            String targetNodesStr = (String) targetNodesObj;
            if (!targetNodesStr.isEmpty()) {
                // PostgreSQL array format: {node1,node2,node3}
                String cleaned = targetNodesStr.substring(1, targetNodesStr.length() - 1);
                entry.setTargetNodes(List.of(cleaned.split(",")));
            }
        }
    }
}