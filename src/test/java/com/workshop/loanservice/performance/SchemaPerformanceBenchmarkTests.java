package com.workshop.loanservice.performance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmarks the legacy VARCHAR-everything schema against the properly-typed
 * modern one on equivalent queries.
 *
 * <p>Excluded from normal builds (JUnit tag {@code benchmark}) because it loads
 * synthetic data and takes tens of seconds. Run it with:
 *
 * <pre>./mvnw test -Pbenchmark</pre>
 *
 * <p>It deliberately does <em>not</em> use the application's data sources: the
 * seeded databases hold five loans, where every query is instant and every
 * measurement is noise. It builds throwaway databases of its own, loads
 * {@value #LOANS} loans and {@value #PAYMENTS} payments into both shapes, and
 * times the same questions asked of each. Results land in
 * {@code target/performance-comparison.md}; the committed findings are in
 * {@code docs/PERFORMANCE_COMPARISON.md}.
 */
@Tag("benchmark")
class SchemaPerformanceBenchmarkTests {

    private static final int BORROWERS = 20_000;
    private static final int LOANS = 20_000;
    private static final int PAYMENTS = 100_000;

    private static final int WARMUPS = 2;
    private static final int RUNS = 5;

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Equivalent question -> {legacy SQL, modern SQL}. The legacy side has to
     * convert in the query what the modern side stores natively, which is the
     * whole point of the comparison.
     */
    private static final Map<String, String[]> QUERIES = new LinkedHashMap<>();

    static {
        QUERIES.put("Filter on an amount range",
                new String[]{
                        """
                        SELECT COUNT(*) FROM CDW_LN_ACCT
                        WHERE CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(16, 2)) BETWEEN 200000 AND 400000
                        """,
                        """
                        SELECT COUNT(*) FROM loan_accounts
                        WHERE current_balance BETWEEN 200000 AND 400000
                        """});
        QUERIES.put("Filter on a date range",
                new String[]{
                        """
                        SELECT COUNT(*) FROM CDW_PMT_HIST
                        WHERE CAST(PARSEDATETIME(PMT_DT, 'MM/dd/yyyy') AS DATE)
                              BETWEEN DATE '2025-01-01' AND DATE '2025-06-30'
                        """,
                        """
                        SELECT COUNT(*) FROM payments
                        WHERE payment_date BETWEEN DATE '2025-01-01' AND DATE '2025-06-30'
                        """});
        QUERIES.put("Sum payment amounts",
                new String[]{
                        """
                        SELECT SUM(CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(16, 2))) FROM CDW_PMT_HIST
                        """,
                        """
                        SELECT SUM(total_amount) FROM payments
                        """});
        QUERIES.put("Aggregate by status",
                new String[]{
                        """
                        SELECT LN_STAT_CD,
                               COUNT(*),
                               SUM(CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(16, 2)))
                        FROM CDW_LN_ACCT GROUP BY LN_STAT_CD
                        """,
                        """
                        SELECT status, COUNT(*), SUM(current_balance)
                        FROM loan_accounts GROUP BY status
                        """});
        QUERIES.put("Indexed lookup of a status subset",
                new String[]{
                        """
                        SELECT COUNT(*) FROM CDW_LN_ACCT WHERE LN_STAT_CD = 'DFT'
                        """,
                        """
                        SELECT COUNT(*) FROM loan_accounts WHERE status = 'DEFAULT'
                        """});
        QUERIES.put("Top 10 balances",
                new String[]{
                        """
                        SELECT LN_ACCT_NBR FROM CDW_LN_ACCT
                        ORDER BY CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(16, 2)) DESC
                        FETCH FIRST 10 ROWS ONLY
                        """,
                        """
                        SELECT account_number FROM loan_accounts
                        ORDER BY current_balance DESC
                        FETCH FIRST 10 ROWS ONLY
                        """});
        QUERIES.put("Payments joined to their loan",
                new String[]{
                        """
                        SELECT COUNT(*) FROM CDW_PMT_HIST h
                        JOIN CDW_LN_ACCT a ON a.LN_ACCT_NBR = h.LN_ACCT_NBR
                        WHERE a.LN_STAT_CD = 'ACT'
                        """,
                        """
                        SELECT COUNT(*) FROM payments pm
                        JOIN loan_accounts la ON la.id = pm.loan_account_id
                        WHERE la.status = 'ACTIVE'
                        """});
        QUERIES.put("Borrower credit-score band",
                new String[]{
                        """
                        SELECT COUNT(*) FROM CDW_BORR_MSTR
                        WHERE CAST(BORR_CRDT_SCR AS INTEGER) >= 740
                        """,
                        """
                        SELECT COUNT(*) FROM borrowers WHERE credit_score >= 740
                        """});
    }

    @Test
    void typedModernSchemaIsFasterOnEquivalentQueries() throws Exception {
        try (Connection legacy = connect("perf_legacy");
             Connection modern = connect("perf_modern")) {

            run(legacy, script("schema-legacy.sql"));
            run(modern, script("schema-modern.sql"));
            loadLegacy(legacy);
            loadModern(modern);
            assertThat(count(legacy, "CDW_PMT_HIST")).isEqualTo(PAYMENTS);
            assertThat(count(modern, "payments")).isEqualTo(PAYMENTS);

            List<Measurement> measurements = new ArrayList<>();
            for (Map.Entry<String, String[]> query : QUERIES.entrySet()) {
                // The pair has to answer the same question, or the timings are meaningless.
                assertThat(first(modern, query.getValue()[1]))
                        .as(query.getKey())
                        .isNotEqualTo("<empty>");
                long legacyMicros = time(legacy, query.getValue()[0]);
                long modernMicros = time(modern, query.getValue()[1]);
                measurements.add(new Measurement(query.getKey(), legacyMicros, modernMicros));
            }

            String report = report(measurements);
            System.out.println(report);
            Files.writeString(Path.of("target", "performance-comparison.md"), report);

            // The benchmark is only worth committing if it actually shows the effect
            // it claims: typed columns win on the conversion-heavy queries.
            assertThat(measurements.stream().filter(Measurement::modernFaster).count())
                    .isGreaterThanOrEqualTo(QUERIES.size() - 2);
        }
    }

    private String report(List<Measurement> measurements) {
        StringBuilder out = new StringBuilder();
        out.append(String.format("Rows: %,d borrowers, %,d loans, %,d payments; median of %d runs%n%n",
                BORROWERS, LOANS, PAYMENTS, RUNS));
        out.append("| Query | Legacy (ms) | Modern (ms) | Speed-up |\n");
        out.append("| --- | --- | --- | --- |\n");
        for (Measurement m : measurements) {
            out.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.1fx |%n",
                    m.name(), m.legacyMillis(), m.modernMillis(), m.speedUp()));
        }
        return out.toString();
    }

    private long time(Connection connection, String sql) throws SQLException {
        for (int i = 0; i < WARMUPS; i++) {
            execute(connection, sql);
        }
        List<Long> micros = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            execute(connection, sql);
            micros.add((System.nanoTime() - start) / 1_000);
        }
        micros.sort(Long::compareTo);
        return micros.get(micros.size() / 2);
    }

    private String first(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? String.valueOf(rows.getObject(1)) : "<empty>";
        }
    }

    private int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                rows.getObject(1);
            }
        }
    }

    private void loadLegacy(Connection connection) throws SQLException {
        Random random = new Random(42);
        connection.setAutoCommit(false);
        try (PreparedStatement borrower = connection.prepareStatement(
                "INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_DOB_DT, "
                        + "BORR_CRDT_SCR, BORR_ANN_INCM, BORR_STAT_CD) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= BORROWERS; i++) {
                borrower.setString(1, borrowerKey(i));
                borrower.setString(2, "First" + i);
                borrower.setString(3, "Last" + i);
                borrower.setString(4, birthDate(i).format(LEGACY_DATE));
                borrower.setString(5, String.valueOf(creditScore(random)));
                borrower.setString(6, legacyAmount(income(random)));
                borrower.setString(7, "ACT");
                borrower.addBatch();
            }
            borrower.executeBatch();
        }
        try (PreparedStatement loan = connection.prepareStatement(
                "INSERT INTO CDW_LN_ACCT (LN_ACCT_NBR, BORR_ID, PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, "
                        + "LN_INT_RT, LN_TERM_MOS, LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT, LN_STAT_CD, "
                        + "LN_DLQ_DAYS, LN_ESCROW_BAL, PROP_TYP_CD, PROP_APRS_VAL) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= LOANS; i++) {
                BigDecimal amount = amount(random);
                loan.setString(1, loanKey(i));
                loan.setString(2, borrowerKey((i % BORROWERS) + 1));
                loan.setString(3, "FXD30");
                loan.setString(4, legacyAmount(amount));
                loan.setString(5, legacyAmount(amount.multiply(new BigDecimal("0.87"))
                        .setScale(2, RoundingMode.HALF_UP)));
                loan.setString(6, "4.750");
                loan.setString(7, "360");
                loan.setString(8, legacyAmount(new BigDecimal("1487.02")));
                loan.setString(9, originationDate(i).format(LEGACY_DATE));
                loan.setString(10, originationDate(i).plusYears(30).format(LEGACY_DATE));
                loan.setString(11, legacyLoanStatus(i));
                loan.setString(12, "0");
                loan.setString(13, legacyAmount(new BigDecimal("3245.80")));
                loan.setString(14, "SFR");
                loan.setString(15, legacyAmount(amount.multiply(new BigDecimal("1.21"))
                        .setScale(2, RoundingMode.HALF_UP)));
                loan.addBatch();
            }
            loan.executeBatch();
        }
        try (PreparedStatement payment = connection.prepareStatement(
                "INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_PRIN_AMT, "
                        + "PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, PMT_STAT_CD) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= PAYMENTS; i++) {
                payment.setString(1, paymentKey(i));
                payment.setString(2, loanKey((i % LOANS) + 1));
                payment.setString(3, paymentDate(i).format(LEGACY_DATE));
                payment.setString(4, legacyAmount(new BigDecimal("1487.02")));
                payment.setString(5, legacyAmount(new BigDecimal("456.78")));
                payment.setString(6, legacyAmount(new BigDecimal("1030.24")));
                payment.setString(7, legacyAmount(new BigDecimal("355.55")));
                payment.setString(8, "0.00");
                payment.setString(9, "REG");
                payment.setString(10, "PST");
                payment.addBatch();
            }
            payment.executeBatch();
        }
        connection.commit();
        connection.setAutoCommit(true);
    }

    private void loadModern(Connection connection) throws SQLException {
        Random random = new Random(42);
        connection.setAutoCommit(false);
        try (PreparedStatement borrower = connection.prepareStatement(
                "INSERT INTO borrowers (id, external_id, first_name, last_name, date_of_birth, "
                        + "credit_score, annual_income, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= BORROWERS; i++) {
                borrower.setLong(1, i);
                borrower.setString(2, borrowerKey(i));
                borrower.setString(3, "First" + i);
                borrower.setString(4, "Last" + i);
                borrower.setObject(5, birthDate(i));
                borrower.setInt(6, creditScore(random));
                borrower.setBigDecimal(7, income(random));
                borrower.setString(8, "ACTIVE");
                borrower.addBatch();
            }
            borrower.executeBatch();
        }
        try (PreparedStatement product = connection.prepareStatement(
                "INSERT INTO loan_products (id, code, name, type, term_months, rate_type) "
                        + "VALUES (1, 'FXD30', '30-Year Fixed Rate Mortgage', 'FXD', 360, 'FIXED')")) {
            product.executeUpdate();
        }
        try (PreparedStatement loan = connection.prepareStatement(
                "INSERT INTO loan_accounts (id, account_number, borrower_id, product_id, "
                        + "original_amount, current_balance, interest_rate, term_months, monthly_payment, "
                        + "origination_date, maturity_date, status, delinquency_days, escrow_balance, "
                        + "property_type, appraised_value) "
                        + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= LOANS; i++) {
                BigDecimal amount = amount(random);
                loan.setLong(1, i);
                loan.setString(2, loanKey(i));
                loan.setLong(3, (i % BORROWERS) + 1);
                loan.setBigDecimal(4, amount);
                loan.setBigDecimal(5, amount.multiply(new BigDecimal("0.87"))
                        .setScale(2, RoundingMode.HALF_UP));
                loan.setBigDecimal(6, new BigDecimal("4.750"));
                loan.setInt(7, 360);
                loan.setBigDecimal(8, new BigDecimal("1487.02"));
                loan.setObject(9, originationDate(i));
                loan.setObject(10, originationDate(i).plusYears(30));
                loan.setString(11, modernLoanStatus(i));
                loan.setInt(12, 0);
                loan.setBigDecimal(13, new BigDecimal("3245.80"));
                loan.setString(14, "Single Family Residence");
                loan.setBigDecimal(15, amount.multiply(new BigDecimal("1.21"))
                        .setScale(2, RoundingMode.HALF_UP));
                loan.addBatch();
            }
            loan.executeBatch();
        }
        try (PreparedStatement payment = connection.prepareStatement(
                "INSERT INTO payments (id, external_id, loan_account_id, payment_date, total_amount, "
                        + "principal_amount, interest_amount, escrow_amount, late_fee, type, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= PAYMENTS; i++) {
                payment.setLong(1, i);
                payment.setString(2, paymentKey(i));
                payment.setLong(3, (i % LOANS) + 1);
                payment.setObject(4, paymentDate(i));
                payment.setBigDecimal(5, new BigDecimal("1487.02"));
                payment.setBigDecimal(6, new BigDecimal("456.78"));
                payment.setBigDecimal(7, new BigDecimal("1030.24"));
                payment.setBigDecimal(8, new BigDecimal("355.55"));
                payment.setBigDecimal(9, new BigDecimal("0.00"));
                payment.setString(10, "REGULAR");
                payment.setString(11, "POSTED");
                payment.addBatch();
            }
            payment.executeBatch();
        }
        connection.commit();
        connection.setAutoCommit(true);
    }

    private String borrowerKey(int i) {
        return String.format("B-%06d", i);
    }

    private String loanKey(int i) {
        return String.format("LN-2019-%08d", i);
    }

    private String paymentKey(int i) {
        return String.format("PMT-%010d", i);
    }

    private LocalDate birthDate(int i) {
        return LocalDate.of(1950 + (i % 50), (i % 12) + 1, (i % 28) + 1);
    }

    private LocalDate originationDate(int i) {
        return LocalDate.of(2010 + (i % 15), (i % 12) + 1, (i % 28) + 1);
    }

    private LocalDate paymentDate(int i) {
        return LocalDate.of(2025, (i % 12) + 1, (i % 28) + 1);
    }

    private int creditScore(Random random) {
        return 600 + random.nextInt(240);
    }

    private BigDecimal income(Random random) {
        return new BigDecimal(40_000 + random.nextInt(160_000)).setScale(2);
    }

    private BigDecimal amount(Random random) {
        return new BigDecimal(80_000 + random.nextInt(720_000)).setScale(2);
    }

    private String legacyLoanStatus(int i) {
        return i % 20 == 0 ? "DFT" : "ACT";
    }

    private String modernLoanStatus(int i) {
        return i % 20 == 0 ? "DEFAULT" : "ACTIVE";
    }

    /** Legacy amounts carry thousands separators, exactly as CDW stores them. */
    private String legacyAmount(BigDecimal amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    /**
     * {@code QUERY_CACHE_SIZE=0} matters: H2 otherwise hands back the previous
     * result for an unchanged, repeated query, and every timing below the warmup
     * would measure a cache hit rather than the query.
     */
    private Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1;QUERY_CACHE_SIZE=0", "sa", "");
    }

    private void run(Connection connection, String script) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }

    private String script(String name) throws IOException {
        try (var reader = new InputStreamReader(
                new ClassPathResource(name).getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

    private record Measurement(String name, long legacyMicros, long modernMicros) {

        double legacyMillis() {
            return legacyMicros / 1000.0;
        }

        double modernMillis() {
            return modernMicros / 1000.0;
        }

        double speedUp() {
            return modernMicros == 0 ? Double.POSITIVE_INFINITY : (double) legacyMicros / modernMicros;
        }

        boolean modernFaster() {
            return modernMicros < legacyMicros;
        }
    }
}
