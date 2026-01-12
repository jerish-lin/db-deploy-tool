package org.jerish.dbdeploy.service;


import org.jerish.dbdeploy.entity.DatabaseStatus;

import java.util.Map;

public interface DatabaseDeployManager {
    void deploy(String changeLogConfigPath, boolean dryRun) throws Exception;

    void deploy(String changeLogConfigPath, boolean dryRun, Map<String, String> parameters) throws Exception;

    void rollback(String changeLogConfigPath, boolean dryRun) throws Exception;

    void rollback(String changeLogConfigPath, boolean dryRun, Map<String, String> parameters) throws Exception;

    void deployOrRollback(String changeLogConfigPath, boolean dryRun);

    DatabaseStatus status();
}