package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Reconciles the modern schema (populated by {@link DataMigrationRunner} at startup) against the
 * legacy seed data using SQL issued directly against both databases.
 */
@SpringBootTest
class DataMigrationServiceTest {

    @Autowired
    private DataMigrationService migrationService;

    @Autowired
    private DataSource modernDataSource;

    @Autowired
    @Qualifier("legacyDataSource")
    private DataSource legacyDataSource;

    private JdbcTemplate modern;
    private JdbcTemplate legacy;

    @BeforeEach
    void setUp() {
        modern = new JdbcTemplate(modernDataSource);
        legacy = new JdbcTemplate(legacyDataSource);
    }

    @Test
    void rowCountsReconcile() {
        assertThat(count(modern, "borrowers")).isEqualTo(5).isEqualTo(count(legacy, "CDW_BORR_MSTR"));
        assertThat(count(modern, "loan_products")).isEqualTo(5).isEqualTo(count(legacy, "CDW_LN_PROD"));
        assertThat(count(modern, "loan_accounts")).isEqualTo(5).isEqualTo(count(legacy, "CDW_LN_ACCT"));
        assertThat(count(modern, "payments")).isEqualTo(10).isEqualTo(count(legacy, "CDW_PMT_HIST"));
    }

    @Test
    void monetaryTotalsReconcile() {
        assertThat(modernSum("loan_accounts", "original_amount"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "LN_ORIG_AMT"))
                .isEqualByComparingTo("1590000.00");
        assertThat(modernSum("loan_accounts", "monthly_payment"))
                .isEqualByComparingTo(legacySum("CDW_LN_ACCT", "LN_PMT_AMT"));
        assertThat(modernSum("payments", "total_amount"))
                .isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_AMT"))
                .isEqualByComparingTo("17536.42");
        assertThat(modernSum("payments", "principal_amount"))
                .isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_PRIN_AMT"));
        assertThat(modernSum("payments", "interest_amount"))
                .isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_INT_AMT"));
        assertThat(modernSum("payments", "escrow_amount"))
                .isEqualByComparingTo(legacySum("CDW_PMT_HIST", "PMT_ESCROW_AMT"));
    }

    @Test
    void foreignKeysResolveToTheSameBusinessKeys() {
        Integer loanMismatches = modern.queryForObject("""
                SELECT COUNT(*) FROM loan_accounts la
                JOIN borrowers b ON b.id = la.borrower_id
                JOIN loan_products p ON p.id = la.product_id""", Integer.class);
        assertThat(loanMismatches).isEqualTo(5);
        assertThat(modern.queryForObject("""
                SELECT b.external_id FROM loan_accounts la JOIN borrowers b ON b.id = la.borrower_id
                WHERE la.account_number = 'LN-2019-00142'""", String.class)).isEqualTo("B-10001");
        assertThat(modern.queryForObject("""
                SELECT COUNT(*) FROM payments p JOIN loan_accounts la ON la.id = p.loan_account_id
                WHERE la.account_number = 'LN-2019-00142'""", Integer.class))
                .isEqualTo(legacy.queryForObject(
                        "SELECT COUNT(*) FROM CDW_PMT_HIST WHERE LN_ACCT_NBR = 'LN-2019-00142'", Integer.class));
    }

    @Test
    void rerunIsIdempotent() {
        MigrationReport report = migrationService.migrate();

        assertThat(report.isSuccessful()).as(report.summary()).isTrue();
        assertThat(report.getTables().values()).allSatisfy(stats -> {
            assertThat(stats.getInserted()).isZero();
            assertThat(stats.getSkippedDuplicates()).isEqualTo(stats.getRead());
        });
        assertThat(count(modern, "payments")).isEqualTo(10);
    }

    @Test
    @DirtiesContext
    void malformedAndOrphanRowsAreRejectedAndFailReconciliation() {
        legacy.update("""
                INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)
                VALUES ('PMT-BAD', 'LN-2019-00142', '13/45/2025', '1,O00.00', 'REG', 'PST')""");
        legacy.update("""
                INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)
                VALUES ('PMT-ORPHAN', 'LN-MISSING', '01/15/2026', '100.00', 'REG', 'PST')""");

        MigrationReport report = migrationService.migrate();

        assertThat(report.isSuccessful()).isFalse();
        assertThat(report.getTable("payments").getRejected()).isEqualTo(2);
        assertThat(report.getErrors()).anyMatch(e -> e.contains("PMT-BAD"))
                .anyMatch(e -> e.contains("PMT-ORPHAN"));
        assertThat(count(modern, "payments")).isEqualTo(10);
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private BigDecimal modernSum(String table, String column) {
        return modern.queryForObject("SELECT SUM(" + column + ") FROM " + table, BigDecimal.class);
    }

    /** Independent oracle: sums the raw legacy strings in SQL rather than via the migration code. */
    private BigDecimal legacySum(String table, String column) {
        return legacy.queryForObject(
                "SELECT SUM(CAST(REPLACE(" + column + ", ',', '') AS DECIMAL(15, 2))) FROM " + table,
                BigDecimal.class);
    }
}
