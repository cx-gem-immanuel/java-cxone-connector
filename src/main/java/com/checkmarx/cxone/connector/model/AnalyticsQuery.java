package com.checkmarx.cxone.connector.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Fluent builder for the request body of
 * {@code POST /api/data_analytics/analyticsAPI/v1} (the "Data Analytics"
 * / KPI query API).
 *
 * <p>Unlike a typed request POJO, this class is just a thin, fluent
 * wrapper around a Jackson {@link ObjectNode} - i.e. a generic JSON tree,
 * built up field by field and serialized as-is via {@link #toJsonNode()}.
 * There is no fixed Java field per request property, so any field the API
 * adds later can be set with {@link #set(String, String)} /
 * {@link #setArray(String, List)} without touching this class.
 *
 * <p>Every field is optional except {@code kpi} (set automatically by the
 * typed {@code CxOneClient.get*(...)} convenience methods) - fields never
 * set are simply absent from the request body.
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
 *   JsonNode result = client.getVulnerabilitiesBySeverityTotal(query);
 * </pre>
 */
public final class AnalyticsQuery {

    private final ObjectNode node = JsonNodeFactory.instance.objectNode();

    private AnalyticsQuery() {
    }

    public static AnalyticsQuery create() {
        return new AnalyticsQuery();
    }

    /** Project IDs and/or project names to filter by. */
    public AnalyticsQuery projects(List<String> projects) {
        return setArray("projects", projects);
    }

    /** Application IDs and/or application names to filter by. */
    public AnalyticsQuery applications(List<String> applications) {
        return setArray("applications", applications);
    }

    /** Environment IDs and/or environment names to filter by. */
    public AnalyticsQuery environments(List<String> environments) {
        return setArray("environments", environments);
    }

    /** Scan engines to filter by: sast, iac, sca, dast, containers, secretdetection, repohealth, byor. */
    public AnalyticsQuery scanners(List<String> scanners) {
        return setArray("scanners", scanners);
    }

    public AnalyticsQuery applicationTags(List<String> applicationTags) {
        return setArray("applicationTags", applicationTags);
    }

    public AnalyticsQuery projectTags(List<String> projectTags) {
        return setArray("projectTags", projectTags);
    }

    public AnalyticsQuery scanTags(List<String> scanTags) {
        return setArray("scanTags", scanTags);
    }

    /** Result states to filter by: toVerify, notExploitable, proposedNotExploitable, confirmed, urgent. */
    public AnalyticsQuery states(List<String> states) {
        return setArray("states", states);
    }

    /** Severities to filter by: critical, high, medium, low, information. */
    public AnalyticsQuery severities(List<String> severities) {
        return setArray("severities", severities);
    }

    public AnalyticsQuery branchNames(List<String> branchNames) {
        return setArray("branchNames", branchNames);
    }

    /** Timezone identifier (e.g. "GMT") used to interpret startDate/endDate and format returned dates. */
    public AnalyticsQuery timezone(String timezone) {
        return set("timezone", timezone);
    }

    public AnalyticsQuery groupIds(List<String> groupIds) {
        return setArray("groupIds", groupIds);
    }

    /** Literal {@code yyyy-MM-ddTHH:mm:ss}, no timezone offset - see {@link #timezone(String)}. */
    public AnalyticsQuery startDate(String startDate) {
        return set("startDate", startDate);
    }

    /** Literal {@code yyyy-MM-ddTHH:mm:ss}, no timezone offset - see {@link #timezone(String)}. */
    public AnalyticsQuery endDate(String endDate) {
        return set("endDate", endDate);
    }

    /** Required for the mostCommonVulnerabilities (1-100), mostAgingVulnerabilities (1-100), and allVulnerabilities (1-1000) KPIs. */
    public AnalyticsQuery limit(Integer limit) {
        if (limit != null) {
            node.put("limit", limit);
        }
        return this;
    }

    /** Required for the allVulnerabilities KPI. */
    public AnalyticsQuery offset(Integer offset) {
        if (offset != null) {
            node.put("offset", offset);
        }
        return this;
    }

    /** Result statuses to filter by: NEW, RECURRENT. */
    public AnalyticsQuery status(List<String> status) {
        return setArray("status", status);
    }

    /**
     * Sets the KPI type directly. Not normally needed - the typed
     * {@code CxOneClient.get*(...)} methods set this automatically for the
     * KPI they implement; set it yourself only when calling a KPI that has
     * no dedicated typed method (see {@code CxOneClient.queryAnalyticsRaw}).
     */
    public AnalyticsQuery kpi(String kpi) {
        return set("kpi", kpi);
    }

    /** Sets an arbitrary string field not covered by a named method above. */
    public AnalyticsQuery set(String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
        return this;
    }

    /** Sets an arbitrary string-array field not covered by a named method above. */
    public AnalyticsQuery setArray(String field, List<String> values) {
        if (values != null) {
            ArrayNode array = node.putArray(field);
            values.forEach(array::add);
        }
        return this;
    }

    public String getKpi() {
        return node.path("kpi").asText(null);
    }

    public boolean hasLimit() {
        return node.has("limit");
    }

    /** The raw JSON request body (a generic JSON tree), ready to serialize as-is - no intermediate POJO involved. */
    public JsonNode toJsonNode() {
        return node;
    }
}
