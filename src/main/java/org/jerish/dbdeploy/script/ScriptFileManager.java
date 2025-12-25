package org.jerish.dbdeploy.script;

import org.jerish.dbdeploy.config.ScriptConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ScriptFileManager {
    private static final Logger logger = LoggerFactory.getLogger(ScriptFileManager.class);

    private final String basePath;

    public ScriptFileManager(String basePath) {
        this.basePath = basePath;
    }

    public List<ScriptFile> loadScripts(List<ScriptConfig> scriptConfigs) throws IOException {
        List<ScriptFile> scriptFiles = new ArrayList<>();

        for (ScriptConfig config : scriptConfigs) {
            ScriptFile scriptFile = loadScript(config);
            scriptFiles.add(scriptFile);
        }

        return scriptFiles;
    }

    public ScriptFile loadScript(ScriptConfig config) throws IOException {
        String applyScriptPath = resolveScriptPath(config.getApplyScriptPath());
        String rollbackScriptPath = resolveScriptPath(config.getRollbackScriptPath());
        String rollbackVerifyScriptPath = resolveScriptPath(config.getRollbackVerifyScriptPath());

        String applyContent = readScriptContent(applyScriptPath);
        String rollbackContent = readScriptContent(rollbackScriptPath);
        String rollbackVerifyContent = null;
        
        // Try to read rollback verify script if it exists
        try {
            rollbackVerifyContent = readScriptContent(rollbackVerifyScriptPath);
        } catch (IOException e) {
            // It's okay if rollback verify script doesn't exist
            logger.debug("Rollback verify script not found for: {}", config.getName());
        }

        return new ScriptFile(
                config.getName(),
                applyScriptPath,
                rollbackScriptPath,
                applyContent,
                rollbackContent,
                rollbackVerifyContent
        );
    }

    private String resolveScriptPath(String scriptPath) {
        if (Paths.get(scriptPath).isAbsolute()) {
            return scriptPath;
        }

        return Paths.get(basePath, scriptPath).toString();
    }

    private String readScriptContent(String scriptPath) throws IOException {
        Path path = Paths.get(scriptPath);
        if (!Files.exists(path)) {
            throw new IOException("Script file not found: " + scriptPath);
        }

        return Files.readString(path);
    }

    public boolean scriptExists(String scriptPath) {
        Path path = Paths.get(resolveScriptPath(scriptPath));
        return Files.exists(path);
    }

    public String getScriptBasePath() {
        return basePath;
    }

    public static class ScriptFile {
        private final String name;
        private final String applyPath;
        private final String rollbackPath;
        private final String applyContent;
        private final String rollbackContent;
        private final String rollbackVerifyContent;

        public ScriptFile(String name, String applyPath, String rollbackPath,
                          String applyContent, String rollbackContent, String rollbackVerifyContent) {
            this.name = name;
            this.applyPath = applyPath;
            this.rollbackPath = rollbackPath;
            this.applyContent = applyContent;
            this.rollbackContent = rollbackContent;
            this.rollbackVerifyContent = rollbackVerifyContent;
        }

        public String getName() {
            return name;
        }

        public String getApplyPath() {
            return applyPath;
        }

        public String getRollbackPath() {
            return rollbackPath;
        }

        public String getApplyContent() {
            return applyContent;
        }

        public String getRollbackContent() {
            return rollbackContent;
        }

        public String getRollbackVerifyContent() {
            return rollbackVerifyContent;
        }

        public String getScriptName() {
            return Paths.get(applyPath).getFileName().toString();
        }
    }
}