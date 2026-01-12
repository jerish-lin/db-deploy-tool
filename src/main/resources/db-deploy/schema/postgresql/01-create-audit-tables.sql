-- PostgreSQL Schema for Database Deployment Tool
-- Audit tables for tracking script execution

-- Create db_change_log table
CREATE TABLE IF NOT EXISTS db_change_log (
    id BIGSERIAL PRIMARY KEY,
    script_name VARCHAR(500) NOT NULL,
    script_checksum VARCHAR(64) NOT NULL,
    execution_status VARCHAR(20) NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms BIGINT,
    error_message TEXT,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create database_lock table
CREATE TABLE IF NOT EXISTS database_lock (
    id BIGSERIAL PRIMARY KEY,
    lock_key VARCHAR(100) NOT NULL UNIQUE,
    lock_owner VARCHAR(255),
    lock_acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);