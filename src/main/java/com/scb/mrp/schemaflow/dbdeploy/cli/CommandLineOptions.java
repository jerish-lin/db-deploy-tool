package com.scb.mrp.schemaflow.dbdeploy.cli;

import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

@Command(name = "db-deploy-tool",
        mixinStandardHelpOptions = true,
        description = "Database version management and deployment tool",
        version = "1.0.0")
@Data
public class CommandLineOptions {

    @Option(names = {"-a", "--action"},
            description = "Action to perform: ${COMPLETION-CANDIDATES}",
            required = false,
            defaultValue = "DEPLOY_OR_ROLLBACK")
    private Action action = Action.DEPLOY_OR_ROLLBACK;

    @Option(names = {"--config"},
            description = "Path to custom Spring configuration file (YAML or properties)")
    private String configPath;

    @Option(names = {"-c", "--changelog"},
            required = false,
            description = "Path to the db-changelog.yml file (default to classpath:db/db-changelog.yml)")
    private String changelogPath;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose logging")
    private boolean verbose = false;

    @Option(names = {"--dry-run"},
            description = "Show what would be executed without making changes")
    private boolean dryRun = false;

    @Option(names = {"-p", "--param"},
            description = "Parameter for SQL placeholder replacement in format: param_name=value. Can be specified multiple times.",
            split = ",")
    private Map<String, String> parameters = new HashMap<>();

    public enum Action {
        DEPLOY, ROLLBACK, STATUS, DEPLOY_OR_ROLLBACK;
    }


    public static CommandLineOptions parseArgs(String[] args) {
        CommandLineOptions options = new CommandLineOptions();
        CommandLine cmd = new CommandLine(options);

        try {
            cmd.parseArgs(args);
        } catch (CommandLine.ParameterException e) {
            System.err.println(e.getMessage());
            cmd.usage(System.err);
            System.exit(1);
        }

        return options;
    }
}