package com.checkmarx.cxone.connector;

import com.checkmarx.cxone.connector.model.AnalyticsQuery;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runnable demo that exercises {@link CxOneClient} end to end against a real
 * Checkmarx One tenant.
 *
 * <p><b>Every response is read as generic parsed JSON</b> ({@link JsonNode},
 * or a {@code List}/{@code Map} of them), not typed model classes - fields
 * are pulled out with {@code node.path("fieldName")} as needed, right where
 * they're used below. See {@link CxOneClient}'s class Javadoc for why.
 *
 * <p><b>&#9888; Demo scope - every pull below is capped at
 * {@value #DEMO_LIMIT} items.</b> This is intentional: the goal of this demo
 * is to prove each API call works, not to download a tenant's entire history
 * of projects/scans/vulnerabilities. Every step below therefore uses a
 * single, bounded {@code limit}/{@code offset} page (e.g.
 * {@code client.getProjectsPage(DEMO_LIMIT, 0)}) instead of the
 * corresponding "fetch everything, auto-paged" convenience method
 * ({@code getAllProjects()}, {@code getLatestScans()}, {@code listScans(...)}
 * with no explicit page count) that {@link CxOneClient} also exposes for
 * real, non-demo use - those un-capped methods keep paging until the whole
 * tenant has been retrieved, which is exactly what this demo deliberately
 * avoids. Raise or remove {@link #DEMO_LIMIT} (and swap in the un-capped
 * methods) to pull full result sets in your own code.
 *
 * <p>It performs the following pulls, in order:
 * <ol>
 *   <li><b>Projects</b> ({@value #DEMO_LIMIT} max) -
 *       {@code GET /api/projects/}, a single page.</li>
 *   <li><b>Latest scan per project</b> ({@value #DEMO_LIMIT} max) -
 *       {@code GET /api/projects/last-scan} with no filters, a single page.
 *       Each entry is still the most recent scan for its project - the cap
 *       only limits how many <i>projects'</i> latest scans are returned.</li>
 *   <li><b>Latest-scan filter examples</b> ({@value #DEMO_LIMIT} max each) -
 *       a few illustrative calls to {@code GET /api/projects/last-scan}
 *       showing how to combine the {@code engine}, {@code project-ids}, and
 *       {@code scan-status} filters.</li>
 *   <li><b>Generic scan listing</b> ({@value #DEMO_LIMIT} max) -
 *       {@code GET /api/scans/}, filtered by a list of statuses, a single
 *       page. Unlike step 2, this can return several scans per project.</li>
 *   <li><b>Scan summaries</b> ({@value #DEMO_LIMIT} max scan IDs) -
 *       {@code GET /api/scan-summary/} for a handful of the scan IDs
 *       returned by step 2, with every nested key/counter value extracted
 *       and printed via {@link ScanSummaryPrinter}.</li>
 *   <li><b>Analytics KPI samples</b> - a few illustrative calls to
 *       {@code POST /api/data_analytics/analyticsAPI/v1} (the Data Analytics
 *       KPI query API), covering four different KPI types over the last
 *       {@value #DEMO_ANALYTICS_LOOKBACK_DAYS} days; the one KPI that takes a
 *       result-count parameter ({@code mostCommonVulnerabilities}) is also
 *       capped at {@value #DEMO_LIMIT}.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar cxone-java-connector-jar-with-dependencies.jar [path-to-properties-file]
 * </pre>
 * The properties file path is optional; if omitted, {@code cxone.properties}
 * in the current working directory is used. See {@code cxone.properties.sample}
 * for the required keys ({@code cxone.iam.host}, {@code cxone.ast.host},
 * {@code cxone.tenant}, {@code cxone.api.key}, {@code cxone.verify.ssl},
 * {@code cxone.page.size}).
 */
public final class App {

    /**
     * Hard cap applied to <b>every</b> pull in this demo - the number of
     * projects listed, the number of projects whose latest scan is fetched,
     * the number of scans returned by the generic scan listing, the number
     * of scan IDs summarized, and the {@code limit} passed to the
     * {@code mostCommonVulnerabilities} analytics KPI (allowed range 1-100).
     *
     * <p>This exists purely so running this demo against a real tenant
     * cannot accidentally download that tenant's entire project/scan/
     * vulnerability history - it is a demo safety limit, not an API
     * constraint. Production code that genuinely needs "all" of something
     * should call the corresponding un-capped, auto-paging method on
     * {@link CxOneClient} instead (e.g. {@link CxOneClient#getAllProjects()},
     * {@link CxOneClient#getLatestScans()},
     * {@link CxOneClient#listScans(List)}), or simply pass a larger value
     * here.
     */
    private static final int DEMO_LIMIT = 10;

    /**
     * Scan statuses requested in step 4 of the demo (the generic scan
     * listing), passed as the {@code statuses} query parameter to
     * {@code GET /api/scans/}, which accepts an array of values.
     *
     * <p>The full set of values accepted by the API is (case insensitive,
     * OR'd together server-side when more than one is supplied):
     * <ul>
     *   <li>{@code Queued} - scan has been accepted and is waiting for an
     *       available engine slot</li>
     *   <li>{@code Running} - scan is actively executing</li>
     *   <li>{@code Completed} - scan finished successfully and results are
     *       available</li>
     *   <li>{@code Failed} - scan could not finish (e.g. engine error,
     *       invalid source)</li>
     *   <li>{@code Partial} - scan finished but one or more engines did not
     *       complete (partial results are available)</li>
     *   <li>{@code Canceled} - scan was stopped before completion, either by
     *       a user or the system</li>
     * </ul>
     * This demo requests {@code Running}, {@code Queued}, and
     * {@code Completed} so the printed list shows both scans currently in
     * flight and the most recently finished ones. Adjust this list (or pass
     * {@code null}/an empty list to {@link CxOneClient#getScansPage(List, int, int)}
     * for no status filter at all) to suit your own use case.
     */
    private static final List<String> DEMO_SCAN_STATUSES = List.of("Running", "Queued", "Completed");

    /**
     * Date format required by the Analytics API's {@code startDate}/
     * {@code endDate} query fields: literal {@code yyyy-MM-ddTHH:mm:ss},
     * with no timezone offset (see {@link AnalyticsQuery#timezone(String)}
     * to interpret the range in a specific zone instead of the server's
     * default).
     */
    private static final DateTimeFormatter ANALYTICS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** How far back the step-6 analytics samples look, ending "now". */
    private static final int DEMO_ANALYTICS_LOOKBACK_DAYS = 30;

    private App() {
    }

    public static void main(String[] args) throws Exception {
        // arg[0], if provided, overrides the default properties file path.
        // See cxone.properties.sample for the full list of expected keys.
        String propsPath = args.length > 0 ? args[0] : "cxone.properties";
        CxOneConfig config = CxOneConfig.fromFile(propsPath);

        // CxOneClient implements Closeable so the underlying Apache
        // HttpClient (and its connection pool) is released deterministically
        // when the demo finishes.
        try (CxOneClient client = new CxOneClient(config)) {

            // ----------------------------------------------------------------
            // Step 1: projects (demo-limited to DEMO_LIMIT).
            //
            // A single GET /api/projects/ call with limit=DEMO_LIMIT,
            // offset=0 - NOT client.getAllProjects(), which would instead
            // auto-page until every project in the tenant was fetched.
            // getProjectsPage(...) returns the raw response JSON; individual
            // projects are the elements of its "projects" array.
            // ----------------------------------------------------------------
            System.out.println("=== Projects (demo-limited to " + DEMO_LIMIT + ") ===");
            List<JsonNode> projects = toList(client.getProjectsPage(DEMO_LIMIT, 0).path("projects"));
            System.out.println("Fetched " + projects.size() + " project(s) (tenant may contain more).");
            for (JsonNode p : projects) {
                System.out.printf("  %-36s %s%n", p.path("id").asText(), p.path("name").asText());
            }

            // ----------------------------------------------------------------
            // Step 2: latest scan per project (demo-limited to DEMO_LIMIT
            // projects).
            //
            // A single GET /api/projects/last-scan call with limit=DEMO_LIMIT,
            // offset=0, no filters - NOT client.getLatestScans(), which
            // would instead auto-page until every project in the tenant had
            // its latest scan fetched. Each entry returned is still the true
            // latest scan for that project; only the number of projects
            // covered is capped. Returned as project-id -> scan JsonNode.
            // ----------------------------------------------------------------
            System.out.println();
            System.out.println("=== Latest scan per project (demo-limited to " + DEMO_LIMIT + " projects) ===");
            Map<String, JsonNode> latestScansByProject = client.getProjectsLastScanPage(
                    null, null, null, null, null, DEMO_LIMIT, 0);
            System.out.println("Fetched latest scan for " + latestScansByProject.size() + " project(s) (tenant may contain more).");
            printLatestScans(latestScansByProject);

            // ----------------------------------------------------------------
            // Step 3: latest-scan filter examples (each demo-limited to
            // DEMO_LIMIT).
            //
            // GET /api/projects/last-scan also accepts "engine", "branch",
            // "project-ids", and "scan-status" filters. A couple of
            // combinations are demonstrated below; each calls
            // getProjectsLastScanPage(...) directly for a single page capped
            // at DEMO_LIMIT.
            // ----------------------------------------------------------------
            System.out.println();
            System.out.println("=== Latest-scan filter examples (each demo-limited to " + DEMO_LIMIT + ") ===");

            // 3a. Filter by a single engine: only projects whose latest scan
            //     included the SAST engine, and only if that scan completed.
            //     "engine" accepts one of: sast, sca, kics, apisec.
            printFilterDemo(client,
                    "engine=sast, scan-status=Completed",
                    /* projectIds */ null,
                    /* scanStatus */ "Completed",
                    /* engine     */ "sast");

            // 3b. Filter by explicit project-ids: restrict the lookup to (up
            //     to) the first DEMO_LIMIT projects fetched in step 1 (if
            //     any exist). "project-ids" accepts an array of project
            //     UUIDs and is sent as one repeated query parameter per ID.
            List<String> sampleProjectIds = projects.stream()
                    .map(p -> p.path("id").asText())
                    .limit(DEMO_LIMIT)
                    .toList();
            if (!sampleProjectIds.isEmpty()) {
                printFilterDemo(client,
                        "project-ids=" + sampleProjectIds,
                        sampleProjectIds,
                        /* scanStatus */ null,
                        /* engine     */ null);
            }

            // 3c. Combine engine + scan-status + project-ids in one call:
            //     the latest SCA-engine scan, if it failed, for the same
            //     sample project IDs used in 3b.
            if (!sampleProjectIds.isEmpty()) {
                printFilterDemo(client,
                        "project-ids=" + sampleProjectIds + ", engine=sca, scan-status=Failed",
                        sampleProjectIds,
                        /* scanStatus */ "Failed",
                        /* engine     */ "sca");
            }

            // ----------------------------------------------------------------
            // Step 4: generic scan listing (NOT one-per-project;
            // demo-limited to DEMO_LIMIT).
            //
            // A single GET /api/scans/ call with statuses=DEMO_SCAN_STATUSES,
            // limit=DEMO_LIMIT, offset=0 - NOT client.listScans(...), which
            // would instead auto-page until every matching scan in the
            // tenant had been retrieved. Individual scans are the elements
            // of the response's "scans" array.
            // ----------------------------------------------------------------
            System.out.println();
            System.out.println("=== Scan listing (statuses = " + DEMO_SCAN_STATUSES
                    + ", demo-limited to " + DEMO_LIMIT + ") ===");
            List<JsonNode> scans = toList(client.getScansPage(DEMO_SCAN_STATUSES, DEMO_LIMIT, 0).path("scans"));
            System.out.println("Fetched " + scans.size() + " scan(s) (tenant may contain more matching scans).");
            for (JsonNode s : scans) {
                System.out.printf("  %-36s %-10s %-30s %s%n",
                        s.path("id").asText(), s.path("status").asText(),
                        s.path("projectName").asText(), s.path("createdAt").asText());
            }

            // ----------------------------------------------------------------
            // Step 5: scan summaries for a handful of the latest scans from
            // step 2 (demo-limited to DEMO_LIMIT scan IDs).
            //
            // getScanSummaries(scanIds) calls GET /api/scan-summary/, passing
            // one "scan-ids" query parameter per requested scan ID. The
            // response contains, per scan, one block of counters per engine
            // (sastCounters, kicsCounters, scaCounters, ...), each broken
            // down by severity/status/state/age/etc. ScanSummaryPrinter
            // walks and prints every one of those key/counter pairs directly
            // from the parsed JSON; see its javadoc for details.
            // ----------------------------------------------------------------
            if (!latestScansByProject.isEmpty()) {
                System.out.println();
                System.out.println("=== Scan summaries (demo-limited to " + DEMO_LIMIT + " of the latest scans above) ===");
                List<String> scanIds = latestScansByProject.values().stream()
                        .map(scan -> scan.path("id").asText())
                        .limit(DEMO_LIMIT)
                        .toList();

                JsonNode summariesResponse = client.getScanSummaries(scanIds);
                for (JsonNode summary : summariesResponse.path("scansSummaries")) {
                    System.out.println();
                    ScanSummaryPrinter.print(summary);
                }
            }

            // ----------------------------------------------------------------
            // Step 6: analytics KPI samples.
            //
            // POST /api/data_analytics/analyticsAPI/v1 answers ~13 different
            // KPI queries via one endpoint, selected by the request body's
            // "kpi" field; CxOneClient exposes a typed method per KPI shown
            // below (AnalyticsQuery.kpi(...) is set automatically by each),
            // each returning the raw response JSON. All four samples share
            // the same date range - the last DEMO_ANALYTICS_LOOKBACK_DAYS
            // days, ending now - built once here and reused via query()
            // below. Only mostCommonVulnerabilities (6d) takes a
            // result-count parameter; it is demo-limited to DEMO_LIMIT the
            // same as every other step.
            // ----------------------------------------------------------------
            System.out.println();
            System.out.println("=== Analytics KPI samples (last " + DEMO_ANALYTICS_LOOKBACK_DAYS + " days) ===");
            String endDate = LocalDateTime.now().format(ANALYTICS_DATE_FORMAT);
            String startDate = LocalDateTime.now().minusDays(DEMO_ANALYTICS_LOOKBACK_DAYS).format(ANALYTICS_DATE_FORMAT);

            // 6a. vulnerabilitiesBySeverityTotal: overall vulnerability count
            //     broken down by severity (critical/high/medium/low/information).
            //     This KPI has no result-count limit to demo-cap - it always
            //     returns one bucket per severity. Response is a JSON object
            //     with a "distribution" array plus "loc"/"total".
            System.out.println();
            System.out.println("--- vulnerabilitiesBySeverityTotal ---");
            JsonNode bySeverity = client.getVulnerabilitiesBySeverityTotal(query(startDate, endDate));
            printDistribution(bySeverity);

            // 6b. vulnerabilitiesByStateTotal: overall vulnerability count
            //     broken down by result state (toVerify/notExploitable/
            //     proposedNotExploitable/confirmed/urgent), restricted here to
            //     the SAST engine as an example of combining a scanner filter
            //     with the date range. Same response shape as 6a.
            System.out.println();
            System.out.println("--- vulnerabilitiesByStateTotal (scanners=sast) ---");
            JsonNode byState = client.getVulnerabilitiesByStateTotal(
                    query(startDate, endDate).scanners(List.of("sast")));
            printDistribution(byState);

            // 6c. vulnerabilitiesBySeverityAndStateTotal: same two dimensions
            //     as 6a/6b combined into one call, filtered to only
            //     "critical"/"high" severities as an example of the
            //     severities filter. Response is a top-level JSON array.
            System.out.println();
            System.out.println("--- vulnerabilitiesBySeverityAndStateTotal (severities=critical,high) ---");
            JsonNode bySeverityAndState = client.getVulnerabilitiesBySeverityAndStateTotal(
                    query(startDate, endDate).severities(List.of("critical", "high")));
            printSeverityAndState(bySeverityAndState);

            // 6d. mostCommonVulnerabilities: the most frequently occurring
            //     vulnerability names, each with its own severity breakdown.
            //     "limit" is required for this KPI - demo-limited to
            //     DEMO_LIMIT, same as every other step. Response is a
            //     top-level JSON array.
            System.out.println();
            System.out.println("--- mostCommonVulnerabilities (demo-limited to " + DEMO_LIMIT + ") ---");
            JsonNode mostCommon = client.getMostCommonVulnerabilities(
                    query(startDate, endDate).limit(DEMO_LIMIT));
            printMostCommonVulnerabilities(mostCommon);
        }
    }

    /** Collects a (possibly missing/non-array) JsonNode's elements into a plain List, defaulting to empty. */
    private static List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        return list;
    }

    /** Builds a fresh {@link AnalyticsQuery} scoped to the given date range; {@code kpi} is set by the caller's typed method. */
    private static AnalyticsQuery query(String startDate, String endDate) {
        return AnalyticsQuery.create().startDate(startDate).endDate(endDate);
    }

    /** Prints a severity/state/status distribution response's buckets plus its loc/total. */
    private static void printDistribution(JsonNode response) {
        for (JsonNode item : response.path("distribution")) {
            System.out.printf("  %-14s results=%-6s percentage=%s%% density=%s%n",
                    item.path("label").asText(), item.path("results").asText(),
                    item.path("percentage").asText(), item.path("density").asText());
        }
        System.out.println("  loc=" + response.path("loc").asText() + " total=" + response.path("total").asText());
    }

    /** Prints each state entry (including the trailing "Totals" row) with its nested severity breakdown. */
    private static void printSeverityAndState(JsonNode items) {
        for (JsonNode item : items) {
            System.out.printf("  %-26s results=%s%n", item.path("label").asText(), item.path("results").asText());
            for (JsonNode sev : item.path("severities")) {
                System.out.printf("      %-14s results=%s%n", sev.path("label").asText(), sev.path("results").asText());
            }
        }
    }

    /** Prints each vulnerability name with its total and severity breakdown. */
    private static void printMostCommonVulnerabilities(JsonNode items) {
        for (JsonNode item : items) {
            System.out.printf("  %-40s total=%s%n", item.path("vulnerabilityName").asText(), item.path("total").asText());
            for (JsonNode sev : item.path("severities")) {
                System.out.printf("      %-14s results=%s%n", sev.path("label").asText(), sev.path("results").asText());
            }
        }
    }

    /** Prints one line per project-id -> scan entry returned by the last-scan endpoint. */
    private static void printLatestScans(Map<String, JsonNode> latestScansByProject) {
        latestScansByProject.forEach((projectId, scan) ->
                System.out.printf("  %-36s -> %-36s %-10s %s%n",
                        projectId, scan.path("id").asText(), scan.path("status").asText(), scan.path("createdAt").asText()));
    }

    /**
     * Runs one {@code GET /api/projects/last-scan} call (capped at
     * {@link #DEMO_LIMIT} results) with the given filters and prints the
     * result, labeled with {@code description}.
     */
    private static void printFilterDemo(CxOneClient client, String description,
                                         List<String> projectIds, String scanStatus, String engine) throws Exception {
        System.out.println();
        System.out.println("--- " + description + " ---");
        Map<String, JsonNode> result = client.getProjectsLastScanPage(
                projectIds, scanStatus, /* branch */ null, engine, /* applicationId */ null,
                DEMO_LIMIT, /* offset */ 0);
        if (result.isEmpty()) {
            System.out.println("  (no matching scans)");
        } else {
            printLatestScans(result);
        }
    }
}
