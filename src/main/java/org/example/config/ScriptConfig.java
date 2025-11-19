package org.example.config;

public class ScriptConfig {
    private String id;
    private String apply;
    private String rollback;

    public ScriptConfig() {
    }

    public ScriptConfig(String id, String apply, String rollback) {
        this.id = id;
        this.apply = apply;
        this.rollback = rollback;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApply() {
        return apply;
    }

    public void setApply(String apply) {
        this.apply = apply;
    }

    public String getRollback() {
        return rollback;
    }

    public void setRollback(String rollback) {
        this.rollback = rollback;
    }


}