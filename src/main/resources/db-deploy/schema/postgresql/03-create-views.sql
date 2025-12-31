-- PostgreSQL Views for Database Deployment Tool
-- Supporting views for deployment state and script execution history

-- Current Deployment State View
CREATE OR REPLACE VIEW current_deployment_state AS
SELECT 
    dt.tag_name,
    dt.description,
    dt.deployment_time,
    dt.created_by,
    COUNT(dcl.id) as total_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'SUCCESS' THEN 1 END) as successful_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'FAILED' THEN 1 END) as failed_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'ROLLED_BACK' THEN 1 END) as rolled_back_scripts
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = TRUE
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;

-- Script Execution History View
CREATE OR REPLACE VIEW script_execution_history AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_status,
    dcl.execution_time,
    dcl.execution_duration_ms,
    dcl.tag_name,
    dt.description as tag_description,
    dcl.error_message,
    dcl.created_at,
    dcl.updated_at
FROM db_change_log dcl
LEFT JOIN deployment_tags dt ON dcl.tag_name = dt.tag_name
ORDER BY dcl.execution_time DESC;

-- Active Deployment Tags View
CREATE OR REPLACE VIEW active_deployment_tags AS
SELECT 
    dt.id,
    dt.tag_name,
    dt.description,
    dt.deployment_time,
    dt.created_by,
    COUNT(dcl.id) as script_count,
    MAX(dcl.execution_time) as last_execution_time
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = TRUE
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;

-- Failed Scripts View
CREATE OR REPLACE VIEW failed_scripts AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_time,
    dcl.error_message,
    dcl.tag_name,
    dt.description as tag_description,
    dcl.created_at
FROM db_change_log dcl
LEFT JOIN deployment_tags dt ON dcl.tag_name = dt.tag_name
WHERE dcl.execution_status = 'FAILED'
ORDER BY dcl.execution_time DESC;

-- Rollback Scripts View
CREATE OR REPLACE VIEW rollback_scripts AS
SELECT 
    dcl.script_name,
    dcl.script_checksum,
    dcl.execution_time,
    dcl.tag_name,
    dt.description as tag_description,
    dcl.rollback_script_content,
    dcl.rollback_verify_script_content,
    dcl.created_at
FROM db_change_log dcl
LEFT JOIN deployment_tags dt ON dcl.tag_name = dt.tag_name
WHERE dcl.execution_status = 'ROLLED_BACK'
ORDER BY dcl.execution_time DESC;

-- Deployment Summary View
CREATE OR REPLACE VIEW deployment_summary AS
SELECT 
    dt.tag_name,
    dt.description,
    dt.deployment_time,
    dt.created_by,
    COUNT(dcl.id) as total_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'SUCCESS' THEN 1 END) as successful_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'FAILED' THEN 1 END) as failed_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'ROLLED_BACK' THEN 1 END) as rolled_back_scripts,
    ROUND(AVG(dcl.execution_duration_ms), 2) as avg_execution_duration_ms,
    MAX(dcl.execution_time) as last_execution_time,
    CASE 
        WHEN COUNT(CASE WHEN dcl.execution_status = 'FAILED' THEN 1 END) > 0 THEN 'FAILED'
        WHEN COUNT(CASE WHEN dcl.execution_status = 'ROLLED_BACK' THEN 1 END) > 0 THEN 'PARTIALLY_ROLLED_BACK'
        ELSE 'SUCCESS'
    END as overall_status
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = TRUE
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;