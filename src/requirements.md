# Requirements for Database Version Management and Deployment Tool

## Introduction

This document defines the functional requirements for a database version management and deployment tool. The tool aims
to streamline the management of database scripts, ensure smooth collaboration among developers, and provide robust
mechanisms for deployment and rollback.

---

## Functional Requirements

### 1. Library Integration and Configuration

- **JAR Distribution**: The tool should be released as a standalone JAR file that can be included as a dependency in applications
- **Dependency Integration**: Applications should be able to import the tool as a Maven/Gradle dependency and use it programmatically
- **Resource Discovery**: The tool must automatically discover and read changelog configuration files and SQL scripts from the application's classpath/resources
- **Aligned Structure**: Applications should organize their changelog config and SQL scripts in a structure aligned with the tool's expectations
- **Classpath Loading**: Support loading configuration and scripts from the application's classpath, enabling seamless integration when packaged as JAR/WAR

### 2. Administrative User Configuration

- **Admin User Support**: Configure and use a dedicated database user with DDL and DML permissions for deployment operations
- **Separation of Concerns**: The admin user should be different from the regular application database user used for runtime operations
- **Security Isolation**: Support configuring separate credentials for admin operations and regular application operations
- **Permission Management**: Ensure the admin user has sufficient privileges for schema changes, table creation/alteration, and data modifications

### 3. Script Versioning and Management

- Maintain a main configuration file listing all scripts in execution order.
- Enable developers to create database scripts for specific features or bug fixes, and add into the configuration file.
- Allow organizing scripts into subfolders based on user preferences.
- Ensure each script includes both "apply" and "rollback" versions and the related verify sql for deployment and rollback.
  - e.g. create-favorite-field-table.apply.sql and create-favorite-field-table.rollback.sql
  - create-favorite-field-table.apply.verify.sql and create-favorite-field-table.rollback.verify.sql

### 4. Database Deployment Execution

- Execute database scripts in the order specified in the configuration file.
- Automatically detect and execute pending scripts, skipping already deployed ones.
- Record deployment statuses (such as success or failure) in a dedicated database table for auditing.
- On failure:
    - Roll back the current transaction.
    - Mark the script as failed and halt further execution.
    - Raise an error.
- On success:
    - Mark the script as successful.
    - Store the associated rollback script for future use.

### 5. Tagging and Rollback Execution

- Support tagging current deployment state with specific tag (e.g., "v5.5.0", "20251027.1").
- Support rolling back to a specific previous tagged state.
- Identify scripts to roll back based on the current and target rollback states.
    - Retrieve scripts applied after the target rollback state.
    - Retrieve and execute rollback scripts in reverse order.

### 6. Smart Deploy
- **Intelligent Deployment Decision**: When provided with a target tag name and changelog path, automatically analyze the current database state to determine whether to deploy or rollback
- **State Comparison**: Compare the target tag's scripts with the current database state to identify the optimal action:
  - If target tag represents a newer state than current: execute deployment
  - If target tag represents an older state than current: execute rollback
  - If target tag matches current state: no action required
- **Automatic Action Selection**: Eliminate the need for manual deploy/rollback decision by intelligently choosing the correct operation based on state analysis
- **Changelog Validation**: Ensure the specified changelog path contains all necessary scripts to reach the target state
- **Dependency Resolution**: Automatically identify and execute any intermediate scripts required to transition from current to target state
- **Conflict Prevention**: Detect and prevent incompatible state transitions that could result in data corruption

### 7. Comprehensive Status Checking

- **Deployment Status Monitoring**: Check if deployment is running via database locks, show lock details and owner
- **Deployment History**: Show chronological deployment operations with tags, status, timestamps, and execution duration
- **Current State Analysis**: Display active deployment tag, script counts by status, last deployment info, and database version
- **Script Status**: Track last applied changelog, pending scripts, failed scripts, and rollback status
- **Database Health**: Show connection status, pool statistics, database version, and audit table integrity
- **Performance Metrics**: Display execution times, success rates, longest running scripts, and resource utilization

