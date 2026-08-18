package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One entry of the {@code vulnerabilitiesBySeverityAndStateTotal} KPI's
 * response ({@code SeverityAndStateDistribution}), which is a JSON array at
 * the top level (one entry per result state, plus a trailing "Totals" entry),
 * each broken down by severity, e.g.:
 * <pre>
 *   {"label": "To Verify", "results": 676, "severities": [{"label": "Critical", "results": 56}, ...]}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeverityAndStateItem {

    private String label;
    private Integer results;
    private List<LabeledResult> severities;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getResults() {
        return results;
    }

    public void setResults(Integer results) {
        this.results = results;
    }

    public List<LabeledResult> getSeverities() {
        return severities;
    }

    public void setSeverities(List<LabeledResult> severities) {
        this.severities = severities;
    }
}
