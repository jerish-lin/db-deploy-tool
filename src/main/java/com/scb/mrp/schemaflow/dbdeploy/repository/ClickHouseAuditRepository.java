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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse-specific implementation of AuditRepository.
 */
@Slf4j
@Repository
@ConditionalOnDatabaseDriver(DatabaseType.CLICKHOUSE)
public class ClickHouseAuditRepository extends AbstractAuditRepository {

    public ClickHouseAuditRepository(JdbcTemplate dbDeployJdbcTemplate) {
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
            String nodesArray = metadata.getTargetNodes() != null ? "['" + String.join("','", metadata.getTargetNodes()) + "']" : "[]";
            ps.setString(5, nodesArray);
            ps.setTimestamp(6, Timestamp.valueOf(metadata.getCreatedAt() != null ? metadata.getCreatedAt() : LocalDateTime.now()));
            return ps;
        }, keyHolder);
    }

    @Override
    protected String getAcquireLockSql(int timeoutMinutes) {
        return String.format(
                "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                        "VALUES (?, ?, now() + INTERVAL %d MINUTE, 1)", timeoutMinutes);
    }

    @Override
    protected void handleTargetNodesForAuditHistory(Map<String, Object> row, DatabaseStatus.AuditHistoryEntry entry) {
        // Handle target_nodes from script table (stored as array)
        Object targetNodesObj = row.get("target_nodes");
        if (targetNodesObj != null && targetNodesObj instanceof String) {
            String targetNodesStr = (String) targetNodesObj;
            if (!targetNodesStr.isEmpty()) {
                // ClickHouse array format: ['node1','node2','node3']
                String cleaned = targetNodesStr.substring(1, targetNodesStr.length() - 1).replaceAll("'", "");
                entry.setTargetNodes(List.of(cleaned.split(",")));
            }
        }
    }
}