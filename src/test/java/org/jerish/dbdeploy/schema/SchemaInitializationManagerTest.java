package org.jerish.dbdeploy.schema;

import org.jerish.dbdeploy.entity.DatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit tests for SchemaInitializationManager.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchemaInitializationManager Tests")
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
    private SchemaInitializationStrategy strategy;

    private SchemaInitializationManager schemaInitializationManager;

    @BeforeEach
    public void setUp() throws Exception {
        // Mock the JDBC chain
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:sqlite:test.db");

        // Create the manager manually
        Map<DatabaseType, SchemaInitializationStrategy> strategyMap = new HashMap<>();
        strategyMap.put(DatabaseType.SQLITE, strategy);

        schemaInitializationManager = new SchemaInitializationManager(strategyMap, jdbcTemplate);

        when(strategy.getSupportedDatabaseType()).thenReturn(DatabaseType.SQLITE);
    }

    @Test
    @DisplayName("Should initialize schema when not initialized")
    public void testInitializeSchemaIfNeededWhenNotInitialized() {
        when(strategy.isSchemaInitialized(jdbcTemplate)).thenReturn(false);

        schemaInitializationManager.initializeSchemaIfNeeded();

        verify(strategy, times(1)).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Should not initialize schema when already initialized")
    public void testInitializeSchemaIfNeededWhenAlreadyInitialized() {
        when(strategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        schemaInitializationManager.initializeSchemaIfNeeded();

        verify(strategy, never()).initializeSchema(jdbcTemplate);
    }

    @Test
    @DisplayName("Should force initialize schema regardless of current state")
    public void testInitializeSchemaForce() {
        when(strategy.isSchemaInitialized(jdbcTemplate)).thenReturn(true);

        schemaInitializationManager.initializeSchema();

        verify(strategy, times(1)).initializeSchema(jdbcTemplate);
    }
}