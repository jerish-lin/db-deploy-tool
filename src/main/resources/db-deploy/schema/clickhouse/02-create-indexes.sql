-- ClickHouse Indexes for SchemaFlow (Refactored)
-- Note: ClickHouse uses ORDER BY and PRIMARY KEY for indexing
-- This file contains additional optimizations for status queries

-- Materialized view for latest script status (optimizes getScriptSummary)
CREATE MATERIALIZED VIEW IF NOT EXISTS latest_script_status_mv
ENGINE = AggregatingMergeTree()
ORDER BY (script_id)
AS SELECT
    script_id,
    argMax(execution_status, execution_time) AS latest_status,
    max(execution_time) AS last_execution_time
FROM schemaflow_changelog_audit
GROUP BY script_id;

-- Note: Primary indexes are defined in table creation:
-- - schemaflow_changelog_script: ORDER BY (id)
-- - schemaflow_changelog_audit: ORDER BY (script_id, execution_time)
-- - schemaflow_deploy_lock: ORDER BY (lock_key, lock_acquired_at)

-- These ORDER BY clauses provide efficient access for:
-- - getScriptSummary: Uses (script_id, execution_time) ordering
-- - getRecentAuditHistory: Uses (execution_time) ordering
-- - Lock queries: Uses (lock_key, lock_acquired_at) ordering