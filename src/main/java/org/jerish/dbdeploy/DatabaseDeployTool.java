package org.jerish.dbdeploy;

import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.cli.CommandLineOptions;
import org.jerish.dbdeploy.cli.CommandLineOptionsResolver;
import org.jerish.dbdeploy.entity.DatabaseStatus;
import org.jerish.dbdeploy.service.DatabaseDeployManager;
import org.jerish.dbdeploy.service.DatabaseStatusPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
@Slf4j
public class DatabaseDeployTool implements CommandLineRunner {
    @Autowired
    private DatabaseDeployManager deployManager;
    @Autowired
    private CommandLineOptionsResolver optionsResolver;
    @Autowired
    private DatabaseStatusPrinter statusPrinter;

    public static void main(String[] args) {
        try {
            log.info("Starting Database Deploy Tool");

            // Parse command line arguments first to get config path
            CommandLineOptions options = CommandLineOptions.parseArgs(args);

            // Create SpringApplicationBuilder with custom config location if specified
            SpringApplicationBuilder builder = new SpringApplicationBuilder(DatabaseDeployTool.class);

            if (options.getConfigPath() != null) {
                log.info("Using custom config file: {}", options.getConfigPath());
                // Set the config location - this will override application.yml
                builder.properties("spring.config.location=" + options.getConfigPath());
            } else {
                log.info("Using default config file from classpath");
            }

            SpringApplication app = builder.build();
            System.exit(SpringApplication.exit(app.run(args)));
        } catch (Exception e) {
            log.error("Database deploy tool failed", e);
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) throws Exception {
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
                deployManager.deployOrRollback(options.getChangelogPath(), options.isDryRun());
            }
            case DEPLOY -> {
                deployManager.deploy(options.getChangelogPath(), options.isDryRun());
            }
            case ROLLBACK -> {
                deployManager.rollback(options.getChangelogPath(), options.isDryRun());
            }
            case STATUS -> {
                DatabaseStatus status = deployManager.status();
                statusPrinter.printStatus(status);
            }
        }
    }
}