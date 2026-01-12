package org.jerish.dbdeploy.integration;

import org.jerish.dbdeploy.TestApplication;
import org.jerish.dbdeploy.service.DatabaseDeployManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SQLiteDeployTestBase {
    private static final String DB_FILE = "testdb.sqlite";

    @Autowired
    protected DatabaseDeployManager deployManager;

    @Autowired
    protected JdbcTemplate dbDeployJdbcTemplate;

    @Autowired
    protected ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        resetDatabaseFile();
    }

    @AfterEach
    void tearDown() {
//        resetDatabaseFile();
    }

    private void resetDatabaseFile() {
        try {
            // Force garbage collection to help release any lingering file handles
            System.gc();
            System.runFinalization();

            // Wait a moment for cleanup
            Thread.sleep(100);

            File dbFile = new File(DB_FILE);
            if (dbFile.exists()) {
                // Write empty string to override existing content
                try (java.io.FileWriter writer = new java.io.FileWriter(dbFile, false)) {
                    writer.write("");
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not reset database file: " + e.getMessage());
        }
    }
}
