package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Request body for {@code POST /api/data_analytics/analyticsAPI/v1} (the
 * "Data Analytics" / KPI query API).
 *
 * <p>Every field is optional except {@code kpi} (set automatically by the
 * typed {@code CxOneClient.get*(...)} convenience methods) - fields left
 * unset are simply omitted from the request body ({@code @JsonInclude(NON_NULL)}).
 *
 * <p>Allowed values, per the OpenAPI spec
 * ({@code n-virginia-metrics-data-analytics-api-ANALYTICS_API.yaml}):
 * <ul>
 *   <li>{@link #scanners(List)} - {@code sast}, {@code iac}, {@code sca},
 *       {@code dast}, {@code containers}, {@code secretdetection},
 *       {@code repohealth}, {@code byor}</li>
 *   <li>{@link #states(List)} - {@code toVerify}, {@code notExploitable},
 *       {@code proposedNotExploitable}, {@code confirmed}, {@code urgent}</li>
 *   <li>{@link #severities(List)} - {@code critical}, {@code high},
 *       {@code medium}, {@code low}, {@code information}</li>
 *   <li>{@link #status(List)} - {@code NEW}, {@code RECURRENT} (only used by
 *       KPIs other than {@code fixedVulnerabilitiesBySeverityOvertime} /
 *       {@code meanTimeToResolution})</li>
 *   <li>{@link #startDate(String)} / {@link #endDate(String)} - literal
 *       {@code yyyy-MM-ddTHH:mm:ss} (no timezone offset); see
 *       {@link #timezone(String)} to interpret them in a specific zone</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   AnalyticsQuery query = AnalyticsQuery.create()
 *           .startDate("2026-07-19T00:00:00")
 *           .endDate("2026-08-18T00:00:00")
 *           .severities(List.of("critical", "high"));
 *   DistributionResponse result = client.getVulnerabilitiesBySeverityTotal(query);
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsQuery {

    private List<String> projects;
    private List<String> applications;
    private List<String> environments;
    private List<String> scanners;
    private List<String> applicationTags;
    private List<String> projectTags;
    private List<String> scanTags;
    private List<String> states;
    private List<String> severities;
    private List<String> branchNames;
    private String timezone;
    private List<String> groupIds;
    private String startDate;
    private String endDate;
    private Integer limit;
    private Integer offset;
    private List<String> status;

    /** Set by {@code CxOneClient}'s typed convenience methods; set it yourself only when using a raw/untyped query. */
    private String kpi;

    public static AnalyticsQuery create() {
        return new AnalyticsQuery();
    }

    /** Project IDs and/or project names to filter by. */
    public AnalyticsQuery projects(List<String> projects) {
        this.projects = projects;
        return this;
    }

    /** Application IDs and/or application names to filter by. */
    public AnalyticsQuery applications(List<String> applications) {
        this.applications = applications;
        return this;
    }

    /** Environment IDs and/or environment names to filter by. */
    public AnalyticsQuery environments(List<String> environments) {
        this.environments = environments;
        return this;
    }

    /** Scan engines to filter by: sast, iac, sca, dast, containers, secretdetection, repohealth, byor. */
    public AnalyticsQuery scanners(List<String> scanners) {
        this.scanners = scanners;
        return this;
    }

    public AnalyticsQuery applicationTags(List<String> applicationTags) {
        this.applicationTags = applicationTags;
        return this;
    }

    public AnalyticsQuery projectTags(List<String> projectTags) {
        this.projectTags = projectTags;
        return this;
    }

    public AnalyticsQuery scanTags(List<String> scanTags) {
        this.scanTags = scanTags;
        return this;
    }

    /** Result states to filter by: toVerify, notExploitable, proposedNotExploitable, confirmed, urgent. */
    public AnalyticsQuery states(List<String> states) {
        this.states = states;
        return this;
    }

    /** Severities to filter by: critical, high, medium, low, information. */
    public AnalyticsQuery severities(List<String> severities) {
        this.severities = severities;
        return this;
    }

    public AnalyticsQuery branchNames(List<String> branchNames) {
        this.branchNames = branchNames;
        return this;
    }

    /** Timezone identifier (e.g. "GMT") used to interpret startDate/endDate and format returned dates. */
    public AnalyticsQuery timezone(String timezone) {
        this.timezone = timezone;
        return this;
    }

    public AnalyticsQuery groupIds(List<String> groupIds) {
        this.groupIds = groupIds;
        return this;
    }

    /** Literal {@code yyyy-MM-ddTHH:mm:ss}, no timezone offset - see {@link #timezone(String)}. */
    public AnalyticsQuery startDate(String startDate) {
        this.startDate = startDate;
        return this;
    }

    /** Literal {@code yyyy-MM-ddTHH:mm:ss}, no timezone offset - see {@link #timezone(String)}. */
    public AnalyticsQuery endDate(String endDate) {
        this.endDate = endDate;
        return this;
    }

    /** Required for the mostCommonVulnerabilities (1-100), mostAgingVulnerabilities (1-100), and allVulnerabilities (1-1000) KPIs. */
    public AnalyticsQuery limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    /** Required for the allVulnerabilities KPI. */
    public AnalyticsQuery offset(Integer offset) {
        this.offset = offset;
        return this;
    }

    /** Result statuses to filter by: NEW, RECURRENT. */
    public AnalyticsQuery status(List<String> status) {
        this.status = status;
        return this;
    }

    /**
     * Sets the KPI type directly. Not normally needed - the typed
     * {@code CxOneClient.get*(...)} methods set this automatically for the
     * KPI they implement; set it yourself only when calling a KPI that has
     * no dedicated typed method (see {@code CxOneClient.queryAnalyticsRaw}).
     */
    public AnalyticsQuery kpi(String kpi) {
        this.kpi = kpi;
        return this;
    }

    public List<String> getProjects() {
        return projects;
    }

    public List<String> getApplications() {
        return applications;
    }

    public List<String> getEnvironments() {
        return environments;
    }

    public List<String> getScanners() {
        return scanners;
    }

    public List<String> getApplicationTags() {
        return applicationTags;
    }

    public List<String> getProjectTags() {
        return projectTags;
    }

    public List<String> getScanTags() {
        return scanTags;
    }

    public List<String> getStates() {
        return states;
    }

    public List<String> getSeverities() {
        return severities;
    }

    public List<String> getBranchNames() {
        return branchNames;
    }

    public String getTimezone() {
        return timezone;
    }

    public List<String> getGroupIds() {
        return groupIds;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public List<String> getStatus() {
        return status;
    }

    public String getKpi() {
        return kpi;
    }
}
