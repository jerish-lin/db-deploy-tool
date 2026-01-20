package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.entity.DatabaseType;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL-specific schema initialization strategy.
 */
@Component
public class PostgreSqlSchemaInitializationStrategy extends AbstractSchemaInitializationStrategy {

    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String getSchemaFilesBasePath() {
        return "db-deploy/schema/postgresql";
    }
}