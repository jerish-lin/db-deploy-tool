package com.scb.mrp.schemaflow.dbdeploy.changelog;

import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;
import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptConfig;
import com.scb.mrp.schemaflow.dbdeploy.script.FileReader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    private static final FileReader fileReader = new FileReader();

    public static ChangeLogConfig loadChangeLogConfig(String configPath) throws Exception {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(loaderOptions);

        String content = fileReader.readFile(configPath);
        ChangeLogConfig config = new ChangeLogConfig();

        // Parse basePath and fileName from configPath
        parsePathComponents(configPath, config);

        // Load as generic map to handle both formats
        Object rawConfig = yaml.load(content);
        if (rawConfig == null) {
            throw new RuntimeException("ChangeLog config is null or empty");
        }
        config.setScripts(parseChangeLogConfig(rawConfig));
        return config;
    }

    /**
     * Parse the config path into basePath and fileName components.
     * If the path starts with "classpath:", the entire path (including prefix) is kept in basePath.
     * Otherwise, the parent directory is used as basePath and the file name is extracted.
     */
    private static void parsePathComponents(String configPath, ChangeLogConfig config) {
        if (configPath.startsWith("classpath:")) {
            // For classpath resources, keep the entire path including "classpath:" prefix in basePath
            // Extract just the file name
            int lastSlashIndex = configPath.lastIndexOf('/');
            if (lastSlashIndex == -1) {
                lastSlashIndex = configPath.lastIndexOf('\\');
            }

            if (lastSlashIndex > 0) {
                config.setBasePath(configPath.substring(0, lastSlashIndex + 1));
                config.setFileName(configPath.substring(lastSlashIndex + 1));
            } else {
                config.setBasePath(configPath);
                config.setFileName("");
            }
        } else {
            // For file system paths, use parent directory as basePath
            Path path = Paths.get(configPath);
            Path parent = path.getParent();
            if (parent != null) {
                config.setBasePath(parent.toString());
            } else {
                config.setBasePath(".");
            }
            config.setFileName(path.getFileName().toString());
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
                        // New format: direct string (single-node, backward compatible)
                        scriptConfigs.add(new ScriptConfig((String) scriptItem, null));
                    } else if (scriptItem instanceof Map) {
                        // Object format: with name and optional nodes field
                        Map<String, Object> scriptMap = (Map<String, Object>) scriptItem;
                        Object nameObj = scriptMap.get("name");
                        Object nodesObj = scriptMap.get("nodes");

                        if (nameObj instanceof String) {
                            String name = (String) nameObj;
                            List<String> nodes = null;

                            // Extract nodes if present
                            if (nodesObj instanceof List) {
                                nodes = new ArrayList<>();
                                for (Object nodeItem : (List<?>) nodesObj) {
                                    if (nodeItem instanceof String) {
                                        nodes.add((String) nodeItem);
                                    }
                                }
                            } else if (nodesObj instanceof String) {
                                // Handle single string value like "ALL"
                                nodes = new ArrayList<>();
                                nodes.add((String) nodesObj);
                            }

                            scriptConfigs.add(new ScriptConfig(name, nodes));
                        }
                    }
                }
            }
        }

        return scriptConfigs;
    }
}