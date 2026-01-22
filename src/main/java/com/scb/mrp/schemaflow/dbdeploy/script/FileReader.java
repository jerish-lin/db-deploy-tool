package com.scb.mrp.schemaflow.dbdeploy.script;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File manager for reading script content from various sources.
 * Supports reading from absolute file paths and classpath resources.
 */
@Slf4j
@Service
public class FileReader {

    /**
     * Read file content from the given path.
     * The path can be:
     * - An absolute file path (e.g., "C:/scripts/my-script.sql")
     * - A classpath resource prefixed with "classpath:" (e.g., "classpath:scripts/my-script.sql")
     *
     * @param path The path to read from
     * @return The file content as a String
     * @throws IOException if the file cannot be read
     */
    public String readFile(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            throw new IOException("Path cannot be null or empty");
        }

        if (path.startsWith("classpath:")) {
            return readFromClasspath(path);
        } else {
            return readFromFileSystem(path);
        }
    }

    /**
     * Read file content from the classpath
     *
     * @param classpathPath The classpath path (must start with "classpath:")
     * @return The file content as a String
     * @throws IOException if the file cannot be read from classpath
     */
    private String readFromClasspath(String classpathPath) throws IOException {
        String resourcePath = classpathPath.substring("classpath:".length());

        // Remove leading slash if present
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        log.debug("Reading file from classpath: {}", resourcePath);

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Classpath resource not found: " + classpathPath);
            }

            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Read file content from the file system
     *
     * @param filePath The absolute or relative file path
     * @return The file content as a String
     * @throws IOException if the file cannot be read from file system
     */
    private String readFromFileSystem(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        log.debug("Reading file from filesystem: {}", path.toAbsolutePath());

        return Files.readString(path, StandardCharsets.UTF_8);
    }
}