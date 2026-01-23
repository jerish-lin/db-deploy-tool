package com.scb.mrp.schemaflow.dbdeploy.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Manager class for database schema initialization using the Strategy pattern.
 * Delegates to the appropriate strategy bean which is conditionally created based on database driver.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaInitializationManager {

    private final SchemaInitializationStrategy strategy;
    private final JdbcTemplate dbDeployJdbcTemplate;

    /**
     * Check if the database schema is initialized for the current database.
     *
     * @return true if schema exists, false otherwise
     */
    public boolean isSchemaInitialized() {
        return strategy.isSchemaInitialized(dbDeployJdbcTemplate);
    }

    /**
     * Initialize the database schema for the current database.
     * Only initializes if the schema doesn't already exist.
     */
    public void initializeSchemaIfNeeded() {
        if (!isSchemaInitialized()) {
            initializeSchema();
        } else {
            log.debug("Database schema is already initialized");
        }
    }

    /**
     * Force initialize the database schema for the current database.
     */
    public void initializeSchema() {
        log.info("Initializing database schema");

        try {
            strategy.initializeSchema(dbDeployJdbcTemplate);
            log.info("Database schema initialization completed");
        } catch (Exception e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Schema initialization failed", e);
        }
    }
}