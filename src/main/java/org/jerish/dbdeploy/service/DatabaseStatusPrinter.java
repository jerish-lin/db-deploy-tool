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

        // Print script summary
        if (status.getScriptSummary() != null) {
            DatabaseStatus.ScriptSummary summary = status.getScriptSummary();
            log.info("Total Scripts: {}", summary.getTotalScripts());
            log.info("Executed Scripts: {}", summary.getExecutedScripts());
            log.info("Failed Scripts: {}", summary.getFailedScripts());
            log.info("Rolled Back Scripts: {}", summary.getRolledBackScripts());

            if (summary.getScripts() != null && !summary.getScripts().isEmpty()) {
                log.info("Script Status:");
                for (DatabaseStatus.ScriptStatus scriptStatus : summary.getScripts()) {
                    log.info("  - {}: {}", scriptStatus.getScriptName(), scriptStatus.getLatestStatus());
                }
            }
        }

        // Print recent audit history
        if (status.getRecentAuditHistory() != null && !status.getRecentAuditHistory().isEmpty()) {
            log.info("Recent Audit History (last 10):");
            for (DatabaseStatus.AuditHistoryEntry entry : status.getRecentAuditHistory()) {
                log.info("  - [{}] {} at {}",
                        entry.getExecutionStatus(),
                        entry.getScriptName(),
                        entry.getExecutionTime());
                if (entry.getErrorMessage() != null && !entry.getErrorMessage().isEmpty()) {
                    log.info("    Error: {}", entry.getErrorMessage());
                }
            }
        }

        // Print lock info
        if (status.getLockInfo() != null && status.getLockInfo().isActive()) {
            log.info("Deployment Lock: ACTIVE (held by: {})", status.getLockInfo().getLockOwner());
        }

        log.info("=== End Status ===");
    }
}