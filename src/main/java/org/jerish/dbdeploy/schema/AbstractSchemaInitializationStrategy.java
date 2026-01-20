package org.jerish.dbdeploy.schema;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class AbstractSchemaInitializationStrategy implements SchemaInitializationStrategy {

    @Override
    public boolean isSchemaInitialized(JdbcTemplate jdbcTemplate) {
        try {
            // Check if the main audit table exists
            String sql = """
                SELECT COUNT(*) FROM information_schema.tables 
                WHERE table_name = 'schemaflow_change_log'
                """;
            
            // For SQLite, use different query
            if (getSupportedDatabaseType().name().toLowerCase().contains("sqlite")) {
                sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='schemaflow_change_log'";
            }
            
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Error checking schema initialization status", e);
            return false;
        }
    }

    @Override
    public void initializeSchema(JdbcTemplate jdbcTemplate) {
        log.info("Initializing database schema for {}", getSupportedDatabaseType());

        List<String> schemaFiles = getSchemaFiles();

        for (String schemaFile : schemaFiles) {
            executeSqlFile(jdbcTemplate, schemaFile);
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
        files.add("03-create-views.sql");
        return files;
    }

    /**
     * Execute a SQL file from the classpath resources.
     *
     * @param jdbcTemplate JdbcTemplate for database operations
     * @param fileName      the SQL file name
     */
    protected void executeSqlFile(JdbcTemplate jdbcTemplate, String fileName) {
        String fullPath = getSchemaFilesBasePath() + "/" + fileName;

        log.debug("Executing SQL file: {}", fullPath);

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fullPath)) {
            if (inputStream == null) {
                throw new RuntimeException("Schema file not found: " + fullPath);
            }

            String sqlContent = readSqlContent(inputStream);

            // Split content by semicolon and execute each statement
            String[] statements = sqlContent.split(";");

            for (String statementStr : statements) {
                String trimmedStatement = statementStr.trim();
                if (!trimmedStatement.isEmpty()) {
                    log.debug("Executing SQL: {}", trimmedStatement);
                    jdbcTemplate.execute(trimmedStatement);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute SQL file: " + fullPath, e);
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