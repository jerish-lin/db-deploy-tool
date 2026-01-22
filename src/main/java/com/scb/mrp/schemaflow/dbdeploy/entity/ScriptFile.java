package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Paths;

@AllArgsConstructor
@Data
public class ScriptFile {
    private final String name;
    private final String applyPath;
    private final String applyVerificationPath;
    private final String rollbackPath;
    private final String rollbackVerificationPath;

    public String getScriptName() {
        return Paths.get(applyPath).getFileName().toString();
    }
}
