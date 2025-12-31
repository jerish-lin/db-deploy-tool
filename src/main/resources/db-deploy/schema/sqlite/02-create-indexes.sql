-- SQLite Indexes for Database Deployment Tool
-- Performance indexes for audit tables

-- Indexes for db_change_log table
CREATE INDEX IF NOT EXISTS idx_script_name ON db_change_log(script_name);
CREATE INDEX IF NOT EXISTS idx_execution_status ON db_change_log(execution_status);
CREATE INDEX IF NOT EXISTS idx_execution_time ON db_change_log(execution_time);
CREATE INDEX IF NOT EXISTS idx_tag_name ON db_change_log(tag_name);
CREATE INDEX IF NOT EXISTS idx_created_at ON db_change_log(created_at);

-- Indexes for deployment_tags table
CREATE INDEX IF NOT EXISTS idx_deployment_tags_tag_name ON deployment_tags(tag_name);
CREATE INDEX IF NOT EXISTS idx_deployment_time ON deployment_tags(deployment_time);
CREATE INDEX IF NOT EXISTS idx_is_active ON deployment_tags(is_active);

-- Indexes for database_lock table
CREATE INDEX IF NOT EXISTS idx_lock_key ON database_lock(lock_key);
CREATE INDEX IF NOT EXISTS idx_lock_expires_at ON database_lock(lock_expires_at);
CREATE INDEX IF NOT EXISTS idx_lock_is_active ON database_lock(is_active);