### 8. Safety and Prevention Mechanisms
- **Concurrent Deployment Prevention**: Block new deployments when deployment is running, show lock owner and wait time
- **Application Startup Protection**: Prevent app startup during deployment, validate database state, provide safe mode bypass
- **Failed Deployment Recovery**: Block deployments after failures, require resolution, provide failure details and guidance
- **Version Compatibility Validation**: Compare app vs database versions, prevent startup if incompatible, ensure forward compatibility
- **Database State Integrity**: Verify audit table consistency, check for orphaned records, validate tag integrity
- **Configuration Consistency**: Validate configuration files, changelog integrity, script file accessibility

### 9. Database Compatibility: 
- Ensure support for multiple database systems 
  - PostgreSQL
  - Clickhouse
  - Sqlite (for unit testing)

### 10. Adhoc requirements for clickhouse
- Adhoc requirements for ClickHouse cluster environments to support distributed SQL execution and data backfilling operations across multiple nodes.
- This is possibly trigger the tool to be designed to support running same sql to multiple nodes in clickhouse.

### 11. Existing Database Schema Integration

- **Schema Discovery**: Automatically detect and analyze existing database schema when the tool is first introduced to an application
- **Baseline Creation**: Create initial deployment tag representing the current state of the existing database schema
- **Change Gap Analysis**: Identify differences between existing schema and expected schema based on current changelog files
- **Migration Planning**: Provide recommendations for integrating existing schema with the tool's version management system
- **Historical Reconstruction**: Generate synthetic deployment history to establish proper audit trail for existing schemas
- **Compatibility Validation**: Ensure existing schema is compatible with the tool's audit tables and deployment mechanisms
- **Rollback Capability**: Establish rollback scripts for existing schema components to enable future rollback operations
- **Gradual Migration**: Support phased introduction of the tool while maintaining existing database functionality

### 12. Additional Requirements
- **Logging and Reporting**: Provide detailed logs and reports for deployment and rollback activities.
- **Conflict Prevention**: Align way of working to prevent conflicts when multiple developers work on database scripts
  simultaneously.
- **Data Migration in custom Java Code??**: Allow integration of custom Java code for complex data migrations that cannot
  be handled by SQL scripts alone.

## Script Folder Organization

To ensure consistency and maintainability, the following structure is used to organize database scripts:
These db scripts would be put and maintained in the application repository's resources folder which will be pacakged into the app's jar.

### Current Implementation (Flat Structure)

```
project-root/
--resources/
  --db/
    --db-changelog.yml # Configuration file listing all scripts in execution order
    --scripts/  # All Sqls are put under the scripts folder
      --feature-12346-create-users-table.apply.sql # Apply script for creating users table
      --feature-12346-create-users-table.rollback.sql # Rollback script for creating users table
      --feature-12346-create-users-table.apply.verify.sql # Verification script for apply
      --feature-12346-create-users-table.rollback.verify.sql # Verification script for rollback
      --feature-12347-create-products-table.apply.sql
      --feature-12347-create-products-table.rollback.sql
      --feature-12347-create-products-table.apply.verify.sql
      --feature-12347-create-products-table.rollback.verify.sql
      --... # Additional scripts
```

### Alternative sub folder Structure (Also Supported)

```
project-root/
--resources/
  --db/
    --db-changelog.yml # Configuration file listing all scripts in execution order
    --scripts/ 
        --feature-12346/
          --create-favorite-field-table.apply.sql
          --create-favorite-field-table.rollback.sql
          --create-favorite-field-table.apply.verify.sql
          --create-favorite-field-table.rollback.verify.sql
        --feature-12347/
          --update-favorite-field-table.apply.sql
          --update-favorite-field-table.rollback.sql
          --update-favorite-field-table.apply.verify.sql
          --update-favorite-field-table.rollback.verify.sql
        --... # Additional versions
```

**Script Naming Convention:**

- Feature-based naming: `feature-{number}-{description}.{type}.sql`
- Types: `apply`, `rollback`, `apply.verify`, `rollback.verify`
- Verification scripts are optional but recommended for testing

### db-changelog.yml design

```yaml
# db-changelog.yml
scripts:
  - name: feature-12346-create-users-table
  - name: feature-12347-create-products-table
  - name: feature-12348-add-user-email-index
```

```yaml
# db-changelog.yml include subfolder
scripts:
  - name: feature-12346/create-favorite-field-table
  - name: feature-12347/update-favorite-field-table
```

**Note**: The actual implementation uses a simplified structure where:

