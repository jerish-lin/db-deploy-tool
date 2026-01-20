package org.jerish.dbdeploy.autoconfigure;

import org.jerish.dbdeploy.annotation.EnableDbDeployCheck;
import org.jerish.dbdeploy.service.DBStatusPreCheckService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

/**
 * Auto-configuration for database deployment status pre-check.
 * 
 * This configuration is activated by the {@link EnableDbDeployCheck} annotation
 * or by setting the property {@code db.deploy.check.enabled=true}.
 * 
 * Configuration properties:
 * <ul>
 *   <li>{@code db.deploy.check.enabled}: Enable/disable the pre-check (default: false)</li>
 *   <li>{@code db.deploy.check.fail-on-deployment-in-progress}: Fail if deployment in progress (default: true)</li>
 *   <li>{@code db.deploy.check.fail-on-last-deployment-failed}: Fail if last deployment failed (default: true)</li>
 *   <li>{@code db.deploy.check.fail-on-pending-changelog}: Fail if pending changelog exists (default: true)</li>
 *   <li>{@code db.deploy.check.changelog-path}: Path to changelog file (default: "classpath:db-changelog.yml")</li>
 * </ul>
 * 
 * Usage via annotation:
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableDbDeployCheck
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 * 
 * Usage via properties:
 * <pre>
 * db.deploy.check.enabled=true
 * db.deploy.check.changelog-path=classpath:my-changelog.yml
 * </pre>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "db.deploy.check", name = "enabled", havingValue = "true")
@PropertySource("classpath:db-deploy-check.properties")
public class DbDeployCheckAutoConfiguration {
    // The DBStatusPreCheckService is automatically registered via @ComponentScan
    // and @ConditionalOnProperty annotation on the service class
}