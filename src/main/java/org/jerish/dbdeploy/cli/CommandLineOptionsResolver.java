package org.jerish.dbdeploy.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.config.ChangeLogPathConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Resolver for command line options that handles parameter validation
 * and changelog path resolution with fallback logic.
 */
@Component
@EnableConfigurationProperties(ChangeLogPathConfig.class)
@Slf4j
@RequiredArgsConstructor
public class CommandLineOptionsResolver {
    private final ChangeLogPathConfig changeLogPathConfig;

    public CommandLineOptions resolve(String[] args) {
        CommandLineOptions options = CommandLineOptions.parseArgs(args);
        validateTagNameRequired(options);
        options.setChangelogPath(resolveChangelogPath(options.getChangelogPath()));

        return options;
    }

    private void validateTagNameRequired(CommandLineOptions options) {
        if (options.getAction().tagRequired() && options.getTagName() == null) {
            throw new IllegalArgumentException("Tag name is required for deploy / rollback / deployOrRollback action");
        }
    }

    public String resolveChangelogPath(String commandLinePath) {
        String resolvedPath = commandLinePath;

        if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
            // Try to get from ChangeLogPathConfig
            try {
                if (changeLogPathConfig != null && changeLogPathConfig.getPath() != null && !changeLogPathConfig.getPath().trim().isEmpty()) {
                    resolvedPath = changeLogPathConfig.getPath();
                    log.debug("Using changelog path from configuration: {}", resolvedPath);
                }
            } catch (Exception e) {
                log.debug("ChangeLogPathConfig not available or error accessing it", e);
            }
        } else {
            log.debug("Using changelog path from command line: {}", resolvedPath);
        }

        if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
            // Fallback to classpath resource
            resolvedPath = "classpath:db/db-changelog.yml";
            log.debug("Using changelog path from classpath: {}", resolvedPath);
        }

        // Handle classpath resources
        if (resolvedPath.startsWith("classpath:")) {
            try {
                return copyClasspathResourcesToTemp(resolvedPath);
            } catch (Exception e) {
                log.error("Failed to copy classpath resources to temp folder", e);
                throw new RuntimeException("Failed to resolve classpath changelog: " + resolvedPath, e);
            }
        }

        return resolvedPath;
    }

    /**
     * Copies the changelog file and all files under scripts folder from classpath to a temporary folder
     * and returns the path to the changelog file in the temp folder.
     */
    private String copyClasspathResourcesToTemp(String classpathPath) throws IOException {
        // Extract the resource path from classpath: prefix
        String resourcePath = classpathPath.substring("classpath:".length());

        // Create temporary directory
        Path tempDir = Files.createTempDirectory("db-deploy-" + UUID.randomUUID().toString().substring(0, 8));
        log.debug("Created temporary directory: {}", tempDir);

        // Copy the changelog file
        Path changelogTargetPath = tempDir.resolve(Paths.get(resourcePath).getFileName());
        copyClasspathResource(resourcePath, changelogTargetPath);
        log.debug("Copied changelog from classpath:{} to {}", resourcePath, changelogTargetPath);

        // Copy all files under scripts folder
        copyClasspathDirectory("scripts", tempDir.resolve("scripts"));

        // Return the path to the changelog file in temp folder
        return changelogTargetPath.toString();
    }

    /**
     * Copies a single classpath resource to the target path
     */
    private void copyClasspathResource(String resourcePath, Path targetPath) throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new RuntimeException("Classpath resource not found: " + resourcePath);
        }

        // Create parent directories if they don't exist
        Files.createDirectories(targetPath.getParent());

        try (inputStream) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies all files from a classpath directory to the target directory
     */
    private void copyClasspathDirectory(String classpathDir, Path targetDir) throws IOException {
        // Create target directory
        Files.createDirectories(targetDir);

        // Get the class loader
        ClassLoader classLoader = this.getClass().getClassLoader();

        try {
            // Try to get the directory as a resource
            var resource = classLoader.getResource(classpathDir);
            if (resource != null && resource.getProtocol().equals("file")) {
                // If it's a file system resource, copy directly
                Path sourceDir = Paths.get(resource.toURI());
                copyDirectory(sourceDir, targetDir);
                return;
            }

            // For jar resources or other cases, try to discover all files dynamically
            boolean foundAny = false;

            // Use Spring's PathMatchingResourcePatternResolver if available
            try {
                // Try to use Spring's resource pattern matcher
                Class<?> resolverClass = Class.forName("org.springframework.core.io.support.PathMatchingResourcePatternResolver");
                Object resolver = resolverClass.getDeclaredConstructor().newInstance();
                Method getResources = resolverClass.getMethod("getResources", String.class);

                Object[] resources = (Object[]) getResources.invoke(resolver, "classpath:" + classpathDir + "/**");

                for (Object res : resources) {
                    Class<?> resourceClass = Class.forName("org.springframework.core.io.Resource");
                    Method exists = resourceClass.getMethod("exists");
                    Method getInputStream = resourceClass.getMethod("getInputStream");
                    Method getURL = resourceClass.getMethod("getURL");

                    if ((Boolean) exists.invoke(res)) {
                        String resourcePath = ((java.net.URL) getURL.invoke(res)).getPath();
                        // Extract relative path from the full resource path
                        if (resourcePath.contains(classpathDir)) {
                            String relativePath = resourcePath.substring(resourcePath.indexOf(classpathDir) + classpathDir.length() + 1);
                            if (!relativePath.endsWith("/")) {
                                Path targetPath = targetDir.resolve(relativePath);
                                try (InputStream inputStream = (InputStream) getInputStream.invoke(res)) {
                                    Files.createDirectories(targetPath.getParent());
                                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                    log.debug("Copied script resource to {}", targetPath);
                                    foundAny = true;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Spring resource resolver not available", e);
            }

            if (!foundAny) {
                log.warn("No script files found in classpath directory: {}", classpathDir);
            }

        } catch (Exception e) {
            log.error("Error copying classpath directory: " + classpathDir, e);
            throw new IOException("Failed to copy classpath directory: " + classpathDir, e);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .filter(path -> !Files.isDirectory(path))
                .forEach(sourcePath -> {
                    try {
                        Path relativePath = source.relativize(sourcePath);
                        Path targetPath = target.resolve(relativePath);
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("Copied file from {} to {}", sourcePath, targetPath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to copy file: " + sourcePath, e);
                    }
                });
    }

}