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
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "test.db.file=testdb.sqlite")
public class SQLiteDeployTestBase {
    private static final AtomicLong counter = new AtomicLong(0);
    private String dbFile;

    @Autowired
    protected DatabaseDeployManager deployManager;

    @Autowired
    protected JdbcTemplate dbDeployJdbcTemplate;

    @Autowired
    protected ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        // Clean up database file before each test
        try {
            System.gc();
            System.runFinalization();
            Thread.sleep(100);

            File dbFileObj = new File("testdb.sqlite");
            if (dbFileObj.exists()) {
                boolean deleted = dbFileObj.delete();
                if (!deleted) {
                    System.err.println("Warning: Could not delete database file before test: testdb.sqlite");
                    // Try to write empty content to clear the file
                    try (java.io.FileWriter writer = new java.io.FileWriter(dbFileObj, false)) {
                        writer.write("");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not clean up database file: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up database file after each test
        try {
            // Force garbage collection to close connections
            System.gc();
            System.runFinalization();
            Thread.sleep(100);

            File dbFileObj = new File("testdb.sqlite");
            if (dbFileObj.exists()) {
                boolean deleted = dbFileObj.delete();
                if (!deleted) {
                    System.err.println("Warning: Could not delete database file: testdb.sqlite");
                    // Try to delete on exit
                    dbFileObj.deleteOnExit();
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not delete database file: " + e.getMessage());
        }
    }
}
