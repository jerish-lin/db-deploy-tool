package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;

public interface DatabaseDeployService {

    void deploy(ChangeLogConfig changeLogConfig, String tagName, boolean dryRun) throws Exception;

    void rollback(String targetTagName, boolean dryRun) throws Exception;

    DatabaseStatus getComprehensiveStatus();
}