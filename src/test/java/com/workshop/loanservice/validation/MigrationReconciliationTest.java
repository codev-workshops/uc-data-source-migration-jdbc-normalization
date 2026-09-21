package com.workshop.loanservice.validation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL-level reconciliation of the modern tables against the legacy CDW tables after the
 * startup migration: row counts, amount totals, FK integrity and payment identity.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reconciliation;DB_CLOSE_DELAY=-1")
class MigrationReconciliationTest {

    private static final Map<String, String> MODERN_TO_LEGACY = Map.of(
            "borrowers", "CDW_BORR_MSTR",
            "loan_products", "CDW_LN_PROD",
            "loan_accounts", "CDW_LN_ACCT",
            "payments", "CDW_PMT_HIST");

    private static final Map<String, Integer> EXPECTED_COUNTS = Map.of(
            "borrowers", 5,
            "loan_products", 5,
            "loan_accounts", 5,
            "payments", 10);

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void modernRowCountsMatchExpectedAndLegacy() {
        MODERN_TO_LEGACY.forEach((modern, legacy) -> {
            assertThat(count(modern)).as("rows in %s", modern).isEqualTo(EXPECTED_COUNTS.get(modern));
            assertThat(count(modern)).as("%s vs %s", modern, legacy).isEqualTo(count(legacy));
        });
    }

    @Test
    void loanAmountTotalsMatchLegacyCommaStrippedAmounts() {
        assertThat(sum("loan_accounts", "original_amount"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "LN_ORIG_AMT"));
        assertThat(sum("loan_accounts", "current_balance"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "LN_CURR_BAL"));
        assertThat(sum("loan_accounts", "monthly_payment"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "LN_PMT_AMT"));
        assertThat(sum("loan_accounts", "escrow_balance"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "LN_ESCROW_BAL"));
        assertThat(sum("loan_accounts", "appraised_value"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "PROP_APRS_VAL"));
    }

    @Test
    void paymentAmountTotalsMatchLegacyCommaStrippedAmounts() {
        assertThat(sum("payments", "total_amount")).isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_AMT"));
        assertThat(sum("payments", "principal_amount")).isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_PRIN_AMT"));
        assertThat(sum("payments", "interest_amount")).isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_INT_AMT"));
        assertThat(sum("payments", "escrow_amount")).isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_ESCROW_AMT"));
        assertThat(sum("payments", "late_fee")).isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_LATE_FEE"));
    }

    @Test
    void borrowerIncomeTotalsMatchLegacy() {
        assertThat(sum("borrowers", "annual_income"))
                .isEqualByComparingTo(legacySum("CDW_BORR_MSTR", "BORR_ANN_INCM"));
    }

    @Test
    void noOrphanForeignKeys() {
        assertThat(scalar("SELECT COUNT(*) FROM loan_accounts la LEFT JOIN borrowers b ON b.id = la.borrower_id"
                + " WHERE b.id IS NULL")).as("loan_accounts without borrower").isZero();
        assertThat(scalar("SELECT COUNT(*) FROM loan_accounts la LEFT JOIN loan_products p ON p.id = la.product_id"
                + " WHERE p.id IS NULL")).as("loan_accounts without product").isZero();
        assertThat(scalar("SELECT COUNT(*) FROM payments pm LEFT JOIN loan_accounts la ON la.id = pm.loan_account_id"
                + " WHERE la.id IS NULL")).as("payments without loan_account").isZero();
    }

    @Test
    void loanAccountsResolveToTheSameParentsAsLegacy() {
        assertThat(scalar("""
                SELECT COUNT(*) FROM CDW_LN_ACCT l
                LEFT JOIN loan_accounts la ON la.account_number = l.LN_ACCT_NBR
                LEFT JOIN borrowers b ON b.id = la.borrower_id
                LEFT JOIN loan_products p ON p.id = la.product_id
                WHERE la.id IS NULL OR b.external_id <> l.BORR_ID OR p.code <> l.PROD_CD
                """)).as("loan accounts with mismatched borrower/product vs legacy").isZero();
    }

    @Test
    void everyPaymentCarriesUniqueLegacyPaymentIdMatchingLegacy() {
        assertThat(scalar("SELECT COUNT(*) FROM payments WHERE legacy_payment_id IS NULL"))
                .as("payments without legacy_payment_id").isZero();
        assertThat(scalar("SELECT COUNT(DISTINCT legacy_payment_id) FROM payments"))
                .as("distinct legacy_payment_id").isEqualTo(count("payments"));

        assertThat(scalar("""
                SELECT COUNT(*) FROM payments pm
                LEFT JOIN CDW_PMT_HIST h ON h.PMT_SEQ_NBR = pm.legacy_payment_id
                WHERE h.PMT_SEQ_NBR IS NULL
                """)).as("payments whose legacy_payment_id is not a legacy PMT_SEQ_NBR").isZero();
        assertThat(scalar("""
                SELECT COUNT(*) FROM CDW_PMT_HIST h
                LEFT JOIN payments pm ON pm.legacy_payment_id = h.PMT_SEQ_NBR
                WHERE pm.id IS NULL
                """)).as("legacy payments not migrated").isZero();

        assertThat(scalar("""
                SELECT COUNT(*) FROM payments pm
                JOIN CDW_PMT_HIST h ON h.PMT_SEQ_NBR = pm.legacy_payment_id
                JOIN loan_accounts la ON la.id = pm.loan_account_id
                WHERE la.account_number <> h.LN_ACCT_NBR
                """)).as("payments attached to a different loan than in legacy").isZero();
    }

    @Test
    void naturalKeysMatchLegacyOneToOne() {
        List<String> legacyBorrowers = jdbc.queryForList("SELECT BORR_ID FROM CDW_BORR_MSTR ORDER BY BORR_ID", String.class);
        List<String> modernBorrowers = jdbc.queryForList("SELECT external_id FROM borrowers ORDER BY external_id", String.class);
        assertThat(modernBorrowers).containsExactlyElementsOf(legacyBorrowers);

        List<String> legacyLoans = jdbc.queryForList("SELECT LN_ACCT_NBR FROM CDW_LN_ACCT ORDER BY LN_ACCT_NBR", String.class);
        List<String> modernLoans = jdbc.queryForList("SELECT account_number FROM loan_accounts ORDER BY account_number", String.class);
        assertThat(modernLoans).containsExactlyElementsOf(legacyLoans);

        List<String> legacyProducts = jdbc.queryForList("SELECT PROD_CD FROM CDW_LN_PROD ORDER BY PROD_CD", String.class);
        List<String> modernProducts = jdbc.queryForList("SELECT code FROM loan_products ORDER BY code", String.class);
        assertThat(modernProducts).containsExactlyElementsOf(legacyProducts);
    }

    private int count(String table) {
        return scalar("SELECT COUNT(*) FROM " + table);
    }

    private int scalar(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        assertThat(n).as(sql).isNotNull();
        return n;
    }

    private BigDecimal sum(String table, String column) {
        BigDecimal total = jdbc.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table, BigDecimal.class);
        assertThat(total).as("SUM(%s.%s)", table, column).isNotNull();
        return total;
    }

    /** Sums a free-text legacy amount column after stripping thousands separators. */
    private BigDecimal legacySum(String table, String column) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CAST(REPLACE(" + column + ", ',', '') AS DECIMAL(18, 3))), 0) FROM " + table
                        + " WHERE " + column + " IS NOT NULL AND TRIM(" + column + ") <> ''",
                BigDecimal.class);
        assertThat(total).as("legacy SUM(%s.%s)", table, column).isNotNull();
        return total;
    }
}
