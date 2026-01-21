package org.jerish.dbdeploy.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing script metadata stored in changelog_script table.
 * This table stores immutable script information that doesn't change over time.
 */
@Data
@NoArgsConstructor
public class ScriptMetadata {
    private Long id;
    private String scriptName;
    private String scriptChecksum;
    private String applyScriptContent;
    private String rollbackScriptContent;
    private String applyVerifyScriptContent;
    private String rollbackVerifyScriptContent;
    private LocalDateTime createdAt;

    public ScriptMetadata(String scriptName, String scriptChecksum) {
        this.scriptName = scriptName;
        this.scriptChecksum = scriptChecksum;
    }
}