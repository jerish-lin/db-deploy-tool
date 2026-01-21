-- PostgreSQL Indexes for SchemaFlow (Refactored)

-- Indexes for schemaflow_changelog_script table
CREATE INDEX IF NOT EXISTS idx_changelog_script_name ON schemaflow_changelog_script(script_name);
CREATE INDEX IF NOT EXISTS idx_changelog_script_checksum ON schemaflow_changelog_script(script_checksum);

-- Indexes for schemaflow_changelog_audit table
CREATE INDEX IF NOT EXISTS idx_changelog_audit_script_id ON schemaflow_changelog_audit(script_id);
CREATE INDEX IF NOT EXISTS idx_changelog_audit_execution_time ON schemaflow_changelog_audit(execution_time);
CREATE INDEX IF NOT EXISTS idx_changelog_audit_execution_status ON schemaflow_changelog_audit(execution_status);
CREATE INDEX IF NOT EXISTS idx_changelog_audit_parent_audit_id ON schemaflow_changelog_audit(parent_audit_id);

-- Composite index for latest script status queries
CREATE INDEX IF NOT EXISTS idx_changelog_audit_script_time ON schemaflow_changelog_audit(script_id, execution_time DESC);

-- GIN index for target_nodes array (PostgreSQL-specific)
CREATE INDEX IF NOT EXISTS idx_changelog_audit_target_nodes ON schemaflow_changelog_audit USING GIN(target_nodes);

-- Index for schemaflow_deploy_lock table
CREATE INDEX IF NOT EXISTS idx_deploy_lock_key ON schemaflow_deploy_lock(lock_key);
CREATE INDEX IF NOT EXISTS idx_deploy_lock_expires ON schemaflow_deploy_lock(lock_expires_at, is_active);