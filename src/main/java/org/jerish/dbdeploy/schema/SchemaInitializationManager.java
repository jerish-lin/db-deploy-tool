package org.jerish.dbdeploy.schema;

import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.database.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manager class for database schema initialization using the Strategy pattern.
 * Handles strategy selection and provides a unified interface for schema operations.
 */
@Component
@Slf4j
public class SchemaInitializationManager {

    private final Map<DatabaseType, SchemaInitializationStrategy> strategyMap;
    private final DatabaseConnectionManager connectionManager;

    @Autowired
    public SchemaInitializationManager(List<SchemaInitializationStrategy> strategies,
                                       DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
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
     * @throws SQLException if database access error occurs
     */
    public boolean isSchemaInitialized() throws SQLException {
        DatabaseType databaseType = getCurrentDatabaseType();
        SchemaInitializationStrategy strategy = getStrategyForDatabaseType(databaseType);

        try (Connection connection = connectionManager.getConnection()) {
            return strategy.isSchemaInitialized(connection);
        }
    }

    /**
     * Initialize the database schema for the current database type.
     * Only initializes if the schema doesn't already exist.
     *
     * @throws SQLException if schema initialization fails
     */
    public void initializeSchemaIfNeeded() throws SQLException {
        if (!isSchemaInitialized()) {
            initializeSchema();
        } else {
            log.debug("Database schema is already initialized");
        }
    }

    /**
     * Force initialize the database schema for the current database type.
     *
     * @throws SQLException if schema initialization fails
     */
    public void initializeSchema() throws SQLException {
        DatabaseType databaseType = getCurrentDatabaseType();
        SchemaInitializationStrategy strategy = getStrategyForDatabaseType(databaseType);

        log.info("Initializing database schema for type: {}", databaseType);

        try (Connection connection = connectionManager.getConnection()) {
            strategy.initializeSchema(connection);
            log.info("Database schema initialization completed for type: {}", databaseType);
        } catch (SQLException e) {
            log.error("Failed to initialize database schema for type: {}", databaseType, e);
            throw e;
        }
    }

    /**
     * Get the current database type from the connection metadata.
     *
     * @return the current database type
     * @throws SQLException if unable to determine database type
     */
    private DatabaseType getCurrentDatabaseType() throws SQLException {
        try (Connection connection = connectionManager.getConnection()) {
            String url = connection.getMetaData().getURL().toLowerCase();

            if (url.contains("postgresql")) {
                return DatabaseType.POSTGRESQL;
            } else if (url.contains("sqlite")) {
                return DatabaseType.SQLITE;
            } else if (url.contains("clickhouse")) {
                return DatabaseType.CLICKHOUSE;
            } else {
                throw new SQLException("Unsupported database type. URL: " + url);
            }
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
    public java.util.Set<DatabaseType> getSupportedDatabaseTypes() {
        return strategyMap.keySet();
    }
}