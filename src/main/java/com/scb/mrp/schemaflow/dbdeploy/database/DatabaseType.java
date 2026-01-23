package com.scb.mrp.schemaflow.dbdeploy.database;

/**
 * Enum representing supported database types with their corresponding driver class names.
 * Used for conditional bean creation based on database configuration.
 */
public enum DatabaseType {
    POSTGRESQL("org.postgresql.Driver"),
    SQLITE("org.sqlite.JDBC"),
    CLICKHOUSE("com.clickhouse.jdbc.ClickHouseDriver");

    private final String driverClass;

    DatabaseType(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getDriverClass() {
        return driverClass;
    }

    /**
     * Find DatabaseType by driver class name (case-insensitive).
     *
     * @param driverClass the driver class name
     * @return matching DatabaseType
     * @throws IllegalArgumentException if no matching type found
     */
    public static DatabaseType fromDriverClass(String driverClass) {
        if (driverClass == null) {
            throw new IllegalArgumentException("Driver class cannot be null");
        }

        String normalizedDriver = driverClass.trim();
        for (DatabaseType type : values()) {
            if (type.driverClass.equalsIgnoreCase(normalizedDriver)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported database driver: " + driverClass);
    }
}