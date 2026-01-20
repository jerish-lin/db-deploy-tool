package org.jerish.dbdeploy.annotation;

import org.jerish.dbdeploy.autoconfigure.DbDeployCheckAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable database deployment status pre-check.
 * When applied to a Spring Boot application class, this will:
 * 1. Check if a deployment is currently in progress (lock acquired by others)
 * 2. Check if the last deployment status is FAILED
 * 3. Check if there are pending changelog scripts not yet applied
 * 
 * If any of these conditions are met, the application startup will fail.
 * 
 * Usage:
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableDbDeployCheck
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(DbDeployCheckAutoConfiguration.class)
public @interface EnableDbDeployCheck {
    
    /**
     * Whether to fail the application startup if deployment is in progress.
     * Default is true.
     */
    boolean failOnDeploymentInProgress() default true;
    
    /**
     * Whether to fail the application startup if last deployment status is FAILED.
     * Default is true.
     */
    boolean failOnLastDeploymentFailed() default true;
    
    /**
     * Whether to fail the application startup if there are pending changelog scripts.
     * Default is true.
     */
    boolean failOnPendingChangelog() default true;
    
    /**
     * The changelog file path to check for pending scripts.
     * Default is "classpath:db-changelog.yml".
     */
    String changelogPath() default "classpath:db-changelog.yml";
}