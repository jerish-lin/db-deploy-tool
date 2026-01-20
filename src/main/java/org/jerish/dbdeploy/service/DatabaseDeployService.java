package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.entity.ChangeLogConfig;

import java.util.Map;

public interface DatabaseDeployService {

    void deploy(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception;

    void deploy(ChangeLogConfig changeLogConfig, boolean dryRun, Map<String, String> parameters) throws Exception;

    void rollback(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception;

    void rollback(ChangeLogConfig changeLogConfig, boolean dryRun, Map<String, String> parameters) throws Exception;
}