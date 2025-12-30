package org.jerish.dbdeploy.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChangeLogConfig {
    private String changelogFilePath;
    private List<ScriptConfig> scripts;

    @Override
    public String toString() {
        return "ChangeLogConfig{" +
                "changelogFilePath=" + changelogFilePath +
                "scripts=" + scripts +
                '}';
    }
}