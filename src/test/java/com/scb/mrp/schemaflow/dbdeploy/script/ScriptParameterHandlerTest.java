package com.scb.mrp.schemaflow.dbdeploy.script;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScriptParameterHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScriptParameterHandler Tests")
public class ScriptParameterHandlerTest {

    @InjectMocks
    private ScriptParameterHandler parameterHandler;

    @Test
    @DisplayName("Replace single placeholder successfully")
    public void testReplaceSinglePlaceholder() {
        String content = "SELECT * FROM ${table_name} WHERE id = 1;";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("SELECT * FROM users WHERE id = 1;", result, "Placeholder should be replaced");
    }

    @Test
    @DisplayName("Replace multiple placeholders successfully")
    public void testReplaceMultiplePlaceholders() {
        String content = "INSERT INTO ${table_name} (id, ${column_name}) VALUES (${id}, '${value}');";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");
        parameters.put("column_name", "name");
        parameters.put("id", "1");
        parameters.put("value", "John");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("INSERT INTO users (id, name) VALUES (1, 'John');", result, "All placeholders should be replaced");
    }

    @Test
    @DisplayName("Replace placeholder with empty string")
    public void testReplacePlaceholderWithEmptyString() {
        String content = "SELECT * FROM ${table_name};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("SELECT * FROM ;", result, "Placeholder should be replaced with empty string");
    }

    @Test
    @DisplayName("Replace placeholder with special characters")
    public void testReplacePlaceholderWithSpecialCharacters() {
        String content = "SELECT * FROM ${table_name};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "user_data; DROP TABLE users; --");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("SELECT * FROM user_data; DROP TABLE users; --;", result, "Placeholder should be replaced with special characters");
    }

    @Test
    @DisplayName("Replace placeholder with numbers")
    public void testReplacePlaceholderWithNumbers() {
        String content = "SELECT * FROM table_${version};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("version", "1.0");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("SELECT * FROM table_1.0;", result, "Placeholder should be replaced with numbers");
    }

