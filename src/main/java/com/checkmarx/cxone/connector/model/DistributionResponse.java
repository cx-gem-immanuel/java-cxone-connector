package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response shape shared by three KPIs of the Data Analytics API -
 * {@code vulnerabilitiesBySeverityTotal} ({@code SeverityDistribution}),
 * {@code vulnerabilitiesByStateTotal} ({@code StateDistribution}), and
 * {@code vulnerabilitiesByStatusTotal} ({@code StatusDistribution}) - which
 * are all structurally identical: a list of labeled buckets plus overall
 * totals.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DistributionResponse {

    private List<DistributionItem> distribution;
    private Integer loc;
    private Integer total;

    public List<DistributionItem> getDistribution() {
        return distribution;
    }

    public void setDistribution(List<DistributionItem> distribution) {
        this.distribution = distribution;
    }

    /** Total lines of code scanned. */
    public Integer getLoc() {
        return loc;
    }

    public void setLoc(Integer loc) {
        this.loc = loc;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
