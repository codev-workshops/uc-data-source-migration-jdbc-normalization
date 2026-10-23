package com.workshop.loanservice.benchmark;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Loads the same synthetic portfolio into the legacy CDW tables (all VARCHAR: comma-formatted amounts,
 * MM/DD/YYYY dates, short codes) and the modern schema (typed columns, surrogate-key FKs), so queries
 * against either schema return equivalent results.
 */
final class BenchmarkDataGenerator {

    static final LocalDate LATEST_PAYMENT_MONTH = LocalDate.of(2025, 12, 1);

    private static final int BATCH_SIZE = 5_000;
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String[] FIRST_NAMES = {"James", "Sarah", "Michael", "Emily", "Robert", "Ana", "Wei", "Priya"};
    private static final String[] LAST_NAMES = {"Mitchell", "Chen", "Torres", "Johnson", "Williams", "Garcia", "Patel"};
    private static final String[] STATES = {"IL", "OR", "TX", "CO", "AZ", "CA", "NY", "FL", "WA", "GA"};
    private static final String[][] PRODUCTS = {
        {"FXD30", "30-Year Fixed Rate Mortgage", "FXD", "360", "FIXED"},
        {"FXD15", "15-Year Fixed Rate Mortgage", "FXD", "180", "FIXED"},
        {"ARM51", "5/1 Adjustable Rate Mortgage", "ARM", "360", "VARIABLE"},
        {"FHA30", "FHA 30-Year Fixed", "FHA", "360", "FIXED"},
        {"VA30", "VA 30-Year Fixed", "VA", "360", "FIXED"},
    };
    /** Legacy code, modern value; weighted so roughly 85% of loans are active. */
    private static final String[][] LOAN_STATUSES = {{"ACT", "ACTIVE"}, {"CLO", "CLOSED"}, {"DFT", "DEFAULT"},
        {"FRB", "FORBEARANCE"}};
    private static final String[][] PROPERTY_TYPES = {{"SFR", "Single Family Residence"}, {"CND", "Condominium"},
        {"MFR", "Multi-Family Residence"}, {"TWN", "Townhouse"}};

    private final int loanCount;
    private final int borrowerCount;
    private final int paymentsPerLoan;
    private final Random random;

    BenchmarkDataGenerator(int loanCount, int paymentsPerLoan, long seed) {
        this.loanCount = loanCount;
        this.borrowerCount = Math.max(1, loanCount / 2);
        this.paymentsPerLoan = paymentsPerLoan;
        this.random = new Random(seed);
    }

    static String accountNumber(int loanId) {
        return String.format("LN-%04d-%06d", 2015 + loanId % 10, loanId);
    }

    void load(JdbcTemplate legacy, JdbcTemplate modern) {
        loadBorrowers(legacy, modern);
        loadProducts(legacy, modern);
        loadLoansAndPayments(legacy, modern);
        legacy.execute("ANALYZE");
        modern.execute("ANALYZE");
    }

    private void loadBorrowers(JdbcTemplate legacy, JdbcTemplate modern) {
        Batch legacyRows = new Batch(legacy, "INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM,"
                + " BORR_CTY_NM, BORR_ST_CD, BORR_EMAIL_ADDR, BORR_CRDT_SCR, BORR_ANN_INCM, BORR_STAT_CD)"
                + " VALUES (?,?,?,?,?,?,?,?,?)");
        Batch modernRows = new Batch(modern, "INSERT INTO borrowers (id, external_id, first_name, last_name, city,"
                + " state, email, credit_score, annual_income, status) VALUES (?,?,?,?,?,?,?,?,?,?)");
        for (int id = 1; id <= borrowerCount; id++) {
            String externalId = "B-" + (100_000 + id);
            String first = pick(FIRST_NAMES);
            String last = pick(LAST_NAMES);
            String state = pick(STATES);
            String email = first.toLowerCase(Locale.ROOT) + "." + id + "@example.com";
            int creditScore = 580 + random.nextInt(241);
            long income = 40_000L + random.nextInt(211) * 1_000L;
            legacyRows.add(externalId, first, last, "City " + state, state, email, String.valueOf(creditScore),
                    legacyAmount(income), "ACT");
            modernRows.add((long) id, externalId, first, last, "City " + state, state, email, creditScore,
                    BigDecimal.valueOf(income), "ACTIVE");
        }
        legacyRows.flush();
        modernRows.flush();
    }

    private void loadProducts(JdbcTemplate legacy, JdbcTemplate modern) {
        for (int i = 0; i < PRODUCTS.length; i++) {
            String[] p = PRODUCTS[i];
            legacy.update("INSERT INTO CDW_LN_PROD (PROD_CD, PROD_DESC_TXT, PROD_TYP_CD, PROD_TERM_MOS, PROD_RT_TYP,"
                    + " PROD_STAT_CD) VALUES (?,?,?,?,?,'ACT')", p[0], p[1], p[2], p[3], p[4]);
            modern.update("INSERT INTO loan_products (id, code, name, type, term_months, rate_type, is_active)"
                    + " VALUES (?,?,?,?,?,?,TRUE)", (long) i + 1, p[0], p[1], p[2], Integer.valueOf(p[3]), p[4]);
        }
    }

