package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migration is run once at startup by {@code MigrationRunner}, so these tests assert against an
 * already-migrated store and then re-run the migration to prove it is safe to run again - which is
 * the property that makes a resumable, restartable backfill possible at all.
 */
@SpringBootTest
class MigrationIT {

    @Autowired
    private LegacyToModernMigrationService migrationService;
    @Autowired
    private ReconciliationService reconciliation;
    @Autowired
    private BorrowerRepository borrowers;
    @Autowired
    private LoanProductRepository products;
    @Autowired
    private LoanAccountRepository accounts;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private LegacyBorrowerRepository legacyBorrowers;
    @Autowired
    private LegacyLoanAccountRepository legacyAccounts;
    @Autowired
    private LegacyPaymentRepository legacyPayments;

    @Test
    void everyLegacyRowLanded() {
        assertThat(borrowers.count()).isEqualTo(legacyBorrowers.count());
        assertThat(accounts.count()).isEqualTo(legacyAccounts.count());
        assertThat(payments.count()).isEqualTo(legacyPayments.count());
        assertThat(products.count()).isPositive();
    }

    @Test
    void reRunningWritesNothingAndRejectsNothing() {
        long before = accounts.count();

        MigrationReport report = migrationService.migrate();

        assertThat(report.getRejections()).isEmpty();
        assertThat(report.totalWritten()).isZero();
        assertThat(report.totalSkipped()).isEqualTo(report.totalRead());
        assertThat(accounts.count()).isEqualTo(before);
    }

    @Test
    void stringsBecameTypedValues() {
        LoanAccount account = accounts.findByAccountNumberWithBorrowerAndProduct("LN-2019-00142").orElseThrow();

        assertThat(account.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("285000"));
        assertThat(account.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("271432.56"));
        assertThat(account.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(account.getTermMonths()).isEqualTo(360);
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getPropertyType()).isEqualTo("SINGLE_FAMILY");
    }

    @Test
    void foreignKeysReplacedTheDenormalisedCopies() {
        LoanAccount account = accounts.findByAccountNumberWithBorrowerAndProduct("LN-2019-00142").orElseThrow();

        assertThat(account.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(account.getBorrower().getFirstName()).isEqualTo("James");
        assertThat(account.getProduct().getCode()).isEqualTo("FXD30");
    }

    /** The workshop requires the stored value to survive untouched; re-hashing would break matching. */
    @Test
    void ssnValueIsCarriedOverVerbatim() {
        assertThat(borrowers.findByExternalId("B-10001").orElseThrow().getSsnHash())
            .isEqualTo(legacyBorrowers.findById("B-10001").orElseThrow().getSsnEncrypted());
    }

    /** Freezing the legacy sequence number is what keeps the v1 paymentId stable after cutover. */
    @Test
    void legacyPaymentIdentifiersArePreserved() {
        assertThat(payments.findByLegacyId("PMT-2025120001")).isPresent();
    }

    @Test
    void bothStoresReconcile() {
        assertThat(reconciliation.reconcile()).allSatisfy(drift -> {
            assertThat(drift.difference()).isZero();
        });
        assertThat(reconciliation.isFullyReconciled()).isTrue();
    }
}
