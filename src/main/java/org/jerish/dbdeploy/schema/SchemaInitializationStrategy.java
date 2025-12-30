package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.database.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Strategy interface for database schema initialization.
 * Defines the contract for different database-specific schema initialization approaches.
 */
public interface SchemaInitializationStrategy {

    /**
     * Check if the database schema is already initialized.
     *
     * @param connection database connection
     * @return true if schema exists, false otherwise
     * @throws SQLException if database access error occurs
     */
    boolean isSchemaInitialized(Connection connection) throws SQLException;

    /**
     * Initialize the database schema by executing the appropriate SQL scripts.
     *
     * @param connection database connection
     * @throws SQLException if database access error occurs
     */
    void initializeSchema(Connection connection) throws SQLException;

    /**
     * Get the database type this strategy supports.
     *
     * @return the supported database type
     */
    DatabaseType getSupportedDatabaseType();

    /**
     * Get the base path for SQL schema files.
     *
     * @return the schema files base path
     */
    String getSchemaFilesBasePath();
}