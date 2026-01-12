package org.jerish.dbdeploy.service;


import org.jerish.dbdeploy.entity.DatabaseStatus;

public interface DatabaseDeployManager {
    void deploy(String changeLogConfigPath, boolean dryRun) throws Exception;

    void rollback(String changeLogConfigPath, boolean dryRun) throws Exception;

    void deployOrRollback(String changeLogConfigPath, boolean dryRun);

    DatabaseStatus status();
}