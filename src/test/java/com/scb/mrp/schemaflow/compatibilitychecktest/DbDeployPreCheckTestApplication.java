package com.scb.mrp.schemaflow.compatibilitychecktest;

import com.scb.mrp.schemaflow.compatibilitycheck.annotation.EnableDbCompatibilityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Test application to verify EnableDbDeployCheck annotation functionality.
 * This application uses the @EnableDbDeployCheck annotation to enable
 * database deployment status pre-checks during startup.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDbCompatibilityCheck
public class DbDeployPreCheckTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbDeployPreCheckTestApplication.class, args);
    }
}