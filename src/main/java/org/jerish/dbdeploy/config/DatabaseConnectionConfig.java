package org.jerish.dbdeploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "db-deploy.database")
@Data
public class DatabaseConnectionConfig {
    private String url;
    private String username;
    private String password;
    private String driver;
    private int maxPoolSize = 10;
    private int connectionTimeout = 30000;
    private int idleTimeout = 600000;
    private int maxLifetime = 1800000;
}