package org.jerish.dbdeploy.service;

import org.jerish.dbdeploy.entity.ChangeLogConfig;
import org.jerish.dbdeploy.entity.DatabaseStatus;

public interface DatabaseDeployService {

    void deploy(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception;

    void rollback(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception;

    DatabaseStatus getComprehensiveStatus();
}