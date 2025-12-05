package org.jerish.dbdeploy.model;

import java.time.LocalDateTime;

public class ChangeLogEntry {
    private Long id;
    private String scriptId;
    private String scriptName;
    private String scriptPath;
    private String scriptChecksum;
    private ScriptExecutionStatus executionStatus;
    private LocalDateTime executionTime;
    private Long executionDurationMs;
    private String errorMessage;
    private String rollbackScriptPath;
    private String rollbackScriptContent;
    private String tagName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ChangeLogEntry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScriptId() {
        return scriptId;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getScriptChecksum() {
        return scriptChecksum;
    }

    public void setScriptChecksum(String scriptChecksum) {
        this.scriptChecksum = scriptChecksum;
    }

    public ScriptExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ScriptExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(LocalDateTime executionTime) {
        this.executionTime = executionTime;
    }

    public Long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(Long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRollbackScriptPath() {
        return rollbackScriptPath;
    }

    public void setRollbackScriptPath(String rollbackScriptPath) {
        this.rollbackScriptPath = rollbackScriptPath;
    }

    public String getRollbackScriptContent() {
        return rollbackScriptContent;
    }

    public void setRollbackScriptContent(String rollbackScriptContent) {
        this.rollbackScriptContent = rollbackScriptContent;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}