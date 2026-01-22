package com.scb.mrp.schemaflow.dbdeploy.service;

import com.scb.mrp.schemaflow.dbdeploy.entity.ChangeLogConfig;

import java.util.Map;

public interface DatabaseDeployService {

    void deploy(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception;

    void deploy(ChangeLogConfig changeLogConfig, boolean dryRun, Map<String, String> parameters) throws Exception;

    void rollback(ChangeLogConfig changeLogConfig, boolean dryRun) throws Exception;

    void rollback(ChangeLogConfig changeLogConfig, boolean dryRun, Map<String, String> parameters) throws Exception;
}