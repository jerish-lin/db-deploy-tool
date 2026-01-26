package com.scb.mrp.schemaflow.statusinfo.autoconfigure;

import com.scb.mrp.schemaflow.dbdeploy.changelog.ChangeLogManager;
import com.scb.mrp.schemaflow.dbdeploy.config.ChangeLogPathConfig;
import com.scb.mrp.schemaflow.dbdeploy.config.DatabaseConnectionConfig;
import com.scb.mrp.schemaflow.dbdeploy.config.DatasourceConfiguration;
import com.scb.mrp.schemaflow.dbdeploy.repository.ClickHouseAuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.repository.PostgreSQLAuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.repository.SQLiteAuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.schema.ClickHouseSchemaInitializationStrategy;
import com.scb.mrp.schemaflow.dbdeploy.schema.PostgreSqlSchemaInitializationStrategy;
import com.scb.mrp.schemaflow.dbdeploy.schema.SQLiteSchemaInitializationStrategy;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusService;
import com.scb.mrp.schemaflow.statusinfo.config.StatusInfoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for database status information feature.
 * This configuration only activates when:
 * 1. The application is a web application
 * 2. Spring Boot Actuator is available on the classpath
 * 3. The @EnableStatusInfo annotation is present
 * 4. The schemaflow.statusinfo.enabled property is true (default)
 *
 * This ensures no web dependencies are added to the library unless the consuming
 * application is web-based and explicitly enables this feature.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "schemaflow.statusinfo", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StatusInfoProperties.class)
@Import({
        // Database configuration
        ChangeLogPathConfig.class,
        DatabaseConnectionConfig.class,
        DatasourceConfiguration.class,

        // Schema initialization
        PostgreSqlSchemaInitializationStrategy.class,
        SQLiteSchemaInitializationStrategy.class,
        ClickHouseSchemaInitializationStrategy.class,
        SchemaInitializationManager.class,

        // Repository implementations
        ClickHouseAuditRepository.class,
        PostgreSQLAuditRepository.class,
        SQLiteAuditRepository.class,

        // Services
        ChangeLogManager.class,
        DatabaseStatusService.class
})
public class StatusInfoAutoConfiguration {

    /**
     * Create the health indicator bean for database deployment status.
     * This bean will be automatically registered with Spring Boot Actuator.
     */
    @Bean
    @ConditionalOnMissingBean(name = "statusInfoHealthIndicator")
    @ConditionalOnEnabledHealthIndicator("dbstatus")
    public StatusInfoHealthIndicator statusInfoHealthIndicator(
            DatabaseStatusService databaseStatusService,
            SchemaInitializationManager schemaInitializationManager,
            StatusInfoProperties properties) {
        log.info("Registering StatusInfoHealthIndicator with includeDetails={}", properties.isIncludeDetails());
        return new StatusInfoHealthIndicator(
                databaseStatusService,
                schemaInitializationManager,
                properties.isIncludeDetails()
        );
    }
}