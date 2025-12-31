package org.jerish.dbdeploy.service;

import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseStatusPrinter {

    public void printStatus(DatabaseStatus status) {
        if (!status.isDatabaseConnected()) {
            log.error("Database is not connected or accessible");
            return;
        }

        log.info("=== Database Deployment Status ===");
        log.info("Database Name: {}", status.getDatabaseName());
        
        // Print deployment state
        DatabaseStatus.DeploymentStateInfo deploymentState = status.getDeploymentState();
        if (deploymentState != null) {
            log.info("Current Tag: {}", deploymentState.getCurrentTag() != null ? deploymentState.getCurrentTag() : "None");
            log.info("Total Scripts: {}", deploymentState.getTotalScripts());
            log.info("Executed Scripts: {}", deploymentState.getSuccessfulScripts());
            log.info("Failed Scripts: {}", deploymentState.getFailedScripts());
            log.info("Rolled Back Scripts: {}", deploymentState.getRolledBackScripts());
            log.info("Last Deployment Time: {}", deploymentState.getDeploymentTime() != null ? deploymentState.getDeploymentTime() : "Never");
        }
        
        // Print database health
        DatabaseStatus.DatabaseHealthInfo healthInfo = status.getHealthInfo();
        if (healthInfo != null) {
            log.info("Database Version: {}", healthInfo.getVersion() != null ? healthInfo.getVersion() : "Unknown");
        }

        // Print script status
        DatabaseStatus.ScriptStatusInfo scriptStatus = status.getScriptStatus();
        if (scriptStatus != null && !scriptStatus.getExecutedScriptNames().isEmpty()) {
            log.info("Executed Scripts:");
            scriptStatus.getExecutedScriptNames().forEach(script -> log.info("  - {}", script));
        }

        log.info("=== End Status ===");
    }
}
