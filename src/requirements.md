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

- **Deployment Status Monitoring**: Check if any deployment operations are currently running:
  - Query database_lock table for active deployment locks
  - Display lock owner, acquisition time, and expiration details
  - Show estimated remaining time if available
  - Provide option to force release stale locks if necessary

- **Complete Deployment History**: Provide comprehensive deployment audit trail:
  - Chronological list of all deployment operations with timestamps
  - Show deployment tags, script counts, success/failure status
  - Display execution duration and performance metrics
  - Include rollback operations with reasons and affected scripts
  - Filter by date range, tag, or status for focused analysis

- **Current State Analysis**: Detailed snapshot of database current state:
  - Active deployment tag and its creation timestamp
  - Count of scripts by status (SUCCESS, FAILED, ROLLED_BACK)
  - Last successful deployment timestamp and tag
  - Last failed deployment with error details
  - Database schema version information

- **Changelog and Script Status**: Track script execution and changelog state:
  - Last applied changelog file path and checksum
  - List of pending scripts (not yet executed)
  - Scripts with execution failures and error messages
  - Scripts that have been rolled back with rollback timestamps
  - Verification script execution status and results

- **Database Connection and Health**: System health and connectivity information:
  - Database connection status and response time
  - Connection pool statistics (active, idle, max connections)
  - Database version and configuration details
  - Available disk space and memory usage
  - Audit tables integrity check

- **Performance and Metrics**: Deployment performance analytics:
  - Average script execution time by category
  - Historical deployment success rate
  - Longest running scripts and optimization suggestions
  - Database lock contention statistics
  - Resource utilization trends during deployments

### 8. Safety and Prevention Mechanisms
  - **Concurrent Deployment Prevention**: 
    - Detect active deployment operations via database locks
    - Automatically block new deploy attempts when deployment is running
    - Return clear error message with lock owner and estimated wait time
    - Provide option to queue deploy request or force-cancel stale locks
  
  - **Application Startup Protection**:
    - Check deployment status before allowing application to start
    - Prevent application startup when deployment is in progress
    - Validate database state consistency before application initialization
    - Provide safe mode bypass for emergency situations with proper warnings
  
  - **Failed Deployment Recovery**:
    - Detect and prevent new deployments when last changelog execution failed
    - Require explicit resolution of failed deployment before proceeding
    - Provide detailed failure information and resolution guidance
    - Support force-deploy with confirmation for emergency recovery scenarios
  
  - **Version Compatibility Validation**:
    - Compare application version with last deployed changelog version
    - Prevent application startup when app version < last changelog version
    - Ensure forward compatibility between application code and database schema
    - Provide version downgrade warnings and rollback recommendations
  
  - **Database State Integrity Checks**:
    - Verify audit table consistency before allowing operations
    - Check for orphaned records or inconsistent states
    - Validate deployment tag integrity and activation status
    - Prevent operations when database state is compromised
  
  - **Configuration Consistency Validation**:
    - Ensure configuration files match expected deployment state
    - Validate changelog file integrity and accessibility
    - Check for missing or corrupted script files
    - Prevent operations when configuration is invalid

### 8. Additional Requirements

- **Logging and Reporting**: Provide detailed logs and reports for deployment and rollback activities.
- **Database Compatibility**: Ensure support for multiple database systems (e.g. PostgreSQL, Clickhouse).
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



## Preventing Conflict During Development

To prevent conflicts when multiple developers work on database scripts simultaneously, the following practices should be
adopted:

- When developer working on a new feature that requires DB changes, they should create their scripts in a separate
  branch
- Before merging, ensure that the main configuration file is updated to include the new scripts in the correct order
- Conduct code reviews to verify that no conflicting changes exist in the database scripts
- TBC...

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
