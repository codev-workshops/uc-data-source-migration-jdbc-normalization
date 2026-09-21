package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import com.workshop.loanservice.service.migration.MigrationSummary;
import com.workshop.loanservice.service.migration.MigrationSummary.Quarantined;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Boots the full context (startup runner migrates once), then verifies modern counts match
 * legacy counts from data-legacy.sql and that re-running the migration is a no-op.
 */
@SpringBootTest
class MigrationServiceIntegrationTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BorrowerRepository borrowers;

    @Autowired
    private LoanAccountRepository loanAccounts;

    @Autowired
    private PaymentRepository payments;

    @AfterEach
    void removeInjectedLegacyRows() {
        jdbc.update("DELETE FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR LIKE 'PMT-BAD-%'");
        jdbc.update("DELETE FROM CDW_LN_ACCT WHERE LN_ACCT_NBR LIKE 'LN-BAD-%'");
        jdbc.update("DELETE FROM CDW_BORR_MSTR WHERE BORR_ID LIKE 'B-BAD-%'");
    }

    @Test
    void malformedLegacyRowsAreQuarantinedNotInsertedNorDefaulted() {
        jdbc.update("INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_DOB_DT, BORR_ANN_INCM, BORR_STAT_CD)"
                + " VALUES ('B-BAD-DOB', 'Bad', 'Date', '13/45/1978', '1,000', 'ACT')");
        jdbc.update("INSERT INTO CDW_BORR_MSTR (BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_ANN_INCM, BORR_STAT_CD)"
                + " VALUES ('B-BAD-STAT', 'Bad', 'Status', '1,000', 'ZZZ')");
        jdbc.update("INSERT INTO CDW_LN_ACCT (LN_ACCT_NBR, BORR_ID, PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, LN_INT_RT,"
                + " LN_TERM_MOS, LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT, LN_STAT_CD, PROP_TYP_CD)"
                + " VALUES ('LN-BAD-AMT', 'B-10001', 'FXD30', '1,0x0', '1', '1', '1', '1', '01/01/2020', '01/01/2021', 'ACT', 'SFR')");
        jdbc.update("INSERT INTO CDW_LN_ACCT (LN_ACCT_NBR, BORR_ID, PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, LN_INT_RT,"
                + " LN_TERM_MOS, LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT, LN_STAT_CD, PROP_TYP_CD)"
                + " VALUES ('LN-BAD-ORPHAN', 'B-NOPE', 'FXD30', '1', '1', '1', '1', '1', '01/01/2020', '01/01/2021', 'ACT', 'SFR')");
        jdbc.update("INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)"
                + " VALUES ('PMT-BAD-TYPE', 'LN-2019-00142', '01/01/2025', '1.00', 'XXX', 'PST')");
        jdbc.update("INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)"
                + " VALUES ('PMT-BAD-BLANK', 'LN-2019-00142', '01/01/2025', '', 'REG', 'PST')");
        jdbc.update("INSERT INTO CDW_PMT_HIST (PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_TYP_CD, PMT_STAT_CD)"
                + " VALUES ('PMT-BAD-ORPHAN', 'LN-NOPE', '01/01/2025', '1.00', 'REG', 'PST')");

        MigrationSummary summary = migrationService.migrate();

        assertThat(summary.getQuarantined())
                .extracting(Quarantined::table, Quarantined::recordId)
                .containsExactlyInAnyOrder(
                        tuple(MigrationService.TABLE_BORROWERS, "B-BAD-DOB"),
                        tuple(MigrationService.TABLE_BORROWERS, "B-BAD-STAT"),
                        tuple(MigrationService.TABLE_LOAN_ACCOUNTS, "LN-BAD-AMT"),
                        tuple(MigrationService.TABLE_LOAN_ACCOUNTS, "LN-BAD-ORPHAN"),
                        tuple(MigrationService.TABLE_PAYMENTS, "PMT-BAD-TYPE"),
                        tuple(MigrationService.TABLE_PAYMENTS, "PMT-BAD-BLANK"),
                        tuple(MigrationService.TABLE_PAYMENTS, "PMT-BAD-ORPHAN"));
        assertThat(summary.getQuarantined()).extracting(Quarantined::reason)
                .anySatisfy(r -> assertThat(r).contains("BORR_DOB_DT"))
                .anySatisfy(r -> assertThat(r).contains("BORR_STAT_CD"))
                .anySatisfy(r -> assertThat(r).contains("LN_ORIG_AMT"))
                .anySatisfy(r -> assertThat(r).contains("PMT_TYP_CD"))
                .anySatisfy(r -> assertThat(r).contains("PMT_AMT"));

        assertThat(summary.getTables().get(MigrationService.TABLE_BORROWERS).inserted()).isZero();
        assertThat(summary.getTables().get(MigrationService.TABLE_LOAN_ACCOUNTS).inserted()).isZero();
        assertThat(summary.getTables().get(MigrationService.TABLE_PAYMENTS).inserted()).isZero();

        assertThat(count("borrowers")).isEqualTo(5);
        assertThat(count("loan_accounts")).isEqualTo(5);
        assertThat(count("payments")).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM borrowers WHERE external_id LIKE 'B-BAD-%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE legacy_payment_id LIKE 'PMT-BAD-%'", Integer.class)).isZero();
    }

    @Test
    void startupMigrationPopulatesModernTablesWithLegacyCounts() {
        assertThat(count("CDW_BORR_MSTR")).isEqualTo(5);
        assertThat(count("CDW_LN_PROD")).isEqualTo(5);
        assertThat(count("CDW_LN_ACCT")).isEqualTo(5);
        assertThat(count("CDW_PMT_HIST")).isEqualTo(10);

        assertThat(count("borrowers")).isEqualTo(count("CDW_BORR_MSTR"));
        assertThat(count("loan_products")).isEqualTo(count("CDW_LN_PROD"));
        assertThat(count("loan_accounts")).isEqualTo(count("CDW_LN_ACCT"));
        assertThat(count("payments")).isEqualTo(count("CDW_PMT_HIST"));
    }

    @Test
    void secondRunIsIdempotent() {
        MigrationSummary summary = migrationService.migrate();

        assertThat(summary.getQuarantined()).isEmpty();
        assertThat(summary.getTables().get(MigrationService.TABLE_BORROWERS).inserted()).isZero();
        assertThat(summary.getTables().get(MigrationService.TABLE_BORROWERS).skipped()).isEqualTo(5);
        assertThat(summary.getTables().get(MigrationService.TABLE_LOAN_PRODUCTS).skipped()).isEqualTo(5);
        assertThat(summary.getTables().get(MigrationService.TABLE_LOAN_ACCOUNTS).skipped()).isEqualTo(5);
        assertThat(summary.getTables().get(MigrationService.TABLE_PAYMENTS).inserted()).isZero();
        assertThat(summary.getTables().get(MigrationService.TABLE_PAYMENTS).skipped()).isEqualTo(10);

        assertThat(count("borrowers")).isEqualTo(5);
        assertThat(count("loan_products")).isEqualTo(5);
        assertThat(count("loan_accounts")).isEqualTo(5);
        assertThat(count("payments")).isEqualTo(10);
    }

    @Test
    @Transactional(readOnly = true)
    void valuesAreTransformedAndReferencesResolved() {
        Borrower b = borrowers.findByExternalId("B-10001").orElseThrow();
        assertThat(b.getStatus()).isEqualTo("ACTIVE");
        assertThat(b.getAnnualIncome()).isEqualByComparingTo(new BigDecimal("92500.00"));
        assertThat(b.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(b.getCreditScore()).isEqualTo(745);

        LoanAccount a = loanAccounts.findByAccountNumber("LN-2018-00089").orElseThrow();
        assertThat(a.getBorrower().getExternalId()).isEqualTo("B-10003");
        assertThat(a.getProduct().getCode()).isEqualTo("ARM51");
        assertThat(a.getDelinquencyDays()).isEqualTo(15);
        assertThat(a.getInterestRate()).isEqualByComparingTo("5.250");
        assertThat(a.getPropertyType()).isEqualTo("SINGLE_FAMILY");

        assertThat(payments.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2019-00142"))
                .hasSize(2)
                .allSatisfy(p -> {
                    assertThat(p.getType()).isEqualTo("REGULAR");
                    assertThat(p.getStatus()).isEqualTo("POSTED");
                    assertThat(p.getTotalAmount()).isEqualByComparingTo("1487.02");
                });
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? -1 : n;
    }
}