- Only the script `name` is specified in the YAML
- Script paths are automatically generated by appending suffixes:
    - Apply script: `{name}.apply.sql`
    - Rollback script: `{name}.rollback.sql`
    - Apply verification script: `{name}.apply.verify.sql`
    - Rollback verification script: `{name}.rollback.verify.sql`
    - 

## Database Configuration

## Deployment and Rollback Workflow

### Deploy Workflow

1. Parse command-line arguments and load configuration files (database-config.yml, db-changelog.yml)
2. Initialize database connection using HikariCP connection pooling
3. Ensure audit tables exist (db_change_log, deployment_tags, database_lock)
4. Acquire database lock to prevent concurrent deployments
5. Identify scripts to be applied by comparing current database state with the db-changelog.yml configuration
6. Execute each pending "apply" script in the order specified in the configuration file:
    - Begin database transaction
    - Execute the "apply" script
    - Calculate and store script checksum
    - On success:
        - Mark script as SUCCESS in db_change_log table
        - Store rollback script content in audit table
        - Record execution time and metadata
        - Commit transaction
    - On failure:
        - Roll back transaction
        - Mark script as FAILED in db_change_log table
        - Log error details
        - Release lock and halt execution
7. If tag specified, create deployment tag with specified name
8. Release database lock
9. Generate deployment summary report

### Rollback Workflow

1. Parse command-line arguments and load database configuration
2. Initialize database connection and ensure audit tables exist
3. Acquire database lock to prevent concurrent operations
4. Determine target rollback state from specified tag name
5. Validate target tag exists in deployment_tags table
6. Retrieve list of scripts applied after target rollback state from db_change_log table
7. Execute rollback scripts in reverse chronological order:
    - Begin database transaction
    - Retrieve rollback script content from audit table
    - Execute the "rollback" script
    - On success:
        - Mark script as ROLLED_BACK in db_change_log table
        - Update deployment_tags (deactivate tags rolled back)
        - Commit transaction
    - On failure:
        - Roll back transaction
        - Log error details
        - Release lock and halt execution
8. Activate target rollback tag in deployment_tags table
9. Release database lock
10. Generate rollback summary report

### Status Workflow

1. Parse command-line arguments and load database configuration
2. Initialize database connection with both application and admin users
3. **Safety Prevention Checks**:
   - Check for active deployment locks and block conflicting operations
   - Validate last deployment status and prevent operations on failed state
   - Compare application version with database version for compatibility
   - Verify database integrity and configuration consistency
   - Generate prevention warnings with resolution guidance
4. **Deployment Status Check**:
   - Query database_lock table for active deployment operations
   - Check lock validity and identify stale locks
   - Display current deployment operation status if running
5. **Current State Analysis**:
   - Retrieve active deployment tag and metadata
   - Count scripts by execution status (SUCCESS, FAILED, ROLLED_BACK)
   - Identify last successful and failed deployments
6. **Deployment History Retrieval**:
   - Query complete deployment history from audit tables
   - Calculate success rates and performance metrics
   - Identify patterns and trends in deployment operations
7. **Changelog and Script Status**:
   - Load current changelog configuration
   - Compare with executed scripts to identify pending items
   - Verify script integrity and checksum consistency
8. **System Health Assessment**:
   - Test database connectivity and response times
   - Check connection pool status and resource utilization
   - Validate audit table integrity and performance
9. **Generate Comprehensive Report**:
   - Display deployment status with lock information
   - Show current state with detailed metrics
   - Present deployment history with analytics
   - Include system health and security status
   - Provide safety check results and recommendations

## How This Tool Is Built and Used

### Building the Tool

- **Maven-based Java project** using standard Maven structure and dependency management
- **Package structure**: `org.jerish.dbdeploy` with clear separation of concerns:
    - `cli/` - Command line interface handling with Picocli
    - `config/` - YAML configuration parsing with SnakeYAML
    - `dao/` - Database access objects for audit functionality
    - `database/` - Database connection and transaction management with HikariCP
    - `model/` - Data models (ChangeLogEntry, DeploymentTag, ScriptExecutionStatus)
    - `script/` - Script execution and file management
    - `service/` - Business logic interfaces and implementations
