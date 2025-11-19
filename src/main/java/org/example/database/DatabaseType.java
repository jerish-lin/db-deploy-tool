package org.example.database;

public enum DatabaseType {
    POSTGRESQL("postgresql", "org.postgresql.Driver"),
    SQLITE("sqlite", "org.sqlite.JDBC"),
    CLICKHOUSE("clickhouse", "com.clickhouse.jdbc.ClickHouseDriver"),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver"),
    ORACLE("oracle", "oracle.jdbc.OracleDriver"),
    SQL_SERVER("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");

    private final String scheme;
    private final String driverClass;

    DatabaseType(String scheme, String driverClass) {
        this.scheme = scheme;
        this.driverClass = driverClass;
    }

    public static DatabaseType fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL cannot be null");
        }

        String lowerUrl = jdbcUrl.toLowerCase();
        for (DatabaseType type : values()) {
            if (lowerUrl.startsWith("jdbc:" + type.scheme + ":")) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported database type for URL: " + jdbcUrl);
    }

    public String getScheme() {
        return scheme;
    }

    public String getDriverClass() {
        return driverClass;
    }
}