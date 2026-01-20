package org.jerish.dbdeploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "schemaflow")
@Data
public class NodesConfig {
    private List<DatabaseConnectionConfig> nodes = new ArrayList<>();
}