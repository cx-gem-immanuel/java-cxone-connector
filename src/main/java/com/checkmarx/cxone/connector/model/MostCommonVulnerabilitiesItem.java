package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One entry of the {@code mostCommonVulnerabilities} KPI's response
 * ({@code MostCommonVulnerabilitiesDistribution}), which is a JSON array at
 * the top level, e.g.:
 * <pre>
 *   {"vulnerabilityName": "SQL Injection", "total": 12, "severities": [{"label": "Critical", "results": 4}, ...]}
 * </pre>
 * Requires {@code limit} (1-100) to be set on the {@link AnalyticsQuery}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MostCommonVulnerabilitiesItem {

    private String vulnerabilityName;
    private Integer total;
    private List<LabeledResult> severities;

    public String getVulnerabilityName() {
        return vulnerabilityName;
    }

    public void setVulnerabilityName(String vulnerabilityName) {
        this.vulnerabilityName = vulnerabilityName;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<LabeledResult> getSeverities() {
        return severities;
    }

    public void setSeverities(List<LabeledResult> severities) {
        this.severities = severities;
    }
}
