# CxOne Java Connector Sample

Sample Java connector for the Checkmarx One (CxOne) REST API, built with
[Apache HttpClient](https://hc.apache.org/httpcomponents-client-4.5.x/) and
configured via a Java `.properties` file.

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
  (`getProjectsPage(limit, offset)` - what the demo uses, capped at
  `DEMO_LIMIT`) or auto-paged until every project in the tenant is fetched
  (`getAllProjects()`).
- **Latest scan per project, for the whole tenant** - `GET /api/projects/last-scan`,
  optionally filtered by `project-ids`, a single `scan-status`, `branch`,
  and/or `engine`: a single bounded page (`getProjectsLastScanPage(projectIds,
  scanStatus, branch, engine, applicationId, limit, offset)` - what the demo
  uses) or auto-paged across the whole tenant (`getLatestScans()`). This
  endpoint returns at most one (the most recent) scan per project.
- **Generic scan listing** - `GET /api/scans/`, optionally filtered by an
  array of statuses: a single bounded page (`getScansPage(statuses, limit,
  offset)` - what the demo uses, filtered to `Running`/`Queued`/`Completed`)
  or auto-paged until every matching scan is fetched (`listScans(statuses)`).
  Unlike the latest-scan endpoint above, this can return several scans per
  project.
- **Scan summaries** - `GET /api/scan-summary/` for a set of scan IDs
  (`getScanSummaries(scanIds)`). The response is a large, deeply nested set
  of per-engine counters (severity/status/state/age/query/... breakdowns);
  `ScanSummaryPrinter.print(summary)` walks and prints every label/counter
  pair it contains, without needing a dedicated model class per nested shape.
- **Analytics KPIs** - `POST /api/data_analytics/analyticsAPI/v1` (the [Data
  Analytics API](https://ast.checkmarx.net/spec/v1/n-virginia-metrics-data-analytics-api-ANALYTICS_API.yaml)),
  a single endpoint answering ~13 different KPI queries selected by the
  request body's `kpi` field. Four are exposed as typed methods -
  `getVulnerabilitiesBySeverityTotal`, `getVulnerabilitiesByStateTotal`,
  `getVulnerabilitiesBySeverityAndStateTotal`, and
  `getMostCommonVulnerabilities` - each taking an `AnalyticsQuery` (a fluent
  builder for the shared project/application/date-range/severity/etc.
  filters). Only `getMostCommonVulnerabilities` takes a result-count
  parameter (`limit`, capped at `DEMO_LIMIT` in the demo). Any other KPI
  (e.g. `allVulnerabilities`, `agingTotal`, `meanTimeToResolution`) can be
  run via `queryAnalyticsRaw(query)`, which returns the raw JSON response.

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
  CxOneClient.java       - the REST connector (Apache HttpClient)
  ScanSummaryPrinter.java- extracts/prints scan-summary counters
  App.java               - runnable demo
  model/
    Project.java, ProjectPage.java
    Scan.java, ScanPage.java
    ScanSummary.java, ScanSummaryResponse.java
    AnalyticsQuery.java            - Analytics API request builder
    DistributionResponse.java, DistributionItem.java
    SeverityAndStateItem.java
    MostCommonVulnerabilitiesItem.java, LabeledResult.java
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
    List<Project> projects = client.getAllProjects();

    // Exactly one (the latest) scan per project, across the whole tenant.
    Map<String, Scan> latestScanByProject = client.getLatestScans();

    // Optionally filter the latest-scan lookup by engine / project-ids / a
    // single scan-status (see CxOneClient.getProjectsLastScanPage javadoc
    // for the full parameter list and allowed values).
    Map<String, Scan> latestSastCompletedScans = client.getProjectsLastScanPage(
            null, "Completed", null, "sast", null, 100, 0);

    // Generic, possibly multiple-scans-per-project listing, filtered by an
    // array of statuses.
    List<Scan> activeScans = client.listScans(List.of("Running", "Queued"));

    List<String> scanIds = activeScans.stream().map(Scan::getId).toList();
    ScanSummaryResponse summaries = client.getScanSummaries(scanIds);

    // Analytics KPIs: vulnerability count by severity over a date range.
    AnalyticsQuery query = AnalyticsQuery.create()
            .startDate("2026-07-19T00:00:00")
            .endDate("2026-08-18T00:00:00")
            .severities(List.of("critical", "high"));
    DistributionResponse bySeverity = client.getVulnerabilitiesBySeverityTotal(query);

    // Any other KPI not covered by a typed method (see the OpenAPI spec's
    // components.schemas for that KPI's response shape):
    JsonNode agingTotal = client.queryAnalyticsRaw(query.kpi("agingTotal"));
}
```
