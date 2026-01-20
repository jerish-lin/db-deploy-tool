-- ClickHouse Views for SchemaFlow
-- Supporting views for deployment state and script execution history

-- Script Execution History View
CREATE VIEW script_execution_history AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_status,
    dcl.execution_time,
    dcl.execution_duration_ms,
    dcl.error_message,
    dcl.created_at,
    dcl.updated_at
FROM schemaflow_change_log dcl
ORDER BY dcl.execution_time DESC;

-- Failed Scripts View
CREATE VIEW failed_scripts AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_time,
    dcl.error_message,
    dcl.created_at
FROM schemaflow_change_log dcl
WHERE dcl.execution_status = 'FAILED'
ORDER BY dcl.execution_time DESC;

-- Rollback Scripts View
CREATE VIEW rollback_scripts AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_time,
    dcl.rollback_script_content,
    dcl.rollback_verify_script_content,
    dcl.created_at
FROM schemaflow_change_log dcl
WHERE dcl.execution_status = 'ROLLED_BACK'
ORDER BY dcl.execution_time DESC;