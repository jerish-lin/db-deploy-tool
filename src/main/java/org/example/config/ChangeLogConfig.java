package org.example.config;

import java.util.List;

public class ChangeLogConfig {
    private List<ScriptConfig> scripts;

    public ChangeLogConfig() {
    }

    public List<ScriptConfig> getScripts() {
        return scripts;
    }

    public void setScripts(List<ScriptConfig> scripts) {
        this.scripts = scripts;
    }
}