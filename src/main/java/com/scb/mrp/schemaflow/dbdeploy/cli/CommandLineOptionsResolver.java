package com.scb.mrp.schemaflow.dbdeploy.cli;

import com.scb.mrp.schemaflow.dbdeploy.config.ChangeLogPathConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolver for command line options that handles parameter validation
 * and changelog path resolution with fallback logic.
 */
@Component
@EnableConfigurationProperties(ChangeLogPathConfig.class)
@Slf4j
@RequiredArgsConstructor
public class CommandLineOptionsResolver {
    private final ChangeLogPathConfig changeLogPathConfig;

    public CommandLineOptions resolve(String[] args) {
        CommandLineOptions options = CommandLineOptions.parseArgs(args);
        options.setChangelogPath(resolveChangelogPath(options.getChangelogPath()));

        return options;
    }

    public String resolveChangelogPath(String commandLinePath) {
        return Optional.ofNullable(commandLinePath).orElse(changeLogPathConfig.getPath());
    }

}