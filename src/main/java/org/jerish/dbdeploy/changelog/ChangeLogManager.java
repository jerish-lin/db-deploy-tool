package org.jerish.dbdeploy.changelog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.jerish.dbdeploy.entity.ScriptFileContent;
import org.jerish.dbdeploy.model.ChangeLogEntry;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.script.FileReader;
import org.jerish.dbdeploy.script.ScriptParameterHandler;
import org.springframework.stereotype.Service;

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
     * @throws IOException if script files cannot be loaded
     */
    public List<ScriptFileContent> determinePendingScripts(ChangeLogConfig changeLogConfig, Map<String, String> parameters) throws IOException {
        // Load all script contents with parameter replacement
        List<ScriptFileContent> allScripts = loadScriptContent(changeLogConfig, parameters);

        // Get executed scripts from database
        Set<String> executedScriptNames = auditRepository.getAllExecutedScripts().stream()
                .map(entry -> entry.getScriptName())
                .collect(Collectors.toSet());

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
        final List<String> currentScriptNames = Optional.ofNullable(changeLogConfig)
                .map(config -> config.getScripts())
                .map(scripts -> scripts.stream()
                        .map(ScriptConfig::getName).toList())
                .orElse(List.of());

        // Get executed scripts from database
        List<ChangeLogEntry> executedChangeLog = auditRepository.getAllExecutedScripts();
        Set<String> executedScriptNames = executedChangeLog.stream()
                .map(ChangeLogEntry::getScriptName)
                .collect(Collectors.toSet());

        // Check if there are scripts in changelog that are not executed (need deploy)
        boolean needDeploy = currentScriptNames.stream()
                .anyMatch(scriptName -> !executedScriptNames.contains(scriptName));

        // Check if there are scripts executed that are not in changelog (need rollback)
        boolean needRollback = executedScriptNames.stream()
                .anyMatch(scriptName -> !currentScriptNames.contains(scriptName));

        if (needDeploy && needRollback) {
            // Both deploy and rollback needed - this is an ambiguous state
            // Return NONE to indicate manual intervention is required
            log.warn("Both deployment and rollback are needed. Database state is ambiguous.");
            return DeploymentAction.NONE;
        } else if (needDeploy) {
            return DeploymentAction.DEPLOY;
        } else if (needRollback) {
            return DeploymentAction.ROLLBACK;
        } else {
            return DeploymentAction.NONE;
        }
    }

    /**
     * Compare the changelog with database changelog to determine which scripts need to be rolled back.
     * Returns scripts that are in the database but not in the current changelog.
     * Results are returned in reversed order so scripts are rolled back in reverse execution order.
     *
     * @param changeLogConfig The changelog configuration
     * @return List of ChangeLogEntry objects that need to be rolled back (in reversed execution order)
     */
    public List<ChangeLogEntry> determineRollbackScripts(ChangeLogConfig changeLogConfig) {
        // Get script names from current changelog
        final List<String> currentScriptNames = Optional.ofNullable(changeLogConfig)
                .map(config -> config.getScripts())
                .map(scripts -> scripts.stream()
                        .map(ScriptConfig::getName).toList())
                .orElse(List.of());

        // Get executed scripts from database
        List<ChangeLogEntry> executedChangeLog = auditRepository.getAllExecutedScripts();

        // Find scripts that are executed but not in current changelog (need rollback)
        // Return in reversed order so scripts are rolled back in reverse execution order
        return executedChangeLog.stream()
                .filter(changlog -> !currentScriptNames.contains(changlog.getScriptName()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> {
                            Collections.reverse(list);
                            return list;
                        }
                ));
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
            return basePath + "/scripts";
        }

        // For file system paths, resolve "scripts" directory
        if (basePath.endsWith("/") || basePath.endsWith("\\")) {
            return basePath + "scripts";
        }

        return basePath + java.io.File.separator + "scripts";
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

        return basePath + java.io.File.separator + scriptPath;
    }
}
