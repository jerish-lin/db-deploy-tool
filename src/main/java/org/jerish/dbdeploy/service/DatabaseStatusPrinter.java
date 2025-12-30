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
        log.info("Current Tag: {}", status.getCurrentTag() != null ? status.getCurrentTag() : "None");
        log.info("Total Scripts: {}", status.getTotalScripts());
        log.info("Executed Scripts: {}", status.getExecutedScripts());
        log.info("Failed Scripts: {}", status.getFailedScripts());
        log.info("Rolled Back Scripts: {}", status.getRolledBackScripts());
        log.info("Last Deployment Time: {}", status.getLastDeploymentTime() != null ? status.getLastDeploymentTime() : "Never");
        log.info("Database Version: {}", status.getDatabaseVersion() != null ? status.getDatabaseVersion() : "Unknown");

        if (!status.getExecutedScriptNames().isEmpty()) {
            log.info("Executed Scripts:");
            status.getExecutedScriptNames().forEach(script -> log.info("  - {}", script));
        }

        log.info("=== End Status ===");
    }
}
