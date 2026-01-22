package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChangeLogConfig {
    private String basePath;
    private String fileName;
    private List<ScriptConfig> scripts;

    @Override
    public String toString() {
        return "ChangeLogConfig{" +
                "basePath=" + basePath +
                ", fileName=" + fileName +
                ", scripts=" + scripts +
                '}';
    }
}