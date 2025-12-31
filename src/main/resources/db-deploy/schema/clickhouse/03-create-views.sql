-- ClickHouse Views for Database Deployment Tool
-- Supporting views for deployment state and script execution history

-- Current Deployment State View
CREATE VIEW current_deployment_state AS
SELECT 
    dt.tag_name,
    dt.description,
    dt.deployment_time,
    dt.created_by,
    count(dcl.id) as total_scripts,
    countIf(dcl.execution_status = 'SUCCESS') as successful_scripts,
    countIf(dcl.execution_status = 'FAILED') as failed_scripts,
    countIf(dcl.execution_status = 'ROLLED_BACK') as rolled_back_scripts
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = 1
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;

-- Script Execution History View
CREATE VIEW script_execution_history AS
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
CREATE VIEW active_deployment_tags AS
SELECT 
    dt.id,
    dt.tag_name,
    dt.description,
    dt.deployment_time,
    dt.created_by,
    count(dcl.id) as script_count,
    max(dcl.execution_time) as last_execution_time
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = 1
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;

-- Failed Scripts View
CREATE VIEW failed_scripts AS
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
CREATE VIEW rollback_scripts AS
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
CREATE VIEW deployment_summary AS
SELECT 
    dt.tag_name,
    dt.description,
    dt.deployment_time,
    dt.created_by,
    count(dcl.id) as total_scripts,
    countIf(dcl.execution_status = 'SUCCESS') as successful_scripts,
    countIf(dcl.execution_status = 'FAILED') as failed_scripts,
    countIf(dcl.execution_status = 'ROLLED_BACK') as rolled_back_scripts,
    round(avg(dcl.execution_duration_ms), 2) as avg_execution_duration_ms,
    max(dcl.execution_time) as last_execution_time,
    multiIf(
        countIf(dcl.execution_status = 'FAILED') > 0, 'FAILED',
        countIf(dcl.execution_status = 'ROLLED_BACK') > 0, 'PARTIALLY_ROLLED_BACK',
        'SUCCESS'
    ) as overall_status
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = 1
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;

-- Performance Metrics View
CREATE VIEW performance_metrics AS
SELECT 
    dcl.tag_name,
    dt.description as tag_description,
    dcl.script_name,
    dcl.execution_status,
    dcl.execution_duration_ms,
    dcl.execution_time,
    date_format(dcl.execution_time, '%Y-%m') as execution_month,
    row_number() OVER (PARTITION BY dcl.tag_name ORDER BY dcl.execution_duration_ms DESC) as slowest_script_rank
FROM db_change_log dcl
LEFT JOIN deployment_tags dt ON dcl.tag_name = dt.tag_name
WHERE dcl.execution_duration_ms IS NOT NULL
ORDER BY dcl.execution_time DESC;