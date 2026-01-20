package org.jerish.dbdeploy.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manager class for database schema initialization using the Strategy pattern.
 * Handles strategy selection and provides a unified interface for schema operations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaInitializationManager {

    private final Map<DatabaseType, SchemaInitializationStrategy> strategyMap;
    private final JdbcTemplate dbDeployJdbcTemplate;

    @Autowired
    public SchemaInitializationManager(List<SchemaInitializationStrategy> strategies,
                                       JdbcTemplate dbDeployJdbcTemplate) {
        this.dbDeployJdbcTemplate = dbDeployJdbcTemplate;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        SchemaInitializationStrategy::getSupportedDatabaseType,
                        Function.identity()
                ));

        log.info("Schema initialization strategies loaded: {}",
                strategyMap.keySet().stream().map(DatabaseType::name).collect(Collectors.joining(", ")));
    }

    /**
     * Check if the database schema is initialized for the current database type.
     *
     * @return true if schema exists, false otherwise
     */
    public boolean isSchemaInitialized() {
        DatabaseType databaseType = getCurrentDatabaseType();
        SchemaInitializationStrategy strategy = getStrategyForDatabaseType(databaseType);

        return strategy.isSchemaInitialized(dbDeployJdbcTemplate);
    }

    /**
     * Initialize the database schema for the current database type.
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
     * Force initialize the database schema for the current database type.
     */
    public void initializeSchema() {
        DatabaseType databaseType = getCurrentDatabaseType();
        SchemaInitializationStrategy strategy = getStrategyForDatabaseType(databaseType);

        log.info("Initializing database schema for type: {}", databaseType);

        try {
            strategy.initializeSchema(dbDeployJdbcTemplate);
            log.info("Database schema initialization completed for type: {}", databaseType);
        } catch (Exception e) {
            log.error("Failed to initialize database schema for type: {}", databaseType, e);
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    /**
     * Get the current database type from the connection metadata.
     *
     * @return the current database type
     * @throws RuntimeException if unable to determine database type
     */
    private DatabaseType getCurrentDatabaseType() {
        try {
            // Try to get database URL using JdbcTemplate's DataSource
            String url = dbDeployJdbcTemplate.getDataSource().getConnection().getMetaData().getURL().toLowerCase();

            if (url.contains("postgresql")) {
                return DatabaseType.POSTGRESQL;
            } else if (url.contains("sqlite")) {
                return DatabaseType.SQLITE;
            } else if (url.contains("clickhouse")) {
                return DatabaseType.CLICKHOUSE;
            } else {
                throw new RuntimeException("Unsupported database type. URL: " + url);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to determine database type", e);
        }
    }

    /**
     * Get the appropriate strategy for the given database type.
     *
     * @param databaseType the database type
     * @return the corresponding strategy
     * @throws IllegalArgumentException if no strategy found for the database type
     */
    private SchemaInitializationStrategy getStrategyForDatabaseType(DatabaseType databaseType) {
        SchemaInitializationStrategy strategy = strategyMap.get(databaseType);
        if (strategy == null) {
            throw new IllegalArgumentException("No schema initialization strategy found for database type: " + databaseType);
        }
        return strategy;
    }

    /**
     * Get all supported database types.
     *
     * @return set of supported database types
     */
    public Set<DatabaseType> getSupportedDatabaseTypes() {
        return strategyMap.keySet();
    }
}