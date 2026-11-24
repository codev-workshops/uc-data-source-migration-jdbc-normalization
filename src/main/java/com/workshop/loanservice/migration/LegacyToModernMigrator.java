package com.workshop.loanservice.migration;

import com.workshop.loanservice.migration.LegacyValueParser.DecimalSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copies the legacy CDW tables into the normalized modern schema over plain JDBC.
 *
 * <p>Parents are migrated before children (borrowers, loan products, loan accounts, payments).
 * Each legacy row is parsed into typed values, its codes are expanded and its string foreign
 * keys are resolved to modern surrogate keys. Legacy tables are streamed row by row; only the
 * natural-key indexes needed for idempotency and FK resolution are kept in memory.
 *
 * <ul>
 *   <li>Rows whose natural key already exists in the modern schema are skipped, so the
 *       migration can be re-run.</li>
 *   <li>A natural key that repeats within a legacy table is migrated once; later rows are
 *       reported as duplicates.</li>
 *   <li>Rows that cannot be converted, or whose parent was rejected or doesn't exist, are
 *       rejected and reported. Each row is inserted under its own savepoint, so a failed
 *       insert never aborts the run.</li>
 * </ul>
 */
@Component
public class LegacyToModernMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyToModernMigrator.class);

    private static final int FETCH_SIZE = 500;

    private static final DecimalSpec DECIMAL_12_2 = new DecimalSpec(12, 2);
    private static final DecimalSpec DECIMAL_10_2 = new DecimalSpec(10, 2);
    private static final DecimalSpec DECIMAL_5_3 = new DecimalSpec(5, 3);
    private static final DecimalSpec DECIMAL_5_2 = new DecimalSpec(5, 2);

    private final JdbcTemplate legacy;
    private final JdbcTemplate modern;

    public LegacyToModernMigrator(@Qualifier("legacyJdbcTemplate") JdbcTemplate legacyJdbcTemplate,
                                  @Qualifier("modernJdbcTemplate") JdbcTemplate modern) {
        this.legacy = new JdbcTemplate(Objects.requireNonNull(legacyJdbcTemplate.getDataSource()));
        this.legacy.setFetchSize(FETCH_SIZE);
        this.modern = modern;
    }

    @Transactional("transactionManager")
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        MigrationReport.TableReport borrowers = report.table("borrowers");
        MigrationReport.TableReport products = report.table("loan_products");
        MigrationReport.TableReport accounts = report.table("loan_accounts");
        migrateBorrowers(borrowers);
        migrateLoanProducts(products);
        migrateLoanAccounts(accounts, borrowers, products);
        migratePayments(report.table("payments"), accounts);
        return report;
    }

    // Legacy rows are read in table order (no ORDER BY), the same order the legacy API
    // listed them in, so modern ids and therefore modern list ordering match.

    private void migrateBorrowers(MigrationReport.TableReport report) {
        copy(report, "SELECT * FROM CDW_BORR_MSTR", "BORR_ID", keys("SELECT external_id FROM borrowers"), row ->
                modern.update("""
                        INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash,
                            date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email,
                            credit_score, employment_status, annual_income, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, CURRENT_TIMESTAMP))""",
                        row.text("BORR_ID"),
                        row.required("BORR_FST_NM", row.text("BORR_FST_NM")),
                        row.required("BORR_LST_NM", row.text("BORR_LST_NM")),
                        row.text("BORR_MID_INIT"),
                        row.text("BORR_SSN_ENCR"),
                        row.date("BORR_DOB_DT"),
                        row.text("BORR_ADDR_LN1"),
                        row.text("BORR_ADDR_LN2"),
                        row.text("BORR_CTY_NM"),
                        row.text("BORR_ST_CD"),
                        row.text("BORR_ZIP_CD"),
                        row.text("BORR_PH_NBR"),
                        row.text("BORR_EMAIL_ADDR"),
                        row.integer("BORR_CRDT_SCR"),
                        row.text("BORR_EMP_STAT"),
                        row.amount("BORR_ANN_INCM", DECIMAL_12_2),
                        LegacyCodeMappings.borrowerStatus(row.raw("BORR_STAT_CD")).name(),
                        row.timestamp("BORR_CRET_DT"),
                        row.timestamp("BORR_UPDT_DT")));
    }

    private void migrateLoanProducts(MigrationReport.TableReport report) {
        copy(report, "SELECT * FROM CDW_LN_PROD", "PROD_CD", keys("SELECT code FROM loan_products"), row ->
                modern.update("""
                        INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount,
                            max_amount, is_active, effective_date, expiration_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        row.text("PROD_CD"),
                        row.required("PROD_DESC_TXT", row.text("PROD_DESC_TXT")),
                        row.required("PROD_TYP_CD", row.text("PROD_TYP_CD")),
                        row.required("PROD_TERM_MOS", row.integer("PROD_TERM_MOS")),
                        row.required("PROD_RT_TYP", row.text("PROD_RT_TYP")),
                        row.amount("PROD_MIN_AMT", DECIMAL_12_2),
                        row.amount("PROD_MAX_AMT", DECIMAL_12_2),
                        LegacyCodeMappings.productActive(row.raw("PROD_STAT_CD")),
                        row.date("PROD_EFF_DT"),
                        row.date("PROD_EXP_DT")));
    }

    private void migrateLoanAccounts(MigrationReport.TableReport report,
                                     MigrationReport.TableReport borrowers,
                                     MigrationReport.TableReport products) {
        Map<String, Long> borrowerIds = ids("SELECT external_id, id FROM borrowers");
        Map<String, Long> productIds = ids("SELECT code, id FROM loan_products");
        copy(report, "SELECT * FROM CDW_LN_ACCT", "LN_ACCT_NBR", keys("SELECT account_number FROM loan_accounts"), row ->
                modern.update("""
                        INSERT INTO loan_accounts (account_number, borrower_id, product_id, original_amount,
                            current_balance, interest_rate, term_months, monthly_payment, origination_date,
                            maturity_date, first_payment_date, next_payment_date, status, delinquency_days,
                            escrow_balance, ltv_percent, property_address, property_city, property_state,
                            property_zip, property_type, appraised_value, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, CURRENT_TIMESTAMP))""",
                        row.text("LN_ACCT_NBR"),
                        resolve(borrowerIds, borrowers, "BORR_ID", row.text("BORR_ID"), "borrower"),
                        resolve(productIds, products, "PROD_CD", row.text("PROD_CD"), "loan product"),
                        row.required("LN_ORIG_AMT", row.amount("LN_ORIG_AMT", DECIMAL_12_2)),
                        row.required("LN_CURR_BAL", row.amount("LN_CURR_BAL", DECIMAL_12_2)),
                        row.required("LN_INT_RT", row.decimal("LN_INT_RT", DECIMAL_5_3)),
                        row.required("LN_TERM_MOS", row.integer("LN_TERM_MOS")),
                        row.required("LN_PMT_AMT", row.amount("LN_PMT_AMT", DECIMAL_10_2)),
                        row.required("LN_ORIG_DT", row.date("LN_ORIG_DT")),
                        row.required("LN_MAT_DT", row.date("LN_MAT_DT")),
                        row.date("LN_1ST_PMT_DT"),
                        row.date("LN_NXT_PMT_DT"),
                        LegacyCodeMappings.loanStatus(row.raw("LN_STAT_CD")).name(),
                        Objects.requireNonNullElse(row.integer("LN_DLQ_DAYS"), 0),
                        Objects.requireNonNullElse(row.amount("LN_ESCROW_BAL", DECIMAL_10_2), BigDecimal.ZERO),
                        row.decimal("LN_LTV_PCT", DECIMAL_5_2),
                        row.text("PROP_ADDR_LN1"),
                        row.text("PROP_CTY_NM"),
                        row.text("PROP_ST_CD"),
                        row.text("PROP_ZIP_CD"),
                        LegacyCodeMappings.propertyType(row.raw("PROP_TYP_CD")),
                        row.amount("PROP_APRS_VAL", DECIMAL_12_2),
                        row.timestamp("LN_CRET_DT"),
                        row.timestamp("LN_UPDT_DT")));
    }

    private void migratePayments(MigrationReport.TableReport report, MigrationReport.TableReport accounts) {
        Map<String, Long> loanAccountIds = ids("SELECT account_number, id FROM loan_accounts");
        copy(report, "SELECT * FROM CDW_PMT_HIST", "PMT_SEQ_NBR", keys("SELECT external_id FROM payments"), row ->
                modern.update("""
                        INSERT INTO payments (external_id, loan_account_id, payment_date, total_amount,
                            principal_amount, interest_amount, escrow_amount, late_fee, type, status,
                            received_date, processed_date, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, CURRENT_TIMESTAMP))""",
                        row.text("PMT_SEQ_NBR"),
                        resolve(loanAccountIds, accounts, "LN_ACCT_NBR", row.text("LN_ACCT_NBR"), "loan account"),
                        row.required("PMT_DT", row.date("PMT_DT")),
                        row.required("PMT_AMT", row.amount("PMT_AMT", DECIMAL_10_2)),
                        row.amount("PMT_PRIN_AMT", DECIMAL_10_2),
                        row.amount("PMT_INT_AMT", DECIMAL_10_2),
                        row.amount("PMT_ESCROW_AMT", DECIMAL_10_2),
                        Objects.requireNonNullElse(row.amount("PMT_LATE_FEE", DECIMAL_10_2), BigDecimal.ZERO),
                        LegacyCodeMappings.paymentType(row.raw("PMT_TYP_CD")).name(),
                        LegacyCodeMappings.paymentStatus(row.raw("PMT_STAT_CD")).name(),
                        row.date("PMT_RECV_DT"),
                        row.date("PMT_PROC_DT"),
                        row.timestamp("PMT_CRET_DT"),
                        row.timestamp("PMT_UPDT_DT")));
    }

    /**
     * Compares legacy and modern data: row counts, amount totals and foreign-key mappings.
     * Legacy rows the report lists as rejected or duplicate are excluded from the expected
     * values, so only unexplained differences fail a check.
     */
    public List<ReconciliationCheck> reconcile(MigrationReport report) {
        List<ReconciliationCheck> checks = new ArrayList<>();
        checkRowCount(checks, "CDW_BORR_MSTR", "borrowers", report);
        checkRowCount(checks, "CDW_LN_PROD", "loan_products", report);
        checkRowCount(checks, "CDW_LN_ACCT", "loan_accounts", report);
        checkRowCount(checks, "CDW_PMT_HIST", "payments", report);

        checkAmountTotal(checks, "CDW_BORR_MSTR", "BORR_ID", "BORR_ANN_INCM", "borrowers", "annual_income", report);
        checkAmountTotal(checks, "CDW_LN_PROD", "PROD_CD", "PROD_MIN_AMT", "loan_products", "min_amount", report);
        checkAmountTotal(checks, "CDW_LN_PROD", "PROD_CD", "PROD_MAX_AMT", "loan_products", "max_amount", report);
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ACCT_NBR", "LN_ORIG_AMT", "loan_accounts", "original_amount", report);
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ACCT_NBR", "LN_CURR_BAL", "loan_accounts", "current_balance", report);
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ACCT_NBR", "LN_PMT_AMT", "loan_accounts", "monthly_payment", report);
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ACCT_NBR", "LN_ESCROW_BAL", "loan_accounts", "escrow_balance", report);
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ACCT_NBR", "PROP_APRS_VAL", "loan_accounts", "appraised_value", report);
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT_AMT", "payments", "total_amount", report);
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT_PRIN_AMT", "payments", "principal_amount", report);
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT_INT_AMT", "payments", "interest_amount", report);
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT_ESCROW_AMT", "payments", "escrow_amount", report);
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_SEQ_NBR", "PMT_LATE_FEE", "payments", "late_fee", report);

        checkForeignKey(checks, "loan_accounts.borrower_id", report.get("loan_accounts"),
                "SELECT LN_ACCT_NBR, BORR_ID FROM CDW_LN_ACCT", "SELECT BORR_ID FROM CDW_BORR_MSTR",
                "SELECT a.account_number, b.external_id FROM loan_accounts a JOIN borrowers b ON b.id = a.borrower_id",
                "SELECT COUNT(*) FROM loan_accounts a LEFT JOIN borrowers b ON b.id = a.borrower_id WHERE b.id IS NULL");
        checkForeignKey(checks, "loan_accounts.product_id", report.get("loan_accounts"),
                "SELECT LN_ACCT_NBR, PROD_CD FROM CDW_LN_ACCT", "SELECT PROD_CD FROM CDW_LN_PROD",
                "SELECT a.account_number, p.code FROM loan_accounts a JOIN loan_products p ON p.id = a.product_id",
                "SELECT COUNT(*) FROM loan_accounts a LEFT JOIN loan_products p ON p.id = a.product_id WHERE p.id IS NULL");
        checkForeignKey(checks, "payments.loan_account_id", report.get("payments"),
                "SELECT PMT_SEQ_NBR, LN_ACCT_NBR FROM CDW_PMT_HIST", "SELECT LN_ACCT_NBR FROM CDW_LN_ACCT",
                "SELECT p.external_id, a.account_number FROM payments p JOIN loan_accounts a ON a.id = p.loan_account_id",
                "SELECT COUNT(*) FROM payments p LEFT JOIN loan_accounts a ON a.id = p.loan_account_id WHERE a.id IS NULL");
        return checks;
    }

    private void copy(MigrationReport.TableReport report, String legacySql, String keyColumn,
                      Set<String> existingKeys, Consumer<LegacyRow> insert) {
        Set<String> seenInSource = new HashSet<>();
        legacy.query(legacySql, rs -> {
            LegacyRow row = LegacyRow.of(rs);
            report.recordRead();
            String key = row.text(keyColumn);
            if (key == null) {
                reject(report, null, keyColumn + " is blank");
                return;
            }
            if (!seenInSource.add(key)) {
                String reason = "duplicate " + keyColumn + " in legacy source; only the first occurrence is migrated";
                log.warn("Skipped legacy {} record {}: {}", report.getTable(), key, reason);
                report.recordDuplicate(key, reason);
                return;
            }
            if (existingKeys.contains(key)) {
                report.recordSkippedExisting();
                return;
            }
            try {
                inSavepoint(() -> insert.accept(row));
                existingKeys.add(key);
                report.recordInserted();
            } catch (MalformedLegacyDataException e) {
                reject(report, key, e.getMessage());
            } catch (DataAccessException e) {
                reject(report, key, e.getMostSpecificCause().getMessage());
            }
        });
    }

    /**
     * Runs one row's inserts inside a JDBC savepoint of the surrounding migration transaction, so a failed
     * row leaves no partial writes behind. JpaTransactionManager cannot create savepoints itself.
     */
    private void inSavepoint(Runnable work) {
        DataSource dataSource = Objects.requireNonNull(modern.getDataSource());
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            Savepoint savepoint = connection.setSavepoint();
            try {
                work.run();
            } catch (RuntimeException e) {
                connection.rollback(savepoint);
                throw e;
            }
            connection.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            throw new TransactionSystemException("Savepoint handling failed during legacy migration", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void reject(MigrationReport.TableReport report, String key, String reason) {
        log.warn("Rejected legacy {} record {}: {}", report.getTable(), key, reason);
        report.recordRejected(key, reason);
    }

    private static Long resolve(Map<String, Long> ids, MigrationReport.TableReport parent,
                                String column, String key, String target) {
        if (key == null) {
            throw new MalformedLegacyDataException(column, null, "missing " + target + " reference");
        }
        Long id = ids.get(key);
        if (id != null) {
            return id;
        }
        if (parent.isRejected(key)) {
            throw new MalformedLegacyDataException(column, key, target + " " + key + " was rejected");
        }
        throw new MalformedLegacyDataException(column, key, "orphaned reference: no " + target + " with this key");
    }

    private Set<String> keys(String sql) {
        return new HashSet<>(modern.queryForList(sql, String.class));
    }

    private Map<String, Long> ids(String sql) {
        Map<String, Long> ids = new HashMap<>();
        modern.query(sql, rs -> {
            ids.put(rs.getString(1), rs.getLong(2));
        });
        return ids;
    }

    private void checkRowCount(List<ReconciliationCheck> checks, String legacyTable, String modernTable,
                               MigrationReport report) {
        long legacyCount = count(legacy, "SELECT COUNT(*) FROM " + legacyTable);
        long modernCount = count(modern, "SELECT COUNT(*) FROM " + modernTable);
        long excluded = report.get(modernTable).getExcludedCount();
        long expected = legacyCount - excluded;
        checks.add(new ReconciliationCheck(modernTable + " row count",
                expected + " (" + legacyCount + " rows - " + excluded + " rejected/duplicate)",
                String.valueOf(modernCount), expected == modernCount));
    }

    private void checkAmountTotal(List<ReconciliationCheck> checks, String legacyTable, String keyColumn,
                                  String legacyColumn, String modernTable, String modernColumn, MigrationReport report) {
        MigrationReport.TableReport tableReport = report.get(modernTable);
        Set<String> seen = new HashSet<>();
        BigDecimal[] legacyTotal = {BigDecimal.ZERO};
        int[] unparseable = {0};
        legacy.query("SELECT " + keyColumn + ", " + legacyColumn + " FROM " + legacyTable, rs -> {
            String key = LegacyValueParser.clean(rs.getString(1));
            if (key == null || !seen.add(key) || tableReport.isRejected(key)) {
                return;
            }
            try {
                BigDecimal value = LegacyValueParser.parseAmount(legacyColumn, rs.getString(2));
                if (value != null) legacyTotal[0] = legacyTotal[0].add(value);
            } catch (MalformedLegacyDataException e) {
                unparseable[0]++;
            }
        });
        BigDecimal modernTotal = modern.queryForObject(
                "SELECT COALESCE(SUM(" + modernColumn + "), 0) FROM " + modernTable, BigDecimal.class);
        boolean passed = unparseable[0] == 0 && legacyTotal[0].compareTo(modernTotal) == 0;
        checks.add(new ReconciliationCheck(modernTable + "." + modernColumn + " total",
                legacyTotal[0].toPlainString() + (unparseable[0] == 0 ? "" : " (" + unparseable[0] + " unparseable)"),
                modernTotal.toPlainString(), passed));
    }

    /**
     * Expected links are the legacy child rows that were neither rejected nor duplicates.
     * Also counts legacy children whose parent key doesn't exist in the legacy parent table
     * (orphaned in the source; these are rejected), expected links that are missing or point
     * elsewhere in the modern schema (unmapped), modern links without an expected legacy link
     * (unexpected) and modern rows whose FK points at no parent row (orphaned in the target).
     */
    private void checkForeignKey(List<ReconciliationCheck> checks, String name, MigrationReport.TableReport childReport,
                                 String legacyLinksSql, String legacyParentKeysSql,
                                 String modernLinksSql, String modernOrphansSql) {
        Set<String> legacyParents = new HashSet<>();
        legacy.query(legacyParentKeysSql, rs -> {
            String key = LegacyValueParser.clean(rs.getString(1));
            if (key != null) legacyParents.add(key);
        });

        Map<String, String> expected = new HashMap<>();
        Set<String> seen = new HashSet<>();
        long[] legacyOrphans = {0};
        legacy.query(legacyLinksSql, rs -> {
            String child = LegacyValueParser.clean(rs.getString(1));
            String parent = LegacyValueParser.clean(rs.getString(2));
            if (child == null || !seen.add(child)) {
                return;
            }
            if (parent == null || !legacyParents.contains(parent)) {
                legacyOrphans[0]++;
            }
            if (!childReport.isRejected(child)) {
                expected.put(child, parent);
            }
        });

        Map<String, String> modernLinks = new HashMap<>();
        modern.query(modernLinksSql, rs -> {
            modernLinks.put(rs.getString(1), rs.getString(2));
        });
        long matching = expected.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), modernLinks.get(e.getKey())))
                .count();
        long unmapped = expected.size() - matching;
        long unexpected = modernLinks.keySet().stream().filter(key -> !expected.containsKey(key)).count();
        long modernOrphans = count(modern, modernOrphansSql);

        checks.add(new ReconciliationCheck(name + " mapping",
                expected.size() + " links (" + legacyOrphans[0] + " orphaned in legacy)",
                matching + " matching, " + unmapped + " unmapped, " + unexpected + " unexpected, "
                        + modernOrphans + " orphaned",
                unmapped == 0 && unexpected == 0 && modernOrphans == 0));
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }
}
