package org.example.model;

import java.time.LocalDateTime;

public class DeploymentTag {
    private Long id;
    private String tagName;
    private String description;
    private LocalDateTime deploymentTime;
    private String buildVersion;
    private String createdBy;
    private String environment;
    private Boolean isActive;

    public DeploymentTag() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDeploymentTime() {
        return deploymentTime;
    }

    public void setDeploymentTime(LocalDateTime deploymentTime) {
        this.deploymentTime = deploymentTime;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}