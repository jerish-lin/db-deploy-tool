package org.example;

import org.example.cli.CommandLineOptions;
import org.example.config.ChangeLogConfig;
import org.example.config.ConfigLoader;
import org.example.config.DatabaseConfig;
import org.example.database.DatabaseConnectionManager;
import org.example.dao.AuditDao;
import org.example.service.DatabaseDeployService;
import org.example.service.InhouseDatabaseDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseDeployTool {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseDeployTool.class);
    
    public static void main(String[] args) {
        try {
            CommandLineOptions options = CommandLineOptions.parseArgs(args);
            
            if (options.isVerbose()) {
                System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
            }
            
            logger.info("Starting Database Deploy Tool with action: {}", options.getAction());
            
            DatabaseDeployTool tool = new DatabaseDeployTool();
            tool.run(options);
            
        } catch (Exception e) {
            logger.error("Database deploy tool failed", e);
            System.exit(1);
        }
    }
    
    public void run(CommandLineOptions options) throws Exception {
        DatabaseConfig dbConfig = ConfigLoader.loadDatabaseConfig(options.getDatabaseConfigPath());
        ChangeLogConfig changeLogConfig = ConfigLoader.loadChangeLogConfig(options.getChangelogPath());
        
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
                options.getScriptBasePath()
            );
            
            switch (options.getAction()) {
                case DEPLOY -> {
                    if (options.getTagName() == null) {
                        throw new IllegalArgumentException("Tag name is required for deploy action");
                    }
                    deployService.deploy(changeLogConfig, options.getTagName(), options.getBuildVersion(), 
                        options.getEnvironment(), options.isDryRun());
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