-- ClickHouse Schema for Database Deployment Tool
-- Audit tables for tracking script execution

-- Create db_change_log table
CREATE TABLE IF NOT EXISTS db_change_log (
    id UInt64,
    script_name String NOT NULL,
    script_checksum String NOT NULL,
    execution_status Enum8('SUCCESS' = 1, 'FAILED' = 2, 'ROLLED_BACK' = 3) NOT NULL,
    execution_time DateTime NOT NULL DEFAULT now(),
    execution_duration_ms Nullable(UInt64),
    error_message Nullable(String),
    rollback_script_content Nullable(String),
    rollback_verify_script_content Nullable(String),
    created_at DateTime NOT NULL DEFAULT now(),
    updated_at DateTime NOT NULL DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(execution_time)
ORDER BY (execution_time, id);

-- Create database_lock table
CREATE TABLE IF NOT EXISTS database_lock (
    id UInt64,
    lock_key String NOT NULL,
    lock_owner Nullable(String),
    lock_acquired_at DateTime NOT NULL DEFAULT now(),
    lock_expires_at Nullable(DateTime),
    is_active UInt8 DEFAULT 1
) ENGINE = MergeTree()
ORDER BY (lock_key, id);