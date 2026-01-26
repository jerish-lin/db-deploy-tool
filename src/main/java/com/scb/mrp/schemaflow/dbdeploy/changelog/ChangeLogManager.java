package com.scb.mrp.schemaflow.dbdeploy.changelog;

import com.scb.mrp.schemaflow.dbdeploy.entity.*;
import com.scb.mrp.schemaflow.dbdeploy.repository.AuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.script.FileReader;
import com.scb.mrp.schemaflow.dbdeploy.script.ScriptParameterHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager for handling changelog operations including loading script content,
 * comparing changelogs with database state, and determining pending/rollback scripts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeLogManager {

    private final FileReader fileReader;
    private final AuditRepository auditRepository;
    private final ScriptParameterHandler scriptParameterHandler;

    /**
     * Load script content for all scripts in the ChangeLogConfig.
     * Uses FileReader to load file content for apply, rollback, apply verification, and rollback verification.
     * If any files can't be found, the content is set to null.
     * Throws exception if any apply or rollback files are missing (verification files are optional).
     * Parameter placeholders are replaced with actual values from the parameters map.
     *
     * @param changeLogConfig The changelog configuration
     * @param parameters      Map of parameter names to values for placeholder replacement
     * @return List of ScriptFileContent objects with loaded content
     * @throws IOException if required script files (apply or rollback) are missing
     */
    public List<ScriptFileContent> loadScriptContent(ChangeLogConfig changeLogConfig, Map<String, String> parameters) throws IOException {
        if (changeLogConfig == null || changeLogConfig.getScripts() == null || changeLogConfig.getScripts().isEmpty()) {
            return List.of();
        }

        List<ScriptFileContent> scriptFileContents = new ArrayList<>();
        String scriptBasePath = deriveScriptBasePath(changeLogConfig.getBasePath());
        List<String> missingRequiredFiles = new ArrayList<>();

        for (ScriptConfig config : changeLogConfig.getScripts()) {
            ScriptFileContent content = new ScriptFileContent();
            content.setName(config.getName());

            // Load apply script content
            String applyPath = resolvePath(scriptBasePath, config.getApplyScriptPath());
            content.setApplyPath(applyPath);
            try {
                String applyContent = fileReader.readFile(applyPath);
                content.setApplyContent(scriptParameterHandler.replacePlaceholders(applyContent, parameters));
            } catch (IOException e) {
                log.warn("Apply script not found: {}", applyPath);
                missingRequiredFiles.add("Apply script: " + applyPath);
            }

            // Load rollback script content
            String rollbackPath = resolvePath(scriptBasePath, config.getRollbackScriptPath());
            content.setRollbackPath(rollbackPath);
            try {
                String rollbackContent = fileReader.readFile(rollbackPath);
                content.setRollbackContent(scriptParameterHandler.replacePlaceholders(rollbackContent, parameters));
            } catch (IOException e) {
                log.warn("Rollback script not found: {}", rollbackPath);
                missingRequiredFiles.add("Rollback script: " + rollbackPath);
            }

            // Load apply verification script content (optional)
            if (config.getApplyVerifyScriptPath() != null) {
                String applyVerifyPath = resolvePath(scriptBasePath, config.getApplyVerifyScriptPath());
                content.setApplyVerificationPath(applyVerifyPath);
                try {
                    String applyVerifyContent = fileReader.readFile(applyVerifyPath);
                    content.setApplyVerificationContent(scriptParameterHandler.replacePlaceholders(applyVerifyContent, parameters));
                } catch (IOException e) {
                    log.debug("Apply verification script not found (optional): {}", applyVerifyPath);
                    // Verification files are optional, so content remains null
                }
            }

            // Load rollback verification script content (optional)
            if (config.getRollbackVerifyScriptPath() != null) {
                String rollbackVerifyPath = resolvePath(scriptBasePath, config.getRollbackVerifyScriptPath());
                content.setRollbackVerificationPath(rollbackVerifyPath);
                try {
                    String rollbackVerifyContent = fileReader.readFile(rollbackVerifyPath);
                    content.setRollbackVerificationContent(scriptParameterHandler.replacePlaceholders(rollbackVerifyContent, parameters));
                } catch (IOException e) {
                    log.debug("Rollback verification script not found (optional): {}", rollbackVerifyPath);
                    // Verification files are optional, so content remains null
                }
            }

            scriptFileContents.add(content);
        }

        // Throw exception if any required files are missing
        if (!missingRequiredFiles.isEmpty()) {
            String errorMessage = "Required script files are missing:\n" +
                    String.join("\n", missingRequiredFiles);
            throw new IOException(errorMessage);
        }

        return scriptFileContents;
    }

    /**
     * Compare the changelog with database changelog to determine which scripts are not executed.
     * Parameter placeholders are replaced with actual values from the parameters map.
     *
     * @param changeLogConfig The changelog configuration
     * @param parameters      Map of parameter names to values for placeholder replacement
     * @return List of ScriptFileContent for scripts that need to be executed
     * @throws IOException           if script files cannot be loaded
     * @throws IllegalStateException if the latest script has failed status
     */
    public List<ScriptFileContent> determinePendingScripts(ChangeLogConfig changeLogConfig, Map<String, String> parameters) throws IOException {
        // Load all script contents with parameter replacement
        List<ScriptFileContent> allScripts = loadScriptContent(changeLogConfig, parameters);

        // Get script summary from database
        DatabaseStatus.ScriptSummary scriptSummary = auditRepository.getScriptSummary();

        // Get executed script names
        List<String> executedScriptNames = scriptSummary.getSuccessScripts()
                .stream().map(DatabaseStatus.ChangeLogScriptStatus::getScriptName).toList();

        // Filter out scripts that have already been executed
        return allScripts.stream()
                .filter(script -> !executedScriptNames.contains(script.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Enum representing the type of deployment action needed
     */
    public enum DeploymentAction {
        DEPLOY,
        ROLLBACK,
        NONE
    }

    /**
     * Get script names from change log configuration
     *
     * @param changeLogConfig The changelog configuration
     * @return List of script names
     */
    private List<String> getChangeLogScriptNames(ChangeLogConfig changeLogConfig) {
        return Optional.ofNullable(changeLogConfig)
                .map(config -> config.getScripts())
                .map(scripts -> scripts.stream()
                        .map(ScriptConfig::getName).toList())
                .orElse(List.of());
    }

    /**
     * Determine what deployment action is needed based on comparing the changelog with database state.
     * Returns DEPLOY if there are scripts in changelog that are not executed.
     * Returns ROLLBACK if there are scripts executed that are not in changelog.
     * Returns NONE if the database is already at the target state.
     *
     * @param changeLogConfig The changelog configuration
     * @return DeploymentAction indicating what action is needed
     */
    public DeploymentAction determineDeploymentAction(ChangeLogConfig changeLogConfig) {
        // Get script names from current changelog
        List<String> currentScriptNames = getChangeLogScriptNames(changeLogConfig);

        // Get executed scripts from database
        DatabaseStatus.ScriptSummary scriptSummary = auditRepository.getScriptSummary();
        List<DatabaseStatus.ChangeLogScriptStatus> executedScriptNames = scriptSummary.getSuccessAndFailedScripts();

        if (isNeedRollback(executedScriptNames, currentScriptNames)) {
            return DeploymentAction.ROLLBACK;
        }

        if (isLastExecutedScriptFailed(executedScriptNames)) {
            log.error("Last deployment is failed, Please resolve the failures before deploy new changes.");
            return DeploymentAction.NONE;
        }

        if(isNeedDeploy(currentScriptNames, executedScriptNames)){
            return DeploymentAction.DEPLOY;
        }

        log.info("No new changes to deploy.");
        return DeploymentAction.NONE;
    }

    private static boolean isNeedRollback(List<DatabaseStatus.ChangeLogScriptStatus> executedScriptNames, List<String> currentScriptNames) {
        return executedScriptNames.stream()
                .anyMatch(script -> !currentScriptNames.contains(script.getScriptName()));
    }

    private boolean isLastExecutedScriptFailed(List<DatabaseStatus.ChangeLogScriptStatus> executedScriptNames) {
        if (executedScriptNames.isEmpty()){
            return false;
        }

        DatabaseStatus.ChangeLogScriptStatus lastExecutedScript = executedScriptNames.get(executedScriptNames.size() - 1);
        return ScriptExecutionStatus.FAILED.equals(lastExecutedScript.getLatestStatus())
                || ScriptExecutionStatus.ROLLBACK_FAILED.equals(lastExecutedScript.getLatestStatus());
    }

    private static boolean isNeedDeploy(List<String> currentScriptNames, List<DatabaseStatus.ChangeLogScriptStatus> executedScriptNames) {
        return currentScriptNames.stream()
                .anyMatch(scriptName -> !executedScriptNames.contains(scriptName));
    }

    /**
     * Compare the changelog with database changelog to determine which scripts need to be rolled back.
     * Returns scripts that have SUCCESS or FAILED status in the database but are not in the current changelog.
     * Scripts with ROLLED_BACK status are ignored.
     * Results are returned in reversed order so scripts are rolled back in reverse execution order.
     *
     * @param changeLogConfig The changelog configuration
     * @return List of ScriptMetadata objects that need to be rolled back (in reversed execution order)
     */
    public List<ChangeLogScript> determineRollbackScripts(ChangeLogConfig changeLogConfig) {
        // Get script names from current changelog
        List<String> currentScriptNames = getChangeLogScriptNames(changeLogConfig);

        // Get script summary from database
        DatabaseStatus.ScriptSummary scriptSummary = auditRepository.getScriptSummary();

        // Find scripts that have SUCCESS or FAILED status but are not in current changelog (need rollback)
        // Scripts with ROLLED_BACK status are ignored
        List<ChangeLogScript> rollbackScripts = scriptSummary.getSuccessAndFailedScripts().stream()
                .filter(status -> !currentScriptNames.contains(status.getScriptName()))
                .map(DatabaseStatus.ChangeLogScriptStatus::toScriptMetadata)
                .collect(Collectors.toList());

        // Return in reversed order so scripts are rolled back in reverse execution order
        Collections.reverse(rollbackScripts);
        return rollbackScripts;
    }

    /**
     * Derive the script base path from the changelog base path.
     * Appends "scripts" directory to the base path.
     *
     * @param basePath The changelog base path
     * @return The script base path
     */
    private String deriveScriptBasePath(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            return "scripts";
        }

        // For classpath paths, keep the prefix and append "scripts"
        if (basePath.startsWith("classpath:")) {
            return basePath + "scripts";
        }

        // For file system paths, resolve "scripts" directory
        if (basePath.endsWith("/") || basePath.endsWith("\\")) {
            return basePath + "scripts";
        }

        return basePath + File.separator + "scripts";
    }

    /**
     * Resolve the full path for a script relative to the base path.
     *
     * @param basePath   The base path
     * @param scriptPath The relative script path
     * @return The resolved full path
     */
    private String resolvePath(String basePath, String scriptPath) {
        if (scriptPath == null || scriptPath.isEmpty()) {
            return null;
        }

        // If the path is already absolute, return as is
        if (scriptPath.startsWith("/") || scriptPath.startsWith("\\") ||
                (scriptPath.length() > 1 && scriptPath.charAt(1) == ':')) {
            return scriptPath;
        }

        // For classpath paths, use forward slash
        if (basePath.startsWith("classpath:")) {
            return basePath + "/" + scriptPath;
        }

        // For file system paths, use system-specific separator
        if (basePath.endsWith("/") || basePath.endsWith("\\")) {
            return basePath + scriptPath;
        }

        return basePath + File.separator + scriptPath;
    }
}
