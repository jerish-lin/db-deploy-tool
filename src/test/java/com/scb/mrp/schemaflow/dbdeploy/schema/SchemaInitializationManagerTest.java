package com.scb.mrp.schemaflow.dbdeploy.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private SchemaInitializationStrategy strategy;

    private SchemaInitializationManager schemaInitializationManager;

    @BeforeEach
    public void setUp() {
        schemaInitializationManager = new SchemaInitializationManager(strategy, jdbcTemplate);
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