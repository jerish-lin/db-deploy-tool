package org.jerish.dbdeploy.integration.failure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for deployment failure handling in the database deployment tool using SQLite.
 * This test verifies that the deployment tool handles failures correctly by:
 * 1. Deploying working scripts up to v1.0.1
 * 2. Attempting to deploy v1.0.2 which contains a script that will fail
 * 3. Verifying the tool stops at the failed script and doesn't execute subsequent scripts
 * 4. Verifying audit information correctly tracks the failure
 */
public class SQLiteDeploymentFailureTest extends SQLiteFailureTestBase {

    @Test
    @DisplayName("Test deployment failure handling")
    void testDeploymentFailure() throws Exception {
        // First deploy up to v1.0.1 (working scripts)
        String changelogPathV1_0_1 = "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog-first3.yml";
        String tagNameV1_0_1 = "1.0.1.20231110.1";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathV1_0_1, tagNameV1_0_1, false),
                "Initial deployment up to v1.0.1 should complete without errors");

        // Verify v1.0.1 deployment was successful
        verifySuccessfulDeploymentV1_0_1();

        // Now attempt to deploy v1.0.2 which contains a failing script
        String changelogPathV1_0_2 = "src/test/resources/sqlite-scripts-failure/sqlite-test-changelog.yml";
        String tagNameV1_0_2 = "1.0.2.20231110.1";

        // This should fail due to the intentional SQL error in create-user-orders-view
        assertThrows(Exception.class, () -> deployManager.deploy(changelogPathV1_0_2, tagNameV1_0_2, false),
                "Deployment should fail due to intentional SQL error");

        // Verify failure state
        verifyFailureState("feature-12350-create-user-orders-view");
    }
}