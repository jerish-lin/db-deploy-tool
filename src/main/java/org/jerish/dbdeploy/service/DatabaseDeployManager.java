package org.jerish.dbdeploy.service;


import org.jerish.dbdeploy.entity.DatabaseStatus;

public interface DatabaseDeployManager {
    void deploy(String changeLogConfigPath, String tagName, boolean dryRun) throws Exception;

    void rollback(String targetTagName, boolean dryRun) throws Exception;

    void deployOrRollback(String changeLogConfigPath, String tagName, boolean dryRun);

    DatabaseStatus status();
}