    private void loadLoansAndPayments(JdbcTemplate legacy, JdbcTemplate modern) {
        Batch legacyLoans = new Batch(legacy, "INSERT INTO CDW_LN_ACCT (LN_ACCT_NBR, BORR_ID, BORR_FST_NM, BORR_LST_NM,"
                + " PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, LN_INT_RT, LN_TERM_MOS, LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT,"
                + " LN_STAT_CD, LN_DLQ_DAYS, PROP_ADDR_LN1, PROP_CTY_NM, PROP_ST_CD, PROP_ZIP_CD, PROP_TYP_CD)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        Batch modernLoans = new Batch(modern, "INSERT INTO loan_accounts (id, account_number, borrower_id, product_id,"
                + " original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date,"
                + " maturity_date, status, delinquency_days, property_address, property_city, property_state,"
                + " property_zip, property_type) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        Batch legacyPayments = new Batch(legacy, "INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT,"
                + " PMT_PRIN_AMT, PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, PMT_STAT_CD)"
                + " VALUES (?,?,?,?,?,?,?,?,'REG','PST')", legacyLoans);
        Batch modernPayments = new Batch(modern, "INSERT INTO payments (id, external_id, loan_account_id, payment_date,"
                + " total_amount, principal_amount, interest_amount, escrow_amount, late_fee, type, status)"
                + " VALUES (?,?,?,?,?,?,?,?,?,'REGULAR','POSTED')", modernLoans);
        long paymentId = 0;
        for (int loanId = 1; loanId <= loanCount; loanId++) {
            String accountNumber = accountNumber(loanId);
            int borrowerId = 1 + random.nextInt(borrowerCount);
            int productIndex = random.nextInt(PRODUCTS.length);
            int termMonths = Integer.parseInt(PRODUCTS[productIndex][3]);
            long original = 100_000L + random.nextInt(701) * 1_000L;
            BigDecimal currentBalance = BigDecimal.valueOf(original)
                    .multiply(BigDecimal.valueOf(50 + random.nextInt(49), 2)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rate = BigDecimal.valueOf(2_500 + random.nextInt(5_001), 3);
            BigDecimal monthlyPayment = monthlyPayment(original, rate, termMonths);
            LocalDate originated = LocalDate.of(2015 + random.nextInt(10), 1 + random.nextInt(12), 1);
            String[] status = loanStatus();
            String[] propertyType = pick(PROPERTY_TYPES);
            String state = pick(STATES);
            String zip = String.format("%05d", random.nextInt(100_000));

            legacyLoans.add(accountNumber, "B-" + (100_000 + borrowerId), pick(FIRST_NAMES), pick(LAST_NAMES),
                    PRODUCTS[productIndex][0], legacyAmount(original), legacyAmount(currentBalance),
                    rate.toPlainString(),
                    String.valueOf(termMonths), legacyAmount(monthlyPayment), legacyDate(originated),
                    legacyDate(originated.plusMonths(termMonths)), status[0], "0", loanId + " Main Street",
                    "City " + state, state, zip, propertyType[0]);
            modernLoans.add((long) loanId, accountNumber, (long) borrowerId, (long) productIndex + 1,
                    BigDecimal.valueOf(original), currentBalance, rate, termMonths, monthlyPayment, originated,
                    originated.plusMonths(termMonths), status[1], 0, loanId + " Main Street", "City " + state, state,
                    zip, propertyType[1]);

            int paymentDay = random.nextBoolean() ? 1 : 15;
            for (int month = 0; month < paymentsPerLoan; month++) {
                paymentId++;
                LocalDate paidOn = LATEST_PAYMENT_MONTH.minusMonths(month).withDayOfMonth(paymentDay);
                BigDecimal interest = monthlyPayment.multiply(new BigDecimal("0.55")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal escrow = monthlyPayment.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal principal = monthlyPayment.subtract(interest).subtract(escrow);
                BigDecimal lateFee = random.nextInt(20) == 0 ? new BigDecimal("47.50") : new BigDecimal("0.00");
                String externalId = String.format("PMT-%010d", paymentId);
                legacyPayments.add(externalId, accountNumber, legacyDate(paidOn), legacyAmount(monthlyPayment),
                        legacyAmount(principal), legacyAmount(interest), legacyAmount(escrow), legacyAmount(lateFee));
                modernPayments.add(paymentId, externalId, (long) loanId, paidOn, monthlyPayment, principal, interest,
                        escrow, lateFee);
            }
        }
        legacyPayments.flush();
        modernPayments.flush();
    }

    private String[] loanStatus() {
        int roll = random.nextInt(100);
        if (roll < 85) {
            return LOAN_STATUSES[0];
        }
        return roll < 93 ? LOAN_STATUSES[1] : roll < 97 ? LOAN_STATUSES[2] : LOAN_STATUSES[3];
    }

    private static BigDecimal monthlyPayment(long principal, BigDecimal annualRatePercent, int termMonths) {
        double monthlyRate = annualRatePercent.doubleValue() / 100 / 12;
        double payment = principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -termMonths));
        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }

    private <T> T pick(T[] values) {
        return values[random.nextInt(values.length)];
    }

    private static String legacyAmount(long wholeDollars) {
        return String.format(Locale.US, "%,d", wholeDollars);
    }

    private static String legacyAmount(BigDecimal amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private static String legacyDate(LocalDate date) {
        return date.format(LEGACY_DATE);
    }

    /** Buffers rows and writes them with JDBC batch inserts, after any rows of the batch they reference. */
    private static final class Batch {

        private final JdbcTemplate jdbc;
        private final String sql;
        private final Batch referenced;
        private final List<Object[]> rows = new ArrayList<>(BATCH_SIZE);

        Batch(JdbcTemplate jdbc, String sql) {
            this(jdbc, sql, null);
        }

        Batch(JdbcTemplate jdbc, String sql, Batch referenced) {
            this.jdbc = jdbc;
            this.sql = sql;
            this.referenced = referenced;
        }

        void add(Object... row) {
            rows.add(row);
            if (rows.size() == BATCH_SIZE) {
                flush();
            }
        }

        void flush() {
            if (referenced != null) {
                referenced.flush();
            }
            if (!rows.isEmpty()) {
                jdbc.batchUpdate(sql, rows);
                rows.clear();
            }
        }
    }
}
