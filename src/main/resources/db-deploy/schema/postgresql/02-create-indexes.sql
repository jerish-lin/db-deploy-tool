-- PostgreSQL Indexes for SchemaFlow

-- Primary index for script status queries (getScriptSummary)
CREATE INDEX IF NOT EXISTS idx_changelog_audit_script_time ON schemaflow_changelog_audit(script_id, execution_time DESC);

-- Index for recent audit history (getRecentAuditHistory)
CREATE INDEX IF NOT EXISTS idx_changelog_audit_execution_time ON schemaflow_changelog_audit(execution_time DESC);

-- Index for filtering by execution status
CREATE INDEX IF NOT EXISTS idx_changelog_audit_execution_status ON schemaflow_changelog_audit(execution_status);

-- Indexes for schemaflow_deploy_lock table
CREATE INDEX IF NOT EXISTS idx_deploy_lock_expires ON schemaflow_deploy_lock(lock_expires_at, is_active);