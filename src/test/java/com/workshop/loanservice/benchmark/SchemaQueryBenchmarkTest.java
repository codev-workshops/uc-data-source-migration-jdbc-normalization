package com.workshop.loanservice.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Times representative read queries against the legacy all-VARCHAR CDW schema and the typed, indexed
 * modern schema, loaded with the same synthetic data ({@link BenchmarkDataGenerator}). Skipped in normal
 * builds; run with:
 *
 * <pre>mvn -B test -Dtest=SchemaQueryBenchmarkTest -Dbenchmark=true [-Dbenchmark.loans=20000
 *     -Dbenchmark.paymentsPerLoan=12 -Dbenchmark.warmup=5 -Dbenchmark.iterations=25]</pre>
 *
 * <p>Each query first has its legacy and modern results checked for equivalence, then is warmed up and
 * timed over the measured iterations. The report (timings plus H2 query plans) is printed and written to
 * {@code target/benchmark/schema-query-benchmark.md}.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class SchemaQueryBenchmarkTest {

    private static final int LOANS = Integer.getInteger("benchmark.loans", 20_000);
    private static final int PAYMENTS_PER_LOAN = Integer.getInteger("benchmark.paymentsPerLoan", 12);
    private static final int WARMUP = Integer.getInteger("benchmark.warmup", 5);
    private static final int ITERATIONS = Integer.getInteger("benchmark.iterations", 25);
    private static final long SEED = 42L;
    private static final Path REPORT = Path.of("target/benchmark/schema-query-benchmark.md");
    private static final Pattern LEGACY_AMOUNT = Pattern.compile("-?[\\d,]+(\\.\\d+)?");

    /** Legacy MM/DD/YYYY text rearranged to a sortable YYYYMMDD string (no index can serve it). */
    private static final String LEGACY_PMT_DT_SORTABLE =
            "SUBSTRING(PMT_DT, 7, 4) || SUBSTRING(PMT_DT, 1, 2) || SUBSTRING(PMT_DT, 4, 2)";

    private record Scenario(String name, String legacySql, String modernSql, IntFunction<Object[]> arguments) {
    }

    private record Timing(double minMs, double medianMs, double p95Ms, double meanMs) {
    }

    private record Result(Scenario scenario, int rows, Timing legacy, Timing modern) {

        double speedup() {
            return legacy.medianMs() / modern.medianMs();
        }
    }

    @Test
    void compareLegacyAndModernQueryTimes() throws IOException {
        JdbcTemplate legacy = database("legacy", "schema-legacy.sql");
        JdbcTemplate modern = database("modern", "schema-modern.sql");
        long loadStart = System.nanoTime();
        new BenchmarkDataGenerator(LOANS, PAYMENTS_PER_LOAN, SEED).load(legacy, modern);
        double loadSeconds = (System.nanoTime() - loadStart) / 1e9;

        List<Result> results = new ArrayList<>();
        for (Scenario scenario : scenarios()) {
            int rows = assertEquivalentResults(scenario, legacy, modern);
            results.add(new Result(scenario, rows, time(legacy, scenario.legacySql(), scenario.arguments()),
                    time(modern, scenario.modernSql(), scenario.arguments())));
        }

        String report = report(results, legacy, modern, loadSeconds);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        System.out.println(report);
    }

    private static List<Scenario> scenarios() {
        IntFunction<Object[]> none = iteration -> new Object[0];
        return List.of(
                new Scenario("Filter loans by status",
                        "SELECT LN_ACCT_NBR, LN_CURR_BAL FROM CDW_LN_ACCT WHERE LN_STAT_CD = 'DFT'",
                        "SELECT account_number, current_balance FROM loan_accounts WHERE status = 'DEFAULT'",
                        none),
                new Scenario("Join loans -> borrowers -> products (one state)",
                        "SELECT a.LN_ACCT_NBR, b.BORR_LST_NM, p.PROD_DESC_TXT, a.LN_CURR_BAL FROM CDW_LN_ACCT a"
                                + " JOIN CDW_BORR_MSTR b ON b.BORR_ID = a.BORR_ID"
                                + " JOIN CDW_LN_PROD p ON p.PROD_CD = a.PROD_CD WHERE b.BORR_ST_CD = 'TX'",
                        "SELECT la.account_number, b.last_name, p.name, la.current_balance FROM loan_accounts la"
                                + " JOIN borrowers b ON b.id = la.borrower_id"
                                + " JOIN loan_products p ON p.id = la.product_id WHERE b.state = 'TX'",
                        none),
                new Scenario("Aggregate payment totals per loan",
                        "SELECT LN_ACCT_NBR, COUNT(*), SUM(CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(15, 2)))"
                                + " FROM CDW_PMT_HIST GROUP BY LN_ACCT_NBR",
                        "SELECT la.account_number, COUNT(*), SUM(p.total_amount) FROM payments p"
                                + " JOIN loan_accounts la ON la.id = p.loan_account_id GROUP BY la.account_number",
                        none),
                new Scenario("Payments in a date range (one month)",
                        "SELECT COUNT(*), SUM(CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(15, 2))) FROM CDW_PMT_HIST"
                                + " WHERE " + LEGACY_PMT_DT_SORTABLE + " BETWEEN '20251101' AND '20251130'",
                        "SELECT COUNT(*), SUM(total_amount) FROM payments"
                                + " WHERE payment_date BETWEEN DATE '2025-11-01' AND DATE '2025-11-30'",
                        none),
                new Scenario("Payment history of one loan (GET /api/loans/{id}/payments)",
                        "SELECT PMT_SEQ_NBR, PMT_DT, PMT_AMT FROM CDW_PMT_HIST WHERE LN_ACCT_NBR = ?"
                                + " ORDER BY " + LEGACY_PMT_DT_SORTABLE + " DESC",
                        "SELECT p.external_id, p.payment_date, p.total_amount FROM payments p"
                                + " JOIN loan_accounts la ON la.id = p.loan_account_id"
                                + " WHERE la.account_number = ? ORDER BY p.payment_date DESC",
                        iteration -> new Object[] {
                            BenchmarkDataGenerator.accountNumber(1 + (iteration * 7_919) % LOANS)}),
                new Scenario("Numeric range filter (balance > 500,000)",
                        "SELECT LN_ACCT_NBR FROM CDW_LN_ACCT"
                                + " WHERE CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15, 2)) > 500000",
                        "SELECT account_number FROM loan_accounts WHERE current_balance > 500000",
                        none));
    }

    private static JdbcTemplate database(String name, String schemaScript) {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:bench-" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource(schemaScript)).execute(dataSource);
        return new JdbcTemplate(dataSource);
    }

    /** Both schemas must answer the same question, or the timings are not comparable. */
    private static int assertEquivalentResults(Scenario scenario, JdbcTemplate legacy, JdbcTemplate modern) {
        Object[] arguments = scenario.arguments().apply(0);
        List<String> legacyRows = normalizedRows(legacy, scenario.legacySql(), arguments);
        List<String> modernRows = normalizedRows(modern, scenario.modernSql(), arguments);
        assertThat(legacyRows).as(scenario.name()).isNotEmpty().containsExactlyInAnyOrderElementsOf(modernRows);
        return legacyRows.size();
    }

    private static List<String> normalizedRows(JdbcTemplate jdbc, String sql, Object[] arguments) {
        return jdbc.query(sql, (resultSet, rowNumber) -> {
            int columns = resultSet.getMetaData().getColumnCount();
            List<String> row = new ArrayList<>(columns);
            for (int column = 1; column <= columns; column++) {
                row.add(normalize(resultSet.getObject(column)));
            }
            return row.toString();
        }, arguments);
    }

    /** Numbers by value; legacy comma-formatted amounts as numbers; legacy MM/DD/YYYY dates as ISO dates. */
    private static String normalize(Object value) {
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof String text && LEGACY_AMOUNT.matcher(text).matches()) {
            return new BigDecimal(text.replace(",", "")).stripTrailingZeros().toPlainString();
        }
        if (value instanceof String text && text.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return text.substring(6) + "-" + text.substring(0, 2) + "-" + text.substring(3, 5);
        }
        return String.valueOf(value);
    }

    private static Timing time(JdbcTemplate jdbc, String sql, IntFunction<Object[]> arguments) {
        for (int i = 0; i < WARMUP; i++) {
            consume(jdbc, sql, arguments.apply(i));
        }
        double[] millis = new double[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            Object[] iterationArguments = arguments.apply(WARMUP + i);
            long start = System.nanoTime();
            consume(jdbc, sql, iterationArguments);
            millis[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(millis);
        return new Timing(millis[0], percentile(millis, 50), percentile(millis, 95),
                Arrays.stream(millis).average().orElse(0));
    }

    /** Reads every column of every row, as the application would. */
    private static void consume(JdbcTemplate jdbc, String sql, Object[] arguments) {
        jdbc.query(sql, resultSet -> {
            for (int column = 1; column <= resultSet.getMetaData().getColumnCount(); column++) {
                resultSet.getObject(column);
            }
        }, arguments);
    }

    private static double percentile(double[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static String report(List<Result> results, JdbcTemplate legacy, JdbcTemplate modern, double loadSeconds) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "# Schema query benchmark%n%n"
                        + "H2 %s in-memory, Java %s; %,d borrowers, %,d loans, %,d payments"
                        + " (seed %d, loaded in %.1f s);"
                        + " %d warm-up + %d measured iterations per query.%n%n",
                modern.queryForObject("SELECT H2VERSION()", String.class), System.getProperty("java.version"),
                count(modern, "borrowers"), count(modern, "loan_accounts"), count(modern, "payments"), SEED,
                loadSeconds, WARMUP, ITERATIONS));
        out.append("| Query | Rows | Legacy median (p95) ms | Modern median (p95) ms | Speedup |\n");
        out.append("|---|---:|---:|---:|---:|\n");
        for (Result result : results) {
            out.append(String.format(Locale.ROOT, "| %s | %,d | %.2f (%.2f) | %.2f (%.2f) | %.1fx |%n",
                    result.scenario().name(), result.rows(), result.legacy().medianMs(), result.legacy().p95Ms(),
                    result.modern().medianMs(), result.modern().p95Ms(), result.speedup()));
        }
        out.append("\n## Query plans\n");
        for (Result result : results) {
            Scenario scenario = result.scenario();
            out.append("\n### ").append(scenario.name()).append("\n\nLegacy:\n```\n")
                    .append(plan(legacy, scenario.legacySql(), scenario.arguments().apply(0)))
                    .append("\n```\nModern:\n```\n")
                    .append(plan(modern, scenario.modernSql(), scenario.arguments().apply(0)))
                    .append("\n```\n");
        }
        return out.toString();
    }

    private static String plan(JdbcTemplate jdbc, String sql, Object[] arguments) {
        return jdbc.queryForList("EXPLAIN " + sql, String.class, arguments).stream()
                .collect(Collectors.joining("\n"));
    }

    private static long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
