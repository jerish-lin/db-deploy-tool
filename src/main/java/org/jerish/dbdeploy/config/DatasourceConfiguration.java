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
     * Create the default DataSource for audit tables and single-node scripts
     */
    @Bean
    public DataSource dbDeployDataSource(DatabaseConnectionConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriver());
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setPoolName("db-deploy-tool-pool");

        return new HikariDataSource(hikariConfig);
        //        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        //        dataSource.setDriverClassName(config.getDriver());
        //        dataSource.setUrl(config.getUrl());
        //        dataSource.setUsername(config.getUsername());
        //        dataSource.setPassword(config.getPassword());
        //        return dataSource;
    }

    /**
     * Create the default JdbcTemplate
     */
    @Bean
    public JdbcTemplate dbDeployJdbcTemplate(DataSource dbDeployDataSource) {
        return new JdbcTemplate(dbDeployDataSource);
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
                DataSource dataSource = createDataSourceForNode(node);
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

    /**
     * Create a DataSource for a specific node configuration
     */
    private DataSource createDataSourceForNode(DatabaseConnectionConfig node) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(node.getUrl());
        hikariConfig.setUsername(node.getUsername() != null ? node.getUsername() : "");
        hikariConfig.setPassword(node.getPassword() != null ? node.getPassword() : "");
        hikariConfig.setDriverClassName(node.getDriver());
        hikariConfig.setPoolName("node-" + node.getName() + "-pool");

        // Use node-specific pool settings if provided, otherwise use defaults
        hikariConfig.setMaximumPoolSize(node.getMaxPoolSize() != null ? node.getMaxPoolSize() : 5);
        hikariConfig.setConnectionTimeout(node.getConnectionTimeout() != null ? node.getConnectionTimeout() : 10000);
        hikariConfig.setIdleTimeout(node.getIdleTimeout() != null ? node.getIdleTimeout() : 300000);
        hikariConfig.setMaxLifetime(node.getMaxLifetime() != null ? node.getMaxLifetime() : 600000);

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Check if multi-node configuration is available
     */
    @Bean
    public boolean isMultiNodeEnabled() {
        return nodesConfig.getNodes() != null && !nodesConfig.getNodes().isEmpty();
    }
}