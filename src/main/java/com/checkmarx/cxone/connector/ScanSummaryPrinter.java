package com.checkmarx.cxone.connector;

import com.checkmarx.cxone.connector.model.ScanSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Extracts and prints every key/counter value nested inside a
 * {@link ScanSummary}.
 *
 * <p>{@code GET /api/scan-summary/} returns a very large, deeply nested
 * response: each scan has one block per engine ({@code sastCounters},
 * {@code kicsCounters}, {@code scaCounters}, {@code scaPackagesCounters},
 * {@code scaContainersCounters}, {@code apiSecCounters},
 * {@code microEnginesCounters}, {@code containersCounters},
 * {@code aiscCounters}), and each block contains several counter
 * breakdowns - most shaped as a list of small objects such as
 * {@code {"severity": "CRITICAL", "counter": 0}} or
 * {@code {"compliance": "string", "count": 0}}, a few as bare numeric
 * totals such as {@code totalCounter} or {@code filesScannedCounter}, and
 * some (e.g. {@code ageCounters}) nesting another counter list inside each
 * entry.
 *
 * <p>Rather than hand-modeling every one of those ~20 nested shapes,
 * {@link ScanSummary} captures them as raw parsed JSON
 * ({@code Map}/{@code List}/primitive values) via Jackson's
 * {@code @JsonAnySetter}. This class walks that raw structure generically -
 * at any nesting depth - and prints every label/counter pair it finds, so
 * new counter blocks the API adds later show up automatically without
 * requiring new model classes.
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
     */
    public static void print(ScanSummary summary) {
        System.out.println("scanId=" + summary.getScanId() + ", tenantId=" + summary.getTenantId());
        // Sorted so the counter blocks always print in the same order,
        // regardless of what order the server happened to send them in.
        new TreeMap<>(summary.getAdditionalCounters()).forEach((name, value) -> {
            if (!isEmpty(value)) {
                printNode(name, value, "  ");
            }
        });
    }

    /**
     * True if {@code value} carries no information: an empty list, a zero
     * number, or a map whose values are all themselves empty (recursively).
     * Strings/booleans are never considered empty since they always convey
     * something (a label, a flag).
     */
    private static boolean isEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().allMatch(ScanSummaryPrinter::isEmpty);
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() == 0.0;
        }
        return value == null;
    }

    /**
     * Recursively prints one JSON node, as parsed by Jackson into
     * {@code Map}/{@code List}/primitive values: objects are descended into
     * by key (e.g. {@code sastCounters}), arrays are descended into element
     * by element (e.g. {@code severityCounters}), and scalar leaves
     * (numbers/strings/booleans, e.g. {@code totalCounter}) are printed as
     * {@code key = value}.
     */
    @SuppressWarnings("unchecked")
    private static void printNode(String name, Object value, String indent) {
        if (value instanceof Map<?, ?> map) {
            System.out.println(indent + name + ":");
            ((Map<String, Object>) map).forEach((key, child) -> printNode(key, child, indent + "  "));
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                System.out.println(indent + name + ": (none)");
                return;
            }
            System.out.println(indent + name + ":");
            for (Object element : list) {
                printListEntry(element, indent + "  ");
            }
        } else {
            System.out.println(indent + name + " = " + value);
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
    @SuppressWarnings("unchecked")
    private static void printListEntry(Object element, String indent) {
        if (!(element instanceof Map<?, ?> rawMap)) {
            // Not expected for this API, but fall back to a plain value
            // rather than failing if a future array holds bare scalars.
            System.out.println(indent + element);
            return;
        }
        Map<String, Object> entry = (Map<String, Object>) rawMap;

        StringBuilder labels = new StringBuilder();
        StringBuilder counters = new StringBuilder();
        List<Map.Entry<String, Object>> nested = new ArrayList<>();

        for (Map.Entry<String, Object> field : entry.entrySet()) {
            Object fieldValue = field.getValue();
            if (fieldValue instanceof Map || fieldValue instanceof List) {
                nested.add(field);
            } else if (isCounterField(field.getKey())) {
                if (!counters.isEmpty()) {
                    counters.append(", ");
                }
                counters.append(fieldValue);
            } else {
                if (!labels.isEmpty()) {
                    labels.append(' ');
                }
                labels.append(fieldValue);
            }
        }

        System.out.println(indent + labels + (counters.isEmpty() ? "" : " -> " + counters));
        for (Map.Entry<String, Object> field : nested) {
            printNode(field.getKey(), field.getValue(), indent + "  ");
        }
    }

    /** Most blocks call the numeric field "counter"; {@code complianceCounters} calls it "count". */
    private static boolean isCounterField(String fieldName) {
        return fieldName.equalsIgnoreCase("counter") || fieldName.equalsIgnoreCase("count");
    }
}
