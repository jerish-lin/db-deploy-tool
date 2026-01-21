package org.jerish.precheck.annotation;

import org.jerish.precheck.autoconfigure.DbDeployCheckAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(DbDeployCheckAutoConfiguration.class)
public @interface EnableDbDeployCheck {
}