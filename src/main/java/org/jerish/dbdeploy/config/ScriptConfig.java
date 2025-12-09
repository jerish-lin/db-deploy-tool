package org.jerish.dbdeploy.config;

public class ScriptConfig {
    private String name;

    public ScriptConfig() {
    }

    public ScriptConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return name;
    }

    public String getApplyScriptPath() {
        return name + ".apply.sql";
    }

    public String getRollbackScriptPath() {
        return name + ".rollback.sql";
    }

    public String getApplyVerifyScriptPath() {
        return name + ".apply.verify.sql";
    }

    public String getRollbackVerifyScriptPath() {
        return name + ".rollback.verify.sql";
    }
}