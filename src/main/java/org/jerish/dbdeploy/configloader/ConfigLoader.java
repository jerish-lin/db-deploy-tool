package org.jerish.dbdeploy.configloader;

import org.jerish.dbdeploy.config.DatabaseConfig;
import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.ScriptConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    public static ChangeLogConfig loadChangeLogConfig(String configPath) throws Exception {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(loaderOptions);

        InputStream inputStream = getConfigInputStream(configPath);
        try {
            ChangeLogConfig config = new ChangeLogConfig();
            config.setChangelogFilePath(configPath);
            // Load as generic map to handle both formats
            Object rawConfig = yaml.load(inputStream);
            if (rawConfig == null) {
                throw new RuntimeException("ChangeLog config is null or empty");
            }
            config.setScripts(parseChangeLogConfig(rawConfig));
            return config;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ScriptConfig> parseChangeLogConfig(Object rawConfig) {
        List<ScriptConfig> scriptConfigs = new ArrayList<>();
        if (rawConfig instanceof Map) {
            Map<String, Object> configMap = (Map<String, Object>) rawConfig;
            Object scriptsObj = configMap.get("scripts");

            if (scriptsObj instanceof List) {
                List<Object> scriptsList = (List<Object>) scriptsObj;
                for (Object scriptItem : scriptsList) {
                    if (scriptItem instanceof String) {
                        // New format: direct string
                        scriptConfigs.add(new ScriptConfig((String) scriptItem));
                    } else if (scriptItem instanceof Map) {
                        // Old format: object with name field
                        Map<String, Object> scriptMap = (Map<String, Object>) scriptItem;
                        Object nameObj = scriptMap.get("name");
                        if (nameObj instanceof String) {
                            scriptConfigs.add(new ScriptConfig((String) nameObj));
                        }
                    }
                }
            }
        }

        return scriptConfigs;
    }

    private static InputStream getConfigInputStream(String configPath) throws Exception {
        if (configPath.startsWith("classpath:")) {
            String classpathResource = configPath.substring("classpath:".length());
            InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(classpathResource);
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + classpathResource);
            }
            return inputStream;
        } else {
            Path path = Paths.get(configPath);
            if (!Files.exists(path)) {
                throw new RuntimeException("Config file not found: " + configPath);
            }
            return Files.newInputStream(path);
        }
    }

    public static DatabaseConfig loadDatabaseConfig(String configPath) throws Exception {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(DatabaseConfig.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);

        InputStream inputStream = getConfigInputStream(configPath);
        try {
            DatabaseConfig config = yaml.load(inputStream);
            if (config == null) {
                throw new RuntimeException("Database config is null or empty");
            }
            return config;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}