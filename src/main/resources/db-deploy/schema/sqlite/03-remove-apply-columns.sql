-- SQLite Migration: Remove apply_script_content and apply_verify_script_content columns
-- This script removes the columns that are no longer used (apply scripts are read from files, not stored in DB)

-- SQLite doesn't support ALTER TABLE DROP COLUMN directly, need to recreate the table
BEGIN TRANSACTION;

-- Create new table without the apply columns
CREATE TABLE IF NOT EXISTS schemaflow_changelog_script_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_name TEXT NOT NULL UNIQUE,
    script_checksum TEXT NOT NULL,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Copy data from old table to new table (excluding apply columns)
INSERT INTO schemaflow_changelog_script_new (id, script_name, script_checksum, rollback_script_content, rollback_verify_script_content, created_at)
SELECT id, script_name, script_checksum, rollback_script_content, rollback_verify_script_content, created_at
FROM schemaflow_changelog_script;

-- Drop old table
DROP TABLE schemaflow_changelog_script;

-- Rename new table to original name
ALTER TABLE schemaflow_changelog_script_new RENAME TO schemaflow_changelog_script;

COMMIT;