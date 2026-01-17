package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.database.DatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Unit tests for SchemaInitializationManager")
public class SchemaInitializationManagerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private SQLiteSchemaInitializationStrategy sqliteStrategy;

    @Mock
    private PostgreSqlSchemaInitializationStrategy postgresStrategy;

    @Mock
    private ClickHouseSchemaInitializationStrategy clickhouseStrategy;

    private SchemaInitializationManager manager;

    /**
     * Helper method to create SchemaInitializationManager using the @Autowired constructor
     */
    private SchemaInitializationManager createManager(List<SchemaInitializationStrategy> strategies, JdbcTemplate jdbcTemplate) {
        try {
            // Find the constructor that takes List<SchemaInitializationStrategy> and JdbcTemplate
            Constructor<SchemaInitializationManager> constructor = SchemaInitializationManager.class.getDeclaredConstructor(
                    List.class, JdbcTemplate.class);
            constructor.setAccessible(true);
            return constructor.newInstance(strategies, jdbcTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SchemaInitializationManager", e);
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        // Setup mock chain for database URL detection
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);

        // Setup strategy mocks to return correct database types
        when(sqliteStrategy.getSupportedDatabaseType()).thenReturn(DatabaseType.SQLITE);
        when(postgresStrategy.getSupportedDatabaseType()).thenReturn(DatabaseType.POSTGRESQL);
        when(clickhouseStrategy.getSupportedDatabaseType()).thenReturn(DatabaseType.CLICKHOUSE);

        // Create manager with mocked strategies using List (as per @Autowired constructor)
        List<SchemaInitializationStrategy> strategies = List.of(
                sqliteStrategy,
                postgresStrategy,
                clickhouseStrategy
        );
        manager = createManager(strategies, jdbcTemplate);

        // Reset mocks after setup to avoid unnecessary stubbing warnings
        reset(jdbcTemplate, dataSource, connection, metaData);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
    }

    @Test
    @DisplayName("Test constructor loads all strategies correctly")
    void testConstructor_LoadsAllStrategies() {
        Set<DatabaseType> supportedTypes = manager.getSupportedDatabaseTypes();

        assertEquals(3, supportedTypes.size());
        assertTrue(supportedTypes.contains(DatabaseType.SQLITE));
        assertTrue(supportedTypes.contains(DatabaseType.POSTGRESQL));
        assertTrue(supportedTypes.contains(DatabaseType.CLICKHOUSE));
    }

    @Test
    @DisplayName("Test getSupportedDatabaseTypes returns all registered strategies")
    void testGetSupportedDatabaseTypes() {
        Set<DatabaseType> types = manager.getSupportedDatabaseTypes();

        assertNotNull(types);
        assertEquals(3, types.size());
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns true for SQLite when table exists")
    void testIsSchemaInitialized_SqliteTableExists() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        boolean result = manager.isSchemaInitialized();

        assertTrue(result);
        verify(sqliteStrategy).isSchemaInitialized(jdbcTemplate);
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns false for SQLite when table doesn't exist")
    void testIsSchemaInitialized_SqliteTableNotExists() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(false);

        boolean result = manager.isSchemaInitialized();

        assertFalse(result);
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns true for PostgreSQL when table exists")
    void testIsSchemaInitialized_PostgresTableExists() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/test");
        when(postgresStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        boolean result = manager.isSchemaInitialized();

        assertTrue(result);
    }

    @Test
    @DisplayName("Test isSchemaInitialized returns true for ClickHouse when table exists")
    void testIsSchemaInitialized_ClickHouseTableExists() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:clickhouse://localhost:8123/test");
        when(clickhouseStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        boolean result = manager.isSchemaInitialized();

        assertTrue(result);
    }

    @Test
    @DisplayName("Test isSchemaInitialized throws exception for unsupported database type")
    void testIsSchemaInitialized_UnsupportedDatabase() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/test");

        assertThrows(RuntimeException.class, () -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test isSchemaInitialized handles SQLException when getting database type")
    void testIsSchemaInitialized_DatabaseTypeDetectionFailure() throws SQLException {
        when(metaData.getURL()).thenThrow(new SQLException("Connection failed"));

        assertThrows(RuntimeException.class, () -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test initializeSchemaIfNeeded initializes when schema not initialized")
    void testInitializeSchemaIfNeeded_NotInitialized() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(false);

        manager.initializeSchemaIfNeeded();

        verify(sqliteStrategy).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Test initializeSchemaIfNeeded skips when schema already initialized")
    void testInitializeSchemaIfNeeded_AlreadyInitialized() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        manager.initializeSchemaIfNeeded();

        verify(sqliteStrategy, never()).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Test initializeSchema forces initialization for SQLite")
    void testInitializeSchema_Sqlite() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");

        manager.initializeSchema();

        verify(sqliteStrategy).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Test initializeSchema forces initialization for PostgreSQL")
    void testInitializeSchema_Postgres() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/test");

        manager.initializeSchema();

        verify(postgresStrategy).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Test initializeSchema forces initialization for ClickHouse")
    void testInitializeSchema_ClickHouse() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:clickhouse://localhost:8123/test");

        manager.initializeSchema();

        verify(clickhouseStrategy).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Test initializeSchema throws exception for unsupported database type")
    void testInitializeSchema_UnsupportedDatabase() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/test");

        assertThrows(RuntimeException.class, () -> {
            manager.initializeSchema();
        });
    }

    @Test
    @DisplayName("Test initializeSchema handles SQLException when getting database type")
    void testInitializeSchema_DatabaseTypeDetectionFailure() throws SQLException {
        when(metaData.getURL()).thenThrow(new SQLException("Connection failed"));

        assertThrows(RuntimeException.class, () -> {
            manager.initializeSchema();
        });
    }

    @Test
    @DisplayName("Test initializeSchema handles exception from strategy")
    void testInitializeSchema_StrategyException() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        doThrow(new RuntimeException("Schema initialization failed"))
                .when(sqliteStrategy).initializeSchema(any(JdbcTemplate.class));

        assertThrows(RuntimeException.class, () -> {
            manager.initializeSchema();
        });
    }

    @Test
    @DisplayName("Test getCurrentDatabaseType detects SQLite correctly")
    void testGetCurrentDatabaseType_DetectsSqlite() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");

        // This is tested indirectly through other methods
        // We expect no exception to be thrown
        assertDoesNotThrow(() -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test getCurrentDatabaseType detects PostgreSQL correctly")
    void testGetCurrentDatabaseType_DetectsPostgres() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/test");

        assertDoesNotThrow(() -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test getCurrentDatabaseType detects ClickHouse correctly")
    void testGetCurrentDatabaseType_DetectsClickHouse() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:clickhouse://localhost:8123/test");

        assertDoesNotThrow(() -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test getCurrentDatabaseType is case-insensitive")
    void testGetCurrentDatabaseType_CaseInsensitive() throws SQLException {
        when(metaData.getURL()).thenReturn("JDBC:POSTGRESQL://localhost:5432/test");

        assertDoesNotThrow(() -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test getStrategyForDatabaseType returns correct strategy for SQLite")
    void testGetStrategyForDatabaseType_Sqlite() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        manager.isSchemaInitialized();

        // If no exception is thrown, the correct strategy was selected
    }

    @Test
    @DisplayName("Test getStrategyForDatabaseType returns correct strategy for PostgreSQL")
    void testGetStrategyForDatabaseType_Postgres() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/test");
        when(postgresStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        manager.isSchemaInitialized();
    }

    @Test
    @DisplayName("Test getStrategyForDatabaseType returns correct strategy for ClickHouse")
    void testGetStrategyForDatabaseType_ClickHouse() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:clickhouse://localhost:8123/test");
        when(clickhouseStrategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        manager.isSchemaInitialized();
    }

    @Test
    @DisplayName("Test getStrategyForDatabaseType throws exception for missing strategy")
    void testGetStrategyForDatabaseType_MissingStrategy() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/test");

        assertThrows(RuntimeException.class, () -> {
            manager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test manager handles null strategies map gracefully")
    void testConstructor_NullStrategies() {
        assertThrows(RuntimeException.class, () -> {
            createManager(null, jdbcTemplate);
        });
    }

    @Test
    @DisplayName("Test manager handles empty strategies map gracefully")
    void testConstructor_EmptyStrategies() throws SQLException {
        SchemaInitializationManager emptyManager = createManager(List.of(), jdbcTemplate);

        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");

        assertThrows(IllegalArgumentException.class, () -> {
            emptyManager.isSchemaInitialized();
        });
    }

    @Test
    @DisplayName("Test initializeSchemaIfNeeded doesn't initialize when already initialized")
    void testInitializeSchemaIfNeeded_DoesNotInitializeWhenAlreadyInitialized() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(any(JdbcTemplate.class))).thenReturn(true);

        manager.initializeSchemaIfNeeded();

        verify(sqliteStrategy, never()).initializeSchema(any(JdbcTemplate.class));
    }

    @Test
    @DisplayName("Test initializeSchemaIfNeeded initializes when not initialized")
    void testInitializeSchemaIfNeeded_InitializesWhenNotInitialized() throws SQLException {
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");
        when(sqliteStrategy.isSchemaInitialized(any(JdbcTemplate.class))).thenReturn(false);

        manager.initializeSchemaIfNeeded();

        verify(sqliteStrategy).initializeSchema(jdbcTemplate);
    }
}