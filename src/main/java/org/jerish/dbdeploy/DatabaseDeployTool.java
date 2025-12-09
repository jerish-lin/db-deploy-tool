package org.jerish.dbdeploy;

import org.jerish.dbdeploy.cli.CommandLineOptions;
import org.jerish.dbdeploy.config.ChangeLogConfig;
import org.jerish.dbdeploy.config.ConfigLoader;
import org.jerish.dbdeploy.config.DatabaseConfig;
import org.jerish.dbdeploy.dao.AuditDao;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.service.DatabaseDeployService;
import org.jerish.dbdeploy.service.InhouseDatabaseDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseDeployTool {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseDeployTool.class);

    public static void main(String[] args) throws Exception {
//        try {
        CommandLineOptions options = CommandLineOptions.parseArgs(args);

        if (options.isVerbose()) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }

        logger.info("Starting Database Deploy Tool with action: {}", options.getAction());

        DatabaseDeployTool tool = new DatabaseDeployTool();
        tool.run(options);

//        } catch (Exception e) {
//            logger.error("Database deploy tool failed", e);
////            System.exit(1);
//        }
    }

    public void run(CommandLineOptions options) throws Exception {
        DatabaseConfig dbConfig = ConfigLoader.loadDatabaseConfig(options.getDatabaseConfigPath());
        ChangeLogConfig changeLogConfig = null;

        // Only load changelog config for deploy action
        if (options.getAction() == CommandLineOptions.Action.DEPLOY) {
            changeLogConfig = ConfigLoader.loadChangeLogConfig(options.getChangelogPath());
        }

        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(dbConfig);
        try {
            if (!connectionManager.isValid()) {
                throw new RuntimeException("Failed to establish database connection");
            }

            AuditDao auditDao = new AuditDao(connectionManager);
            auditDao.initializeSchema();

            DatabaseDeployService deployService = new InhouseDatabaseDeployService(
                    connectionManager,
                    auditDao,
                    options.getChangelogPath()
            );

            switch (options.getAction()) {
                case DEPLOY -> {
                    if (options.getTagName() == null) {
                        throw new IllegalArgumentException("Tag name is required for deploy action");
                    }
                    deployService.deploy(changeLogConfig, options.getTagName(), options.isDryRun());
                }
                case ROLLBACK -> {
                    if (options.getTagName() == null) {
                        throw new IllegalArgumentException("Target tag name is required for rollback action");
                    }
                    deployService.rollback(options.getTagName(), options.isDryRun());
                }
                case STATUS -> {
                    deployService.showStatus();
                }
            }
        } finally {
            connectionManager.close();
        }

        logger.info("Database deploy tool completed successfully");
    }
}