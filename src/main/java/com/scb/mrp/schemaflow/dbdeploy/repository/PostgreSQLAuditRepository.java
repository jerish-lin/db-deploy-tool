package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.database.ConditionalOnDatabaseDriver;
import com.scb.mrp.schemaflow.dbdeploy.database.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
    protected String getAcquireLockSql(int timeoutMinutes) {
        return String.format(
                "INSERT INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                        "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '%d minutes', TRUE) " +
                        "ON CONFLICT (lock_key) DO NOTHING", timeoutMinutes);
    }
}