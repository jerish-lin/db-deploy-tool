-- ClickHouse doesn't need explicit indexes for MergeTree engines
-- The ORDER BY clause in table creation serves as the primary sorting/indexing mechanism
-- Additional materialized indexes can be created if needed for specific query patterns

-- Example of creating a materialized index for script_name (if needed for performance)
-- ALTER TABLE db_change_log ADD INDEX idx_script_name script_name TYPE minmax GRANULARITY 1;

-- Example of creating a materialized index for execution_status (if needed for performance)
-- ALTER TABLE db_change_log ADD INDEX idx_execution_status execution_status TYPE set(100) GRANULARITY 1;

-- Note: ClickHouse MergeTree tables are optimized for analytical queries
-- The primary ORDER BY clause provides efficient range scans and filtering