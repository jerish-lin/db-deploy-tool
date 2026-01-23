package com.scb.mrp.schemaflow.dbdeploy.database;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * Conditional annotation to create beans only when the configured database driver matches.
 * This allows Spring to automatically select the appropriate schema initialization strategy
 * based on the database driver configured in application properties.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnDatabaseDriverCondition.class)
public @interface ConditionalOnDatabaseDriver {

    /**
     * The database type to match against.
     * Examples: DatabaseType.POSTGRESQL, DatabaseType.SQLITE, DatabaseType.CLICKHOUSE
     *
     * @return the database type
     */
    DatabaseType value();
}