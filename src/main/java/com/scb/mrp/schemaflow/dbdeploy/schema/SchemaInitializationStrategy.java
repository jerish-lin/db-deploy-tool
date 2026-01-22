package com.scb.mrp.schemaflow.dbdeploy.schema;

import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Strategy interface for database schema initialization.
 * Defines the contract for different database-specific schema initialization approaches.
 */
public interface SchemaInitializationStrategy {

    /**
     * Check if the database schema is already initialized.
     *
     * @param jdbcTemplate JdbcTemplate for database operations
     * @return true if schema exists, false otherwise
     */
    boolean isSchemaInitialized(JdbcTemplate jdbcTemplate);

    /**
     * Initialize the database schema by executing the appropriate SQL scripts.
     *
     * @param jdbcTemplate JdbcTemplate for database operations
     */
    void initializeSchema(JdbcTemplate jdbcTemplate);

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