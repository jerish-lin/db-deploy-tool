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

    /**
     * Get the folder path part of the script name (if any)
     * For example: "feature-12346/create-table" returns "feature-12346"
     * For example: "create-table" returns ""
     */
    public String getFolderPath() {
        int lastSlashIndex = name.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            return name.substring(0, lastSlashIndex);
        }
        return "";
    }

    /**
     * Get the base script name without folder path
     * For example: "feature-12346/create-table" returns "create-table"
     * For example: "create-table" returns "create-table"
     */
    public String getBaseScriptName() {
        int lastSlashIndex = name.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < name.length() - 1) {
            return name.substring(lastSlashIndex + 1);
        }
        return name;
    }

    /**
     * Check if this script is organized in a folder structure
     */
    public boolean hasFolderPath() {
        return name.contains("/");
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScriptConfig that = (ScriptConfig) obj;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}