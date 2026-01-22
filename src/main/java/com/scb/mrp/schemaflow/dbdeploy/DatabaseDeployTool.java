package com.scb.mrp.schemaflow.dbdeploy;

import lombok.extern.slf4j.Slf4j;
import com.scb.mrp.schemaflow.dbdeploy.cli.CommandLineOptions;
import com.scb.mrp.schemaflow.dbdeploy.cli.CommandLineOptionsResolver;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseDeployManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
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
            SpringApplication app = new SpringApplication(DatabaseDeployTool.class);
            app.setWebApplicationType(WebApplicationType.NONE);
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