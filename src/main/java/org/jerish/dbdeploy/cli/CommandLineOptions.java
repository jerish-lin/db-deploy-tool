package org.jerish.dbdeploy.cli;

import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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

    @Option(names = {"-c", "--changelog"},
            description = "Path to the db-changelog.yml file (required for deploy, optional for status, not used for rollback)")
    private String changelogPath;

    @Option(names = {"-t", "--tag"},
            description = "For deploy: tag name to assign after deployment; For rollback: target tag name to rollback to")
    private String tagName;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose logging")
    private boolean verbose = false;

    @Option(names = {"--dry-run"},
            description = "Show what would be executed without making changes")
    private boolean dryRun = false;

    public enum Action {
        DEPLOY, ROLLBACK, STATUS, DEPLOY_OR_ROLLBACK;

        public boolean tagRequired() {
            return this == DEPLOY || this == DEPLOY_OR_ROLLBACK;
        }
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