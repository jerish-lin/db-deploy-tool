package com.scb.mrp.schemaflow.dbdeploy.changelog;

import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigLoader.
 * Tests configuration loading from YAML files with various formats.
 */
@DisplayName("ConfigLoader Tests")
public class ConfigLoaderTest {

    private static final String TEST_DIR = "test-config-loader";

    @Test
    @DisplayName("Test loadChangeLogConfig with simple string format (backward compatible)")
    void testLoadChangeLogConfigWithSimpleStringFormat() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - create-users-table
                  - create-products-table
                  - add-user-email-index
                """;

        String configPath = createTempConfigFile("simple-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(3, config.getScripts().size());

        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("create-users-table", script1.getName());
        assertNull(script1.getNodes());
        assertFalse(script1.isMultiNode());

        ScriptConfig script2 = config.getScripts().get(1);
        assertEquals("create-products-table", script2.getName());
        assertNull(script2.getNodes());

        ScriptConfig script3 = config.getScripts().get(2);
        assertEquals("add-user-email-index", script3.getName());
        assertNull(script3.getNodes());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with object format (name only)")
    void testLoadChangeLogConfigWithObjectFormat() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - name: create-users-table
                  - name: create-products-table
                  - name: add-user-email-index
                """;

        String configPath = createTempConfigFile("object-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(3, config.getScripts().size());

        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("create-users-table", script1.getName());
        assertNull(script1.getNodes());
        assertFalse(script1.isMultiNode());

        ScriptConfig script2 = config.getScripts().get(1);
        assertEquals("create-products-table", script2.getName());
        assertNull(script2.getNodes());

        ScriptConfig script3 = config.getScripts().get(2);
        assertEquals("add-user-email-index", script3.getName());
        assertNull(script3.getNodes());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with object format (name and nodes)")
    void testLoadChangeLogConfigWithObjectFormatAndNodes() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - name: create-users-table
                    nodes: [node1, node2]
                  - name: create-products-table
                  - name: add-user-email-index
                    nodes: [ALL]
                """;

        String configPath = createTempConfigFile("multi-node-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(3, config.getScripts().size());

        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("create-users-table", script1.getName());
        assertEquals(List.of("node1", "node2"), script1.getNodes());
        assertTrue(script1.isMultiNode());
        assertFalse(script1.isAllNodes());

        ScriptConfig script2 = config.getScripts().get(1);
        assertEquals("create-products-table", script2.getName());
        assertNull(script2.getNodes());
        assertFalse(script2.isMultiNode());

        ScriptConfig script3 = config.getScripts().get(2);
        assertEquals("add-user-email-index", script3.getName());
        assertEquals(List.of("ALL"), script3.getNodes());
        assertTrue(script3.isMultiNode());
        assertTrue(script3.isAllNodes());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with object format (nodes as string)")
    void testLoadChangeLogConfigWithNodesAsString() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - name: create-users-table
                    nodes: ALL
                """;

        String configPath = createTempConfigFile("nodes-string-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(1, config.getScripts().size());

        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("create-users-table", script1.getName());
        assertEquals(List.of("ALL"), script1.getNodes());
        assertTrue(script1.isMultiNode());
        assertTrue(script1.isAllNodes());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with folder structure")
    void testLoadChangeLogConfigWithFolderStructure() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - feature-12346/create-users-table
                  - feature-12347/create-products-table
                  - v1.0.0/add-user-email-index
                """;

        String configPath = createTempConfigFile("folder-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(3, config.getScripts().size());

        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("feature-12346/create-users-table", script1.getName());
        assertEquals("feature-12346", script1.getFolderPath());
        assertEquals("create-users-table", script1.getBaseScriptName());
        assertTrue(script1.hasFolderPath());

        ScriptConfig script2 = config.getScripts().get(1);
        assertEquals("feature-12347/create-products-table", script2.getName());
        assertEquals("feature-12347", script2.getFolderPath());
        assertEquals("create-products-table", script2.getBaseScriptName());

        ScriptConfig script3 = config.getScripts().get(2);
        assertEquals("v1.0.0/add-user-email-index", script3.getName());
        assertEquals("v1.0.0", script3.getFolderPath());
        assertEquals("add-user-email-index", script3.getBaseScriptName());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with mixed format")
    void testLoadChangeLogConfigWithMixedFormat() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - simple-script
                  - name: object-script
                  - folder-structure/script
                  - name: multi-node-script
                    nodes: [node1, node2, node3]
                """;

        String configPath = createTempConfigFile("mixed-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(4, config.getScripts().size());

        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("simple-script", script1.getName());
        assertNull(script1.getNodes());

        ScriptConfig script2 = config.getScripts().get(1);
        assertEquals("object-script", script2.getName());
        assertNull(script2.getNodes());

        ScriptConfig script3 = config.getScripts().get(2);
        assertEquals("folder-structure/script", script3.getName());
        assertTrue(script3.hasFolderPath());

        ScriptConfig script4 = config.getScripts().get(3);
        assertEquals("multi-node-script", script4.getName());
        assertEquals(List.of("node1", "node2", "node3"), script4.getNodes());
        assertTrue(script4.isMultiNode());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig parses file path correctly (non-classpath)")
    void testLoadChangeLogConfigFilePathParsing() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - test-script
                """;

        String configPath = createTempConfigFile("test-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertNotNull(config.getBasePath());
        assertEquals("test-changelog.yml", config.getFileName());
        assertTrue(config.getBasePath().contains(TEST_DIR));
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with empty scripts list")
    void testLoadChangeLogConfigWithEmptyScripts() throws Exception {
        // Arrange
        String yamlContent = """
                scripts: []
                """;

        String configPath = createTempConfigFile("empty-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertNotNull(config.getScripts());
        assertTrue(config.getScripts().isEmpty());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with null config throws exception")
    void testLoadChangeLogConfigWithNullConfig() throws Exception {
        // Arrange
        String yamlContent = "";
        String configPath = createTempConfigFile("null-changelog.yml", yamlContent);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            ConfigLoader.loadChangeLogConfig(configPath);
        });
    }

    @Test
    @DisplayName("Test loadChangeLogConfig with multiple node configurations")
    void testLoadChangeLogConfigWithMultipleNodeConfigs() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - name: script1
                    nodes: [node1, node2]
                  - name: script2
                    nodes: [ALL]
                  - name: script3
                    nodes: [node3]
                  - name: script4
                """;

        String configPath = createTempConfigFile("multi-node-configs.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        assertEquals(4, config.getScripts().size());

        assertEquals(List.of("node1", "node2"), config.getScripts().get(0).getNodes());
        assertEquals(List.of("ALL"), config.getScripts().get(1).getNodes());
        assertEquals(List.of("node3"), config.getScripts().get(2).getNodes());
        assertNull(config.getScripts().get(3).getNodes());
    }

    @Test
    @DisplayName("Test loadChangeLogConfig script path generation")
    void testLoadChangeLogConfigScriptPathGeneration() throws Exception {
        // Arrange
        String yamlContent = """
                scripts:
                  - feature-123/create-table
                  - name: add-index
                    nodes: [node1]
                """;

        String configPath = createTempConfigFile("path-gen-changelog.yml", yamlContent);

        // Act
        ChangeLogConfig config = ConfigLoader.loadChangeLogConfig(configPath);

        // Assert
        assertNotNull(config);
        ScriptConfig script1 = config.getScripts().get(0);
        assertEquals("feature-123/create-table.apply.sql", script1.getApplyScriptPath());
        assertEquals("feature-123/create-table.rollback.sql", script1.getRollbackScriptPath());
        assertEquals("feature-123/create-table.apply.verify.sql", script1.getApplyVerifyScriptPath());
        assertEquals("feature-123/create-table.rollback.verify.sql", script1.getRollbackVerifyScriptPath());

        ScriptConfig script2 = config.getScripts().get(1);
        assertEquals("add-index.apply.sql", script2.getApplyScriptPath());
        assertEquals("add-index.rollback.sql", script2.getRollbackScriptPath());
    }

    /**
     * Helper method to create a temporary config file for testing.
     */
    private String createTempConfigFile(String fileName, String content) throws IOException {
        Path testDir = Path.of(TEST_DIR);
        if (!Files.exists(testDir)) {
            Files.createDirectories(testDir);
        }

        Path filePath = testDir.resolve(fileName);
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(content);
        }

        return filePath.toString();
    }

    /**
     * Clean up test files after tests.
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Path testDir = Path.of(TEST_DIR);
                if (Files.exists(testDir)) {
                    Files.walk(testDir)
                            .sorted((a, b) -> -a.compareTo(b))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    // Ignore cleanup errors
                                }
                            });
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }));
    }
}