    @Test
    @DisplayName("Throw exception when placeholder not found in parameters")
    public void testReplacePlaceholderNotFound() {
        String content = "SELECT * FROM ${table_name};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("column_name", "id");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parameterHandler.replacePlaceholders(content, parameters);
        });

        assertTrue(exception.getMessage().contains("No value provided for placeholder: table_name"), 
                "Exception should mention missing placeholder");
    }

    @Test
    @DisplayName("Throw exception when content has placeholders but parameters is empty")
    public void testReplacePlaceholdersWithEmptyParameters() {
        String content = "SELECT * FROM ${table_name};";
        Map<String, String> parameters = new HashMap<>();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parameterHandler.replacePlaceholders(content, parameters);
        });

        // When parameters is empty, the exception message is different from when it's null
        assertTrue(exception.getMessage().contains("SQL contains placeholders but no parameters were provided") ||
                   exception.getMessage().contains("No value provided for placeholder"), 
                "Exception should mention missing placeholder or no parameters");
    }

    @Test
    @DisplayName("Throw exception when content has placeholders but parameters is null")
    public void testReplacePlaceholdersWithNullParameters() {
        String content = "SELECT * FROM ${table_name};";

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parameterHandler.replacePlaceholders(content, null);
        });

        assertTrue(exception.getMessage().contains("SQL contains placeholders but no parameters were provided"), 
                "Exception should mention no parameters provided");
    }

    @Test
    @DisplayName("Return null content when content is null")
    public void testReplacePlaceholdersWithNullContent() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");

        String result = parameterHandler.replacePlaceholders(null, parameters);

        assertNull(result, "Result should be null when content is null");
    }

    @Test
    @DisplayName("Return content unchanged when no placeholders exist")
    public void testReplacePlaceholdersNoPlaceholders() {
        String content = "SELECT * FROM users WHERE id = 1;";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals(content, result, "Content should remain unchanged when no placeholders exist");
    }

    @Test
    @DisplayName("Return content unchanged when no placeholders exist and parameters is empty")
    public void testReplacePlaceholdersNoPlaceholdersEmptyParameters() {
        String content = "SELECT * FROM users WHERE id = 1;";
        Map<String, String> parameters = new HashMap<>();

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals(content, result, "Content should remain unchanged");
    }

    @Test
    @DisplayName("Return content unchanged when no placeholders exist and parameters is null")
    public void testReplacePlaceholdersNoPlaceholdersNullParameters() {
        String content = "SELECT * FROM users WHERE id = 1;";

        String result = parameterHandler.replacePlaceholders(content, null);

        assertEquals(content, result, "Content should remain unchanged");
    }

    @Test
    @DisplayName("Check if content contains placeholders - true")
    public void testContainsPlaceholdersTrue() {
        String content = "SELECT * FROM ${table_name};";

        boolean result = parameterHandler.containsPlaceholders(content);

        assertTrue(result, "Should return true when placeholders exist");
    }

    @Test
    @DisplayName("Check if content contains placeholders - false")
    public void testContainsPlaceholdersFalse() {
        String content = "SELECT * FROM users;";

        boolean result = parameterHandler.containsPlaceholders(content);

        assertFalse(result, "Should return false when no placeholders exist");
    }

    @Test
    @DisplayName("Check if null content contains placeholders")
    public void testContainsPlaceholdersNullContent() {
        boolean result = parameterHandler.containsPlaceholders(null);

        assertFalse(result, "Should return false when content is null");
    }

    @Test
    @DisplayName("Extract placeholder names from content")
    public void testExtractPlaceholderNames() {
        String content = "SELECT * FROM ${table_name} WHERE id = ${id} AND name = '${name}';";

        List<String> placeholders = parameterHandler.extractPlaceholderNames(content);

        assertEquals(3, placeholders.size(), "Should extract 3 placeholders");
        assertTrue(placeholders.contains("table_name"), "Should contain table_name");
        assertTrue(placeholders.contains("id"), "Should contain id");
        assertTrue(placeholders.contains("name"), "Should contain name");
    }

    @Test
    @DisplayName("Extract unique placeholder names from content")
    public void testExtractPlaceholderNamesUnique() {
        String content = "SELECT * FROM ${table_name} WHERE ${table_name}.id = ${id} AND ${table_name}.name = '${name}';";

        List<String> placeholders = parameterHandler.extractPlaceholderNames(content);

        assertEquals(3, placeholders.size(), "Should extract 3 unique placeholders");
        assertTrue(placeholders.contains("table_name"), "Should contain table_name");
        assertTrue(placeholders.contains("id"), "Should contain id");
        assertTrue(placeholders.contains("name"), "Should contain name");
    }

    @Test
    @DisplayName("Extract placeholder names from content with no placeholders")
    public void testExtractPlaceholderNamesNoPlaceholders() {
        String content = "SELECT * FROM users WHERE id = 1;";

        List<String> placeholders = parameterHandler.extractPlaceholderNames(content);

        assertEquals(0, placeholders.size(), "Should extract 0 placeholders");
    }

    @Test
    @DisplayName("Extract placeholder names from null content")
    public void testExtractPlaceholderNamesNullContent() {
        List<String> placeholders = parameterHandler.extractPlaceholderNames(null);

        assertEquals(0, placeholders.size(), "Should extract 0 placeholders from null content");
    }

    @Test
    @DisplayName("Validate placeholders - all present")
    public void testValidatePlaceholdersAllPresent() {
        String content = "SELECT * FROM ${table_name} WHERE id = ${id};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");
        parameters.put("id", "1");

        assertDoesNotThrow(() -> parameterHandler.validatePlaceholders(content, parameters),
                "Should not throw exception when all placeholders are present");
    }

    @Test
    @DisplayName("Validate placeholders - missing placeholder")
    public void testValidatePlaceholdersMissingPlaceholder() {
        String content = "SELECT * FROM ${table_name} WHERE id = ${id};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parameterHandler.validatePlaceholders(content, parameters);
        });

        assertTrue(exception.getMessage().contains("No value provided for placeholder: id"), 
                "Exception should mention missing placeholder");
    }

    @Test
    @DisplayName("Validate placeholders - null parameters")
    public void testValidatePlaceholdersNullParameters() {
        String content = "SELECT * FROM ${table_name};";

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parameterHandler.validatePlaceholders(content, null);
        });

        assertTrue(exception.getMessage().contains("No value provided for placeholder: table_name"), 
                "Exception should mention missing placeholder");
    }

    @Test
    @DisplayName("Validate placeholders - null content")
    public void testValidatePlaceholdersNullContent() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "users");

        assertDoesNotThrow(() -> parameterHandler.validatePlaceholders(null, parameters),
                "Should not throw exception when content is null");
    }

    @Test
    @DisplayName("Validate placeholders - no placeholders in content")
    public void testValidatePlaceholdersNoPlaceholders() {
        String content = "SELECT * FROM users WHERE id = 1;";
        Map<String, String> parameters = new HashMap<>();

        assertDoesNotThrow(() -> parameterHandler.validatePlaceholders(content, parameters),
                "Should not throw exception when no placeholders exist");
    }

    @Test
    @DisplayName("Replace placeholder with underscore in name")
    public void testReplacePlaceholderWithUnderscore() {
        String content = "SELECT * FROM ${table_name};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("table_name", "my_table");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("SELECT * FROM my_table;", result, "Placeholder with underscore should be replaced");
    }

    @Test
    @DisplayName("Replace placeholder with mixed case")
    public void testReplacePlaceholderMixedCase() {
        String content = "SELECT * FROM ${TableName};";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("TableName", "MyTable");

        String result = parameterHandler.replacePlaceholders(content, parameters);

        assertEquals("SELECT * FROM MyTable;", result, "Placeholder with mixed case should be replaced");
    }
}