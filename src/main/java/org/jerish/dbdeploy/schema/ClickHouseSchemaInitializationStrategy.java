package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.entity.DatabaseType;
import org.springframework.stereotype.Component;

/**
 * ClickHouse-specific schema initialization strategy.
 */
@Component
public class ClickHouseSchemaInitializationStrategy extends AbstractSchemaInitializationStrategy {

    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.CLICKHOUSE;
    }

    @Override
    public String getSchemaFilesBasePath() {
        return "db-deploy/schema/clickhouse";
    }
}