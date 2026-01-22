package com.scb.mrp.schemaflow.dbdeploy.integration.failure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for verification failure handling in the database deployment tool using SQLite.
 * This test verifies that the deployment tool handles verification script failures correctly by:
 * 1. Deploying working scripts up to v1.0.1
 * 2. Attempting to deploy including a script with failing verification
 * 3. Verifying the tool stops at the failed verification script
 * 4. Verifying audit information correctly tracks the verification failure
 */
public class SQLiteVerificationFailureTest extends SQLiteFailureTestBase {

    @Test
    @DisplayName("Test verification failure handling")
    void testVerificationFailure() throws Exception {
        // First deploy working scripts up to feature-12348 (before the failing verification)
        String changelogPathInitial = "classpath:sqlite-scripts-failure/sqlite-test-changelog-first3.yml";

        assertDoesNotThrow(() -> deployManager.deploy(changelogPathInitial, false),
                "Initial deployment should complete without errors");

        // Verify initial deployment was successful
        verifySuccessfulDeploymentV1_0_1();

        // Now attempt to deploy including the script with failing verification (feature-12349)
        String changelogPathWithVerificationFailure = "classpath:sqlite-scripts-failure/sqlite-test-changelog-verify-failure.yml";

        // This should fail due to the intentional SQL error in create-user-orders-view
        assertThrows(Exception.class, () -> deployManager.deploy(changelogPathWithVerificationFailure, false),
                "Deployment should fail due to intentional SQL error");

        // Verify failure state
        verifyFailureState("verify-failure/feature-12350-create-user-orders-view");
    }
}