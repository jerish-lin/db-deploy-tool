package org.jerish.dbdeploy.schema;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class AbstractSchemaInitializationStrategy implements SchemaInitializationStrategy {


    @Override
    public boolean isSchemaInitialized(Connection connection) throws SQLException {
        try {
            // Check if the main audit table exists
            var metaData = connection.getMetaData();
            try (var tables = metaData.getTables(null, null, "db_change_log", null)) {
                return tables.next();
            }
        } catch (SQLException e) {
            log.debug("Error checking schema initialization status", e);
            return false;
        }
    }

    @Override
    public void initializeSchema(Connection connection) throws SQLException {
        log.info("Initializing database schema for {}", getSupportedDatabaseType());

        List<String> schemaFiles = getSchemaFiles();

        for (String schemaFile : schemaFiles) {
            executeSqlFile(connection, schemaFile);
        }

        log.info("Database schema initialization completed for {}", getSupportedDatabaseType());
    }

    /**
     * Get the list of SQL schema files to execute in order.
     *
     * @return ordered list of schema file names
     */
    protected List<String> getSchemaFiles() {
        List<String> files = new ArrayList<>();
        files.add("01-create-audit-tables.sql");
        files.add("02-create-indexes.sql");
        return files;
    }

    /**
     * Execute a SQL file from the classpath resources.
     *
     * @param connection database connection
     * @param fileName   the SQL file name
     * @throws SQLException if execution fails
     */
    protected void executeSqlFile(Connection connection, String fileName) throws SQLException {
        String fullPath = getSchemaFilesBasePath() + "/" + fileName;

        log.debug("Executing SQL file: {}", fullPath);

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fullPath)) {
            if (inputStream == null) {
                throw new SQLException("Schema file not found: " + fullPath);
            }

            String sqlContent = readSqlContent(inputStream);

            try (Statement statement = connection.createStatement()) {
                // Split content by semicolon and execute each statement
                String[] statements = sqlContent.split(";");

                for (String statementStr : statements) {
                    String trimmedStatement = statementStr.trim();
                    if (!trimmedStatement.isEmpty() && !trimmedStatement.startsWith("--")) {
                        log.debug("Executing SQL statement: {}", trimmedStatement.substring(0, Math.min(50, trimmedStatement.length())) + "...");
                        statement.execute(trimmedStatement);
                    }
                }
            }

        } catch (Exception e) {
            throw new SQLException("Failed to execute schema file: " + fullPath, e);
        }
    }

    /**
     * Read SQL content from input stream.
     *
     * @param inputStream the input stream
     * @return the SQL content as string
     * @throws Exception if reading fails
     */
    private String readSqlContent(InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // Skip comment lines but preserve them for context
                if (!line.trim().startsWith("--")) {
                    content.append(line).append("\n");
                }
            }

            return content.toString();
        }
    }
}