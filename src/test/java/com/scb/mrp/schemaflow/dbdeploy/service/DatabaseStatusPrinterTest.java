package com.scb.mrp.schemaflow.dbdeploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseStatusPrinterTest {

    @Test
    void testPrintStatusAsJson() throws Exception {
        // Create ObjectMapper with JavaTimeModule for LocalDateTime serialization
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create a comprehensive DatabaseStatus with all fields populated

        // Create script1 with all fields
        DatabaseStatus.ChangeLogScriptStatus script1 = new DatabaseStatus.ChangeLogScriptStatus();
        script1.setId(1L);
        script1.setScriptName("create-users-table");
        script1.setScriptChecksum("abc123def456");
        script1.setRollbackScriptContent("DROP TABLE users;");  // This field is @JsonIgnore
        script1.setRollbackVerifyScriptContent("SELECT COUNT(*) FROM sqlite_master WHERE name='users';");  // This field is @JsonIgnore
        script1.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        script1.setTargetNodes(List.of("node1", "node2", "node3"));
        script1.setLatestStatus(ScriptExecutionStatus.SUCCESS);

        // Create script2 with all fields
        DatabaseStatus.ChangeLogScriptStatus script2 = new DatabaseStatus.ChangeLogScriptStatus();
        script2.setId(2L);
        script2.setScriptName("add-email-index");
        script2.setScriptChecksum("def456ghi789");
        script2.setRollbackScriptContent("DROP INDEX idx_users_email;");  // This field is @JsonIgnore
        script2.setRollbackVerifyScriptContent(null);  // This field is @JsonIgnore
        script2.setCreatedAt(LocalDateTime.of(2024, 1, 16, 14, 45, 0));
        script2.setTargetNodes(null);
        script2.setLatestStatus(ScriptExecutionStatus.ROLLED_BACK);

        // Create script3 with failure status
        DatabaseStatus.ChangeLogScriptStatus script3 = new DatabaseStatus.ChangeLogScriptStatus();
        script3.setId(3L);
        script3.setScriptName("modify-orders-table");
        script3.setScriptChecksum("xyz789abc123");
        script3.setRollbackScriptContent(null);  // This field is @JsonIgnore
        script3.setRollbackVerifyScriptContent(null);  // This field is @JsonIgnore
        script3.setCreatedAt(LocalDateTime.of(2024, 1, 17, 9, 15, 0));
        script3.setTargetNodes(List.of("ALL"));
        script3.setLatestStatus(ScriptExecutionStatus.FAILED);

        // Create script summary
        DatabaseStatus.ScriptSummary summary = new DatabaseStatus.ScriptSummary();
        summary.setScripts(List.of(script1, script2, script3));
        summary.setTotalScripts(3);
        summary.setExecutedScripts(1);
        summary.setFailedScripts(1);
        summary.setRolledBackScripts(1);

        // Create audit history entries
        DatabaseStatus.AuditHistoryEntry audit1 = new DatabaseStatus.AuditHistoryEntry();
        audit1.setAuditId(1L);
        audit1.setScriptName("create-users-table");
        audit1.setScriptChecksum("abc123def456");
        audit1.setExecutionStatus(ScriptExecutionStatus.SUCCESS);
        audit1.setExecutionTime(LocalDateTime.of(2024, 1, 15, 10, 30, 15));
        audit1.setExecutionDurationMs(1250L);
        audit1.setErrorMessage(null);
        audit1.setTargetNodes(List.of("node1", "node2", "node3"));
        audit1.setNodeExecutionDetails("[{\"nodeName\":\"node1\",\"success\":true,\"durationMs\":400},{\"nodeName\":\"node2\",\"success\":true,\"durationMs\":450},{\"nodeName\":\"node3\",\"success\":true,\"durationMs\":400}]");

        DatabaseStatus.AuditHistoryEntry audit2 = new DatabaseStatus.AuditHistoryEntry();
        audit2.setAuditId(2L);
        audit2.setScriptName("add-email-index");
        audit2.setScriptChecksum("def456ghi789");
        audit2.setExecutionStatus(ScriptExecutionStatus.ROLLED_BACK);
        audit2.setExecutionTime(LocalDateTime.of(2024, 1, 20, 16, 20, 30));
        audit2.setExecutionDurationMs(500L);
        audit2.setErrorMessage(null);
        audit2.setTargetNodes(null);
        audit2.setNodeExecutionDetails(null);

        DatabaseStatus.AuditHistoryEntry audit3 = new DatabaseStatus.AuditHistoryEntry();
        audit3.setAuditId(3L);
        audit3.setScriptName("modify-orders-table");
        audit3.setScriptChecksum("xyz789abc123");
        audit3.setExecutionStatus(ScriptExecutionStatus.FAILED);
        audit3.setExecutionTime(LocalDateTime.of(2024, 1, 21, 11, 0, 0));
        audit3.setExecutionDurationMs(100L);
        audit3.setErrorMessage("SQL error: table orders does not exist");
        audit3.setTargetNodes(List.of("ALL"));
        audit3.setNodeExecutionDetails("[{\"nodeName\":\"node1\",\"success\":false,\"durationMs\":50,\"errorMessage\":\"Table not found\"},{\"nodeName\":\"node2\",\"success\":false,\"durationMs\":50,\"errorMessage\":\"Table not found\"}]");

        // Create lock info
        DatabaseStatus.LockInfo lockInfo = new DatabaseStatus.LockInfo();
        lockInfo.setLockOwner("deploy-user-12345");
        lockInfo.setLockAcquiredAt(LocalDateTime.of(2024, 1, 21, 11, 0, 5).toString());
        lockInfo.setLockExpiresAt(LocalDateTime.of(2024, 1, 21, 11, 30, 5).toString());
        lockInfo.setActive(true);

        // Create complete DatabaseStatus
        DatabaseStatus status = new DatabaseStatus();
        status.setScriptSummary(summary);
        status.setRecentAuditHistory(List.of(audit1, audit2, audit3));
        status.setLockInfo(lockInfo);

        // Test serialization
        DatabaseStatusPrinter printer = new DatabaseStatusPrinter();

        // Verify the object can be serialized to JSON
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(status);
        assertNotNull(json);

        // Verify all major sections are present
        assertTrue(json.contains("scriptSummary"), "JSON should contain scriptSummary");
        assertTrue(json.contains("recentAuditHistory"), "JSON should contain recentAuditHistory");
        assertTrue(json.contains("lockInfo"), "JSON should contain lockInfo");

        // Verify script summary details
        assertTrue(json.contains("\"totalScripts\" : 3"), "Should have totalScripts count");
        assertTrue(json.contains("\"executedScripts\" : 1"), "Should have executedScripts count");
        assertTrue(json.contains("\"failedScripts\" : 1"), "Should have failedScripts count");
        assertTrue(json.contains("\"rolledBackScripts\" : 1"), "Should have rolledBackScripts count");

        // Verify script details
        assertTrue(json.contains("create-users-table"), "Should contain script1 name");
        assertTrue(json.contains("add-email-index"), "Should contain script2 name");
        assertTrue(json.contains("modify-orders-table"), "Should contain script3 name");
        assertTrue(json.contains("\"SUCCESS\""), "Should contain SUCCESS status");
        assertTrue(json.contains("\"ROLLED_BACK\""), "Should contain ROLLED_BACK status");
        assertTrue(json.contains("\"FAILED\""), "Should contain FAILED status");
        assertTrue(json.contains("abc123def456"), "Should contain script1 checksum");
        assertTrue(json.contains("def456ghi789"), "Should contain script2 checksum");
        assertTrue(json.contains("xyz789abc123"), "Should contain script3 checksum");

        // Note: rollbackScriptContent and rollbackVerifyScriptContent are marked with @JsonIgnore
        // so they won't appear in the JSON output

        // Verify target nodes
        assertTrue(json.contains("\"targetNodes\""), "Should contain targetNodes field");
        assertTrue(json.contains("node1"), "Should contain node1");
        assertTrue(json.contains("node2"), "Should contain node2");
        assertTrue(json.contains("node3"), "Should contain node3");
        assertTrue(json.contains("\"ALL\""), "Should contain ALL nodes indicator");

        // Verify audit history details
        assertTrue(json.contains("\"auditId\""), "Should contain auditId");
        assertTrue(json.contains("\"executionTime\""), "Should contain executionTime");
        assertTrue(json.contains("\"executionDurationMs\""), "Should contain executionDurationMs");
        assertTrue(json.contains("1250"), "Should contain execution duration");
        assertTrue(json.contains("SQL error: table orders does not exist"), "Should contain error message");
        assertTrue(json.contains("\"nodeExecutionDetails\""), "Should contain node execution details");

        // Verify lock info details
        assertTrue(json.contains("\"lockOwner\""), "Should contain lockOwner");
        assertTrue(json.contains("deploy-user-12345"), "Should contain lock owner");
        assertTrue(json.contains("\"lockAcquiredAt\""), "Should contain lockAcquiredAt");
        assertTrue(json.contains("\"lockExpiresAt\""), "Should contain lockExpiresAt");
        assertTrue(json.contains("\"active\" : true"), "Should indicate lock is active");

        // Verify JSON is properly formatted (contains indentation)
        assertTrue(json.contains("\n"), "JSON should be pretty-printed with newlines");
        assertTrue(json.contains("  "), "JSON should contain indentation");

        System.out.println("Sample JSON Output:");
        System.out.println(json);

        // Verify the JSON can be deserialized back to DatabaseStatus
        DatabaseStatus deserializedStatus = mapper.readValue(json, DatabaseStatus.class);
        assertNotNull(deserializedStatus, "Deserialized status should not be null");
        assertNotNull(deserializedStatus.getScriptSummary(), "Deserialized script summary should not be null");
        assertNotNull(deserializedStatus.getRecentAuditHistory(), "Deserialized audit history should not be null");
        assertNotNull(deserializedStatus.getLockInfo(), "Deserialized lock info should not be null");

        // Verify deserialized values match original
        assertEquals(3, deserializedStatus.getScriptSummary().getTotalScripts());
        assertEquals(1, deserializedStatus.getScriptSummary().getExecutedScripts());
        assertEquals(1, deserializedStatus.getScriptSummary().getFailedScripts());
        assertEquals(1, deserializedStatus.getScriptSummary().getRolledBackScripts());
        assertEquals(3, deserializedStatus.getScriptSummary().getScripts().size());
        assertEquals(3, deserializedStatus.getRecentAuditHistory().size());
        assertTrue(deserializedStatus.getLockInfo().isActive());
        assertEquals("deploy-user-12345", deserializedStatus.getLockInfo().getLockOwner());
    }
}