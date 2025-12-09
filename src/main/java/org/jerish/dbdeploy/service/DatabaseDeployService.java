package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.config.ChangeLogConfig;

public interface DatabaseDeployService {

    void deploy(ChangeLogConfig changeLogConfig, String tagName, boolean dryRun) throws Exception;

    void rollback(String targetTagName, boolean dryRun) throws Exception;

    void showStatus() throws Exception;
}