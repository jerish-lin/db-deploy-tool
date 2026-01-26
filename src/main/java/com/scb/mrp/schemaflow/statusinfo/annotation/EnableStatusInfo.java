package com.scb.mrp.schemaflow.statusinfo.annotation;

import com.scb.mrp.schemaflow.statusinfo.autoconfigure.StatusInfoAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Annotation to enable database deployment status information through Spring Boot Actuator.
 * When used on a configuration class or main application class, this annotation enables:
 * - A health indicator that reports database deployment status under /actuator/health
 * - An optional custom endpoint at /actuator/dbstatus for detailed status information
 *
 * This annotation only activates in web-based applications with Spring Boot Actuator available.
 * It will not add web dependencies to the library.
 *
 * Usage:
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableStatusInfo
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 *
 * Configuration properties:
 * <pre>
 * schemaflow:
 *   statusinfo:
 *     enabled: true
 *     include-details: true
 *     endpoint-path: /actuator/dbstatus
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(StatusInfoAutoConfiguration.class)
public @interface EnableStatusInfo {

    /**
     * Whether to include detailed script information in the health endpoint.
     * When true, includes script names, checksums, and execution details.
     * When false, only includes summary counts.
     *
     * @return true to include details, false for summary only
     */
    boolean includeDetails() default true;

    /**
     * Custom endpoint path for the detailed status information.
     * This endpoint provides full DatabaseStatus object with all details.
     * Default is "/actuator/dbstatus".
     *
     * @return the endpoint path
     */
    String endpointPath() default "/actuator/dbstatus";
}