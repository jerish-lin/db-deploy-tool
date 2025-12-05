package org.jerish.dbdeploy.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {

    public static ChangeLogConfig loadChangeLogConfig(String configPath) throws Exception {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(ChangeLogConfig.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);

        InputStream inputStream = getConfigInputStream(configPath);
        try {
            ChangeLogConfig config = yaml.load(inputStream);
            if (config == null) {
                throw new RuntimeException("ChangeLog config is null or empty");
            }
            return config;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
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