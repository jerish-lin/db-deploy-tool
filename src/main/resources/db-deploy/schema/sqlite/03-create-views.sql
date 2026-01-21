-- SQLite Views for SchemaFlow (Refactored)
-- Supporting views for deployment state and script execution history

-- Script Execution History View - joins script metadata with audit history
CREATE VIEW IF NOT EXISTS script_execution_history AS
SELECT
    ca.id AS audit_id,
    cs.script_name,
    cs.script_checksum,
    ca.execution_status,
    ca.execution_time,
    ca.execution_duration_ms,
    ca.error_message,
    ca.created_at AS audit_created_at
FROM schemaflow_changelog_audit ca
INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
ORDER BY ca.execution_time DESC;

-- Failed Scripts View
CREATE VIEW IF NOT EXISTS failed_scripts AS
SELECT
    cs.script_name,
    cs.script_checksum,
    ca.execution_time,
    ca.error_message,
    ca.created_at AS audit_created_at
FROM schemaflow_changelog_audit ca
INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
WHERE ca.execution_status = 'FAILED'
ORDER BY ca.execution_time DESC;

-- Rollback Scripts View
CREATE VIEW IF NOT EXISTS rollback_scripts AS
SELECT
    cs.script_name,
    cs.script_checksum,
    ca.execution_time,
    cs.rollback_script_content,
    cs.rollback_verify_script_content,
    ca.created_at AS audit_created_at
FROM schemaflow_changelog_audit ca
INNER JOIN schemaflow_changelog_script cs ON ca.script_id = cs.id
WHERE ca.execution_status = 'ROLLED_BACK'
ORDER BY ca.execution_time DESC;

-- Latest Script Status View - shows the most recent execution status for each script
CREATE VIEW IF NOT EXISTS latest_script_status AS
SELECT
    cs.script_name,
    cs.script_checksum,
    cs.apply_script_content,
    cs.rollback_script_content,
    cs.apply_verify_script_content,
    cs.rollback_verify_script_content,
    ca.execution_status,
    ca.execution_time AS last_execution_time,
    ca.execution_duration_ms,
    ca.error_message,
    ca.id AS latest_audit_id
FROM schemaflow_changelog_script cs
LEFT JOIN schemaflow_changelog_audit ca ON cs.id = ca.script_id
WHERE ca.id = (
    SELECT MAX(ca2.id)
    FROM schemaflow_changelog_audit ca2
    WHERE ca2.script_id = cs.id
)
ORDER BY cs.script_name;

-- Scripts with Rollback History View
CREATE VIEW IF NOT EXISTS scripts_with_rollback_history AS
SELECT
    cs.script_name,
    cs.script_checksum,
    COUNT(CASE WHEN ca.execution_status = 'ROLLED_BACK' THEN 1 END) AS rollback_count,
    MAX(CASE WHEN ca.execution_status = 'ROLLED_BACK' THEN ca.execution_time END) AS last_rollback_time
FROM schemaflow_changelog_script cs
LEFT JOIN schemaflow_changelog_audit ca ON cs.id = ca.script_id
GROUP BY cs.id
HAVING rollback_count > 0
ORDER BY cs.script_name;