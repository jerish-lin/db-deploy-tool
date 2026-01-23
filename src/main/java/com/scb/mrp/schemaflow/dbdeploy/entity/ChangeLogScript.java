package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing script metadata stored in changelog_script table.
 * This table stores immutable script information that doesn't change over time.
 */
@Data
@NoArgsConstructor
public class ChangeLogScript {
    private Long id;
    private String scriptName;
    private String scriptChecksum;
    private String rollbackScriptContent;
    private String rollbackVerifyScriptContent;
    private LocalDateTime createdAt;

    /**
     * List of target node names for multi-node execution.
     * Null for single-node execution.
     * Contains "ALL" if script was executed on all configured nodes.
     */
    private List<String> targetNodes;

    public ChangeLogScript(String scriptName, String scriptChecksum) {
        this.scriptName = scriptName;
        this.scriptChecksum = scriptChecksum;
    }
}