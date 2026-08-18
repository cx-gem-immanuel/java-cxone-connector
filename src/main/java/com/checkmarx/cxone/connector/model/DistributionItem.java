package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One bucket of a {@link DistributionResponse}, e.g.
 * {@code {"label": "Critical", "density": 14.43, "percentage": 8.78, "results": 60}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DistributionItem {

    private String label;
    private Float density;
    private Float percentage;
    private Integer results;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Float getDensity() {
        return density;
    }

    public void setDensity(Float density) {
        this.density = density;
    }

    public Float getPercentage() {
        return percentage;
    }

    public void setPercentage(Float percentage) {
        this.percentage = percentage;
    }

    public Integer getResults() {
        return results;
    }

    public void setResults(Integer results) {
        this.results = results;
    }

    @Override
    public String toString() {
        return label + "=" + results + " (" + percentage + "%)";
    }
}
