package com.scb.mrp.schemaflow.dbdeploy.integration.multinode;

import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptExecutionStatus;
import lombok.Data;

/**
 * Simple DTO for test queries that combine script and audit information.
 * This is used only in tests for convenience.
 */
@Data
public class ScriptExecutionEntry {
    private String scriptName;
    private ScriptExecutionStatus executionStatus;
    private String nodeExecutionDetails;
}