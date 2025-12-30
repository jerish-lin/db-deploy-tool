package org.jerish.dbdeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ScriptConfig {
    private String name;

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


}