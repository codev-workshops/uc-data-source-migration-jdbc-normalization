package com.workshop.loanservice.validation;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Parses {@code data/validation/reconciliation_queries.sql} into paired legacy/modern queries and runs
 * them, reporting every row that appears on only one side.
 */
final class ReconciliationQueries {

    static final Path DEFAULT_SCRIPT = Path.of("data/validation/reconciliation_queries.sql");

    private static final String CHECK_MARKER = "-- @check ";
    private static final String LEGACY_MARKER = "-- @legacy";
    private static final String MODERN_MARKER = "-- @modern";

    record Check(String name, String legacySql, String modernSql) {
    }

    record Mismatch(String check, List<List<String>> onlyInLegacy, List<List<String>> onlyInModern) {

        @Override
        public String toString() {
            return check + ": only in legacy " + onlyInLegacy + ", only in modern " + onlyInModern;
        }
    }

    private ReconciliationQueries() {
    }

    static List<Check> load(Path script) throws IOException {
        return parse(Files.readString(script));
    }

    static List<Check> parse(String script) {
        List<Check> checks = new ArrayList<>();
        String name = null;
        StringBuilder legacy = new StringBuilder();
        StringBuilder modern = new StringBuilder();
        StringBuilder current = null;
        for (String line : script.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith(CHECK_MARKER)) {
                addCheck(checks, name, legacy, modern);
                name = trimmed.substring(CHECK_MARKER.length()).trim();
                legacy.setLength(0);
                modern.setLength(0);
                current = null;
            } else if (trimmed.equals(LEGACY_MARKER)) {
                current = legacy;
            } else if (trimmed.equals(MODERN_MARKER)) {
                current = modern;
            } else if (current != null && !trimmed.startsWith("--")) {
                current.append(line).append('\n');
            }
        }
        addCheck(checks, name, legacy, modern);
        return checks;
    }

    static List<Mismatch> run(List<Check> checks, JdbcTemplate legacy, JdbcTemplate modern) {
        List<Mismatch> mismatches = new ArrayList<>();
        for (Check check : checks) {
            Map<List<String>, Integer> legacyRows = rowCounts(legacy, check.legacySql());
            Map<List<String>, Integer> modernRows = rowCounts(modern, check.modernSql());
            List<List<String>> onlyInLegacy = surplus(legacyRows, modernRows);
            List<List<String>> onlyInModern = surplus(modernRows, legacyRows);
            if (!onlyInLegacy.isEmpty() || !onlyInModern.isEmpty()) {
                mismatches.add(new Mismatch(check.name(), onlyInLegacy, onlyInModern));
            }
        }
        return mismatches;
    }

    private static void addCheck(List<Check> checks, String name, StringBuilder legacy, StringBuilder modern) {
        if (name == null) {
            return;
        }
        String legacySql = stripTerminator(legacy);
        String modernSql = stripTerminator(modern);
        if (legacySql.isEmpty() || modernSql.isEmpty()) {
            throw new IllegalArgumentException("Check '" + name + "' needs both an @legacy and an @modern query");
        }
        checks.add(new Check(name, legacySql, modernSql));
    }

    private static String stripTerminator(StringBuilder sql) {
        String trimmed = sql.toString().trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    /** Result rows as a multiset of normalised values. */
    private static Map<List<String>, Integer> rowCounts(JdbcTemplate jdbc, String sql) {
        Map<List<String>, Integer> rows = new HashMap<>();
        jdbc.query(sql, resultSet -> {
            int columns = resultSet.getMetaData().getColumnCount();
            List<String> row = new ArrayList<>(columns);
            for (int column = 1; column <= columns; column++) {
                row.add(normalize(resultSet.getObject(column)));
            }
            rows.merge(row, 1, Integer::sum);
        });
        return rows;
    }

    /** Numbers compare by value regardless of type and scale. */
    private static String normalize(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    private static List<List<String>> surplus(Map<List<String>, Integer> rows, Map<List<String>, Integer> other) {
        List<List<String>> surplus = new ArrayList<>();
        rows.forEach((row, count) -> {
            for (int i = other.getOrDefault(row, 0); i < count; i++) {
                surplus.add(row);
            }
        });
        return surplus;
    }
}
