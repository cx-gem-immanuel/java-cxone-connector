package com.checkmarx.cxone.connector;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Extracts and prints every key/counter value nested inside one entry of
 * {@code GET /api/scan-summary/}'s {@code scansSummaries} array (a raw,
 * generic {@link JsonNode} - see {@link CxOneClient#getScanSummaries(List)} -
 * rather than a typed model class).
 *
 * <p>The full response schema is very large: each scan has one block per
 * engine ({@code sastCounters}, {@code kicsCounters}, {@code scaCounters},
 * {@code scaPackagesCounters}, {@code scaContainersCounters},
 * {@code apiSecCounters}, {@code microEnginesCounters},
 * {@code containersCounters}, {@code aiscCounters}), and each block contains
 * several counter breakdowns - most shaped as a list of small objects such
 * as {@code {"severity": "CRITICAL", "counter": 0}} or
 * {@code {"compliance": "string", "count": 0}}, a few as bare numeric
 * totals such as {@code totalCounter} or {@code filesScannedCounter}, and
 * some (e.g. {@code ageCounters}) nesting another counter list inside each
 * entry.
 *
 * <p>Rather than hand-modeling every one of those ~20 nested shapes as Java
 * classes, this class walks the parsed JSON tree directly - at any nesting
 * depth - and prints every label/counter pair it finds, so new counter
 * blocks the API adds later show up automatically with no code changes.
 */
public final class ScanSummaryPrinter {

    private ScanSummaryPrinter() {
    }

    /**
     * Prints {@code scanId}/{@code tenantId} followed by every counter
     * block of one scan summary. Blocks that carry no information at all
     * (every nested list empty and every nested number zero - e.g. an
     * engine, such as {@code microEnginesCounters}, that simply didn't run)
     * are skipped entirely rather than printed as a wall of
     * {@code (none)}/{@code = 0} lines.
     *
     * @param summary one element of the {@code scansSummaries} array
     *                returned by {@code GET /api/scan-summary/}
     */
    public static void print(JsonNode summary) {
        System.out.println("scanId=" + summary.path("scanId").asText()
                + ", tenantId=" + summary.path("tenantId").asText());

        // Every field except scanId/tenantId is a "*Counters" block; sorted
        // so they always print in the same order, regardless of what order
        // the server happened to send them in.
        Map<String, JsonNode> blocks = new TreeMap<>();
        summary.fields().forEachRemaining(entry -> {
            if (!entry.getKey().equals("scanId") && !entry.getKey().equals("tenantId")) {
                blocks.put(entry.getKey(), entry.getValue());
            }
        });

        blocks.forEach((name, value) -> {
            if (!isEmpty(value)) {
                printNode(name, value, "  ");
            }
        });
    }

    /**
     * True if {@code value} carries no information: an empty array, a zero
     * number, or an object whose values are all themselves empty
     * (recursively). Strings/booleans are never considered empty since they
     * always convey something (a label, a flag).
     */
    private static boolean isEmpty(JsonNode value) {
        if (value.isObject()) {
            for (JsonNode child : value) {
                if (!isEmpty(child)) {
                    return false;
                }
            }
            return true;
        }
        if (value.isArray()) {
            return value.isEmpty();
        }
        if (value.isNumber()) {
            return value.doubleValue() == 0.0;
        }
        return value.isNull() || value.isMissingNode();
    }

    /**
     * Recursively prints one JSON node: objects are descended into by key
     * (e.g. {@code sastCounters}), arrays are descended into element by
     * element (e.g. {@code severityCounters}), and scalar leaves
     * (numbers/strings/booleans, e.g. {@code totalCounter}) are printed as
     * {@code key = value}.
     */
    private static void printNode(String name, JsonNode value, String indent) {
        if (value.isObject()) {
            System.out.println(indent + name + ":");
            value.fields().forEachRemaining(entry -> printNode(entry.getKey(), entry.getValue(), indent + "  "));
        } else if (value.isArray()) {
            if (value.isEmpty()) {
                System.out.println(indent + name + ": (none)");
                return;
            }
            System.out.println(indent + name + ":");
            for (JsonNode element : value) {
                printListEntry(element, indent + "  ");
            }
        } else {
            System.out.println(indent + name + " = " + value.asText());
        }
    }

    /**
     * Prints one element of a counter array, e.g.
     * {@code {"severity": "CRITICAL", "counter": 0}} or
     * {@code {"severity": "CRITICAL", "status": "NEW", "counter": 0}}.
     *
     * <p>The non-counter "label" fields (severity/status/state/file/
     * language/... - whatever the block happens to use) are joined on one
     * line, followed by {@code -> } and the bare counter/count value(s)
     * (e.g. {@code HIGH NEW -> 34}, not {@code HIGH NEW -> counter=34}).
     * Any nested object/array within the same element (e.g. an
     * {@code ageCounters} entry's own {@code severityCounters}) is printed
     * indented underneath.
     */
    private static void printListEntry(JsonNode element, String indent) {
        if (!element.isObject()) {
            // Not expected for this API, but fall back to a plain value
            // rather than failing if a future array holds bare scalars.
            System.out.println(indent + element.asText());
            return;
        }

        StringBuilder labels = new StringBuilder();
        StringBuilder counters = new StringBuilder();
        List<Map.Entry<String, JsonNode>> nested = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> fields = element.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode fieldValue = field.getValue();
            if (fieldValue.isObject() || fieldValue.isArray()) {
                nested.add(field);
            } else if (isCounterField(field.getKey())) {
                if (!counters.isEmpty()) {
                    counters.append(", ");
                }
                counters.append(fieldValue.asText());
            } else {
                if (!labels.isEmpty()) {
                    labels.append(' ');
                }
                labels.append(fieldValue.asText());
            }
        }

        System.out.println(indent + labels + (counters.isEmpty() ? "" : " -> " + counters));
        for (Map.Entry<String, JsonNode> field : nested) {
            printNode(field.getKey(), field.getValue(), indent + "  ");
        }
    }

    /** Most blocks call the numeric field "counter"; {@code complianceCounters} calls it "count". */
    private static boolean isCounterField(String fieldName) {
        return fieldName.equalsIgnoreCase("counter") || fieldName.equalsIgnoreCase("count");
    }
}
