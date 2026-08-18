# CxOne Java Connector Sample

Sample Java connector for the Checkmarx One (CxOne) REST API, built with
[Apache HttpClient](https://hc.apache.org/httpcomponents-client-4.5.x/) and
configured via a Java `.properties` file.

## Responses are generic parsed JSON, not typed model classes

`CxOneClient` returns every response as **generic parsed JSON** -
[`JsonNode`](https://fasterxml.github.io/jackson-databind/javadoc/2.17/com/fasterxml/jackson/databind/JsonNode.html)
(Jackson's tree model), or a `List<JsonNode>`/`Map<String, JsonNode>` of them
- instead of a dedicated model class per response shape. You pull out the
fields you need with `node.path("fieldName")`, right where you use them.

This is a deliberate choice, not a shortcut: several of these responses are
large and deeply nested (scan summaries alone have ~20 nested counter
shapes; the Analytics API has ~13 different KPI response shapes), and
hand-modeling a POJO per shape means a lot of classes to write and keep in
sync whenever the API adds or renames a field. Reading the response as a
JSON tree sidesteps that entirely - new fields just show up under
`node.path(...)` with no code changes - at the cost of losing compile-time
checking of field names/types: a typo in a path (`node.path("staus")`) is
only caught at runtime, and there's no IDE autocomplete for field names.
`AnalyticsQuery` (the one *request* body in this project) follows the same
idea in reverse: it's a thin fluent wrapper around a Jackson `ObjectNode`
rather than a POJO with fixed fields, so it serializes as-is with no
intermediate conversion step.

`ScanSummaryPrinter` and `App` show the pattern in practice - walking a
`JsonNode` tree and extracting/printing the data of interest.

## ⚠️ Demo scope: `App.java` caps every pull at 10

**`App.java` (the runnable demo) intentionally limits every single data pull
to 10 items** - 10 projects, 10 scans, 10 latest-scan-per-project results,
10 scan IDs summarized, 10 results for the `mostCommonVulnerabilities`
analytics KPI, etc. (see the `DEMO_LIMIT` constant in `App.java`). This is
purely a demo safety limit, **not** an API or library constraint: the goal
of the demo is to prove each call works end to end against a real tenant
without accidentally downloading that tenant's entire project/scan/
vulnerability history.

`CxOneClient` (the actual connector class/library) is **not** limited -
it exposes both the capped, single-page calls the demo uses
(`getProjectsPage(limit, offset)`, `getScansPage(statuses, limit, offset)`,
`getProjectsLastScanPage(...)`) and the un-capped, auto-paging "fetch
everything" convenience methods (`getAllProjects()`, `getLatestScans()`,
`listScans(statuses)`) for real, non-demo use. Raise or remove
`App.DEMO_LIMIT` (and swap in the un-capped methods, as shown under "Using
it as a library" below) if you want the demo - or your own code built on
`CxOneClient` - to pull full result sets.

## What it does

`CxOneClient` authenticates to Checkmarx One using the configured API key
and exposes read-only pulls for:

- **Projects** - `GET /api/projects/`: a single bounded page
  (`getProjectsPage(limit, offset)` returns the raw page JSON - what the
  demo uses, capped at `DEMO_LIMIT`) or auto-paged until every project in
  the tenant is fetched (`getAllProjects()` returns `List<JsonNode>`, one
  per project).
- **Latest scan per project, for the whole tenant** - `GET /api/projects/last-scan`,
  optionally filtered by `project-ids`, a single `scan-status`, `branch`,
  and/or `engine`: a single bounded page (`getProjectsLastScanPage(projectIds,
  scanStatus, branch, engine, applicationId, limit, offset)` - what the demo
  uses) or auto-paged across the whole tenant (`getLatestScans()`). Both
  return `Map<String, JsonNode>` (project ID -> that project's latest scan).
  This endpoint returns at most one (the most recent) scan per project.
- **Generic scan listing** - `GET /api/scans/`, optionally filtered by an
  array of statuses: a single bounded page (`getScansPage(statuses, limit,
  offset)` returns the raw page JSON - what the demo uses, filtered to
  `Running`/`Queued`/`Completed`) or auto-paged until every matching scan is
  fetched (`listScans(statuses)` returns `List<JsonNode>`, one per scan).
  Unlike the latest-scan endpoint above, this can return several scans per
  project.
- **Scan summaries** - `GET /api/scan-summary/` for a set of scan IDs
  (`getScanSummaries(scanIds)` returns the raw response JSON). Each element
  of its `"scansSummaries"` array is a large, deeply nested set of
  per-engine counters (severity/status/state/age/query/... breakdowns);
  `ScanSummaryPrinter.print(summary)` walks and prints every label/counter
  pair it contains directly from the parsed JSON.
- **Analytics KPIs** - `POST /api/data_analytics/analyticsAPI/v1` (the [Data
  Analytics API](https://ast.checkmarx.net/spec/v1/n-virginia-metrics-data-analytics-api-ANALYTICS_API.yaml)),
  a single endpoint answering ~13 different KPI queries selected by the
  request body's `kpi` field. Four are exposed as typed methods -
  `getVulnerabilitiesBySeverityTotal`, `getVulnerabilitiesByStateTotal`,
  `getVulnerabilitiesBySeverityAndStateTotal`, and
  `getMostCommonVulnerabilities` - each taking an `AnalyticsQuery` (a fluent
  builder for the shared project/application/date-range/severity/etc.
  filters) and returning the raw response `JsonNode`. Only
  `getMostCommonVulnerabilities` takes a result-count parameter (`limit`,
  capped at `DEMO_LIMIT` in the demo). Any other KPI (e.g.
  `allVulnerabilities`, `agingTotal`, `meanTimeToResolution`) can be run via
  `queryAnalyticsRaw(query)`, which also returns the raw JSON response.

As covered above, `getAllProjects()`, `getLatestScans()`, and
`listScans(statuses)` are the un-capped, auto-paging methods - real
(non-demo) callers who actually want "every project"/"every scan" should
use those instead of the single-page methods the demo uses.

The bearer token is cached and refreshed automatically (5 minutes before
expiry).

## Project layout

```
pom.xml
cxone.properties.sample
src/main/java/com/checkmarx/cxone/connector/
  CxOneConfig.java       - loads settings from a .properties file
  CxOneClient.java       - the REST connector (Apache HttpClient), returns JsonNode
  ScanSummaryPrinter.java- extracts/prints scan-summary counters from JsonNode
  App.java               - runnable demo
  model/
    AnalyticsQuery.java  - Analytics API request builder (backed by an ObjectNode)
```

## Configure

Copy the sample properties file and fill in your tenant details:

```bash
cp cxone.properties.sample cxone.properties
```

```properties
cxone.iam.host=https://iam.checkmarx.net
cxone.ast.host=https://ast.checkmarx.net
cxone.tenant=your-tenant-name
cxone.api.key=your-api-key-here
cxone.verify.ssl=true
cxone.page.size=100
```

`cxone.api.key` is the API key generated from CxOne's "Generate API Key"
page (Access Management). Do not commit a filled-in properties file.

## Build & run

```bash
mvn package
java -jar target/cxone-java-connector-jar-with-dependencies.jar cxone.properties
```

If no path is given, `cxone.properties` in the current directory is used.
Remember: every pull the demo prints is capped at 10 items (`DEMO_LIMIT` in
`App.java`) - see "Demo scope" above.

## Using it as a library

```java
CxOneConfig config = CxOneConfig.fromFile("cxone.properties");
try (CxOneClient client = new CxOneClient(config)) {
    // Every project as a JsonNode (id/name/tags/... - see the API docs for
    // the full field list); pull out what you need with node.path(...).
    List<JsonNode> projects = client.getAllProjects();
    String firstProjectName = projects.get(0).path("name").asText();

    // Exactly one (the latest) scan per project, across the whole tenant -
    // project ID -> that project's latest scan, as a JsonNode.
    Map<String, JsonNode> latestScanByProject = client.getLatestScans();

    // Optionally filter the latest-scan lookup by engine / project-ids / a
    // single scan-status (see CxOneClient.getProjectsLastScanPage javadoc
    // for the full parameter list and allowed values).
    Map<String, JsonNode> latestSastCompletedScans = client.getProjectsLastScanPage(
            null, "Completed", null, "sast", null, 100, 0);

    // Generic, possibly multiple-scans-per-project listing, filtered by an
    // array of statuses.
    List<JsonNode> activeScans = client.listScans(List.of("Running", "Queued"));

    List<String> scanIds = activeScans.stream()
            .map(scan -> scan.path("id").asText())
            .toList();
    JsonNode summariesResponse = client.getScanSummaries(scanIds);
    for (JsonNode summary : summariesResponse.path("scansSummaries")) {
        ScanSummaryPrinter.print(summary);
    }

    // Analytics KPIs: vulnerability count by severity over a date range.
    AnalyticsQuery query = AnalyticsQuery.create()
            .startDate("2026-07-19T00:00:00")
            .endDate("2026-08-18T00:00:00")
            .severities(List.of("critical", "high"));
    JsonNode bySeverity = client.getVulnerabilitiesBySeverityTotal(query);
    for (JsonNode bucket : bySeverity.path("distribution")) {
        System.out.println(bucket.path("label").asText() + " -> " + bucket.path("results").asInt());
    }

    // Any other KPI not covered by a typed method (see the OpenAPI spec's
    // components.schemas for that KPI's response shape):
    JsonNode agingTotal = client.queryAnalyticsRaw(query.kpi("agingTotal"));
}
```
