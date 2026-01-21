package org.jerish.precheck.autoconfigure;

import org.jerish.dbdeploy.changelog.ChangeLogManager;
import org.jerish.dbdeploy.config.*;
import org.jerish.dbdeploy.repository.AuditRepository;
import org.jerish.dbdeploy.script.FileReader;
import org.jerish.dbdeploy.script.ScriptExecutionManager;
import org.jerish.dbdeploy.script.ScriptExecutor;
import org.jerish.dbdeploy.script.ScriptParameterHandler;
import org.jerish.dbdeploy.service.DatabaseStatusService;
import org.jerish.precheck.annotation.EnableDbDeployCheck;
import org.jerish.precheck.service.DBStatusPreCheckService;
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
        DBStatusPreCheckService.class
})
public class DbDeployCheckAutoConfiguration {
}