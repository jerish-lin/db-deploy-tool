package org.jerish.dbdeploy.maintest;

import org.jerish.dbdeploy.TestApplication;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.service.DatabaseDeployManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.sql.SQLException;

@SpringBootTest(classes = TestApplication.class)
@TestPropertySource(locations = "classpath:application-test.yml")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SQLiteDeployTestBase {
    private static final String DB_FILE = "testdb.sqlite";

    @Autowired
    protected DatabaseDeployManager deployManager;

    @Autowired
    protected DatabaseConnectionManager connectionManager;

    @BeforeAll
    static void setUpClass() {
        cleanupDatabase();
    }

    @BeforeEach
    void setUp() throws SQLException {
        // Clean up database file before each test
        cleanupDatabase();
    }

    @AfterEach
    void tearDown() throws SQLException {
        try {
            // Close Spring's connection manager to release all connections
            if (connectionManager != null) {
                connectionManager.close();
            }
        } catch (Exception e) {
            System.err.println("Warning: Error closing connection manager: " + e.getMessage());
        }
        // Clean up database file after each test
        cleanupDatabase();
    }

    private static void cleanupDatabase() {
        // Force garbage collection to help release any lingering file handles
        System.gc();
        System.runFinalization();

        // Wait a moment for cleanup
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        File dbFile = new File(DB_FILE);
        if (dbFile.exists()) {
            // Try multiple times to delete the file
            boolean deleted = false;
            for (int i = 0; i < 5; i++) {
                if (dbFile.delete()) {
                    deleted = true;
                    break;
                }
                // Wait and retry
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                System.gc();
            }

            if (!deleted) {
                System.err.println("Warning: Could not delete existing database file after multiple attempts");
                // Try to delete on exit as last resort
                dbFile.deleteOnExit();
            }
        }
    }
}