- **Main class**: `DatabaseDeployTool.java` handles command-line parameters and orchestrates operations
- **Dependencies**:
    - Picocli for command-line parsing
    - SnakeYAML for configuration parsing
    - HikariCP for connection pooling
    - SLF4J + Logback for logging
    - JUnit 5 for testing
- **Build**: Released as an executable JAR with Maven assembly plugin
- **Testing**: Comprehensive test suite using SQLite for embedded testing

### Include the Tool in an Application

- **Maven dependency**: Include the database deployment tool as a Maven dependency in your application
- **Resource structure**: Create folder structure under `src/main/resources/db/`:
    - `db-changelog.yml` - Script execution order configuration
    - `scripts/` - SQL script files (or version-based folders)
- **Configuration**: Provide `database-config.yml` with database connection settings
- **Script development**: Write database scripts following naming conventions:
    - `{feature-name}.apply.sql` - Forward deployment scripts
    - `{feature-name}.rollback.sql` - Rollback scripts
    - Optional verification scripts for testing

### Deployment and Rollback Execution

- **Standalone execution**: Run the tool as an independent executable JAR
- **Command examples**:
  ```bash
  # Deploy with tag
  java -jar db-deploy-tool.jar -a deploy -c db-changelog.yml -t v1.0.0 -d database-config.yml
  
  # Rollback to tag
  java -jar db-deploy-tool.jar -a rollback -t v1.0.0 -d database-config.yml
  
  # Smart deploy (automatically determines deploy or rollback)
  java -jar db-deploy-tool.jar -a smart-deploy -c db-changelog.yml -t v1.0.0 -d database-config.yml
  
  # Basic status check
  java -jar db-deploy-tool.jar -a status -d database-config.yml
  
  # Comprehensive status with all details
  java -jar db-deploy-tool.jar -a status -d database-config.yml --verbose --full-history
  
  # Status with specific focus areas
  java -jar db-deploy-tool.jar -a status -d database-config.yml --check-deployment --check-health
  
  # Perform safety checks only
  java -jar db-deploy-tool.jar -a safety-check -d database-config.yml
  
  # Force deploy despite safety warnings (emergency use only)
  java -jar db-deploy-tool.jar -a deploy -c db-changelog.yml -t v1.0.0 -d database-config.yml --force
  
  # Check application startup safety
  java -jar db-deploy-tool.jar -a startup-safety -d database-config.yml --app-version 1.2.3
  ```
- **CI/CD integration**: Can be integrated into deployment pipelines for automated database migrations
- **Container support**: Can be run in Docker containers for consistent deployment environments

## Deployment and Rollback Execution Principle

### Deployment

- **Atomic operations**: Each script executes within a database transaction ensuring all-or-nothing execution
- **Audit tracking**: Comprehensive audit trail with checksums, execution times, and status tracking
- **Concurrent safety**: Database locking mechanism prevents simultaneous deployments
- **Tag-based versioning**: Semantic versioning with deployment tags for rollback points
- **Error handling**: Immediate halt on script failure with transaction rollback and detailed error logging

### Rollback

- **Tag-based rollback**: Rollback to any previously deployed tag, not just the immediate previous version
- **Reverse execution**: Rollback scripts executed in reverse chronological order
- **State management**: Tags are activated/deactivated to maintain deployment history
- **Integrity preservation**: All rollback operations are transactional with full audit tracking
- **Validation**: Target tags must exist and be valid deployment points

### Additional Features

- **Status reporting**: Comprehensive deployment status with script counts and current tag information
- **Dry-run mode**: Preview deployment changes without executing them
- **Verbose logging**: Detailed execution logs for troubleshooting and auditing
- **Multi-database support**: PostgreSQL, SQLite, and ClickHouse with database-specific optimizations
- **Connection pooling**: Efficient connection management with configurable pooling parameters

## Preventing Conflict During Development

To prevent conflicts when multiple developers work on database scripts simultaneously, the following practices should be
adopted:

- When developer working on a new feature that requires DB changes, they should create their scripts in a separate
  branch
- Before merging, ensure that the main configuration file is updated to include the new scripts in the correct order
- Conduct code reviews to verify that no conflicting changes exist in the database scripts
- TBC...

---

## ClickHouse Cluster Adhoc Requirements

Adhoc requirements for ClickHouse cluster environments to support distributed SQL execution and data backfilling operations across multiple nodes.

