package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.database.ConditionalOnDatabaseDriver;
import com.scb.mrp.schemaflow.dbdeploy.database.DatabaseType;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogAuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogScript;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SQLite-specific implementation of AuditRepository.
 */
@Slf4j
@Repository
@ConditionalOnDatabaseDriver(DatabaseType.SQLITE)
public class SQLiteAuditRepository extends AbstractAuditRepository {

    public SQLiteAuditRepository(JdbcTemplate dbDeployJdbcTemplate) {
        super(dbDeployJdbcTemplate);
    }

    @Override
    protected void setSaveScriptMetadataParameters(ChangeLogScript metadata, String sql, KeyHolder keyHolder) {
        dbDeployJdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setString(1, metadata.getScriptName());
            ps.setString(2, metadata.getScriptChecksum());
            ps.setString(3, metadata.getRollbackScriptContent());
            ps.setString(4, metadata.getRollbackVerifyScriptContent());
            String targetNodesStr = metadata.getTargetNodes() != null ? String.join(",", metadata.getTargetNodes()) : null;
            ps.setString(5, targetNodesStr);
            ps.setString(6, metadata.getCreatedAt() != null ? metadata.getCreatedAt().toString() : LocalDateTime.now().toString());
            return ps;
        }, keyHolder);
    }

    @Override
    protected String getAcquireLockSql(int timeoutMinutes) {
        return "INSERT OR IGNORE INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                "VALUES (?, ?, datetime('now', '+' || ? || ' minutes'), 1)";
    }

    @Override
    protected void handleTargetNodesForAuditHistory(Map<String, Object> row, DatabaseStatus.AuditHistoryEntry entry) {
        // Handle target_nodes from script table (stored as comma-separated string)
        String targetNodesStr = (String) row.get("target_nodes");
        if (targetNodesStr != null && !targetNodesStr.isEmpty()) {
            entry.setTargetNodes(List.of(targetNodesStr.split(",")));
        }
    }
}