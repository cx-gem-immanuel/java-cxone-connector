package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * A single scan record. Used for two different response shapes:
 * <ul>
 *   <li>an entry of the {@code scans} array returned by
 *       {@code GET /api/scans/} (includes {@code projectId} /
 *       {@code projectName} / {@code tags})</li>
 *   <li>a value in the project-id -> scan map returned by
 *       {@code GET /api/projects/last-scan} (no {@code projectId} /
 *       {@code projectName} / {@code tags} - those fields are simply left
 *       {@code null} for that shape)</li>
 * </ul>
 *
 * <p>Only the fields useful for listing/filtering are modeled here;
 * the deeply nested {@code statusDetails} and {@code metadata} objects are
 * intentionally left unmapped (Jackson ignores them thanks to
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}). Add fields here if
 * your use case needs them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Scan {

    private String id;
    private String status;
    private String branch;
    private String createdAt;
    private String updatedAt;
    private String projectId;
    private String projectName;
    private String userAgent;
    private String initiator;
    private Map<String, String> tags;
    private List<String> engines;
    private String sourceType;
    private String sourceOrigin;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getInitiator() {
        return initiator;
    }

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public List<String> getEngines() {
        return engines;
    }

    public void setEngines(List<String> engines) {
        this.engines = engines;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceOrigin() {
        return sourceOrigin;
    }

    public void setSourceOrigin(String sourceOrigin) {
        this.sourceOrigin = sourceOrigin;
    }

    @Override
    public String toString() {
        return "Scan{id='" + id + "', status='" + status + "', projectName='" + projectName + "'}";
    }
}
