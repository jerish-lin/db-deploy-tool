-- SQLite Indexes for SchemaFlow (Refactored)
-- Optimized for status queries and audit history retrieval

-- Indexes for schemaflow_changelog_script table
CREATE INDEX IF NOT EXISTS idx_changelog_script_name ON schemaflow_changelog_script(script_name);
CREATE INDEX IF NOT EXISTS idx_changelog_script_checksum ON schemaflow_changelog_script(script_checksum);

-- Indexes for schemaflow_changelog_audit table
-- Primary index for script status queries (getScriptSummary)
CREATE INDEX IF NOT EXISTS idx_changelog_audit_script_time ON schemaflow_changelog_audit(script_id, execution_time DESC);

-- Index for recent audit history (getRecentAuditHistory)
CREATE INDEX IF NOT EXISTS idx_changelog_audit_execution_time ON schemaflow_changelog_audit(execution_time DESC);

-- Index for filtering by execution status
CREATE INDEX IF NOT EXISTS idx_changelog_audit_execution_status ON schemaflow_changelog_audit(execution_status);

-- Index for parent-child audit relationships (rollback tracking)
CREATE INDEX IF NOT EXISTS idx_changelog_audit_parent_audit_id ON schemaflow_changelog_audit(parent_audit_id);

-- Indexes for schemaflow_deploy_lock table
CREATE INDEX IF NOT EXISTS idx_deploy_lock_key ON schemaflow_deploy_lock(lock_key);
CREATE INDEX IF NOT EXISTS idx_deploy_lock_expires ON schemaflow_deploy_lock(lock_expires_at, is_active);