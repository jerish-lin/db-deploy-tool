//package org.jerish.dbdeploy.service;
//
//import org.jerish.dbdeploy.entity.DatabaseStatus;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * Unit tests for DatabaseStatusPrinter.
// */
//@ExtendWith(MockitoExtension.class)
//@DisplayName("DatabaseStatusPrinter Tests")
//public class DatabaseStatusPrinterTest {
//
//    private DatabaseStatusPrinter printer;
//    private DatabaseStatus status;
//
//    @BeforeEach
//    void setUp() {
//        printer = new DatabaseStatusPrinter();
//        status = new DatabaseStatus();
//    }
//
//    @Test
//    @DisplayName("Test printStatus with disconnected database")
//    void testPrintStatusDisconnectedDatabase() {
//        status.setDatabaseConnected(false);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with connected database")
//    void testPrintStatusConnectedDatabase() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
//        deploymentState.setCurrentTag("v1.0.0");
//        deploymentState.setTotalScripts(10);
//        deploymentState.setSuccessfulScripts(8);
//        deploymentState.setFailedScripts(1);
//        deploymentState.setRolledBackScripts(1);
//        deploymentState.setDeploymentTime("2024-01-01 12:00:00");
//        status.setDeploymentState(deploymentState);
//
//        DatabaseStatus.DatabaseHealthInfo healthInfo = new DatabaseStatus.DatabaseHealthInfo();
//        healthInfo.setVersion("3.45.1");
//        healthInfo.setHealthy(true);
//        status.setHealthInfo(healthInfo);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with null deployment state")
//    void testPrintStatusNullDeploymentState() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//        status.setDeploymentState(null);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with null health info")
//    void testPrintStatusNullHealthInfo() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//        status.setHealthInfo(null);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with null script status")
//    void testPrintStatusNullScriptStatus() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//        status.setScriptStatus(null);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with empty executed scripts list")
//    void testPrintStatusEmptyExecutedScripts() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
//        scriptStatus.setExecutedScriptNames(new ArrayList<>());
//        status.setScriptStatus(scriptStatus);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with executed scripts")
//    void testPrintStatusWithExecutedScripts() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
//        List<String> executedScripts = List.of("script1", "script2", "script3");
//        scriptStatus.setExecutedScriptNames(executedScripts);
//        status.setScriptStatus(scriptStatus);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with null current tag")
//    void testPrintStatusNullCurrentTag() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
//        deploymentState.setCurrentTag(null);
//        deploymentState.setTotalScripts(5);
//        deploymentState.setSuccessfulScripts(5);
//        deploymentState.setFailedScripts(0);
//        deploymentState.setRolledBackScripts(0);
//        status.setDeploymentState(deploymentState);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with null database version")
//    void testPrintStatusNullDatabaseVersion() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DatabaseHealthInfo healthInfo = new DatabaseStatus.DatabaseHealthInfo();
//        healthInfo.setVersion(null);
//        healthInfo.setHealthy(true);
//        status.setHealthInfo(healthInfo);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with null deployment time")
//    void testPrintStatusNullDeploymentTime() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
//        deploymentState.setCurrentTag("v1.0.0");
//        deploymentState.setTotalScripts(10);
//        deploymentState.setSuccessfulScripts(10);
//        deploymentState.setFailedScripts(0);
//        deploymentState.setRolledBackScripts(0);
//        deploymentState.setDeploymentTime(null);
//        status.setDeploymentState(deploymentState);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus handles all null fields gracefully")
//    void testPrintStatusHandlesAllNullFields() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
//        deploymentState.setCurrentTag(null);
//        deploymentState.setDeploymentTime(null);
//        status.setDeploymentState(deploymentState);
//
//        DatabaseStatus.DatabaseHealthInfo healthInfo = new DatabaseStatus.DatabaseHealthInfo();
//        healthInfo.setVersion(null);
//        status.setHealthInfo(healthInfo);
//
//        DatabaseStatus.ScriptStatusInfo scriptStatus = new DatabaseStatus.ScriptStatusInfo();
//        scriptStatus.setExecutedScriptNames(null);
//        status.setScriptStatus(scriptStatus);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with zero scripts executed")
//    void testPrintStatusZeroScriptsExecuted() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
//        deploymentState.setCurrentTag("v1.0.0");
//        deploymentState.setTotalScripts(0);
//        deploymentState.setSuccessfulScripts(0);
//        deploymentState.setFailedScripts(0);
//        deploymentState.setRolledBackScripts(0);
//        status.setDeploymentState(deploymentState);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//
//    @Test
//    @DisplayName("Test printStatus with failed scripts")
//    void testPrintStatusWithFailedScripts() {
//        status.setDatabaseConnected(true);
//        status.setDatabaseName("testdb");
//
//        DatabaseStatus.DeploymentStateInfo deploymentState = new DatabaseStatus.DeploymentStateInfo();
//        deploymentState.setCurrentTag("v1.0.0");
//        deploymentState.setTotalScripts(10);
//        deploymentState.setSuccessfulScripts(7);
//        deploymentState.setFailedScripts(2);
//        deploymentState.setRolledBackScripts(1);
//        status.setDeploymentState(deploymentState);
//
//        assertDoesNotThrow(() -> {
//            printer.printStatus(status);
//        });
//    }
//}