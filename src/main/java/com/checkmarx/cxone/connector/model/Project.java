package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A single project entry, as returned inside the {@code projects} array of
 * {@code GET /api/projects/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    private String id;
    private String name;
    private String tenantId;
    private String createdAt;
    private String updatedAt;
    private List<String> groups;
    private Map<String, String> tags;
    private String repoUrl;
    private String mainBranch;
    private String origin;
    private String scmRepoId;
    private Long repoId;
    private Integer criticality;
    private Boolean privatePackage;

    @JsonProperty("imported_proj_name")
    private String importedProjName;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getMainBranch() {
        return mainBranch;
    }

    public void setMainBranch(String mainBranch) {
        this.mainBranch = mainBranch;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getScmRepoId() {
        return scmRepoId;
    }

    public void setScmRepoId(String scmRepoId) {
        this.scmRepoId = scmRepoId;
    }

    public Long getRepoId() {
        return repoId;
    }

    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }

    public Integer getCriticality() {
        return criticality;
    }

    public void setCriticality(Integer criticality) {
        this.criticality = criticality;
    }

    public Boolean getPrivatePackage() {
        return privatePackage;
    }

    public void setPrivatePackage(Boolean privatePackage) {
        this.privatePackage = privatePackage;
    }

    public String getImportedProjName() {
        return importedProjName;
    }

    public void setImportedProjName(String importedProjName) {
        this.importedProjName = importedProjName;
    }

    @Override
    public String toString() {
        return "Project{id='" + id + "', name='" + name + "'}";
    }
}
