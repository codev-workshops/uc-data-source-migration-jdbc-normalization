package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import com.workshop.loanservice.service.migration.MigrationSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void paddedNaturalKeysAreRecognizedOnRerun() {
        jdbc.update("UPDATE CDW_BORR_MSTR SET BORR_ID = ' B-10001 ' WHERE BORR_ID = 'B-10001'");
        jdbc.update("UPDATE CDW_LN_PROD SET PROD_CD = ' ARM51 ' WHERE PROD_CD = 'ARM51'");
        jdbc.update("UPDATE CDW_LN_ACCT SET LN_ACCT_NBR = ' LN-2019-00142 ' WHERE LN_ACCT_NBR = 'LN-2019-00142'");
        jdbc.update("UPDATE CDW_PMT_HIST SET PMT_SEQ_NBR = ' PMT-2025120001 ' WHERE PMT_SEQ_NBR = 'PMT-2025120001'");
        try {
            MigrationSummary summary = migrationService.migrate();

            assertThat(summary.getQuarantined()).isEmpty();
            summary.getTables().values().forEach(t -> assertThat(t.inserted()).isZero());
            assertThat(count("borrowers")).isEqualTo(5);
            assertThat(count("loan_products")).isEqualTo(5);
            assertThat(count("loan_accounts")).isEqualTo(5);
            assertThat(count("payments")).isEqualTo(10);
        } finally {
            jdbc.update("UPDATE CDW_BORR_MSTR SET BORR_ID = 'B-10001' WHERE BORR_ID = ' B-10001 '");
            jdbc.update("UPDATE CDW_LN_PROD SET PROD_CD = 'ARM51' WHERE PROD_CD = ' ARM51 '");
            jdbc.update("UPDATE CDW_LN_ACCT SET LN_ACCT_NBR = 'LN-2019-00142' WHERE LN_ACCT_NBR = ' LN-2019-00142 '");
            jdbc.update("UPDATE CDW_PMT_HIST SET PMT_SEQ_NBR = 'PMT-2025120001' WHERE PMT_SEQ_NBR = ' PMT-2025120001 '");
        }
    }

    @Test
    void paymentsWithIdenticalValuesButDistinctSequenceNumbersAreBothMigrated() {
        jdbc.update("INSERT INTO CDW_PMT_HIST SELECT 'PMT-DUPVALUES', LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_PRIN_AMT, "
                + "PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, PMT_STAT_CD, PMT_RECV_DT, PMT_PROC_DT, "
                + "PMT_CRET_DT, PMT_UPDT_DT FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR = 'PMT-2025120001'");
        try {
            MigrationSummary summary = migrationService.migrate();

            assertThat(summary.getQuarantined()).isEmpty();
            assertThat(summary.getTables().get(MigrationService.TABLE_PAYMENTS).inserted()).isEqualTo(1);
            assertThat(payments.findByLegacyPaymentId("PMT-DUPVALUES")).isPresent();
            assertThat(count("payments")).isEqualTo(11);

            assertThat(migrationService.migrate().getTables().get(MigrationService.TABLE_PAYMENTS).inserted()).isZero();
            assertThat(count("payments")).isEqualTo(11);
        } finally {
            jdbc.update("DELETE FROM payments WHERE legacy_payment_id = 'PMT-DUPVALUES'");
            jdbc.update("DELETE FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR = 'PMT-DUPVALUES'");
        }
    }

    @Test
    void quarantinedPaymentDoesNotShadowValidRowWithSameNormalizedId() {
        // padded id sorts first (space < 'P'), references a missing account, and must not reserve 'PMT-SHADOW'
        jdbc.update("INSERT INTO CDW_PMT_HIST SELECT ' PMT-SHADOW', 'LN-MISSING', PMT_DT, PMT_AMT, PMT_PRIN_AMT, "
                + "PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, PMT_STAT_CD, PMT_RECV_DT, PMT_PROC_DT, "
                + "PMT_CRET_DT, PMT_UPDT_DT FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR = 'PMT-2025120001'");
        jdbc.update("INSERT INTO CDW_PMT_HIST SELECT 'PMT-SHADOW', LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_PRIN_AMT, "
                + "PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, PMT_STAT_CD, PMT_RECV_DT, PMT_PROC_DT, "
                + "PMT_CRET_DT, PMT_UPDT_DT FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR = 'PMT-2025120001'");
        try {
            MigrationSummary summary = migrationService.migrate();

            assertThat(summary.getQuarantined()).singleElement()
                    .satisfies(q -> assertThat(q.recordId()).isEqualTo(" PMT-SHADOW"));
            assertThat(summary.getTables().get(MigrationService.TABLE_PAYMENTS).inserted()).isEqualTo(1);
            assertThat(payments.findByLegacyPaymentId("PMT-SHADOW")).isPresent();
        } finally {
            jdbc.update("DELETE FROM payments WHERE legacy_payment_id = 'PMT-SHADOW'");
            jdbc.update("DELETE FROM CDW_PMT_HIST WHERE TRIM(PMT_SEQ_NBR) = 'PMT-SHADOW'");
        }
    }

    @Test
    void amountExceedingColumnPrecisionIsQuarantinedWithoutAbortingRun() {
        jdbc.update("INSERT INTO CDW_PMT_HIST SELECT 'PMT-TOOBIG', LN_ACCT_NBR, PMT_DT, '123,456,789.00', PMT_PRIN_AMT, "
                + "PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, PMT_STAT_CD, PMT_RECV_DT, PMT_PROC_DT, "
                + "PMT_CRET_DT, PMT_UPDT_DT FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR = 'PMT-2025120001'");
        try {
            MigrationSummary summary = migrationService.migrate();

            assertThat(summary.getQuarantined()).singleElement().satisfies(q -> {
                assertThat(q.table()).isEqualTo(MigrationService.TABLE_PAYMENTS);
                assertThat(q.recordId()).isEqualTo("PMT-TOOBIG");
                assertThat(q.reason()).contains("PMT_AMT").contains("DECIMAL(10,2)");
            });
            assertThat(count("payments")).isEqualTo(10);
        } finally {
            jdbc.update("DELETE FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR = 'PMT-TOOBIG'");
        }
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? -1 : n;
    }
}
