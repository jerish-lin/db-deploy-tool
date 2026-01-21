package org.jerish.dbdeploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "schemaflow.changelog")
@Data
public class ChangeLogPathConfig {
    private String path = "classpath:db/db-changelog.yml";
}
