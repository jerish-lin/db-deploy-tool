# Database Schema Design for Database Version Management and Deployment Tool

## Overview

This document outlines the database schema design for the Database Version Management and Deployment Tool. The schema is designed to track script execution, maintain deployment history, and support rollback functionality.

## Core Tables

### 1. Database Change Log Table

```sql
CREATE TABLE db_change_log (
    id BIGSERIAL PRIMARY KEY,
    script_id VARCHAR(255) NOT NULL,
    script_name VARCHAR(500) NOT NULL,
    script_path VARCHAR(1000) NOT NULL,
    script_checksum VARCHAR(64) NOT NULL,
    execution_status VARCHAR(20) NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms BIGINT,
    error_message TEXT,
    rollback_script_path VARCHAR(1000),
    rollback_script_content TEXT,
    tag_name VARCHAR(100),
    context VARCHAR(100),
    labels VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes for performance
    INDEX idx_script_id (script_id),
    INDEX idx_execution_status (execution_status),
    INDEX idx_execution_time (execution_time),
    INDEX idx_tag_name (tag_name),
    UNIQUE INDEX uq_script_execution (script_id, execution_time)
);
```

### 2. Deployment Tags Table

```sql
CREATE TABLE deployment_tags (
    id BIGSERIAL PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    deployment_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    build_version VARCHAR(100),
    created_by VARCHAR(100),
    environment VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_tag_name (tag_name),
    INDEX idx_deployment_time (deployment_time)
);
```

### 3. Script Execution Context Table

```sql
CREATE TABLE script_execution_context (
    id BIGSERIAL PRIMARY KEY,
    change_log_id BIGINT NOT NULL,
    context_key VARCHAR(100) NOT NULL,
    context_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (change_log_id) REFERENCES db_change_log(id) ON DELETE CASCADE,
    INDEX idx_change_log_id (change_log_id),
    INDEX idx_context_key (context_key)
);
```

### 4. Database Lock Table

```sql
CREATE TABLE database_lock (
    id BIGSERIAL PRIMARY KEY,
    lock_key VARCHAR(100) NOT NULL UNIQUE,
    lock_owner VARCHAR(255),
    lock_acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_lock_key (lock_key),
    INDEX idx_lock_expires_at (lock_expires_at)
);
```

## Supporting Views

### 1. Current Deployment State View

```sql
CREATE VIEW current_deployment_state AS
SELECT 
    dt.tag_name,
    dt.build_version,
    dt.deployment_time,
    COUNT(dcl.id) as total_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'SUCCESS' THEN 1 END) as successful_scripts,
    COUNT(CASE WHEN dcl.execution_status = 'FAILED' THEN 1 END) as failed_scripts
FROM deployment_tags dt
LEFT JOIN db_change_log dcl ON dt.tag_name = dcl.tag_name
WHERE dt.is_active = TRUE
GROUP BY dt.id, dt.tag_name, dt.build_version, dt.deployment_time
ORDER BY dt.deployment_time DESC;
```

### 2. Script Execution History View

```sql
CREATE VIEW script_execution_history AS
SELECT 
    dcl.script_id,
    dcl.script_name,
    dcl.execution_status,
    dcl.execution_time,
    dcl.execution_duration_ms,
    dcl.tag_name,
    dt.build_version,
    dcl.error_message
FROM db_change_log dcl
LEFT JOIN deployment_tags dt ON dcl.tag_name = dt.tag_name
ORDER BY dcl.execution_time DESC;
```

## Database-Specific Considerations

### PostgreSQL Implementation

```sql
-- PostgreSQL-specific optimizations
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply updated_at trigger to relevant tables
CREATE TRIGGER update_db_change_log_updated_at 
    BEFORE UPDATE ON db_change_log 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- JSONB support for labels
ALTER TABLE db_change_log 
ADD COLUMN IF NOT EXISTS labels_json JSONB;

-- GIN index for efficient JSONB queries
CREATE INDEX idx_labels_json ON db_change_log USING GIN (labels_json);
```

### ClickHouse Implementation

```sql
-- ClickHouse-specific table structures
CREATE TABLE db_change_log_clickhouse (
    id UInt64,
    script_id String,
    script_name String,
    script_path String,
    script_checksum String,
    execution_status Enum8('SUCCESS' = 1, 'FAILED' = 2, 'ROLLED_BACK' = 3),
    execution_time DateTime,
    execution_duration_ms UInt64,
    error_message String,
    rollback_script_path String,
    rollback_script_content String,
    tag_name String,
    context String,
    labels String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(execution_time)
ORDER BY (script_id, execution_time);
```

## Schema Migration Scripts

### Initial Setup Script

```sql
-- 001-create-initial-schema.apply.sql
-- This script creates the initial database schema for the deployment tool

-- Create main tables
-- (Include all CREATE TABLE statements from above)

-- Insert initial lock record
INSERT INTO database_lock (lock_key, lock_owner, lock_expires_at) 
VALUES ('db_deploy_tool_lock', 'system', CURRENT_TIMESTAMP + INTERVAL '1 hour');

-- Create default system tag
INSERT INTO deployment_tags (tag_name, description, build_version, created_by) 
VALUES ('INITIAL', 'Initial database setup', '1.0.0', 'system');
```

### Rollback Script

```sql
-- 001-create-initial-schema.rollback.sql
-- This script rolls back the initial database schema

DROP VIEW IF EXISTS script_execution_history;
DROP VIEW IF EXISTS current_deployment_state;
DROP TABLE IF EXISTS script_execution_context;
DROP TABLE IF EXISTS database_lock;
DROP TABLE IF EXISTS deployment_tags;
DROP TABLE IF EXISTS db_change_log;
```

## Performance Considerations

1. **Indexing Strategy**: All frequently queried columns are indexed for optimal performance
2. **Partitioning**: Consider partitioning the db_change_log table by execution_time for large deployments
3. **Retention Policy**: Implement a cleanup strategy for old deployment records
4. **Lock Management**: The database_lock table prevents concurrent deployments

## Security Considerations

1. **Access Control**: Implement proper database permissions for the tool's database user
2. **Audit Trail**: All operations are logged with timestamps and execution details
3. **Rollback Safety**: Rollback scripts are stored securely in the database
4. **Encryption**: Consider encrypting sensitive rollback script content

## Data Integrity

1. **Foreign Keys**: Proper foreign key constraints ensure data consistency
2. **Check Constraints**: Status fields are constrained to valid values
3. **Unique Constraints**: Prevent duplicate script executions
4. **Transaction Management**: All operations are properly transactional

## Future Extensions

1. **Multi-Database Support**: Schema can be adapted for different database systems
2. **Advanced Context Support**: Extend context system for complex deployment scenarios
3. **Integration Hooks**: Add tables for external system integration
4. **Performance Metrics**: Add tables for tracking deployment performance over time