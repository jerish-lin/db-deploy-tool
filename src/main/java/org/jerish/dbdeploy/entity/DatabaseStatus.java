package org.jerish.dbdeploy.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DatabaseStatus {
    private String databaseName;
    private String currentTag;
    private int totalScripts;
    private int executedScripts;
    private int failedScripts;
    private int rolledBackScripts;
    private LocalDateTime lastDeploymentTime;
    private List<String> executedScriptNames;
    private boolean isDatabaseConnected;
    private String databaseVersion;
}
