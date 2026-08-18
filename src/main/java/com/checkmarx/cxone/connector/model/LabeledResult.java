package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A simple {@code {"label": ..., "results": ...}} pair, e.g. one severity
 * breakdown inside a {@link SeverityAndStateItem} or a
 * {@link MostCommonVulnerabilitiesItem}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabeledResult {

    private String label;
    private Integer results;

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

    @Override
    public String toString() {
        return label + "=" + results;
    }
}
