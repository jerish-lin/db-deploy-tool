package com.scb.mrp.schemaflow.compatibilitycheck.annotation;

import com.scb.mrp.schemaflow.compatibilitycheck.autoconfigure.CompatibilityCheckAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CompatibilityCheckAutoConfiguration.class)
public @interface EnableDbCompatibilityCheck {
}