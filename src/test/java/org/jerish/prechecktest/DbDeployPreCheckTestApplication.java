package org.jerish.prechecktest;

import org.jerish.precheck.annotation.EnableDbDeployCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Test application to verify EnableDbDeployCheck annotation functionality.
 * This application uses the @EnableDbDeployCheck annotation to enable
 * database deployment status pre-checks during startup.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDbDeployCheck
public class DbDeployPreCheckTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbDeployPreCheckTestApplication.class, args);
    }
}