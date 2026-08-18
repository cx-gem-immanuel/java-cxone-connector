package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single entry of {@code GET /api/scan-summary/}'s {@code scansSummaries} array.
 *
 * <p>The full response schema is very large (per-engine counters for SAST,
 * KICS, SCA, containers, API security, etc., each broken down by severity /
 * status / state / age / ...). Rather than hand-modeling every nested
 * counter object, this class captures {@code scanId}/{@code tenantId}
 * explicitly and collects every other top-level key (the {@code *Counters}
 * blocks) into {@link #additionalCounters} as raw parsed JSON
 * ({@code Map}/{@code List}/primitive values), so callers can still get to
 * any counter they need, e.g.:
 * <pre>
 *   Object sast = summary.getAdditionalCounters().get("sastCounters");
 * </pre>
 * Add a typed field (and remove it from the catch-all) if you need strong
 * typing for a specific counters block.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanSummary {

    private String scanId;
    private String tenantId;

    private final Map<String, Object> additionalCounters = new LinkedHashMap<>();

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @JsonAnySetter
    public void setAdditionalCounter(String name, Object value) {
        additionalCounters.put(name, value);
    }

    public Map<String, Object> getAdditionalCounters() {
        return additionalCounters;
    }

    @Override
    public String toString() {
        return "ScanSummary{scanId='" + scanId + "', counters=" + additionalCounters.keySet() + "}";
    }
}
