-- ClickHouse Migration: Remove apply_script_content and apply_verify_script_content columns
-- This script removes the columns that are no longer used (apply scripts are read from files, not stored in DB)

-- ClickHouse doesn't support ALTER TABLE DROP COLUMN on MergeTree engines directly
-- Need to recreate the table

-- Create new table without the apply columns
CREATE TABLE IF NOT EXISTS schemaflow_changelog_script_new (
    id UInt64,
    script_name String NOT NULL,
    script_checksum String NOT NULL,
    rollback_script_content Nullable(String),
    rollback_verify_script_content Nullable(String),
    created_at DateTime NOT NULL DEFAULT now()
) ENGINE = MergeTree()
ORDER BY (id)
UNIQUE KEY script_name;

-- Insert data from old table to new table (excluding apply columns)
INSERT INTO schemaflow_changelog_script_new (id, script_name, script_checksum, rollback_script_content, rollback_verify_script_content, created_at)
SELECT id, script_name, script_checksum, rollback_script_content, rollback_verify_script_content, created_at
FROM schemaflow_changelog_script;

-- Drop old table
DROP TABLE schemaflow_changelog_script;

-- Rename new table to original name
RENAME TABLE schemaflow_changelog_script_new TO schemaflow_changelog_script;