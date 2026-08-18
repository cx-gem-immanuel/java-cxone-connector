package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body of {@code GET /api/projects/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectPage {

    private int totalCount;
    private int filteredTotalCount;
    private List<Project> projects;

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getFilteredTotalCount() {
        return filteredTotalCount;
    }

    public void setFilteredTotalCount(int filteredTotalCount) {
        this.filteredTotalCount = filteredTotalCount;
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }
}
