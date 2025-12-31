# Database Schema Design for Database Version Management and Deployment Tool

## Overview

This document outlines the database schema design for the Database Version Management and Deployment Tool. The schema is
designed to track script execution, maintain deployment history, and support rollback functionality.

## Core Tables

### 1. Database Change Log Table

#### SQLite Implementation
```sql
CREATE TABLE IF NOT EXISTS db_change_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_name TEXT NOT NULL,
    script_checksum TEXT NOT NULL,
    execution_status TEXT NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms INTEGER,
    error_message TEXT,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    tag_name TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### PostgreSQL Implementation
```sql
CREATE TABLE db_change_log (
    id BIGSERIAL PRIMARY KEY,
    script_name VARCHAR(500) NOT NULL,
    script_checksum VARCHAR(64) NOT NULL,
    execution_status VARCHAR(20) NOT NULL CHECK (execution_status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    execution_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_duration_ms BIGINT,
    error_message TEXT,
    rollback_script_content TEXT,
    rollback_verify_script_content TEXT,
    tag_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 2. Deployment Tags Table

#### SQLite Implementation
```sql
CREATE TABLE IF NOT EXISTS deployment_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_name TEXT NOT NULL UNIQUE,
    description TEXT,
    deployment_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    is_active INTEGER DEFAULT 1
);
```

#### PostgreSQL Implementation
```sql
CREATE TABLE deployment_tags (
    id BIGSERIAL PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    deployment_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE
);
```

### 3. Database Lock Table

#### SQLite Implementation
```sql
CREATE TABLE IF NOT EXISTS database_lock (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lock_key TEXT NOT NULL UNIQUE,
    lock_owner TEXT,
    lock_acquired_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TEXT,
    is_active INTEGER DEFAULT 1
);
```

#### PostgreSQL Implementation
```sql
CREATE TABLE database_lock (
    id BIGSERIAL PRIMARY KEY,
    lock_key VARCHAR(100) NOT NULL UNIQUE,
    lock_owner VARCHAR(255),
    lock_acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);
```

### Database Indexes

#### SQLite Indexes
```sql
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
```

#### PostgreSQL Indexes
```sql
-- Indexes for db_change_log table
CREATE INDEX idx_script_name ON db_change_log(script_name);
CREATE INDEX idx_execution_status ON db_change_log(execution_status);
CREATE INDEX idx_execution_time ON db_change_log(execution_time);
CREATE INDEX idx_tag_name ON db_change_log(tag_name);
CREATE INDEX idx_created_at ON db_change_log(created_at);

-- Indexes for deployment_tags table
CREATE INDEX idx_deployment_tags_tag_name ON deployment_tags(tag_name);
CREATE INDEX idx_deployment_time ON deployment_tags(deployment_time);
CREATE INDEX idx_is_active ON deployment_tags(is_active);

-- Indexes for database_lock table
CREATE INDEX idx_lock_key ON database_lock(lock_key);
CREATE INDEX idx_lock_expires_at ON database_lock(lock_expires_at);
CREATE INDEX idx_lock_is_active ON database_lock(is_active);
```

## Supporting Views

### 1. Current Deployment State View

```sql
-- SQLite Implementation
CREATE VIEW IF NOT EXISTS current_deployment_state AS
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
WHERE dt.is_active = 1
GROUP BY dt.id, dt.tag_name, dt.description, dt.deployment_time, dt.created_by
ORDER BY dt.deployment_time DESC;
```

```sql
-- PostgreSQL Implementation
CREATE VIEW current_deployment_state AS
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
```

### 2. Script Execution History View

```sql
-- SQLite Implementation
CREATE VIEW IF NOT EXISTS script_execution_history AS
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
```

```sql
-- PostgreSQL Implementation
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
```

## Database-Specific Considerations

### SQLite Implementation
- **Data Types**: Uses `TEXT` for timestamps, `INTEGER` for auto-increment IDs and booleans
- **Timestamp Handling**: Stores timestamps as text strings in ISO format
- **Boolean Handling**: Uses `INTEGER` with 0/1 values for boolean fields
- **File-based**: Single file database with WAL mode for better concurrency
- **Connection Pooling**: Best with single connection pool due to file locking
- **Schema Files**: Located in `src/main/resources/db-deploy/schema/sqlite/`

### PostgreSQL Implementation
- **Data Types**: Uses `BIGSERIAL` for auto-increment IDs, `TIMESTAMP` for dates, `BOOLEAN` for booleans
- **Timestamp Handling**: Native timestamp support with timezone awareness
- **Performance**: Supports true connection pooling with multiple connections
- **Advanced Features**: Can use triggers for automatic timestamp updates
- **Schema Files**: Located in `src/main/resources/db-deploy/schema/postgresql/`

### ClickHouse Implementation
```sql
-- ClickHouse-specific table structures
CREATE TABLE db_change_log (
    id UInt64,
    script_name String,
    script_checksum String,
    execution_status Enum8('SUCCESS' = 1, 'FAILED' = 2, 'ROLLED_BACK' = 3),
    execution_time DateTime,
    execution_duration_ms UInt64,
    error_message String,
    rollback_script_content String,
    rollback_verify_script_content String,
    tag_name String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(execution_time)
ORDER BY (script_name, execution_time);
```
- **Schema Files**: Located in `src/main/resources/db-deploy/schema/clickhouse/`

### Connection Pool Configuration
- **SQLite**: Single connection pool (`max-pool-size: 1`) with WAL mode
- **PostgreSQL**: Multiple connections (`max-pool-size: 10-20`) for optimal performance
- **ClickHouse**: Optimized for analytical workloads with larger batch sizes

## Schema Implementation

### File Structure
```
src/main/resources/db-deploy/schema/
├── sqlite/
│   ├── 01-create-audit-tables.sql
│   └── 02-create-indexes.sql
├── postgresql/
│   ├── 01-create-audit-tables.sql
│   └── 02-create-indexes.sql
└── clickhouse/
    ├── 01-create-audit-tables.sql
    └── 02-create-indexes.sql
```

### Schema Initialization Strategy
The tool uses a strategy pattern for database-specific schema initialization:

- **SQLiteSchemaInitializationStrategy**: Handles SQLite-specific schema with path `db-deploy/schema/sqlite`
- **PostgreSqlSchemaInitializationStrategy**: Handles PostgreSQL-specific schema with path `db-deploy/schema/postgresql`
- **ClickHouseSchemaInitializationStrategy**: Handles ClickHouse-specific schema with path `db-deploy/schema/clickhouse`

### Application Integration
- **SchemaInitializationManager**: Orchestrates schema initialization
- **AuditRepository**: Handles all database operations for audit tables using JdbcTemplate
- **Database Detection**: Automatic detection of database type for appropriate schema

### Key Features
1. **Multi-Database Support**: Same logical schema adapted for each database type
2. **Automatic Initialization**: Schema created on first application startup
3. **Idempotent Operations**: Uses `IF NOT EXISTS` for safe re-initialization
4. **Performance Optimized**: Database-specific indexing strategies
5. **Connection Pool Management**: Database-specific HikariCP configurations

## Performance Considerations

1. **Indexing Strategy**: All frequently queried columns are indexed for optimal performance
2. **Connection Pooling**: Database-specific pooling strategies (SQLite: single connection, PostgreSQL: multiple connections)
3. **Query Optimization**: Uses JdbcTemplate for efficient query execution and parameter binding
4. **Lock Management**: The database_lock table prevents concurrent deployments with expiration handling
5. **Retention Policy**: Consider implementing cleanup strategies for old deployment records

## Security Considerations

1. **Access Control**: Implement proper database permissions for the tool's database user
2. **Audit Trail**: All operations are logged with timestamps, execution details, and checksums
3. **Rollback Safety**: Rollback scripts stored securely in the database with verification support
4. **Connection Security**: Uses HikariCP with secure connection configuration
5. **Script Integrity**: Checksum validation ensures script content integrity

## Data Integrity

1. **Check Constraints**: Status fields constrained to valid values ('SUCCESS', 'FAILED', 'ROLLED_BACK')
2. **Unique Constraints**: Prevent duplicate tag names in deployment_tags
3. **Transaction Management**: All operations wrapped in Spring transactions
4. **Schema Validation**: Automatic schema initialization ensures required tables exist
5. **Rollback Verification**: Optional verification scripts for rollback validation

## Application Architecture

### Core Components
- **DatabaseDeployTool**: Main Spring Boot application
- **AuditRepository**: Data access layer for audit operations using JdbcTemplate
- **SchemaInitializationManager**: Manages multi-database schema creation
- **DatasourceConfiguration**: Database connection and pool configuration with SQLite optimizations

### Testing Strategy
- **SQLiteDeployTestBase**: Base class for SQLite integration tests with jdbcTemplate
- **SQLiteRollbackTest**: Comprehensive rollback testing
- **SQLiteDeploymentTest**: Deployment functionality testing
- **SQLiteStatusTest**: Status reporting verification

## Future Extensions

1. **Enhanced Monitoring**: Add performance metrics and deployment analytics
2. **Multi-Environment Support**: Environment-specific configuration management
3. **Script Dependencies**: Support for script dependency management
4. **Parallel Execution**: Safe parallel script execution where supported
5. **API Integration**: REST API for external system integration