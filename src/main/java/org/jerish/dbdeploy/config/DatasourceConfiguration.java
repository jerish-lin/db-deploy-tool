package org.jerish.dbdeploy.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatasourceConfiguration {

    private final DatabaseConnectionConfig databaseConnectionConfig;
    private final NodesConfig nodesConfig;

    /**
     * Create the default JdbcTemplate
     */
    @Bean("dbDeployJdbcTemplate")
    public JdbcTemplate dbDeployJdbcTemplate() {
        return new JdbcTemplate(createDataSource("schemaflow-pool", databaseConnectionConfig));
    }

    /**
     * Map of node names to their corresponding JdbcTemplates for multi-node execution
     */
    @Bean("nodeJdbcTemplateMap")
    public Map<String, JdbcTemplate> nodeJdbcTemplateMap() {
        Map<String, JdbcTemplate> templateMap = new HashMap<>();

        if (nodesConfig.getNodes() == null || nodesConfig.getNodes().isEmpty()) {
            log.info("No multi-node configuration found. Single-node mode will be used.");
            return templateMap;
        }

        log.info("Configuring {} node(s) for multi-node execution", nodesConfig.getNodes().size());

        for (DatabaseConnectionConfig node : nodesConfig.getNodes()) {
            if (node.getName() == null || node.getName().isEmpty()) {
                log.warn("Skipping node configuration without a name");
                continue;
            }

            try {
                DataSource dataSource = createDataSource("node-" + node.getName() + "-pool", node);
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                templateMap.put(node.getName(), jdbcTemplate);
                log.info("Successfully configured JDBC template for node: {}", node.getName());
            } catch (Exception e) {
                log.error("Failed to configure JDBC template for node: {}", node.getName(), e);
                throw new RuntimeException("Failed to configure node: " + node.getName(), e);
            }
        }

        return templateMap;
    }

    private DataSource createDataSource(String poolName, DatabaseConnectionConfig databaseConnectionConfig) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(poolName);
        hikariConfig.setJdbcUrl(databaseConnectionConfig.getUrl());
        hikariConfig.setUsername(databaseConnectionConfig.getUsername() != null ? databaseConnectionConfig.getUsername() : "");
        hikariConfig.setPassword(databaseConnectionConfig.getPassword() != null ? databaseConnectionConfig.getPassword() : "");
        hikariConfig.setDriverClassName(databaseConnectionConfig.getDriver());

        hikariConfig.setMaximumPoolSize(databaseConnectionConfig.getMaxPoolSize() != null ? databaseConnectionConfig.getMaxPoolSize() : 5);
        hikariConfig.setConnectionTimeout(databaseConnectionConfig.getConnectionTimeout() != null ? databaseConnectionConfig.getConnectionTimeout() : 10000);
        hikariConfig.setIdleTimeout(databaseConnectionConfig.getIdleTimeout() != null ? databaseConnectionConfig.getIdleTimeout() : 300000);
        hikariConfig.setMaxLifetime(databaseConnectionConfig.getMaxLifetime() != null ? databaseConnectionConfig.getMaxLifetime() : 600000);

        return new HikariDataSource(hikariConfig);
    }
}