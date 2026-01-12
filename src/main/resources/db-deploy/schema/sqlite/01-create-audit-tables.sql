-- SQLite Schema for Database Deployment Tool
-- Audit tables for tracking script execution

-- Create db_change_log table
CREATE TABLE IF NOT EXISTS db_change_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_name TEXT NOT NULL,
    script_checksum TEXT NOT NULL,
    execution_status TEXT NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms INTEGER,
    error_message TEXT,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create database_lock table
CREATE TABLE IF NOT EXISTS database_lock (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lock_key TEXT NOT NULL UNIQUE,
    lock_owner TEXT,
    lock_acquired_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TEXT,
    is_active INTEGER DEFAULT 1
);