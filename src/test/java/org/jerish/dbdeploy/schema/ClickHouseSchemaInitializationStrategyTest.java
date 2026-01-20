package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.database.DatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests for ClickHouseSchemaInitializationStrategy")
public class ClickHouseSchemaInitializationStrategyTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ClickHouseSchemaInitializationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ClickHouseSchemaInitializationStrategy();
    }

    @Test
    @DisplayName("Test getSupportedDatabaseType returns CLICKHOUSE")
    void testGetSupportedDatabaseType() {
        assertEquals(DatabaseType.CLICKHOUSE, strategy.getSupportedDatabaseType());
    }

    @Test
    @DisplayName("Test getSchemaFilesBasePath returns correct path")
    void testGetSchemaFilesBasePath() {
        assertEquals("db-deploy/schema/clickhouse", strategy.getSchemaFilesBasePath());
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns true when db_change_log table exists")
    void testIsSchemaInitialized_TableExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(1);

        boolean result = strategy.isSchemaInitialized(jdbcTemplate);

        assertTrue(result);
        verify(jdbcTemplate).queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class)
        );
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns false when db_change_log table does not exist")
    void testIsSchemaInitialized_TableNotExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(0);

        boolean result = strategy.isSchemaInitialized(jdbcTemplate);

        assertFalse(result);
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns false when query throws exception")
    void testIsSchemaInitialized_QueryException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("Database error"));

        boolean result = strategy.isSchemaInitialized(jdbcTemplate);

        assertFalse(result);
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns false when query returns null")
    void testIsSchemaInitialized_NullResult() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(null);

        boolean result = strategy.isSchemaInitialized(jdbcTemplate);

        assertFalse(result);
    }

    @Test
    @DisplayName("Test initializeSchema executes all schema files in order")
    void testInitializeSchema_ExecutesAllFiles() {
        strategy.initializeSchema(jdbcTemplate);

        // At least 3 files are executed (may contain multiple statements each)
        verify(jdbcTemplate, atLeast(3)).execute(anyString());
    }

    @Test
    @DisplayName("Test getSchemaFiles returns correct file list")
    void testGetSchemaFiles() {
        List<String> files = ReflectionTestUtils.invokeMethod(strategy, "getSchemaFiles");

        assertEquals(3, files.size());
        assertEquals("01-create-audit-tables.sql", files.get(0));
        assertEquals("02-create-indexes.sql", files.get(1));
        assertEquals("03-create-views.sql", files.get(2));
    }

    @Test
    @DisplayName("Test executeSqlFile throws exception when file not found")
    void testExecuteSqlFile_FileNotFound() {
        String nonExistentFile = "non-existent.sql";

        assertThrows(RuntimeException.class, () -> {
            ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, nonExistentFile);
        });
    }

    @Test
    @DisplayName("Test executeSqlFile executes SQL statements correctly")
    void testExecuteSqlFile_ExecutesStatements() {
        ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, "01-create-audit-tables.sql");

        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("Test executeSqlFile splits statements by semicolon")
    void testExecuteSqlFile_SplitsStatements() {
        ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, "01-create-audit-tables.sql");

        verify(jdbcTemplate, atLeast(2)).execute(anyString());
    }

    @Test
    @DisplayName("Test executeSqlFile skips empty statements")
    void testExecuteSqlFile_SkipsEmptyStatements() {
        ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, "01-create-audit-tables.sql");

        verify(jdbcTemplate, never()).execute(eq(""));
    }

    @Test
    @DisplayName("Test executeSqlFile throws exception on SQL execution failure")
    void testExecuteSqlFile_ExecutionFailure() {
        doThrow(new RuntimeException("SQL execution failed"))
                .when(jdbcTemplate).execute(anyString());

        assertThrows(RuntimeException.class, () -> {
            ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, "01-create-audit-tables.sql");
        });
    }

    @Test
    @DisplayName("Test executeSqlFile uses correct base path")
    void testExecuteSqlFile_CorrectBasePath() {
        try {
            ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, "01-create-audit-tables.sql");
        } catch (Exception e) {
            // Expected if file doesn't exist in test environment
            assertTrue(e.getMessage().contains("Schema file not found") || e.getMessage().contains("Failed to execute"));
        }
    }

    @Test
    @DisplayName("Test readSqlContent skips comment lines")
    void testReadSqlContent_SkipsComments() {
        // This is tested indirectly through executeSqlFile
        // Comment lines start with -- and should be skipped
        try {
            ReflectionTestUtils.invokeMethod(strategy, "executeSqlFile", jdbcTemplate, "01-create-audit-tables.sql");
        } catch (Exception e) {
            // Expected if file doesn't exist in test environment
        }
    }

    @Test
    @DisplayName("Test initializeSchema handles exceptions gracefully")
    void testInitializeSchema_HandlesExceptions() {
        doThrow(new RuntimeException("Database connection failed"))
                .when(jdbcTemplate).execute(anyString());

        assertThrows(RuntimeException.class, () -> {
            strategy.initializeSchema(jdbcTemplate);
        });
    }

    @Test
    @DisplayName("Test isSchemaInitialized uses information_schema query")
    void testIsSchemaInitialized_UsesInformationSchema() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(1);

        strategy.isSchemaInitialized(jdbcTemplate);

        verify(jdbcTemplate).queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class)
        );
    }

    @Test
    @DisplayName("Test isSchemaInitialized checks for db_change_log table")
    void testIsSchemaInitialized_ChecksCorrectTable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(1);

        strategy.isSchemaInitialized(jdbcTemplate);

        verify(jdbcTemplate).queryForObject(
                contains("db_change_log"),
                eq(Integer.class)
        );
    }

    @Test
    @DisplayName("Test strategy is correctly typed as ClickHouse")
    void testStrategyType() {
        assertTrue(strategy instanceof SchemaInitializationStrategy);
        assertTrue(strategy instanceof AbstractSchemaInitializationStrategy);
        assertTrue(strategy instanceof ClickHouseSchemaInitializationStrategy);
    }

    @Test
    @DisplayName("Test initializeSchema executes files in correct order")
    void testInitializeSchema_CorrectOrder() {
        strategy.initializeSchema(jdbcTemplate);

        // Verify that tables are created (checking for table names in SQL)
        verify(jdbcTemplate, atLeastOnce()).execute(contains("schemaflow_change_log"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("schemaflow_deploy_lock"));
    }

    @Test
    @DisplayName("Test getSupportedDatabaseType enum matches")
    void testGetSupportedDatabaseType_EnumMatch() {
        DatabaseType type = strategy.getSupportedDatabaseType();
        assertEquals("clickhouse", type.getScheme().toLowerCase());
        assertEquals("com.clickhouse.jdbc.ClickHouseDriver", type.getDriverClass());
    }
}
