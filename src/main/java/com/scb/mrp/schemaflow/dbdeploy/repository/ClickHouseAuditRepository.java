package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.database.ConditionalOnDatabaseDriver;
import com.scb.mrp.schemaflow.dbdeploy.database.DatabaseType;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogAuditEntry;
import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogScript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

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
    public Long createScriptMetadata(ChangeLogScript metadata) {
        String sql = getCreateScriptMetadataSql();
        // ClickHouse requires explicit ID value, now temporarily using time mills, need to be refactored.
        Long id = System.currentTimeMillis();
        dbDeployJdbcTemplate.queryForObject(sql, Long.class,
                metadata.getScriptName(),
                metadata.getScriptChecksum(),
                metadata.getRollbackScriptContent(),
                metadata.getRollbackVerifyScriptContent(),
                metadata.getTargetNodes() != null ? String.join(",", metadata.getTargetNodes()) : null,
                (Timestamp) prepareTimestamp(metadata.getCreatedAt()),
                id);
        return id;

    }

    private String getCreateScriptMetadataSql() {
        // TODO need to add id.
        return """
                INSERT INTO schemaflow_changelog_script (
                    script_name, script_checksum,
                    rollback_script_content, rollback_verify_script_content, target_nodes, created_at, id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    public void createScriptAuditEntry(ChangeLogAuditEntry entry) {
        String sql = getRecordAuditEntrySql();
        // ClickHouse requires explicit ID value, now temporarily using time mills, need to be refactored.
        Long id = System.currentTimeMillis();
        dbDeployJdbcTemplate.update(sql,
                entry.getScriptId(),
                entry.getExecutionStatus().getValue(),
                prepareTimestamp(entry.getExecutionTime()),
                entry.getExecutionDurationMs(),
                entry.getErrorMessage(),
                entry.getNodeExecutionDetails(),
                prepareTimestamp(entry.getCreatedAt()),
                id);
    }


    protected String getRecordAuditEntrySql() {
        return """
                INSERT INTO schemaflow_changelog_audit (
                    script_id, execution_status, execution_time,
                    execution_duration_ms, error_message,
                    node_execution_details, created_at, id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    protected String getAcquireLockSql(int timeoutMinutes) {
        return String.format(
                "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                        "VALUES (?, ?, now() + INTERVAL %d MINUTE, 1)", timeoutMinutes);
    }
}