package com.scb.mrp.schemaflow.dbdeploy.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OnDatabaseDriverCondition.
 * Tests the Spring condition evaluation for database-specific bean creation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OnDatabaseDriverCondition Tests")
public class OnDatabaseDriverConditionTest {

    private OnDatabaseDriverCondition condition;

    @Mock
    private org.springframework.context.annotation.ConditionContext context;

    @Mock
    private Environment environment;

    @Mock
    private AnnotatedTypeMetadata metadata;

    @BeforeEach
    void setUp() {
        condition = new OnDatabaseDriverCondition();
        lenient().when(context.getEnvironment()).thenReturn(environment);
    }

    @Test
    @DisplayName("Test matches returns true for PostgreSQL driver")
    void testMatches_ReturnsTrueForPostgreSQL() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("org.postgresql.Driver");

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertTrue(result);
        verify(environment).getProperty(eq("schemaflow.database.driver"));
    }

    @Test
    @DisplayName("Test matches returns true for SQLite driver")
    void testMatches_ReturnsTrueForSQLite() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.SQLITE);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("org.sqlite.JDBC");

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test matches returns true for ClickHouse driver")
    void testMatches_ReturnsTrueForClickHouse() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.CLICKHOUSE);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("com.clickhouse.jdbc.ClickHouseDriver");

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test matches returns false when driver does not match")
    void testMatches_ReturnsFalseWhenDriverDoesNotMatch() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("org.sqlite.JDBC");

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test matches returns false when configured driver is null")
    void testMatches_ReturnsFalseWhenConfiguredDriverIsNull() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn(null);

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test matches returns false when configured driver is empty")
    void testMatches_ReturnsFalseWhenConfiguredDriverIsEmpty() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("");

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test matches returns false when annotation value is null")
    void testMatches_ReturnsFalseWhenAnnotationValueIsNull() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", null);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test matches is case-insensitive for driver class")
    void testMatches_IsCaseInsensitiveForDriverClass() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("org.postgresql.driver"); // lowercase

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test matches with mixed case driver")
    void testMatches_WithMixedCaseDriver() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.SQLITE);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("Org.Sqlite.JDBC"); // mixed case

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test matches with whitespace in driver")
    void testMatches_WithWhitespaceInDriver() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("org.postgresql.Driver"); // no whitespace

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test matches for all database types")
    void testMatches_ForAllDatabaseTypes() {
        // Test each database type
        DatabaseType[] types = {DatabaseType.POSTGRESQL, DatabaseType.SQLITE, DatabaseType.CLICKHOUSE};

        for (DatabaseType type : types) {
            // Arrange
            Map<String, Object> annotationAttributes = new HashMap<>();
            annotationAttributes.put("value", type);
            when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                    .thenReturn(annotationAttributes);
            when(environment.getProperty(eq("schemaflow.database.driver")))
                    .thenReturn(type.getDriverClass());

            // Act
            boolean result = condition.matches(context, metadata);

            // Assert
            assertTrue(result, "Should match for " + type.name());
        }
    }

    @Test
    @DisplayName("Test matches returns false for unsupported driver")
    void testMatches_ReturnsFalseForUnsupportedDriver() {
        // Arrange
        Map<String, Object> annotationAttributes = new HashMap<>();
        annotationAttributes.put("value", DatabaseType.POSTGRESQL);
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(annotationAttributes);
        when(environment.getProperty(eq("schemaflow.database.driver")))
                .thenReturn("com.mysql.cj.jdbc.Driver");

        // Act
        boolean result = condition.matches(context, metadata);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test matches with null annotation attributes throws exception")
    void testMatches_WithNullAnnotationAttributes() {
        // Arrange
        when(metadata.getAnnotationAttributes(eq(ConditionalOnDatabaseDriver.class.getName()), eq(true)))
                .thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            condition.matches(context, metadata);
        });
    }
}