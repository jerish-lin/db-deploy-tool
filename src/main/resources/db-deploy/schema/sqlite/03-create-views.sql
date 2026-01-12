-- SQLite Views for Database Deployment Tool
-- Supporting views for deployment state and script execution history

-- Script Execution History View
CREATE VIEW IF NOT EXISTS script_execution_history AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_status,
    dcl.execution_time,
    dcl.execution_duration_ms,
    dcl.error_message,
    dcl.created_at,
    dcl.updated_at
FROM db_change_log dcl
ORDER BY dcl.execution_time DESC;

-- Failed Scripts View
CREATE VIEW IF NOT EXISTS failed_scripts AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_time,
    dcl.error_message,
    dcl.created_at
FROM db_change_log dcl
WHERE dcl.execution_status = 'FAILED'
ORDER BY dcl.execution_time DESC;

-- Rollback Scripts View
CREATE VIEW IF NOT EXISTS rollback_scripts AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_time,
    dcl.rollback_script_content,
    dcl.rollback_verify_script_content,
    dcl.created_at
FROM db_change_log dcl
WHERE dcl.execution_status = 'ROLLED_BACK'
ORDER BY dcl.execution_time DESC;