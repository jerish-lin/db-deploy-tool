package org.jerish.dbdeploy.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ScriptFileManager {

    public List<ScriptFile> loadScripts(String basePath, List<ScriptConfig> scriptConfigs) throws IOException {
        List<ScriptFile> scriptFiles = new ArrayList<>();

        for (ScriptConfig config : scriptConfigs) {
            ScriptFile scriptFile = loadScript(basePath, config);
            scriptFiles.add(scriptFile);
        }

        return scriptFiles;
    }

    public ScriptFile loadScript(String basePath, ScriptConfig config) throws IOException {
        String applyScriptPath = resolveScriptPath(basePath, config.getApplyScriptPath());
        String rollbackScriptPath = resolveScriptPath(basePath, config.getRollbackScriptPath());
        String rollbackVerifyScriptPath = resolveScriptPath(basePath, config.getRollbackVerifyScriptPath());

        String applyContent = readScriptContent(applyScriptPath);
        String rollbackContent = readScriptContent(rollbackScriptPath);
        String rollbackVerifyContent = null;

        // Try to read rollback verify script if it exists
        try {
            rollbackVerifyContent = readScriptContent(rollbackVerifyScriptPath);
        } catch (IOException e) {
            // It's okay if rollback verify script doesn't exist
            log.debug("Rollback verify script not found for: {}", config.getName());
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

    private String resolveScriptPath(String basePath, String scriptPath) {
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

    @AllArgsConstructor
    @Data
    public static class ScriptFile {
        private final String name;
        private final String applyPath;
        private final String rollbackPath;
        private final String applyContent;
        private final String rollbackContent;
        private final String rollbackVerifyContent;

        public String getScriptName() {
            return Paths.get(applyPath).getFileName().toString();
        }
    }
}