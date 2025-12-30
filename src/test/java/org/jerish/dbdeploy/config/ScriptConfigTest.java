package org.jerish.dbdeploy.config;

import org.jerish.dbdeploy.entity.ScriptConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptConfigTest {

    @Test
    public void testScriptConfigWithoutFolder() {
        ScriptConfig config = new ScriptConfig("create-users-table");

        assertEquals("create-users-table", config.getName());
        assertEquals("", config.getFolderPath());
        assertEquals("create-users-table", config.getBaseScriptName());
        assertFalse(config.hasFolderPath());
        assertEquals("create-users-table.apply.sql", config.getApplyScriptPath());
        assertEquals("create-users-table.rollback.sql", config.getRollbackScriptPath());
    }

    @Test
    public void testScriptConfigWithFolder() {
        ScriptConfig config = new ScriptConfig("feature-12346/create-users-table");

        assertEquals("feature-12346/create-users-table", config.getName());
        assertEquals("feature-12346", config.getFolderPath());
        assertEquals("create-users-table", config.getBaseScriptName());
        assertTrue(config.hasFolderPath());
        assertEquals("feature-12346/create-users-table.apply.sql", config.getApplyScriptPath());
        assertEquals("feature-12346/create-users-table.rollback.sql", config.getRollbackScriptPath());
    }

    @Test
    public void testScriptConfigWithNestedFolder() {
        ScriptConfig config = new ScriptConfig("feature-12346/v1.0/create-users-table");

        assertEquals("feature-12346/v1.0/create-users-table", config.getName());
        assertEquals("feature-12346/v1.0", config.getFolderPath());
        assertEquals("create-users-table", config.getBaseScriptName());
        assertTrue(config.hasFolderPath());
        assertEquals("feature-12346/v1.0/create-users-table.apply.sql", config.getApplyScriptPath());
    }
}