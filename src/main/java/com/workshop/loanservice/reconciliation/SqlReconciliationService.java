package com.workshop.loanservice.reconciliation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the SQL validation queries in {@code validation/reconciliation_queries.sql}
 * against both data sources and compares the result sets.
 *
 * <p>Complementary to {@link ReconciliationService}, which compares the DTOs the
 * API serves: these checks go straight at the tables, so they also cover data the
 * API never exposes (aggregate totals, orphaned keys, legacy denormalization
 * conflicts). Because the two schemas are in separate databases the comparison
 * cannot be a {@code JOIN}; each check is a pair of queries whose result sets must
 * be identical, with the legacy side doing in SQL the same type conversion and
 * code expansion the migration does in Java.
 */
@Service
public class SqlReconciliationService {

    private static final String QUERY_FILE = "validation/reconciliation_queries.sql";

    private final JdbcTemplate legacyJdbc;
    private final JdbcTemplate modernJdbc;
    private final List<QueryPair> queryPairs;

    public SqlReconciliationService(@Qualifier("legacyDataSource") DataSource legacyDataSource,
                                    @Qualifier("modernDataSource") DataSource modernDataSource) {
        this.legacyJdbc = new JdbcTemplate(legacyDataSource);
        this.modernJdbc = new JdbcTemplate(modernDataSource);
        this.queryPairs = parse(read(QUERY_FILE));
    }

    public ReconciliationReport reconcile() {
        ReconciliationReport report = new ReconciliationReport();
        for (QueryPair pair : queryPairs) {
            String legacyRows = render(legacyJdbc.queryForList(pair.legacySql()));
            String modernRows = render(modernJdbc.queryForList(pair.modernSql()));
            List<String> differences = legacyRows.equals(modernRows)
                    ? List.of()
                    : List.of(pair.name() + ": legacy " + legacyRows + " != modern " + modernRows);
            report.add(pair.name(), legacyRows, modernRows, differences);
        }
        return report;
    }

    /** The parsed checks, so callers (and tests) can see what is being compared. */
    public List<String> checkNames() {
        return queryPairs.stream().map(QueryPair::name).toList();
    }

    /**
     * Renders a result set so equality means "same data", not "same JDBC types":
     * {@code COUNT(*)} comes back as a {@code Long} on one side and a
     * {@code BigDecimal} on the other often enough to matter, and decimals differ
     * in scale.
     */
    private String render(List<Map<String, Object>> rows) {
        List<String> rendered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>();
            row.forEach((column, value) -> cells.add(column.toLowerCase() + "=" + normalize(value)));
            rendered.add(String.join(", ", cells));
        }
        return rendered.toString();
    }

    private String normalize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    private List<QueryPair> parse(String script) {
        List<QueryPair> pairs = new ArrayList<>();
        for (String block : script.split("(?m)^\\s*--\\s*@check\\s+")) {
            if (block.isBlank() || !block.contains("@legacy")) {
                continue;
            }
            String name = block.substring(0, block.indexOf('\n')).trim();
            String legacy = between(block, "@legacy", "@modern");
            String modern = between(block, "@modern", null);
            pairs.add(new QueryPair(name, legacy, modern));
        }
        if (pairs.isEmpty()) {
            throw new IllegalStateException("No validation queries found in " + QUERY_FILE);
        }
        return pairs;
    }

    private String between(String block, String startMarker, String endMarker) {
        int start = block.indexOf(startMarker) + startMarker.length();
        int end = endMarker == null ? block.length() : block.indexOf(endMarker, start);
        if (end < start) {
            throw new IllegalStateException("Malformed check in " + QUERY_FILE + ": " + block);
        }
        return stripComments(block.substring(start, end));
    }

    private String stripComments(String sql) {
        return sql.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .filter(line -> !line.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .map(String::trim)
                .map(statement -> statement.endsWith(";")
                        ? statement.substring(0, statement.length() - 1)
                        : statement)
                .orElseThrow(() -> new IllegalStateException("Empty query in " + QUERY_FILE));
    }

    private String read(String location) {
        try (var reader = new InputStreamReader(
                new ClassPathResource(location).getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + location, e);
        }
    }

    private record QueryPair(String name, String legacySql, String modernSql) {
    }
}
