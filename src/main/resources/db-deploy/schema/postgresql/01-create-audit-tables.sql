-- PostgreSQL Schema for SchemaFlow (Refactored)
-- Separated script metadata from audit history

-- Create schemaflow_changelog_script table - stores script metadata (immutable)
CREATE TABLE IF NOT EXISTS schemaflow_changelog_script (
    id BIGSERIAL PRIMARY KEY,
    script_name VARCHAR(500) NOT NULL UNIQUE,
    script_checksum VARCHAR(64) NOT NULL,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create schemaflow_changelog_audit table - stores execution history (append-only)
CREATE TABLE IF NOT EXISTS schemaflow_changelog_audit (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL,
    execution_status VARCHAR(20) NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms BIGINT,
    error_message TEXT,
    target_nodes TEXT[],
    node_execution_details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_script FOREIGN KEY (script_id) REFERENCES schemaflow_changelog_script(id) ON DELETE CASCADE
);

-- Create schemaflow_deploy_lock table
CREATE TABLE IF NOT EXISTS schemaflow_deploy_lock (
    id BIGSERIAL PRIMARY KEY,
    lock_key VARCHAR(255) NOT NULL UNIQUE,
    lock_owner VARCHAR(255),
    lock_acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);