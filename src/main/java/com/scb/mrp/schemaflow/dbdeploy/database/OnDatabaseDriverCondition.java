package com.scb.mrp.schemaflow.dbdeploy.database;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition implementation for @ConditionalOnDatabaseDriver annotation.
 * Evaluates whether the configured database driver matches the required database type.
 */
public class OnDatabaseDriverCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // Get the required database type from the annotation
        DatabaseType requiredType = (DatabaseType) metadata.getAnnotationAttributes(
                ConditionalOnDatabaseDriver.class.getName(), true).get("value");

        if (requiredType == null) {
            return false;
        }

        // Get the configured driver from the environment
        String configuredDriver = context.getEnvironment()
                .getProperty("schemaflow.database.driver");

        if (configuredDriver == null || configuredDriver.isEmpty()) {
            return false;
        }

        // Check if configured driver matches the required type's driver class (case-insensitive)
        return requiredType.getDriverClass().equalsIgnoreCase(configuredDriver);
    }
}