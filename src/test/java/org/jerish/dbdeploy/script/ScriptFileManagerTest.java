package org.jerish.dbdeploy.script;

import org.jerish.dbdeploy.entity.ScriptConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScriptFileManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScriptFileManager Tests")
public class ScriptFileManagerTest {

    private ScriptFileManager scriptFileManager;
    private String testScriptBasePath;
    private Path testScriptsDir;

    @BeforeEach
    void setUp() throws IOException {
        scriptFileManager = new ScriptFileManager();
        testScriptBasePath = "src/test/resources/test-scripts";
        testScriptsDir = Path.of(testScriptBasePath);
        
        // Create test scripts directory if it doesn't exist
        if (!Files.exists(testScriptsDir)) {
            Files.createDirectories(testScriptsDir);
        }
    }

    @Test
    @DisplayName("Test loadScript loads apply and rollback scripts")
    void testLoadScriptSuccess() throws IOException {
        // Create test script files
        String scriptName = "test-script";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");

        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
        assertNotNull(scriptFile.getApplyContent());
        assertEquals("CREATE TABLE test (id INT);", scriptFile.getApplyContent().trim());
        assertNotNull(scriptFile.getRollbackContent());
        assertEquals("DROP TABLE test;", scriptFile.getRollbackContent().trim());
    }

    @Test
    @DisplayName("Test loadScript handles missing rollback verify script")
    void testLoadScriptMissingRollbackVerify() throws IOException {
        // Create test script files without rollback verify
        String scriptName = "test-script-no-verify";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");

        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
        assertNull(scriptFile.getRollbackVerifyContent());
    }

