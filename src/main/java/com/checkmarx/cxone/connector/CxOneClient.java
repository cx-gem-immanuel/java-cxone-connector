package com.checkmarx.cxone.connector;

import com.checkmarx.cxone.connector.model.AnalyticsQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connector for the Checkmarx One (CxOne) REST API, built on Apache
 * HttpClient.
 *
 * <p>Handles OAuth2 authentication (exchanging the configured API key for a
 * bearer token) against the IAM host, and exposes read-only helpers for
 * pulling data from Checkmarx One.
 *
 * <p><b>Responses are returned as generic parsed JSON
 * ({@link JsonNode}/{@code List<JsonNode>}/{@code Map<String, JsonNode>}),
 * not typed model classes.</b> Every response shape here is either large,
 * deeply nested, or both (scan summaries alone have ~20 nested counter
 * shapes; the Analytics API has ~13 different KPI response shapes) - hand-
 * modeling a POJO per shape would mean a lot of classes to maintain and
 * update whenever the API adds a field. Reading the response as a JSON tree
 * and pulling out the fields you actually need with
 * {@code node.path("fieldName")} avoids that, at the cost of losing
 * compile-time checking of field names/types (a typo in a path is only
 * caught at runtime). See {@link ScanSummaryPrinter} and {@code App} for
 * examples of walking/extracting from the returned nodes.
 *
 * <ul>
 *   <li>{@link #getAllProjects()} - all projects (auto-paged)</li>
 *   <li>{@link #listScans(List)} - a generic, chronologically-ordered scan
 *       listing across all projects, filterable by status (auto-paged)</li>
 *   <li>{@link #getLatestScans()} - the single latest scan for every
 *       project in the tenant (auto-paged)</li>
 *   <li>{@link #getScanSummaries(List)} - scan-summary counters for a set
 *       of scan IDs</li>
 *   <li>{@link #getVulnerabilitiesBySeverityTotal(AnalyticsQuery)} and
 *       friends - KPI queries against the Data Analytics API</li>
 * </ul>
 *
 * <p>Instances are thread-safe for concurrent GETs; the bearer token is
 * refreshed lazily and cached until shortly before expiry.
 */
public class CxOneClient implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(CxOneClient.class.getName());

    /** Public client id used when exchanging the API key for a bearer token. */
    private static final String CLIENT_ID = "ast-app";

    /** Refresh the token this many seconds before it actually expires. */
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 300;

    private final CxOneConfig config;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String bearerToken;
    private volatile Instant tokenExpiration;

    public CxOneClient(CxOneConfig config) {
        this.config = config;
        this.httpClient = buildHttpClient(config);
    }

    private static CloseableHttpClient buildHttpClient(CxOneConfig config) {
        HttpClientBuilder builder = HttpClients.custom();
        if (!config.isVerifySsl()) {
            try {
                SSLContext sslContext = SSLContextBuilder.create()
                        .loadTrustMaterial(null, (chain, authType) -> true)
                        .build();
                builder.setSSLContext(sslContext)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                LOGGER.warning("CxOne client: TLS certificate verification is disabled (insecure mode).");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure insecure SSL context", e);
            }
        }
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

    /**
     * Returns a cached bearer token, exchanging the configured API key for a
     * new one via the IAM token endpoint if it is missing or about to expire.
     *
     * <p>{@code POST /auth/realms/{tenant}/protocol/openid-connect/token}.
     * Note: CxOne's IAM token endpoint requires the API key to be submitted
     * using the standard OAuth2 {@code refresh_token} grant parameter name -
     * this is simply the wire-protocol field name mandated by the endpoint,
     * not a reflection of what the API key conceptually is.
     */
    private synchronized String getBearerToken() throws IOException {
        if (bearerToken != null && tokenExpiration != null && Instant.now().isBefore(tokenExpiration)) {
            return bearerToken;
        }

        String url = config.getIamHost() + "/auth/realms/" + config.getTenant()
                + "/protocol/openid-connect/token";
        HttpPost post = new HttpPost(url);

        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("grant_type", "refresh_token"));
        form.add(new BasicNameValuePair("client_id", CLIENT_ID));
        form.add(new BasicNameValuePair("refresh_token", config.getApiKey()));
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status != 200) {
                throw new IOException("CxOne authentication failed [" + status + "]: " + body);
            }

            JsonNode json = objectMapper.readTree(body);
            long expiresIn = json.path("expires_in").asLong(3600);
            bearerToken = json.path("access_token").asText();
            tokenExpiration = Instant.now().plusSeconds(Math.max(expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS, 60));
            LOGGER.log(Level.FINE, "Obtained new bearer token (expires in {0}s)", expiresIn);
            return bearerToken;
        }
    }

    // ------------------------------------------------------------------
    // Projects: GET /api/projects/
    // ------------------------------------------------------------------

    /**
     * Fetch a single page of projects as the raw response JSON, e.g.
     * {@code {"totalCount": 167, "filteredTotalCount": 167, "projects": [...]}}.
     * Individual projects are the elements of the {@code "projects"} array,
     * each shaped like
     * {@code {"id": "...", "name": "...", "tenantId": "...", "tags": {...}, ...}}.
     *
     * @param limit  max records to return; 0 returns all projects in one call
     * @param offset number of results to skip before returning results (>= 0)
     */
    public JsonNode getProjectsPage(int limit, int offset) throws IOException {
        URI uri = buildUri("/api/projects/", builder -> {
            builder.addParameter("limit", String.valueOf(limit));
            builder.addParameter("offset", String.valueOf(offset));
        });
        return objectMapper.readTree(executeGet(uri));
    }

    /** Fetch all projects (as individual project JSON nodes), paging automatically using the configured default page size. */
    public List<JsonNode> getAllProjects() throws IOException {
        return getAllProjects(config.getDefaultPageSize());
    }

    /** Fetch all projects (as individual project JSON nodes), paging automatically using {@code pageSize} per request. */
    public List<JsonNode> getAllProjects(int pageSize) throws IOException {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        int total = Integer.MAX_VALUE;

        while (all.size() < total) {
            JsonNode page = getProjectsPage(pageSize, offset);
            total = page.path("filteredTotalCount").asInt(0);
            JsonNode projects = page.path("projects");
            if (!projects.isArray() || projects.isEmpty()) {
                break;
            }
            projects.forEach(all::add);
            offset += projects.size();
        }

        return all;
    }

    // ------------------------------------------------------------------
    // Scan listing (generic, not per-project): GET /api/scans/
    // ------------------------------------------------------------------
    //
    // This endpoint returns a plain, chronologically-ordered (most-recent-
    // first) list of scans across the whole tenant. It can return many
    // scans per project - it does NOT return "the latest scan of each
    // project". For that, see getLatestScans() below, which wraps
    // GET /api/projects/last-scan instead.
    // ------------------------------------------------------------------

    /**
     * Fetch a single page of the generic scan listing as the raw response
     * JSON, e.g.
     * {@code {"totalCount": 22, "filteredTotalCount": 7, "scans": [...]}}.
     * Individual scans are the elements of the {@code "scans"} array, each
     * shaped like
     * {@code {"id": "...", "status": "...", "projectId": "...", "projectName": "...", ...}}.
     *
     * @param statuses statuses to filter by; case insensitive, OR'd together
     *                 server-side. Allowed values: {@code Queued}, {@code Running},
     *                 {@code Completed}, {@code Failed}, {@code Partial},
     *                 {@code Canceled}. Pass null/empty for no status filter.
     * @param limit    max records to return
     * @param offset   number of results to skip before returning results
     */
    public JsonNode getScansPage(List<String> statuses, int limit, int offset) throws IOException {
        URI uri = buildUri("/api/scans/", builder -> {
            if (statuses != null) {
                for (String status : statuses) {
                    builder.addParameter("statuses", status);
                }
            }
            builder.addParameter("limit", String.valueOf(limit));
            builder.addParameter("offset", String.valueOf(offset));
        });
        return objectMapper.readTree(executeGet(uri));
    }

    /**
     * Fetch the generic scan listing (as individual scan JSON nodes) across
     * all projects, optionally filtered by status, paging automatically
     * using the configured default page size.
     *
     * <p>{@code GET /api/scans/} returns scans ordered most-recent-first,
     * but is not restricted to one scan per project - e.g. pass
     * {@code List.of("Running", "Queued")} to pull every scan currently in
     * flight, which may include several scans for the same project. Use
     * {@link #getLatestScans()} instead if you want exactly one (the most
     * recent) scan per project.
     */
    public List<JsonNode> listScans(List<String> statuses) throws IOException {
        return listScans(statuses, config.getDefaultPageSize());
    }

    /** Same as {@link #listScans(List)} but with an explicit page size. */
    public List<JsonNode> listScans(List<String> statuses, int pageSize) throws IOException {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        int total = Integer.MAX_VALUE;

        while (all.size() < total) {
            JsonNode page = getScansPage(statuses, pageSize, offset);
            total = page.path("filteredTotalCount").asInt(0);
            JsonNode scans = page.path("scans");
            if (!scans.isArray() || scans.isEmpty()) {
                break;
            }
            scans.forEach(all::add);
            offset += scans.size();
        }

        return all;
    }

    // ------------------------------------------------------------------
    // Latest scan per project: GET /api/projects/last-scan
    // ------------------------------------------------------------------
    //
    // Unlike GET /api/scans/, this endpoint returns at most one scan per
    // project - the most recent one matching the given filters - keyed by
    // project ID, e.g.:
    //
    //   {
    //     "9228e398-90ba-4754-82a6-397c687340fa": {
    //       "id": "fc6e36d8-943f-4a0b-82db-d0c1b4a79b81",
    //       "status": "Completed",
    //       "createdAt": "2026-06-16T13:54:32.318752Z",
    //       "updatedAt": "2026-06-16T13:54:42.158399Z",
    //       "branch": "main",
    //       "engines": ["sast", "kics"],
    //       ...
    //     }
    //   }
    //
    // The response has no totalCount/filteredTotalCount wrapper, so it
    // cannot be paged the same way as /api/projects/ or /api/scans/ - see
    // getLatestScans(int) for how paging is handled here instead.
    // ------------------------------------------------------------------

    /**
     * Fetch a single page of "latest scan per project" records.
     *
     * @param projectIds    project IDs to restrict the lookup to (sent as
     *                      repeated {@code project-ids} query parameters);
     *                      null/empty returns the latest scan for every
     *                      project in the tenant, subject to
     *                      {@code limit}/{@code offset}. Mutually exclusive
     *                      with {@code applicationId} server-side.
     * @param scanStatus    a single status to filter by. Unlike
     *                      {@code GET /api/scans/}, this endpoint accepts
     *                      only one status value, not an array. Allowed
     *                      values: {@code Queued}, {@code Running},
     *                      {@code Completed}, {@code Failed}, {@code Partial},
     *                      {@code Canceled}. Pass null for no status filter.
     * @param branch        branch name to filter by, or null for no branch filter
     * @param engine        scan engine to filter by. Allowed values:
     *                      {@code sast}, {@code sca}, {@code kics},
     *                      {@code apisec}. Pass null for no engine filter.
     * @param applicationId application ID to filter by, or null. Mutually
     *                      exclusive with {@code projectIds} server-side.
     * @param limit         max number of projects to return (server default is 20)
     * @param offset        number of results to skip before returning results
     * @return map of project ID -> that project's latest scan JSON node matching the filters
     */
    public Map<String, JsonNode> getProjectsLastScanPage(List<String> projectIds, String scanStatus, String branch,
                                                          String engine, String applicationId,
                                                          int limit, int offset) throws IOException {
        URI uri = buildUri("/api/projects/last-scan", builder -> {
            if (projectIds != null) {
                for (String projectId : projectIds) {
                    builder.addParameter("project-ids", projectId);
                }
            }
            if (scanStatus != null) {
                builder.addParameter("scan-status", scanStatus);
            }
            if (branch != null) {
                builder.addParameter("branch", branch);
            }
            if (engine != null) {
                builder.addParameter("engine", engine);
            }
            if (applicationId != null) {
                builder.addParameter("application-id", applicationId);
            }
            builder.addParameter("limit", String.valueOf(limit));
            builder.addParameter("offset", String.valueOf(offset));
        });

        JsonNode root = objectMapper.readTree(executeGet(uri));
        Map<String, JsonNode> result = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    /**
     * Fetch the latest scan for every project in the tenant, with no status,
     * branch, engine, or application filter, paging automatically using the
     * configured default page size.
     */
    public Map<String, JsonNode> getLatestScans() throws IOException {
        return getLatestScans(config.getDefaultPageSize());
    }

    /** Same as {@link #getLatestScans()} but with an explicit page size. */
    public Map<String, JsonNode> getLatestScans(int pageSize) throws IOException {
        Map<String, JsonNode> all = new LinkedHashMap<>();
        int offset = 0;

        // This endpoint reports no total count, so - unlike getAllProjects()
        // or listScans() - we keep paging until a page comes back smaller
        // than the requested limit (or empty), which signals the last page.
        while (true) {
            Map<String, JsonNode> page = getProjectsLastScanPage(null, null, null, null, null, pageSize, offset);
            if (page.isEmpty()) {
                break;
            }
            all.putAll(page);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        return all;
    }

    // ------------------------------------------------------------------
    // Scan summaries: GET /api/scan-summary/
    // ------------------------------------------------------------------

    /**
     * Fetch scan-summary counters (severity/status/state/etc. breakdowns)
     * for the given scan IDs, as the raw response JSON:
     * {@code {"scansSummaries": [...], "totalCount": 0}}. Each element of
     * {@code "scansSummaries"} is one scan's counters and is best consumed
     * via {@link ScanSummaryPrinter#print(JsonNode)}, e.g.:
     * <pre>
     *   for (JsonNode summary : client.getScanSummaries(scanIds).path("scansSummaries")) {
     *       ScanSummaryPrinter.print(summary);
     *   }
     * </pre>
     */
    public JsonNode getScanSummaries(List<String> scanIds) throws IOException {
        if (scanIds == null || scanIds.isEmpty()) {
            throw new IllegalArgumentException("scanIds must not be empty");
        }
        URI uri = buildUri("/api/scan-summary/", builder -> {
            for (String scanId : scanIds) {
                builder.addParameter("scan-ids", scanId);
            }
        });
        return objectMapper.readTree(executeGet(uri));
    }

    // ------------------------------------------------------------------
    // Analytics (Data Analytics API): POST /api/data_analytics/analyticsAPI/v1
    // ------------------------------------------------------------------
    //
    // A single POST endpoint answers ~13 different KPI queries, selected via
    // the "kpi" field of the request body; the response shape depends on
    // which KPI was requested (see the OpenAPI spec:
    // n-virginia-metrics-data-analytics-api-ANALYTICS_API.yaml). All KPIs
    // are returned as raw JsonNode here - the typed methods below just set
    // "kpi" for you; use queryAnalyticsRaw(...) directly for any KPI not
    // covered by a named method (the response shape for every KPI is
    // documented in the spec's "components.schemas").
    // ------------------------------------------------------------------

    /**
     * Total vulnerability count broken down by severity (critical/high/
     * medium/low/information), for the projects/date-range/etc. described by
     * {@code query}. Sets {@code query}'s {@code kpi} to
     * {@code vulnerabilitiesBySeverityTotal}. Response shape:
     * {@code {"distribution": [{"label": ..., "density": ..., "percentage": ..., "results": ...}, ...], "loc": ..., "total": ...}}.
     */
    public JsonNode getVulnerabilitiesBySeverityTotal(AnalyticsQuery query) throws IOException {
        return queryAnalyticsRaw(query.kpi("vulnerabilitiesBySeverityTotal"));
    }

    /**
     * Total vulnerability count broken down by result state (toVerify/
     * notExploitable/proposedNotExploitable/confirmed/urgent), for the
     * projects/date-range/etc. described by {@code query}. Sets
     * {@code query}'s {@code kpi} to {@code vulnerabilitiesByStateTotal}.
     * Same response shape as {@link #getVulnerabilitiesBySeverityTotal}.
     */
    public JsonNode getVulnerabilitiesByStateTotal(AnalyticsQuery query) throws IOException {
        return queryAnalyticsRaw(query.kpi("vulnerabilitiesByStateTotal"));
    }

    /**
     * Total vulnerability count broken down by result status (NEW/
     * RECURRENT), for the projects/date-range/etc. described by
     * {@code query}. Sets {@code query}'s {@code kpi} to
     * {@code vulnerabilitiesByStatusTotal}. Same response shape as
     * {@link #getVulnerabilitiesBySeverityTotal}.
     */
    public JsonNode getVulnerabilitiesByStatusTotal(AnalyticsQuery query) throws IOException {
        return queryAnalyticsRaw(query.kpi("vulnerabilitiesByStatusTotal"));
    }

    /**
     * Vulnerability counts broken down by result state, with a nested
     * severity breakdown per state (plus a trailing "Totals" entry), for the
     * projects/date-range/etc. described by {@code query}. Sets
     * {@code query}'s {@code kpi} to
     * {@code vulnerabilitiesBySeverityAndStateTotal}.
     *
     * <p>Unlike the {@code *Total} KPIs above, the response here is a JSON
     * <b>array</b> at the top level, not an object - e.g.
     * {@code [{"label": "To Verify", "results": 676, "severities": [{"label": "Critical", "results": 56}, ...]}, ...]}.
     */
    public JsonNode getVulnerabilitiesBySeverityAndStateTotal(AnalyticsQuery query) throws IOException {
        return queryAnalyticsRaw(query.kpi("vulnerabilitiesBySeverityAndStateTotal"));
    }

    /**
     * The most common vulnerabilities (by name), each with its own severity
     * breakdown, for the projects/date-range/etc. described by
     * {@code query}. Sets {@code query}'s {@code kpi} to
     * {@code mostCommonVulnerabilities}.
     *
     * <p>Response is a top-level JSON array, e.g.
     * {@code [{"vulnerabilityName": "SQL Injection", "total": 12, "severities": [{"label": "Critical", "results": 4}, ...]}, ...]}.
     *
     * <p>Requires {@code query.limit(...)} to be set (1-100); this KPI is
     * paged via {@code limit}/{@code offset} like the other list endpoints
     * in this class, but the response has no total-count field, so - as
     * with {@link #getLatestScans(int)} - callers should keep advancing
     * {@code offset} by their chosen limit until a page comes back smaller
     * than that limit.
     */
    public JsonNode getMostCommonVulnerabilities(AnalyticsQuery query) throws IOException {
        if (!query.hasLimit()) {
            throw new IllegalArgumentException("query.limit(...) is required for the mostCommonVulnerabilities KPI");
        }
        return queryAnalyticsRaw(query.kpi("mostCommonVulnerabilities"));
    }

    /**
     * Runs any Data Analytics KPI query and returns the raw JSON response,
     * for KPIs with no dedicated typed method above (e.g.
     * {@code mostAgingVulnerabilities}, {@code allVulnerabilities},
     * {@code agingTotal}, {@code ideTotal}, {@code ideOvertime},
     * {@code meanTimeToResolution},
     * {@code fixedVulnerabilitiesBySeverityOvertime}). {@code query.kpi(...)}
     * must be set by the caller. See the OpenAPI spec's
     * {@code components.schemas} for the response shape of each KPI.
     */
    public JsonNode queryAnalyticsRaw(AnalyticsQuery query) throws IOException {
        if (query.getKpi() == null || query.getKpi().isBlank()) {
            throw new IllegalArgumentException("query.kpi(...) must be set");
        }
        URI uri = buildUri("/api/data_analytics/analyticsAPI/v1", builder -> {
        });
        String requestBody = objectMapper.writeValueAsString(query.toJsonNode());
        return objectMapper.readTree(executePost(uri, requestBody));
    }

    // ------------------------------------------------------------------
    // Internal HTTP helpers
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface UriCustomizer {
        void customize(URIBuilder builder) throws URISyntaxException;
    }

    private URI buildUri(String path, UriCustomizer customizer) throws IOException {
        try {
            URIBuilder builder = new URIBuilder(config.getAstHost() + path);
            customizer.customize(builder);
            return builder.build();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to build request URI for path " + path, e);
        }
    }

    private String executeGet(URI uri) throws IOException {
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", "application/json; version=1.0");
        get.setHeader("Authorization", "Bearer " + getBearerToken());
        get.setHeader("CorrelationId", "");

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : "";
            if (status != 200) {
                throw new IOException("GET " + uri + " failed [" + status + "]: " + body);
            }
            return body;
        }
    }

    private String executePost(URI uri, String jsonBody) throws IOException {
        HttpPost post = new HttpPost(uri);
        post.setHeader("Accept", "application/json");
        post.setHeader("Authorization", "Bearer " + getBearerToken());
        post.setHeader("Content-Type", "application/json; version=1.0");
        post.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : "";
            if (status != 200) {
                throw new IOException("POST " + uri + " failed [" + status + "]: " + body);
            }
            return body;
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
