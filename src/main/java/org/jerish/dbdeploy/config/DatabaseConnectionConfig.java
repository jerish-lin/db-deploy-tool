package org.jerish.dbdeploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "db-deploy.database")
@Data
public class DatabaseConnectionConfig {
    private String name;
    private String url;
    private String username;
    private String password;
    private String driver;
    private Integer maxPoolSize = 10;
    private Integer connectionTimeout = 30000;
    private Integer idleTimeout = 600000;
    private Integer maxLifetime = 1800000;
    private Boolean isDefault = false;
}