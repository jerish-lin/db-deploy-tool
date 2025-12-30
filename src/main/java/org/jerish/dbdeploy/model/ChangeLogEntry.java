package org.jerish.dbdeploy.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ChangeLogEntry {
    private Long id;
    private String scriptName;
    private String scriptChecksum;
    private ScriptExecutionStatus executionStatus;
    private LocalDateTime executionTime;
    private Long executionDurationMs;
    private String errorMessage;
    private String rollbackScriptContent;
    private String rollbackVerifyScriptContent;
    private String tagName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}