package org.jerish.dbdeploy.integration.failure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for rollback functionality after deployment failure using SQLite.
 * This test verifies that the deployment tool can correctly rollback after a failure by:
 * 1. Deploying working scripts up to v1.0.1
 * 2. Attempting to deploy v1.0.2 which contains a script that will fail
 * 3. Verifying the tool stops at the failed script
 * 4. Rolling back to v1.0.1 using the v1.0.1 changelog
 * 5. Verifying rollback was successful and only the successful v1.0.2 scripts were rolled back
 */
public class SQLiteRollbackAfterFailureTest extends SQLiteFailureTestBase {

    @Test
    @DisplayName("Test rollback after failure")
    void testRollbackAfterFailure() throws Exception {
        // First deploy up to v1.0.1
        String changelogPathV1_0_1 = "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-first3.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1_0_1, false),
                "Initial deployment up to v1.0.1 should complete without errors");

        // Attempt to deploy v1.0.2 which will fail
        String changelogPathV1_0_2 = "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog.yml";

        assertThrows(Exception.class, () -> deployManager.deploy(changelogPathV1_0_2, false),
                "Deployment should fail due to intentional SQL error");

        // Verify failure state
        verifyFailureState("feature-12350-create-user-orders-view");

        // Now rollback to v1.0.1 using the v1.0.1 changelog
        assertDoesNotThrow(() -> deployManager.rollback(changelogPathV1_0_1, false),
                "Rollback should complete without errors");

        // Verify rollback was successful
        verifyRollbackAfterFailure();
    }
}