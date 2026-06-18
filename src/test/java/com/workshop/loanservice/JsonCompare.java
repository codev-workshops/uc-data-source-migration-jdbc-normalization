package com.workshop.loanservice;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Numeric-aware JSON tree comparison shared by the golden and reconciliation
 * tests. Numbers are compared by value (so {@code 285000} and {@code 285000.00}
 * are equal — see DATA_SOURCE_MIGRATION_NOTES.md); everything else (field
 * presence, strings, ids, date formats, display values, array ordering) must
 * match exactly. Returns a list of human-readable diffs (empty == equal).
 */
final class JsonCompare {

    private JsonCompare() {
    }

    static List<String> diff(JsonNode expected, JsonNode actual) {
        List<String> diffs = new ArrayList<>();
        compare("$", expected, actual, diffs);
        return diffs;
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> diffs) {
        if (expected.isNumber() && actual.isNumber()) {
            if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                diffs.add(path + ": expected " + expected + " but was " + actual);
            }
            return;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            diffs.add(path + ": expected " + expected + " but was " + actual);
            return;
        }
        if (expected.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String name = field.getKey();
                if (!actual.has(name)) {
                    diffs.add(path + "." + name + ": missing in response");
                } else {
                    compare(path + "." + name, field.getValue(), actual.get(name), diffs);
                }
            }
            for (Iterator<String> it = actual.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!expected.has(name)) {
                    diffs.add(path + "." + name + ": unexpected field (" + actual.get(name) + ")");
                }
            }
        } else if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                diffs.add(path + ": expected array of size " + expected.size()
                        + " but was " + actual.size());
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                compare(path + "[" + i + "]", expected.get(i), actual.get(i), diffs);
            }
        } else if (!expected.equals(actual)) {
            diffs.add(path + ": expected " + expected + " but was " + actual);
        }
    }
}
