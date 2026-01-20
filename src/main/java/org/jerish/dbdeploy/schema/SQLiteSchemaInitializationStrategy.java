package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.entity.DatabaseType;
import org.springframework.stereotype.Component;

/**
 * SQLite-specific schema initialization strategy.
 */
@Component
public class SQLiteSchemaInitializationStrategy extends AbstractSchemaInitializationStrategy {

    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.SQLITE;
    }

    @Override
    public String getSchemaFilesBasePath() {
        return "db-deploy/schema/sqlite";
    }
}