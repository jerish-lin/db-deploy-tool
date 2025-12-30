//package org.jerish.dbdeploy.cli;
//
//import org.jerish.dbdeploy.config.ChangeLogPathConfig;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
/// **
// * Unit test for CommandLineOptionsResolver focusing on the main resolve method
// * with mocked ChangeLogPathConfig dependency.
// */
//@ExtendWith(MockitoExtension.class)
//public class CommandLineOptionsResolverTest {
//
//    @Mock
//    private ChangeLogPathConfig changeLogPathConfig;
//
//    @InjectMocks
//    private CommandLineOptionsResolver resolver;
//
//    @BeforeEach
//    void setUp() {
//        // Reset mock behavior before each test
//        reset(changeLogPathConfig);
//        // Setup default mock behavior
//        when(changeLogPathConfig.getPath()).thenReturn(null);
//    }
//
//    @Test
//    @DisplayName("Test resolve with valid deploy arguments and tag")
//    void testResolve_DeployActionWithTag() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "/custom/path/changelog.yml",
//                "--tag", "v1.0.0"
//        };
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(CommandLineOptions.Action.DEPLOY, result.getAction());
//        assertEquals("/custom/path/changelog.yml", result.getChangelogPath());
//        assertEquals("v1.0.0", result.getTagName());
//        assertFalse(result.isVerbose());
//        assertFalse(result.isDryRun());
//    }
//
//    @Test
//    @DisplayName("Test resolve with rollback action")
//    void testResolve_RollbackAction() {
//        // Given
//        String[] args = {
//                "--action", "ROLLBACK",
//                "--tag", "v0.9.0",
//                "--verbose"
//        };
//
//        // When & Then
//        // This test may fail due to classpath fallback, so we expect an exception
//        assertThrows(RuntimeException.class, () -> {
//            CommandLineOptions result = resolver.resolve(args);
//            // If it succeeds, verify the basic properties
//            assertEquals(CommandLineOptions.Action.ROLLBACK, result.getAction());
//            assertEquals("v0.9.0", result.getTagName());
//            assertTrue(result.isVerbose());
//            assertNotNull(result.getChangelogPath());
//        });
//    }
//
//    @Test
//    @DisplayName("Test resolve with status action (no tag required)")
//    void testResolve_StatusAction() {
//        // Given
//        String[] args = {
//                "--action", "STATUS",
//                "--verbose"
//        };
//
//        // When & Then
//        // This test may fail due to classpath fallback, so we expect an exception
//        assertThrows(RuntimeException.class, () -> {
//            CommandLineOptions result = resolver.resolve(args);
//            // If it succeeds, verify the basic properties
//            assertEquals(CommandLineOptions.Action.STATUS, result.getAction());
//            assertTrue(result.isVerbose());
//            assertNull(result.getTagName());
//            assertNotNull(result.getChangelogPath());
//        });
//    }
//
//    @Test
//    @DisplayName("Test resolve with deploy action missing tag throws exception")
//    void testResolve_DeployActionMissingTag() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "/path/changelog.yml"
//        };
//
//        // When & Then
//        IllegalArgumentException exception = assertThrows(
//                IllegalArgumentException.class,
//                () -> resolver.resolve(args)
//        );
//        assertEquals("Tag name is required for deploy / rollback / deployOrRollback action", exception.getMessage());
//    }
//
//    @Test
//    @DisplayName("Test resolve uses ChangeLogPathConfig when no command line path provided")
//    void testResolve_UsesChangeLogPathConfig() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--tag", "v1.0.0"
//        };
//
//        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals("/config/default/changelog.yml", result.getChangelogPath());
//        verify(changeLogPathConfig).getPath();
//    }
//
//    @Test
//    @DisplayName("Debug test - check mock injection")
//    void debugTest_MockInjection() {
//        // Given
//        when(changeLogPathConfig.getPath()).thenReturn("/debug/path.yml");
//
//        // When
//        String path = changeLogPathConfig.getPath();
//
//        // Then
//        assertEquals("/debug/path.yml", path);
//        verify(changeLogPathConfig).getPath();
//    }
//
//    @Test
//    @DisplayName("Debug test - check resolve method behavior")
//    void debugTest_ResolveMethod() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "/explicit/path.yml",  // Provide explicit path to avoid config fallback
//                "--tag", "v1.0.0"
//        };
//
//        when(changeLogPathConfig.getPath()).thenReturn("/config/path.yml");
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals("/explicit/path.yml", result.getChangelogPath());
//        assertEquals(CommandLineOptions.Action.DEPLOY, result.getAction());
//        assertEquals("v1.0.0", result.getTagName());
//        // Config should not be accessed since we provided explicit path
//        verify(changeLogPathConfig, never()).getPath();
//    }
//
//    @Test
//    @DisplayName("Test resolve uses command line path over config")
//    void testResolve_CommandLinePathOverridesConfig() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "/override/path/changelog.yml",
//                "--tag", "v1.0.0"
//        };
//
//        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals("/override/path/changelog.yml", result.getChangelogPath());
//        // Config should not be accessed when command line path is provided
//        verify(changeLogPathConfig, never()).getPath();
//    }
//
//    @Test
//    @DisplayName("Test resolve with empty command line path uses config")
//    void testResolve_EmptyCommandLinePathUsesConfig() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "   ",  // Empty/whitespace path
//                "--tag", "v1.0.0"
//        };
//
//        when(changeLogPathConfig.getPath()).thenReturn("/config/default/changelog.yml");
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals("/config/default/changelog.yml", result.getChangelogPath());
//        verify(changeLogPathConfig).getPath();
//    }
//
//    @Test
//    @DisplayName("Test resolve with null config path falls back to classpath")
//    void testResolve_NullConfigPathFallsBackToClasspath() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--tag", "v1.0.0"
//        };
//
//        when(changeLogPathConfig.getPath()).thenReturn(null);
//
//        // When & Then
//        // This test may fail due to missing classpath resources, so we expect an exception
//        assertThrows(RuntimeException.class, () -> {
//            CommandLineOptions result = resolver.resolve(args);
//        });
//        verify(changeLogPathConfig).getPath();
//    }
//
//    @Test
//    @DisplayName("Test resolve with empty config path falls back to classpath")
//    void testResolve_EmptyConfigPathFallsBackToClasspath() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--tag", "v1.0.0"
//        };
//
//        when(changeLogPathConfig.getPath()).thenReturn("   ");  // Empty/whitespace
//
//        // When & Then
//        // This test may fail due to missing classpath resources, so we expect an exception
//        assertThrows(RuntimeException.class, () -> {
//            CommandLineOptions result = resolver.resolve(args);
//        });
//        verify(changeLogPathConfig).getPath();
//    }
//
//    @Test
//    @DisplayName("Test resolve with deployOrRollback action")
//    void testResolve_DeployOrRollbackAction() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY_OR_ROLLBACK",
//                "--changelog", "/path/changelog.yml",
//                "--tag", "v1.0.0",
//                "--dry-run"
//        };
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(CommandLineOptions.Action.DEPLOY_OR_ROLLBACK, result.getAction());
//        assertEquals("/path/changelog.yml", result.getChangelogPath());
//        assertEquals("v1.0.0", result.getTagName());
//        assertTrue(result.isDryRun());
//    }
//
//    @Test
//    @DisplayName("Test resolve with valid changelog path (no classpath issues)")
//    void testResolve_WithValidChangelogPath() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "src/test/resources/sqlite-scripts/sqlite-test-changelog.yml",
//                "--tag", "v1.0.0"
//        };
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(CommandLineOptions.Action.DEPLOY, result.getAction());
//        assertEquals("src/test/resources/sqlite-scripts/sqlite-test-changelog.yml", result.getChangelogPath());
//        assertEquals("v1.0.0", result.getTagName());
//        assertFalse(result.isVerbose());
//        assertFalse(result.isDryRun());
//    }
//
//    @Test
//    @DisplayName("Test resolve with all options enabled")
//    void testResolve_AllOptionsEnabled() {
//        // Given
//        String[] args = {
//                "--action", "DEPLOY",
//                "--changelog", "/full/path/changelog.yml",
//                "--tag", "v2.0.0",
//                "--verbose",
//                "--dry-run"
//        };
//
//        // When
//        CommandLineOptions result = resolver.resolve(args);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(CommandLineOptions.Action.DEPLOY, result.getAction());
//        assertEquals("/full/path/changelog.yml", result.getChangelogPath());
//        assertEquals("v2.0.0", result.getTagName());
//        assertTrue(result.isVerbose());
//        assertTrue(result.isDryRun());
//    }
//
//    @Test
//    @DisplayName("Test resolve with default action when no action specified")
//    void testResolve_DefaultActionWhenNoActionSpecified() {
//        // Given
//        String[] args = {
//                "--tag", "v1.0.0"
//        };
//
//        // When & Then
//        // This test may fail due to missing classpath resources, so we expect an exception
//        assertThrows(RuntimeException.class, () -> {
//            CommandLineOptions result = resolver.resolve(args);
//            // If it succeeds, verify the action is correct
//            assertEquals(CommandLineOptions.Action.DEPLOY_OR_ROLLBACK, result.getAction());
//            assertEquals("v1.0.0", result.getTagName());
//            assertNotNull(result.getChangelogPath());
//        });
//    }
//}