package org.jerish.dbdeploy.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileReader.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileReader Tests")
public class FileReaderTest {

    @InjectMocks
    private FileReader fileReader;

    private Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        // Create a unique temporary file for each test
        tempFile = Files.createTempFile("test-file-" + System.currentTimeMillis(), ".txt");
        Files.writeString(tempFile, "Test content for file reader", StandardOpenOption.WRITE);
    }

    @Test
    @DisplayName("Read file from classpath successfully")
    public void testReadFileFromClasspath() throws IOException {
        String path = "classpath:test-scripts/test-script.sql";

        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertFalse(content.isEmpty(), "Content should not be empty");
        assertTrue(content.contains("CREATE TABLE"), "Content should contain SQL");
    }

    @Test
    @DisplayName("Read file from classpath with leading slash")
    public void testReadFileFromClasspathWithLeadingSlash() throws IOException {
        String path = "classpath:/test-scripts/test-script.sql";

        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertFalse(content.isEmpty(), "Content should not be empty");
    }

    @Test
    @DisplayName("Read file from filesystem successfully")
    public void testReadFileFromFilesystem() throws IOException {
        String path = tempFile.toAbsolutePath().toString();

        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertEquals("Test content for file reader", content, "Content should match");
    }

    @Test
    @DisplayName("Read file from relative path")
    public void testReadFileFromRelativePath() throws IOException {
        String path = tempFile.toString();

        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertEquals("Test content for file reader", content, "Content should match");
    }

    @Test
    @DisplayName("Throw exception when path is null")
    public void testReadFileNullPath() {
        IOException exception = assertThrows(IOException.class, () -> {
            fileReader.readFile(null);
        });

        assertTrue(exception.getMessage().contains("Path cannot be null or empty"), 
                "Exception should mention null path");
    }

    @Test
    @DisplayName("Throw exception when path is empty")
    public void testReadFileEmptyPath() {
        IOException exception = assertThrows(IOException.class, () -> {
            fileReader.readFile("");
        });

        assertTrue(exception.getMessage().contains("Path cannot be null or empty"), 
                "Exception should mention empty path");
    }

    @Test
    @DisplayName("Throw exception when path is whitespace")
    public void testReadFileWhitespacePath() {
        IOException exception = assertThrows(IOException.class, () -> {
            fileReader.readFile("   ");
        });

        assertTrue(exception.getMessage().contains("Path cannot be null or empty"), 
                "Exception should mention empty path");
    }

    @Test
    @DisplayName("Throw exception when classpath resource not found")
    public void testReadFileFromClasspathNotFound() {
        String path = "classpath:non-existent-file.txt";

        IOException exception = assertThrows(IOException.class, () -> {
            fileReader.readFile(path);
        });

        assertTrue(exception.getMessage().contains("Classpath resource not found"), 
                "Exception should mention resource not found");
    }

    @Test
    @DisplayName("Throw exception when filesystem file not found")
    public void testReadFileFromFilesystemNotFound() {
        String path = "C:\\non-existent\\path\\to\\file.txt";

        IOException exception = assertThrows(IOException.class, () -> {
            fileReader.readFile(path);
        });

        assertTrue(exception.getMessage().contains("File not found"), 
                "Exception should mention file not found");
    }

    @Test
    @DisplayName("Throw exception when path is a directory")
    public void testReadFileWhenPathIsDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("test-dir");
        String path = tempDir.toAbsolutePath().toString();

        IOException exception = assertThrows(IOException.class, () -> {
            fileReader.readFile(path);
        });

        assertTrue(exception.getMessage().contains("Path is not a regular file"), 
                "Exception should mention path is not a regular file");
        
        // Clean up
        Files.deleteIfExists(tempDir);
    }

    @Test
    @DisplayName("Read file with UTF-8 encoding")
    public void testReadFileWithUtf8Encoding() throws IOException {
        String testContent = "Test content with special characters: 你好世界 αβγ";
        Files.writeString(tempFile, testContent, StandardOpenOption.WRITE);
        
        String path = tempFile.toAbsolutePath().toString();
        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertEquals(testContent, content, "Content should match with UTF-8 encoding");
    }

    @Test
    @DisplayName("Read file with multiple lines")
    public void testReadFileWithMultipleLines() throws IOException {
        String testContent = "Line 1\nLine 2\nLine 3";
        Path multiLineFile = Files.createTempFile("test-multiline-" + System.currentTimeMillis(), ".txt");
        Files.writeString(multiLineFile, testContent, StandardOpenOption.WRITE);
        
        String path = multiLineFile.toAbsolutePath().toString();
        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertEquals(testContent, content, "Content should match with multiple lines");
        
        // Clean up
        Files.deleteIfExists(multiLineFile);
    }

    @Test
    @DisplayName("Read file with empty content")
    public void testReadFileWithEmptyContent() throws IOException {
        Path emptyFile = Files.createTempFile("test-empty-" + System.currentTimeMillis(), ".txt");
        Files.writeString(emptyFile, "", StandardOpenOption.WRITE);
        
        String path = emptyFile.toAbsolutePath().toString();
        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertEquals("", content, "Content should be empty");
        
        // Clean up
        Files.deleteIfExists(emptyFile);
    }

    @Test
    @DisplayName("Read file with SQL content")
    public void testReadFileWithSqlContent() throws IOException {
        String testContent = "CREATE TABLE test_table (\n" +
                           "    id INT PRIMARY KEY,\n" +
                           "    name VARCHAR(100)\n" +
                           ");";
        Path sqlFile = Files.createTempFile("test-sql-" + System.currentTimeMillis(), ".sql");
        Files.writeString(sqlFile, testContent, StandardOpenOption.WRITE);
        
        String path = sqlFile.toAbsolutePath().toString();
        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertEquals(testContent, content, "Content should match SQL content");
        
        // Clean up
        Files.deleteIfExists(sqlFile);
    }

    @Test
    @DisplayName("Read file from classpath with subdirectory")
    public void testReadFileFromClasspathWithSubdirectory() throws IOException {
        String path = "classpath:test-scripts/v1.0.0/create-table.apply.sql";

        String content = fileReader.readFile(path);

        assertNotNull(content, "Content should not be null");
        assertFalse(content.isEmpty(), "Content should not be empty");
        assertTrue(content.contains("CREATE TABLE"), "Content should contain SQL");
    }
}