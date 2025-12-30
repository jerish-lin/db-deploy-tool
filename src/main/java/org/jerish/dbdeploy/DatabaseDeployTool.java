package org.jerish.dbdeploy;

import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.cli.CommandLineOptions;
import org.jerish.dbdeploy.cli.CommandLineOptionsResolver;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.service.DatabaseDeployManager;
import org.jerish.dbdeploy.service.DatabaseStatusPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class DatabaseDeployTool implements CommandLineRunner {
    @Autowired
    private DatabaseDeployManager deployManager;
    @Autowired
    private CommandLineOptionsResolver optionsResolver;
    @Autowired
    private DatabaseStatusPrinter statusPrinter;

    @Value("${db-deploy.test-mode:false}")
    private boolean testMode;

    public static void main(String[] args) {
        try {
            log.info("Starting Database Deploy Tool");
            System.exit(SpringApplication.exit(SpringApplication.run(DatabaseDeployTool.class, args)));
        } catch (Exception e) {
            log.error("Database deploy tool failed", e);
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Skip execution in test mode
        if (testMode) {
            log.info("DatabaseDeployTool skipped - running in test mode");
            return;
        }

        CommandLineOptions options = optionsResolver.resolve(args);
        if (options.isVerbose()) {
            System.setProperty("org.slf4j.simplelog.defaultLogLevel", "debug");
        }
        run(options);
    }

    public void run(CommandLineOptions options) throws Exception {
        try {
            executeAction(options);
            log.info("Database deploy tool completed successfully");
        } catch (Exception e) {
            log.error("Error executing action: {}", options.getAction(), e);
            throw e;
        }
    }

    private void executeAction(CommandLineOptions options) throws Exception {
        switch (options.getAction()) {
            case DEPLOY_OR_ROLLBACK -> {
                deployManager.deployOrRollback(options.getChangelogPath(), options.getTagName(), options.isDryRun());
            }
            case DEPLOY -> {
                deployManager.deploy(options.getChangelogPath(), options.getTagName(), options.isDryRun());
            }
            case ROLLBACK -> {
                deployManager.rollback(options.getTagName(), options.isDryRun());
            }
            case STATUS -> {
                DatabaseStatus status = deployManager.status();
                statusPrinter.printStatus(status);
            }
        }
    }
}