    @Test
    @DisplayName("Test loadScript with rollback verify script")
    void testLoadScriptWithRollbackVerify() throws IOException {
        // Create test script files with rollback verify
        String scriptName = "test-script-with-verify";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        Path rollbackVerifyScriptPath = testScriptsDir.resolve(scriptName + ".rollback.verify.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");
        Files.writeString(rollbackVerifyScriptPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='test';");

        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
        assertNotNull(scriptFile.getRollbackVerifyContent());
        assertTrue(scriptFile.getRollbackVerifyContent().contains("sqlite_master"));
    }

    @Test
    @DisplayName("Test loadScript throws exception for missing apply script")
    void testLoadScriptMissingApplyScript() throws IOException {
        String scriptName = "nonexistent-script";
        ScriptConfig config = new ScriptConfig(scriptName, null);

        assertThrows(IOException.class, () -> {
            scriptFileManager.loadScript(testScriptBasePath, config);
        });
    }

    @Test
    @DisplayName("Test loadScript throws exception for missing rollback script")
    void testLoadScriptMissingRollbackScript() throws IOException {
        // Create only apply script
        String scriptName = "test-script-no-rollback";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");

        ScriptConfig config = new ScriptConfig(scriptName, null);

        assertThrows(IOException.class, () -> {
            scriptFileManager.loadScript(testScriptBasePath, config);
        });
    }

    @Test
    @DisplayName("Test loadScripts loads multiple scripts")
    void testLoadScriptsMultiple() throws IOException {
        // Create multiple test scripts
        String script1Name = "script1";
        String script2Name = "script2";
        
        Files.writeString(testScriptsDir.resolve(script1Name + ".apply.sql"), "CREATE TABLE test1 (id INT);");
        Files.writeString(testScriptsDir.resolve(script1Name + ".rollback.sql"), "DROP TABLE test1;");
        
        Files.writeString(testScriptsDir.resolve(script2Name + ".apply.sql"), "CREATE TABLE test2 (id INT);");
        Files.writeString(testScriptsDir.resolve(script2Name + ".rollback.sql"), "DROP TABLE test2;");

        ScriptConfig config1 = new ScriptConfig(script1Name, null);
        ScriptConfig config2 = new ScriptConfig(script2Name, null);
        List<ScriptConfig> configs = List.of(config1, config2);

        List<ScriptFileManager.ScriptFile> scriptFiles = scriptFileManager.loadScripts(testScriptBasePath, configs);

        assertNotNull(scriptFiles);
        assertEquals(2, scriptFiles.size());
        assertEquals(script1Name, scriptFiles.get(0).getName());
        assertEquals(script2Name, scriptFiles.get(1).getName());
    }

    @Test
    @DisplayName("Test loadScripts with empty config list")
    void testLoadScriptsEmptyList() throws IOException {
        List<ScriptConfig> configs = List.of();

        List<ScriptFileManager.ScriptFile> scriptFiles = scriptFileManager.loadScripts(testScriptBasePath, configs);

        assertNotNull(scriptFiles);
        assertTrue(scriptFiles.isEmpty());
    }

    @Test
    @DisplayName("Test loadScript with absolute path")
    void testLoadScriptAbsolutePath() throws IOException {
        // Create test script files
        String scriptName = "test-script-absolute";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");

        // Use absolute path
        String absoluteBasePath = testScriptsDir.toAbsolutePath().toString();
        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(absoluteBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
    }

    @Test
    @DisplayName("Test loadScript with relative path")
    void testLoadScriptRelativePath() throws IOException {
        // Create test script files
        String scriptName = "test-script-relative";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");

        // Use relative path
        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
    }

    @Test
    @DisplayName("Test ScriptFile fields")
    void testScriptFileFields() throws IOException {
        String scriptName = "test-script";
        String applyPath = "/path/to/test-script.apply.sql";
        String rollbackPath = "/path/to/test-script.rollback.sql";
        String applyContent = "CREATE TABLE test (id INT);";
        String rollbackContent = "DROP TABLE test;";
        String rollbackVerifyContent = null;

        ScriptFileManager.ScriptFile scriptFile = new ScriptFileManager.ScriptFile(
                scriptName, applyPath, rollbackPath, applyContent, rollbackContent, rollbackVerifyContent);

        assertEquals(scriptName, scriptFile.getName());
        assertEquals(applyPath, scriptFile.getApplyPath());
        assertEquals(rollbackPath, scriptFile.getRollbackPath());
        assertEquals(applyContent, scriptFile.getApplyContent());
        assertEquals(rollbackContent, scriptFile.getRollbackContent());
        assertEquals(rollbackVerifyContent, scriptFile.getRollbackVerifyContent());
    }

    @Test
    @DisplayName("Test ScriptFile getScriptName extracts filename from path")
    void testScriptFileGetScriptName() throws IOException {
        String applyPath = "/some/path/to/create-table.apply.sql";
        ScriptFileManager.ScriptFile scriptFile = new ScriptFileManager.ScriptFile(
                "create-table", applyPath, "", "", "", null);

        String scriptName = scriptFile.getScriptName();

        assertEquals("create-table.apply.sql", scriptName);
    }

    @Test
    @DisplayName("Test loadScript with folder structure")
    void testLoadScriptWithFolder() throws IOException {
        // Create folder structure
        Path folderPath = testScriptsDir.resolve("v1.0.0");
        Files.createDirectories(folderPath);
        
        String scriptName = "v1.0.0/create-table";
        Path applyScriptPath = folderPath.resolve("create-table.apply.sql");
        Path rollbackScriptPath = folderPath.resolve("create-table.rollback.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");

        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
        assertTrue(scriptFile.getApplyPath().contains("v1.0.0"));
    }

    @Test
    @DisplayName("Test loadScript handles special characters in script names")
    void testLoadScriptSpecialCharacters() throws IOException {
        String scriptName = "test-script_with-special.chars";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        
        Files.writeString(applyScriptPath, "CREATE TABLE test (id INT);");
        Files.writeString(rollbackScriptPath, "DROP TABLE test;");

        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
    }

    @Test
    @DisplayName("Test loadScript handles empty script content")
    void testLoadScriptEmptyContent() throws IOException {
        String scriptName = "empty-script";
        Path applyScriptPath = testScriptsDir.resolve(scriptName + ".apply.sql");
        Path rollbackScriptPath = testScriptsDir.resolve(scriptName + ".rollback.sql");
        
        Files.writeString(applyScriptPath, "");
        Files.writeString(rollbackScriptPath, "");

        ScriptConfig config = new ScriptConfig(scriptName, null);
        ScriptFileManager.ScriptFile scriptFile = scriptFileManager.loadScript(testScriptBasePath, config);

        assertNotNull(scriptFile);
        assertEquals(scriptName, scriptFile.getName());
        assertEquals("", scriptFile.getApplyContent());
        assertEquals("", scriptFile.getRollbackContent());
    }
}