### Overview

ClickHouse cluster environments require special handling for distributed operations, particularly for data backfilling scenarios where the same SQL needs to be executed across multiple nodes in the cluster.

### Cluster Node Execution

- **Multi-Node SQL Execution**: Support running the same SQL script across multiple ClickHouse cluster nodes
- **Node Discovery**: Automatically discover available nodes in the ClickHouse cluster
- **Parallel Execution**: Execute SQL on multiple nodes concurrently for improved performance
- **Node Health Checking**: Verify node availability and connectivity before execution
- **Execution Coordination**: Coordinate execution order and dependencies across nodes

### Data Backfilling Support

- **Backfill Script Execution**: Execute data backfilling scripts on specific cluster nodes
- **Partition Awareness**: Understand and respect ClickHouse partitioning schemes during backfilling
- **Data Consistency**: Ensure data consistency across cluster nodes during backfill operations
- **Incremental Backfilling**: Support incremental data backfilling based on timestamps or other criteria
- **Rollback Support**: Provide rollback capabilities for backfilling operations

### Configuration for Cluster Operations

```yaml
db-deploy:
  clickhouse:
    cluster:
      # Cluster configuration
      name: "production_cluster"
      nodes:
        - host: "ch-node-1.example.com"
          port: 8123
          user: "admin"
          password: "${CH_NODE1_PASSWORD}"
        - host: "ch-node-2.example.com"
          port: 8123
          user: "admin"
          password: "${CH_NODE2_PASSWORD}"
        - host: "ch-node-3.example.com"
          port: 8123
          user: "admin"
          password: "${CH_NODE3_PASSWORD}"
      
      # Execution settings
      parallel-execution: true
      max-concurrent-nodes: 3
      timeout-per-node: 300000  # 5 minutes
      
      # Backfilling settings
      backfill:
        batch-size: 10000
        chunk-size: 1000
        retry-attempts: 3
        verify-consistency: true
```

### Cluster-Aware Script Execution

```yaml
# db-changelog.yml for ClickHouse cluster
scripts:
  - name: cluster-wide-schema-change
    target: "all_nodes"  # Execute on all cluster nodes
    rollback-target: "all_nodes"
  
  - name: backfill-user-data
    target: "node-1,node-2"  # Execute on specific nodes only
    rollback-target: "node-1,node-2"
    backfill:
      table: "user_events"
      date-range: "2024-01-01,2024-12-31"
      partition-column: "event_date"
```

### Execution Patterns

#### **Cluster-Wide Operations**
```sql
-- Schema changes that need to be applied to all nodes
CREATE TABLE IF NOT EXISTS user_events (
    user_id UInt64,
    event_date Date,
    event_type String,
    event_data String
) ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/user_events', '{replica}')
PARTITION BY toYYYYMM(event_date)
ORDER BY (user_id, event_date, event_type);
```

#### **Node-Specific Backfilling**
```sql
-- Data backfilling for specific nodes
INSERT INTO user_events
SELECT user_id, event_date, event_type, event_data
FROM source_events
WHERE event_date BETWEEN '2024-01-01' AND '2024-12-31'
AND cluster_node() = 'node-1';
```

### Error Handling and Recovery

- **Node Failure Handling**: Continue execution on remaining nodes if some nodes fail
- **Partial Rollback**: Support rolling back changes on subset of nodes when needed
- **Consistency Verification**: Verify data consistency across nodes after operations
- **Retry Logic**: Automatic retry for failed node executions with exponential backoff
- **Status Reporting**: Detailed reporting of execution status per node

### Monitoring and Observability

- **Per-Node Status**: Track execution status for each cluster node individually
- **Cluster Health Monitoring**: Monitor cluster health during and after operations
- **Performance Metrics**: Track execution time and resource usage per node
- **Data Consistency Metrics**: Monitor data consistency across cluster nodes
- **Alerting**: Alert on node failures, consistency issues, or performance problems

### Implementation Notes

**Note**: ClickHouse cluster operations require careful handling of distributed transactions and consistency. The deployment tool should provide cluster-aware execution capabilities while maintaining data integrity and operational safety. Specific implementation details should consider ClickHouse's distributed nature and the specific requirements of the target cluster topology.
