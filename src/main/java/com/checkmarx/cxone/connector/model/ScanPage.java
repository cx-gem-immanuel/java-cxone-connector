package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body of {@code GET /api/scans/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanPage {

    private int totalCount;
    private int filteredTotalCount;
    private List<Scan> scans;

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

    public List<Scan> getScans() {
        return scans;
    }

    public void setScans(List<Scan> scans) {
        this.scans = scans;
    }
}
