package com.scb.mrp.schemaflow.dbdeploy.cli;

import com.scb.mrp.schemaflow.dbdeploy.config.ChangeLogPathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandLineOptionsResolver.
 * Tests command line argument parsing and changelog path resolution.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommandLineOptionsResolver Tests")
public class CommandLineOptionsResolverTest {

    @Mock
    private ChangeLogPathConfig changeLogPathConfig;

    @InjectMocks
    private CommandLineOptionsResolver resolver;

    @BeforeEach
    void setUp() {
        reset(changeLogPathConfig);
    }

    @Test
    @DisplayName("Test resolve with command line changelog path")
    void testResolve_WithCommandLineChangelogPath() {
        // Arrange
        String[] args = {
                "--action", "DEPLOY",
                "--changelog", "/custom/path/changelog.yml",
                "--verbose"
        };

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals(CommandLineOptions.Action.DEPLOY, result.getAction());
        assertEquals("/custom/path/changelog.yml", result.getChangelogPath());
        assertTrue(result.isVerbose());
        assertFalse(result.isDryRun());
        // Config is accessed but not used when command line path is provided
        verify(changeLogPathConfig).getPath();
    }

    @Test
    @DisplayName("Test resolve uses config path when command line path is null")
    void testResolve_UsesConfigPathWhenCommandLinePathIsNull() {
        // Arrange
        String[] args = {
                "--action", "DEPLOY"
        };
        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals("/config/default/changelog.yml", result.getChangelogPath());
        verify(changeLogPathConfig).getPath();
    }

    @Test
    @DisplayName("Test resolve with all flags enabled")
    void testResolve_WithAllFlagsEnabled() {
        // Arrange
        String[] args = {
                "--action", "ROLLBACK",
                "--changelog", "/path/changelog.yml",
                "--verbose",
                "--dry-run"
        };

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals(CommandLineOptions.Action.ROLLBACK, result.getAction());
        assertEquals("/path/changelog.yml", result.getChangelogPath());
        assertTrue(result.isVerbose());
        assertTrue(result.isDryRun());
    }

    @Test
    @DisplayName("Test resolve with default action")
    void testResolve_WithDefaultAction() {
        // Arrange
        String[] args = {
                "--changelog", "/path/changelog.yml"
        };

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals(CommandLineOptions.Action.DEPLOY_OR_ROLLBACK, result.getAction());
        assertEquals("/path/changelog.yml", result.getChangelogPath());
    }

    @Test
    @DisplayName("Test resolve with parameters")
    void testResolve_WithParameters() {
        // Arrange
        String[] args = {
                "--action", "DEPLOY",
                "--changelog", "/path/changelog.yml",
                "--param", "env=prod,region=us-west"
        };

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getParameters().size());
        assertEquals("prod", result.getParameters().get("env"));
        assertEquals("us-west", result.getParameters().get("region"));
    }

    @Test
    @DisplayName("Test resolveChangelogPath with command line path")
    void testResolveChangelogPath_WithCommandLinePath() {
        // Arrange
        String commandLinePath = "/custom/path/changelog.yml";
        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");

        // Act
        String result = resolver.resolveChangelogPath(commandLinePath);

        // Assert
        assertEquals("/custom/path/changelog.yml", result);
        // Config is accessed but not used when command line path is provided
        verify(changeLogPathConfig).getPath();
    }

    @Test
    @DisplayName("Test resolveChangelogPath with null command line path uses config")
    void testResolveChangelogPath_NullCommandLinePathUsesConfig() {
        // Arrange
        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");

        // Act
        String result = resolver.resolveChangelogPath(null);

        // Assert
        assertEquals("/config/default/changelog.yml", result);
        verify(changeLogPathConfig).getPath();
    }

    @Test
    @DisplayName("Test resolveChangelogPath with empty command line path returns empty string")
    void testResolveChangelogPath_EmptyCommandLinePathReturnsEmpty() {
        // Arrange
        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");

        // Act
        String result = resolver.resolveChangelogPath("");

        // Assert
        assertEquals("", result);
        // Config is accessed but not used when command line path is provided (even if empty)
        verify(changeLogPathConfig).getPath();
    }

    @Test
    @DisplayName("Test resolveChangelogPath with null config path returns null")
    void testResolveChangelogPath_NullConfigPathReturnsNull() {
        // Arrange
        when(changeLogPathConfig.getPath()).thenReturn(null);

        // Act
        String result = resolver.resolveChangelogPath(null);

        // Assert
        assertNull(result);
        verify(changeLogPathConfig).getPath();
    }

    @Test
    @DisplayName("Test resolve with STATUS action")
    void testResolve_WithStatusAction() {
        // Arrange
        String[] args = {
                "--action", "STATUS",
                "--verbose"
        };

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals(CommandLineOptions.Action.STATUS, result.getAction());
        assertTrue(result.isVerbose());
    }

    @Test
    @DisplayName("Test resolve with config path override")
    void testResolve_ConfigPathOverride() {
        // Arrange
        String[] args = {
                "--action", "DEPLOY"
        };
        when(changeLogPathConfig.getPath()).thenReturn("classpath:db/db-changelog.yml");

        // Act
        CommandLineOptions result = resolver.resolve(args);

        // Assert
        assertNotNull(result);
        assertEquals("classpath:db/db-changelog.yml", result.getChangelogPath());
        verify(changeLogPathConfig).getPath();
    }
}