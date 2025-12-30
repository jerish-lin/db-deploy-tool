package org.jerish.dbdeploy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Spring transaction configuration for the database deployment tool.
 * Enables declarative transaction management using @Transactional annotations.
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig {

    /**
     * Creates a PlatformTransactionManager bean for managing transactions.
     * This will be used by @Transactional annotations throughout the application.
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dbDeployDataSource) {
        return new DataSourceTransactionManager(dbDeployDataSource);
    }

    @Bean
    public JdbcTemplate dbDeployJdbcTemplate(DataSource dbDeployDataSource) {
        return new JdbcTemplate(dbDeployDataSource);
    }
}