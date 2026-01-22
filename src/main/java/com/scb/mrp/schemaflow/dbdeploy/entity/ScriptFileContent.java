package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a script file with its paths and content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptFileContent {
    private String name;
    private String applyPath;
    private String applyContent;
    private String applyVerificationPath;
    private String applyVerificationContent;
    private String rollbackPath;
    private String rollbackContent;
    private String rollbackVerificationPath;
    private String rollbackVerificationContent;
}