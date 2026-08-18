package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body of {@code GET /api/scan-summary/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanSummaryResponse {

    private List<ScanSummary> scansSummaries;
    private int totalCount;

    public List<ScanSummary> getScansSummaries() {
        return scansSummaries;
    }

    public void setScansSummaries(List<ScanSummary> scansSummaries) {
        this.scansSummaries = scansSummaries;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
