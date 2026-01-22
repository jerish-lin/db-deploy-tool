package com.scb.mrp.schemaflow.dbdeploy.config;

import com.scb.mrp.schemaflow.dbdeploy.entity.ScriptConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptConfigTest {

    @Test
    public void testScriptConfigWithoutFolder() {
        ScriptConfig config = new ScriptConfig("create-users-table", null);

        assertEquals("create-users-table", config.getName());
        assertEquals("", config.getFolderPath());
        assertEquals("create-users-table", config.getBaseScriptName());
        assertFalse(config.hasFolderPath());
        assertEquals("create-users-table.apply.sql", config.getApplyScriptPath());
        assertEquals("create-users-table.rollback.sql", config.getRollbackScriptPath());
        assertFalse(config.isMultiNode());
        assertFalse(config.isAllNodes());
        assertNull(config.getNodes());
    }

    @Test
    public void testScriptConfigWithFolder() {
        ScriptConfig config = new ScriptConfig("feature-12346/create-users-table", null);

        assertEquals("feature-12346/create-users-table", config.getName());
        assertEquals("feature-12346", config.getFolderPath());
        assertEquals("create-users-table", config.getBaseScriptName());
        assertTrue(config.hasFolderPath());
        assertEquals("feature-12346/create-users-table.apply.sql", config.getApplyScriptPath());
        assertEquals("feature-12346/create-users-table.rollback.sql", config.getRollbackScriptPath());
        assertFalse(config.isMultiNode());
        assertFalse(config.isAllNodes());
        assertNull(config.getNodes());
    }

    @Test
    public void testScriptConfigWithNestedFolder() {
        ScriptConfig config = new ScriptConfig("feature-12346/v1.0/create-users-table", null);

        assertEquals("feature-12346/v1.0/create-users-table", config.getName());
        assertEquals("feature-12346/v1.0", config.getFolderPath());
        assertEquals("create-users-table", config.getBaseScriptName());
        assertTrue(config.hasFolderPath());
        assertEquals("feature-12346/v1.0/create-users-table.apply.sql", config.getApplyScriptPath());
        assertFalse(config.isMultiNode());
        assertFalse(config.isAllNodes());
        assertNull(config.getNodes());
    }

    @Test
    public void testMultiNodeConfigWithSpecificNodes() {
        ScriptConfig config = new ScriptConfig("create-table", List.of("node1", "node2"));

        assertEquals("create-table", config.getName());
        assertTrue(config.isMultiNode());
        assertFalse(config.isAllNodes());
        assertEquals(List.of("node1", "node2"), config.getNodes());
    }

    @Test
    public void testMultiNodeConfigWithAllNodes() {
        ScriptConfig config = new ScriptConfig("create-table", List.of("ALL"));

        assertEquals("create-table", config.getName());
        assertTrue(config.isMultiNode());
        assertTrue(config.isAllNodes());
        assertEquals(List.of("ALL"), config.getNodes());
    }

    @Test
    public void testMultiNodeConfigWithEmptyNodes() {
        ScriptConfig config = new ScriptConfig("create-table", List.of());

        assertEquals("create-table", config.getName());
        assertFalse(config.isMultiNode());
        assertFalse(config.isAllNodes());
        assertEquals(List.of(), config.getNodes());
    }
}