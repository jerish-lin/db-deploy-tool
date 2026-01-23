package com.scb.mrp.schemaflow.dbdeploy.schema;

import com.scb.mrp.schemaflow.dbdeploy.database.ConditionalOnDatabaseDriver;
import com.scb.mrp.schemaflow.dbdeploy.database.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ClickHouse-specific schema initialization strategy.
 */
@Slf4j
@Component
@ConditionalOnDatabaseDriver(DatabaseType.CLICKHOUSE)
public class ClickHouseSchemaInitializationStrategy extends AbstractSchemaInitializationStrategy {

    @Override
    public boolean isSchemaInitialized(JdbcTemplate jdbcTemplate) {
        try {
            String sql = "SELECT COUNT(*) FROM system.tables WHERE database = currentDatabase() AND name = 'schemaflow_changelog_script'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Error checking schema initialization status", e);
            return false;
        }
    }

    @Override
    public String getSchemaFilesBasePath() {
        return "db-deploy/schema/clickhouse";
    }
}