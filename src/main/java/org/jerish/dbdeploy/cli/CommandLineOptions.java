package org.jerish.dbdeploy.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "db-deploy-tool",
        mixinStandardHelpOptions = true,
        description = "Database version management and deployment tool",
        version = "1.0.0")
public class CommandLineOptions {

    @Option(names = {"-a", "--action"},
            description = "Action to perform: ${COMPLETION-CANDIDATES}",
            required = true,
            defaultValue = "deploy")
    private Action action = Action.DEPLOY;

    @Option(names = {"-c", "--changelog"},
            description = "Path to the db-changelog.yml file (required for deploy, optional for status, not used for rollback)")
    private String changelogPath;

    @Option(names = {"-t", "--tag"},
            description = "For deploy: tag name to assign after deployment; For rollback: target tag name to rollback to")
    private String tagName;

    @Option(names = {"-d", "--database-config"},
            description = "Path to database configuration file",
            required = true)
    private String databaseConfigPath;

    @Option(names = {"-s", "--script-base-path"},
            description = "Base path for script files (default: current directory)",
            defaultValue = ".")
    private String scriptBasePath;

    @Option(names = {"-b", "--build-version"},
            description = "Build version for deployment tagging")
    private String buildVersion;

    @Option(names = {"-e", "--environment"},
            description = "Environment name (e.g., dev, test, prod)",
            defaultValue = "default")
    private String environment;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose logging")
    private boolean verbose = false;

    @Option(names = {"--dry-run"},
            description = "Show what would be executed without making changes")
    private boolean dryRun = false;

    public enum Action {
        DEPLOY, ROLLBACK, STATUS
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getChangelogPath() {
        return changelogPath;
    }

    public void setChangelogPath(String changelogPath) {
        this.changelogPath = changelogPath;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getDatabaseConfigPath() {
        return databaseConfigPath;
    }

    public void setDatabaseConfigPath(String databaseConfigPath) {
        this.databaseConfigPath = databaseConfigPath;
    }

    public String getScriptBasePath() {
        return scriptBasePath;
    }

    public void setScriptBasePath(String scriptBasePath) {
        this.scriptBasePath = scriptBasePath;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
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