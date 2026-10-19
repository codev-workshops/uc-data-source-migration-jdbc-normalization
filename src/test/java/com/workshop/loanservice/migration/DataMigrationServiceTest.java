package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
    private LoanService loanService;

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
        MigrationReport report = migrationService.migrate(true);

        assertThat(report.isSuccessful()).as(report.summary()).isTrue();
        assertThat(report.getTables().values()).allSatisfy(stats -> {
            assertThat(stats.getInserted()).isZero();
            assertThat(stats.getSkippedDuplicates()).isEqualTo(stats.getRead());
        });
        assertThat(count(modern, "payments")).isEqualTo(10);
    }

    @Test
    void modernSchemaScriptIsRerunnableAgainstAnInitializedDatabase() {
        new ResourceDatabasePopulator(new ClassPathResource("schema-modern.sql")).execute(modernDataSource);

        assertThat(count(modern, "borrowers")).isEqualTo(5);
    }

    @Test
    @DirtiesContext
    void badRowsAreRejectedIndividuallyWhenNotFailingOnError() {
        insertLegacyPayment("PMT-BAD", "LN-2019-00142", "13/45/2025", "1,O00.00");
        insertLegacyPayment("PMT-ORPHAN", "LN-MISSING", "01/15/2026", "100.00");
        insertLegacyPayment("PMT-BIG", "LN-2019-00142", "01/15/2026", "999999999999999");

        MigrationReport report = migrationService.migrate(false);

        assertThat(report.isSuccessful()).isFalse();
        assertThat(report.getTable("payments").getRejected()).isEqualTo(3);
        assertThat(report.getErrors())
                .anyMatch(e -> e.contains("PMT-BAD"))
                .anyMatch(e -> e.contains("PMT-ORPHAN"))
                .anyMatch(e -> e.contains("PMT-BIG") && e.contains("DECIMAL(10,2)"))
                .noneMatch(e -> e.contains("13/45/2025") || e.contains("1,O00.00") || e.contains("999999999999999"));
        assertThat(count(modern, "payments")).isEqualTo(10);
    }

    @Test
    @DirtiesContext
    void failOnErrorRollsBackTheWholeRun() {
        legacy.update("INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM) VALUES ('B-99999', 'Ada', 'Lane')");
        insertLegacyPayment("PMT-BAD", "LN-2019-00142", "13/45/2025", "100.00");

        assertThatThrownBy(() -> migrationService.migrate(true))
                .isInstanceOf(MigrationFailedException.class)
                .satisfies(e -> assertThat(((MigrationFailedException) e).getReport().getErrors())
                        .anyMatch(error -> error.contains("PMT-BAD")));
        assertThat(count(modern, "borrowers")).isEqualTo(5);
    }

    @Test
    @DirtiesContext
    void existingModernRowThatDiffersFromLegacyFailsValidation() {
        modern.update("UPDATE borrowers SET first_name = 'Jon' WHERE external_id = 'B-10001'");

        MigrationReport report = migrationService.migrate(false);

        assertThat(report.isSuccessful()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("B-10001") && e.contains("firstName"));
    }

    @Test
    @DirtiesContext
    void malformedOptionalFieldIsStoredAsNullWithWarning() {
        legacy.update("""
                INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_DOB_DT)
                VALUES ('B-99998', 'Ada', 'Lane', '1978-03-15')""");

        MigrationReport report = migrationService.migrate(true);

        assertThat(report.isSuccessful()).as(report.summary()).isTrue();
        assertThat(modern.queryForObject(
                "SELECT date_of_birth FROM borrowers WHERE external_id = 'B-99998'", LocalDate.class)).isNull();
        assertThat(report.getWarnings())
                .anyMatch(w -> w.contains("B-99998") && w.contains("BORR_DOB_DT"))
                .noneMatch(w -> w.contains("1978"));
    }

    @Test
    @DirtiesContext
    void unmappedLoanStatusIsKeptAndShownVerbatim() {
        legacy.update("UPDATE CDW_LN_ACCT SET LN_STAT_CD = 'PIF' WHERE LN_ACCT_NBR = 'LN-2017-00034'");
        modern.update("""
                DELETE FROM payments WHERE loan_account_id =
                    (SELECT id FROM loan_accounts WHERE account_number = 'LN-2017-00034')""");
        modern.update("DELETE FROM loan_accounts WHERE account_number = 'LN-2017-00034'");

        MigrationReport report = migrationService.migrate(true);

        assertThat(report.isSuccessful()).as(report.summary()).isTrue();
        assertThat(report.getWarnings()).anyMatch(w -> w.contains("LN-2017-00034") && w.contains("UNKNOWN"));
        assertThat(loanService.getLoanById("LN-2017-00034").getStatus()).isEqualTo("PIF");
    }

    @Test
    @DirtiesContext
    void denormalizedNameDriftWarningOmitsNames() {
        legacy.update("UPDATE CDW_LN_ACCT SET BORR_FST_NM = 'Jim' WHERE LN_ACCT_NBR = 'LN-2019-00142'");

        MigrationReport report = migrationService.migrate(true);

        assertThat(report.isSuccessful()).as(report.summary()).isTrue();
        assertThat(report.getWarnings())
                .anyMatch(w -> w.contains("LN-2019-00142") && w.contains("B-10001"))
                .noneMatch(w -> w.contains("Jim") || w.contains("James") || w.contains("Mitchell"));
    }

    /** Legacy sorted PMT_DT as MM/DD/YYYY text; the modern DATE column sorts chronologically across years. */
    @Test
    @DirtiesContext
    void paymentsAreOrderedChronologicallyAcrossYears() {
        insertLegacyPayment("PMT-2026010001", "LN-2019-00142", "01/15/2026", "1,487.02");

        assertThat(migrationService.migrate(true).isSuccessful()).isTrue();

        assertThat(loanService.getPaymentsByLoan("LN-2019-00142")).extracting(PaymentDto::getPaymentDate)
                .containsExactly("01/15/2026", "12/15/2025", "11/15/2025");
    }

    private void insertLegacyPayment(String id, String loan, String date, String amount) {
        legacy.update("""
                INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)
                VALUES (?, ?, ?, ?, 'REG', 'PST')""", id, loan, date, amount);
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
