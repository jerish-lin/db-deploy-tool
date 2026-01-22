package com.scb.mrp.schemaflow.dbdeploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "schemaflow")
@Data
public class DeploymentConfig {
    private boolean enableAutoRollback = true;
}