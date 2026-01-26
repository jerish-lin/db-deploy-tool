package com.scb.mrp.schemaflow.statusinfo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for database status information feature.
 *
 * Properties can be configured in application.yml or application.properties:
 *
 * <pre>
 * schemaflow:
 *   statusinfo:
 *     enabled: true
 *     include-details: true
 *     endpoint-path: /actuator/dbstatus
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "schemaflow.statusinfo")
public class StatusInfoProperties {

    /**
     * Whether to enable the status info feature.
     * When false, the health indicator and custom endpoint will not be registered.
     */
    private boolean enabled = true;

    /**
     * Whether to include detailed script information in the health endpoint.
     * When true, includes script names, checksums, and execution details.
     * When false, only includes summary counts.
     */
    private boolean includeDetails = true;

    /**
     * Custom endpoint path for the detailed status information.
     * This endpoint provides full DatabaseStatus object with all details.
     * Default is "/actuator/dbstatus".
     */
    private String endpointPath = "/actuator/dbstatus";
}