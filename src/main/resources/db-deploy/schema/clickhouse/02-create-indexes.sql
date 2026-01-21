-- ClickHouse Indexes for SchemaFlow (Refactored)
-- Note: ClickHouse uses ORDER BY and PRIMARY KEY for indexing

-- Indexes are already defined in table creation statements via ORDER BY
-- Additional materialized views or projections can be added here if needed

-- Materialized view for latest script status (optional optimization)
CREATE MATERIALIZED VIEW IF NOT EXISTS latest_script_status_mv
ENGINE = AggregatingMergeTree()
ORDER BY (script_id)
AS SELECT
    script_id,
    argMax(execution_status, execution_time) AS latest_status,
    max(execution_time) AS last_execution_time
FROM schemaflow_changelog_audit
GROUP BY script_id;