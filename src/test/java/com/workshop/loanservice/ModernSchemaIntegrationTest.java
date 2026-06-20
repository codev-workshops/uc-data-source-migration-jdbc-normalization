package com.workshop.loanservice;

import com.workshop.loanservice.legacy.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.legacy.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.legacy.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.legacy.repository.LegacyPaymentRepository;
import com.workshop.loanservice.migration.DataMigrationService;
import com.workshop.loanservice.migration.MigrationReport;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests against the migrated modern schema: repository business-key
 * lookups, reconciliation against the legacy source, and idempotency of the
 * migration (which has already run once on startup).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModernSchemaIntegrationTest {

    @Autowired BorrowerRepository borrowers;
    @Autowired LoanProductRepository products;
    @Autowired LoanAccountRepository loanAccounts;
    @Autowired PaymentRepository payments;

    @Autowired LegacyBorrowerRepository legacyBorrowers;
    @Autowired LegacyLoanProductRepository legacyProducts;
    @Autowired LegacyLoanAccountRepository legacyLoanAccounts;
    @Autowired LegacyPaymentRepository legacyPayments;

    @Autowired DataMigrationService migrationService;

    @Test
    void repositoriesResolveByStableBusinessKey() {
        assertThat(borrowers.findByExternalId("B-10001")).isPresent()
                .get().satisfies(b -> {
                    assertThat(b.getFirstName()).isEqualTo("James");
                    assertThat(b.getMiddleInitial()).isEqualTo("R");
                    assertThat(b.getLastName()).isEqualTo("Mitchell");
                    assertThat(b.getCreditScore()).isEqualTo(745);
                });

        assertThat(products.findByCode("FXD30")).isPresent()
                .get().satisfies(p -> assertThat(p.getName()).isEqualTo("30-Year Fixed Rate Mortgage"));

        LoanAccount account = loanAccounts.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertThat(account.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(account.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(account.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(account.getInterestRate().scale()).isEqualTo(3);
        assertThat(account.getPropertyType()).isEqualTo("Single Family Residence");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");

        // external_id preserves the legacy PMT_SEQ_NBR
        assertThat(payments.findByExternalId("PMT-2025120001")).isPresent();
    }

    @Test
    void paymentsAreReturnedMostRecentFirst() {
        List<Payment> ordered =
                payments.findByLoanAccount_AccountNumberOrderByPaymentDateDesc("LN-2019-00142");
        assertThat(ordered).extracting(Payment::getExternalId)
                .containsExactly("PMT-2025120001", "PMT-2025110001");
    }

    @Test
    void modernRowCountsReconcileWithLegacy() {
        assertThat(borrowers.count()).isEqualTo(legacyBorrowers.count()).isEqualTo(5);
        assertThat(products.count()).isEqualTo(legacyProducts.count()).isEqualTo(5);
        assertThat(loanAccounts.count()).isEqualTo(legacyLoanAccounts.count()).isEqualTo(5);
        assertThat(payments.count()).isEqualTo(legacyPayments.count()).isEqualTo(10);
    }

    @Test
    void everyLegacyBusinessKeyExistsInModern() {
        legacyBorrowers.findAll().forEach(b ->
                assertThat(borrowers.findByExternalId(b.getBorrowerId())).isPresent());
        legacyPayments.findAll().forEach(p ->
                assertThat(payments.findByExternalId(p.getPaymentSequenceNumber())).isPresent());
    }

    @Test
    void migrationIsIdempotent() {
        MigrationReport report = migrationService.migrate();
        assertThat(report.getInserted()).isZero();
        assertThat(report.getFailed()).isZero();
        assertThat(report.getSkipped()).isEqualTo(25);
    }
}
