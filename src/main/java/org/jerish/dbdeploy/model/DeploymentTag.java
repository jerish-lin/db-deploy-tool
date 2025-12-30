package org.jerish.dbdeploy.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DeploymentTag {
    private Long id;
    private String tagName;
    private String description;
    private LocalDateTime deploymentTime;
    private String createdBy;
    private Boolean isActive;
}