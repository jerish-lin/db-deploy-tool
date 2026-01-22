-- SQLite Schema for SchemaFlow (Refactored)
-- Separated script metadata from audit history

-- Create schemaflow_changelog_script table - stores script metadata (immutable)
CREATE TABLE IF NOT EXISTS schemaflow_changelog_script (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_name TEXT NOT NULL UNIQUE,
    script_checksum TEXT NOT NULL,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create schemaflow_changelog_audit table - stores execution history (append-only)
CREATE TABLE IF NOT EXISTS schemaflow_changelog_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_id INTEGER NOT NULL,
    execution_status TEXT NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms INTEGER,
    error_message TEXT,
    target_nodes TEXT,
    node_execution_details TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES schemaflow_changelog_script(id)
);

-- Create schemaflow_deploy_lock table
CREATE TABLE IF NOT EXISTS schemaflow_deploy_lock (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lock_key TEXT NOT NULL UNIQUE,
    lock_owner TEXT,
    lock_acquired_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TEXT,
    is_active INTEGER DEFAULT 1
);