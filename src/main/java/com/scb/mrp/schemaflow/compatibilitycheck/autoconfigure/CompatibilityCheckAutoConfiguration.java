package com.scb.mrp.schemaflow.compatibilitycheck.autoconfigure;

import com.scb.mrp.schemaflow.compatibilitycheck.service.CompatibilityCheckService;
import com.scb.mrp.schemaflow.dbdeploy.changelog.ChangeLogManager;
import com.scb.mrp.schemaflow.dbdeploy.config.ChangeLogPathConfig;
import com.scb.mrp.schemaflow.dbdeploy.config.DatabaseConnectionConfig;
import com.scb.mrp.schemaflow.dbdeploy.config.DatasourceConfiguration;
import com.scb.mrp.schemaflow.dbdeploy.repository.AuditRepository;
import com.scb.mrp.schemaflow.dbdeploy.schema.ClickHouseSchemaInitializationStrategy;
import com.scb.mrp.schemaflow.dbdeploy.schema.PostgreSqlSchemaInitializationStrategy;
import com.scb.mrp.schemaflow.dbdeploy.schema.SQLiteSchemaInitializationStrategy;
import com.scb.mrp.schemaflow.dbdeploy.schema.SchemaInitializationManager;
import com.scb.mrp.schemaflow.dbdeploy.script.FileReader;
import com.scb.mrp.schemaflow.dbdeploy.script.ScriptParameterHandler;
import com.scb.mrp.schemaflow.dbdeploy.service.DatabaseStatusService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        ChangeLogPathConfig.class,
        DatabaseConnectionConfig.class,
        DatasourceConfiguration.class,

        FileReader.class,
        ScriptParameterHandler.class,

        AuditRepository.class,
        ChangeLogManager.class,
        DatabaseStatusService.class,
        CompatibilityCheckService.class,

        PostgreSqlSchemaInitializationStrategy.class,
        SQLiteSchemaInitializationStrategy.class,
        ClickHouseSchemaInitializationStrategy.class,
        SchemaInitializationManager.class

})
public class CompatibilityCheckAutoConfiguration {
}