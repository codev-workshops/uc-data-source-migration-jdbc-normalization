package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * <p>Each legacy row is parsed into typed values, its codes are expanded and its
 * string foreign keys are resolved to modern surrogate keys. Rows whose natural key
 * already exists in the modern schema are skipped, so the migration can be re-run.
 * Rows that cannot be converted are rejected and reported; they do not stop the run.
 */
@Component
public class LegacyToModernMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyToModernMigrator.class);

    private final JdbcTemplate legacy;
    private final JdbcTemplate modern;

    public LegacyToModernMigrator(@Qualifier("legacyJdbcTemplate") JdbcTemplate legacy,
                                  @Qualifier("modernJdbcTemplate") JdbcTemplate modern) {
        this.legacy = legacy;
        this.modern = modern;
    }

    @Transactional
    public MigrationReport migrate() {
        MigrationReport report = new MigrationReport();
        migrateLoanProducts(report.table("loan_products"));
        migrateBorrowers(report.table("borrowers"));
        migrateLoanAccounts(report.table("loan_accounts"));
        migratePayments(report.table("payments"));
        return report;
    }

    // Legacy rows are read in table order (no ORDER BY) so modern ids follow the
    // order the legacy API listed records in.

    private void migrateLoanProducts(MigrationReport.TableReport report) {
        copy(report, "SELECT * FROM CDW_LN_PROD", "PROD_CD", keys("SELECT code FROM loan_products"), row ->
                modern.update("""
                        INSERT INTO loan_products (code, name, type, term_months, rate_type, min_amount,
                            max_amount, is_active, effective_date, expiration_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        row.text("PROD_CD"),
                        row.text("PROD_DESC_TXT"),
                        row.text("PROD_TYP_CD"),
                        row.integer("PROD_TERM_MOS"),
                        row.text("PROD_RT_TYP"),
                        row.amount("PROD_MIN_AMT"),
                        row.amount("PROD_MAX_AMT"),
                        LegacyCodeMappings.productActive(row.raw("PROD_STAT_CD")),
                        row.date("PROD_EFF_DT"),
                        row.date("PROD_EXP_DT")));
    }

    private void migrateBorrowers(MigrationReport.TableReport report) {
        copy(report, "SELECT * FROM CDW_BORR_MSTR", "BORR_ID", keys("SELECT external_id FROM borrowers"), row ->
                modern.update("""
                        INSERT INTO borrowers (external_id, first_name, last_name, middle_initial, ssn_hash,
                            date_of_birth, address_line1, address_line2, city, state, zip_code, phone, email,
                            credit_score, employment_status, annual_income, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, CURRENT_TIMESTAMP))""",
                        row.text("BORR_ID"),
                        row.text("BORR_FST_NM"),
                        row.text("BORR_LST_NM"),
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
                        row.amount("BORR_ANN_INCM"),
                        LegacyCodeMappings.borrowerStatus(row.raw("BORR_STAT_CD")).name(),
                        row.timestamp("BORR_CRET_DT"),
                        row.timestamp("BORR_UPDT_DT")));
    }

    private void migrateLoanAccounts(MigrationReport.TableReport report) {
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
                        resolve(borrowerIds, "BORR_ID", row.text("BORR_ID"), "borrower"),
                        resolve(productIds, "PROD_CD", row.text("PROD_CD"), "loan product"),
                        row.amount("LN_ORIG_AMT"),
                        row.amount("LN_CURR_BAL"),
                        row.decimal("LN_INT_RT"),
                        row.integer("LN_TERM_MOS"),
                        row.amount("LN_PMT_AMT"),
                        row.date("LN_ORIG_DT"),
                        row.date("LN_MAT_DT"),
                        row.date("LN_1ST_PMT_DT"),
                        row.date("LN_NXT_PMT_DT"),
                        LegacyCodeMappings.loanStatus(row.raw("LN_STAT_CD")).name(),
                        Objects.requireNonNullElse(row.integer("LN_DLQ_DAYS"), 0),
                        Objects.requireNonNullElse(row.amount("LN_ESCROW_BAL"), BigDecimal.ZERO),
                        row.decimal("LN_LTV_PCT"),
                        row.text("PROP_ADDR_LN1"),
                        row.text("PROP_CTY_NM"),
                        row.text("PROP_ST_CD"),
                        row.text("PROP_ZIP_CD"),
                        LegacyCodeMappings.propertyType(row.raw("PROP_TYP_CD")),
                        row.amount("PROP_APRS_VAL"),
                        row.timestamp("LN_CRET_DT"),
                        row.timestamp("LN_UPDT_DT")));
    }

    private void migratePayments(MigrationReport.TableReport report) {
        Map<String, Long> loanAccountIds = ids("SELECT account_number, id FROM loan_accounts");
        copy(report, "SELECT * FROM CDW_PMT_HIST", "PMT_SEQ_NBR", keys("SELECT external_id FROM payments"), row ->
                modern.update("""
                        INSERT INTO payments (external_id, loan_account_id, payment_date, total_amount,
                            principal_amount, interest_amount, escrow_amount, late_fee, type, status,
                            received_date, processed_date, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, CURRENT_TIMESTAMP))""",
                        row.text("PMT_SEQ_NBR"),
                        resolve(loanAccountIds, "LN_ACCT_NBR", row.text("LN_ACCT_NBR"), "loan account"),
                        row.date("PMT_DT"),
                        row.amount("PMT_AMT"),
                        row.amount("PMT_PRIN_AMT"),
                        row.amount("PMT_INT_AMT"),
                        row.amount("PMT_ESCROW_AMT"),
                        Objects.requireNonNullElse(row.amount("PMT_LATE_FEE"), BigDecimal.ZERO),
                        LegacyCodeMappings.paymentType(row.raw("PMT_TYP_CD")).name(),
                        LegacyCodeMappings.paymentStatus(row.raw("PMT_STAT_CD")).name(),
                        row.date("PMT_RECV_DT"),
                        row.date("PMT_PROC_DT"),
                        row.timestamp("PMT_CRET_DT"),
                        row.timestamp("PMT_UPDT_DT")));
    }

    /**
     * Compares legacy and modern data: row counts, amount totals and foreign-key mappings.
     */
    public List<ReconciliationCheck> reconcile() {
        List<ReconciliationCheck> checks = new ArrayList<>();
        checkRowCount(checks, "CDW_LN_PROD", "loan_products");
        checkRowCount(checks, "CDW_BORR_MSTR", "borrowers");
        checkRowCount(checks, "CDW_LN_ACCT", "loan_accounts");
        checkRowCount(checks, "CDW_PMT_HIST", "payments");

        checkAmountTotal(checks, "CDW_LN_PROD", "PROD_MIN_AMT", "loan_products", "min_amount");
        checkAmountTotal(checks, "CDW_LN_PROD", "PROD_MAX_AMT", "loan_products", "max_amount");
        checkAmountTotal(checks, "CDW_BORR_MSTR", "BORR_ANN_INCM", "borrowers", "annual_income");
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ORIG_AMT", "loan_accounts", "original_amount");
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_CURR_BAL", "loan_accounts", "current_balance");
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_PMT_AMT", "loan_accounts", "monthly_payment");
        checkAmountTotal(checks, "CDW_LN_ACCT", "LN_ESCROW_BAL", "loan_accounts", "escrow_balance");
        checkAmountTotal(checks, "CDW_LN_ACCT", "PROP_APRS_VAL", "loan_accounts", "appraised_value");
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_AMT", "payments", "total_amount");
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_PRIN_AMT", "payments", "principal_amount");
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_INT_AMT", "payments", "interest_amount");
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_ESCROW_AMT", "payments", "escrow_amount");
        checkAmountTotal(checks, "CDW_PMT_HIST", "PMT_LATE_FEE", "payments", "late_fee");

        checkForeignKey(checks, "loan_accounts.borrower_id",
                "SELECT LN_ACCT_NBR, BORR_ID FROM CDW_LN_ACCT",
                "SELECT a.account_number, b.external_id FROM loan_accounts a JOIN borrowers b ON b.id = a.borrower_id");
        checkForeignKey(checks, "loan_accounts.product_id",
                "SELECT LN_ACCT_NBR, PROD_CD FROM CDW_LN_ACCT",
                "SELECT a.account_number, p.code FROM loan_accounts a JOIN loan_products p ON p.id = a.product_id");
        checkForeignKey(checks, "payments.loan_account_id",
                "SELECT PMT_SEQ_NBR, LN_ACCT_NBR FROM CDW_PMT_HIST",
                "SELECT p.external_id, a.account_number FROM payments p JOIN loan_accounts a ON a.id = p.loan_account_id");
        return checks;
    }

    private void copy(MigrationReport.TableReport report, String legacySql, String keyColumn,
                      Set<String> existingKeys, Consumer<LegacyRow> insert) {
        for (Map<String, Object> values : legacy.queryForList(legacySql)) {
            LegacyRow row = new LegacyRow(values);
            report.recordRead();
            String key = row.text(keyColumn);
            if (key == null) {
                report.recordRejected(null, keyColumn + " is blank");
                continue;
            }
            if (existingKeys.contains(key)) {
                report.recordSkippedExisting();
                continue;
            }
            try {
                insert.accept(row);
                existingKeys.add(key);
                report.recordInserted();
            } catch (MalformedLegacyDataException e) {
                reject(report, key, e.getMessage());
            } catch (DataAccessException e) {
                reject(report, key, e.getMostSpecificCause().getMessage());
            }
        }
    }

    private void reject(MigrationReport.TableReport report, String key, String reason) {
        log.warn("Rejected legacy {} record {}: {}", report.getTable(), key, reason);
        report.recordRejected(key, reason);
    }

    private static Long resolve(Map<String, Long> ids, String column, String key, String target) {
        Long id = key == null ? null : ids.get(key);
        if (id == null) {
            throw new MalformedLegacyDataException(column, key, "no migrated " + target + " with this key");
        }
        return id;
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

    private void checkRowCount(List<ReconciliationCheck> checks, String legacyTable, String modernTable) {
        Long legacyCount = legacy.queryForObject("SELECT COUNT(*) FROM " + legacyTable, Long.class);
        Long modernCount = modern.queryForObject("SELECT COUNT(*) FROM " + modernTable, Long.class);
        checks.add(new ReconciliationCheck(modernTable + " row count",
                String.valueOf(legacyCount), String.valueOf(modernCount), Objects.equals(legacyCount, modernCount)));
    }

    private void checkAmountTotal(List<ReconciliationCheck> checks, String legacyTable, String legacyColumn,
                                  String modernTable, String modernColumn) {
        BigDecimal legacyTotal = BigDecimal.ZERO;
        for (String raw : legacy.queryForList("SELECT " + legacyColumn + " FROM " + legacyTable, String.class)) {
            try {
                BigDecimal value = LegacyValueParser.parseAmount(legacyColumn, raw);
                if (value != null) legacyTotal = legacyTotal.add(value);
            } catch (MalformedLegacyDataException e) {
                // Unparseable values are reported as rejected rows; they cannot contribute to the total.
            }
        }
        BigDecimal modernTotal = modern.queryForObject(
                "SELECT COALESCE(SUM(" + modernColumn + "), 0) FROM " + modernTable, BigDecimal.class);
        checks.add(new ReconciliationCheck(modernTable + "." + modernColumn + " total",
                legacyTotal.toPlainString(), modernTotal.toPlainString(), legacyTotal.compareTo(modernTotal) == 0));
    }

    private void checkForeignKey(List<ReconciliationCheck> checks, String name, String legacySql, String modernSql) {
        Map<String, String> legacyLinks = links(legacy, legacySql);
        Map<String, String> modernLinks = links(modern, modernSql);
        long matching = legacyLinks.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), modernLinks.get(e.getKey())))
                .count();
        checks.add(new ReconciliationCheck(name + " mapping",
                legacyLinks.size() + " links", matching + " matching",
                matching == legacyLinks.size() && modernLinks.size() == legacyLinks.size()));
    }

    private static Map<String, String> links(JdbcTemplate jdbc, String sql) {
        Map<String, String> links = new HashMap<>();
        jdbc.query(sql, rs -> {
            links.put(LegacyValueParser.clean(rs.getString(1)), LegacyValueParser.clean(rs.getString(2)));
        });
        return links;
    }
}
