package org.jerish.dbdeploy.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfiguration {

    @Bean
    public DataSource dbDeployDataSource(DatabaseConfig config) {
//        HikariConfig hikariConfig = new HikariConfig();
//        hikariConfig.setJdbcUrl(config.getUrl());
//        hikariConfig.setUsername(config.getUsername());
//        hikariConfig.setPassword(config.getPassword());
//        hikariConfig.setDriverClassName(config.getDriver());
//        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
//        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
//        hikariConfig.setIdleTimeout(config.getIdleTimeout());
//        hikariConfig.setMaxLifetime(config.getMaxLifetime());
//        hikariConfig.setPoolName("db-deploy-tool-pool");
//
//        return new HikariDataSource(hikariConfig);
//
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(config.getDriver());
        dataSource.setUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        return dataSource;
//
    }
}