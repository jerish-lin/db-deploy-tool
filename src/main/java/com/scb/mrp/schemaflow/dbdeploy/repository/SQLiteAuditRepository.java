package com.scb.mrp.schemaflow.dbdeploy.repository;

import com.scb.mrp.schemaflow.dbdeploy.database.ConditionalOnDatabaseDriver;
import com.scb.mrp.schemaflow.dbdeploy.database.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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
    protected String getAcquireLockSql(int timeoutMinutes) {
        return "INSERT OR IGNORE INTO schemaflow_deploy_lock (lock_key, lock_owner, lock_expires_at, is_active) " +
                "VALUES (?, ?, datetime('now', '+' || ? || ' minutes'), 1)";
    }

    @Override
    protected Object prepareTimestamp(LocalDateTime dateTime) {
        // SQLite: Store LocalDateTime as ISO-8601 string
        if (dateTime == null) {
            return LocalDateTime.now().toString();
        }
        return dateTime.toString();
    }

    @Override
    protected LocalDateTime parseTimestamp(Object timestampObj) {
        // SQLite: Parse from ISO-8601 string
        if (timestampObj == null) {
            return LocalDateTime.now();
        }
        return parseLocalDateTime(timestampObj.toString());
    }

    private LocalDateTime parseLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}