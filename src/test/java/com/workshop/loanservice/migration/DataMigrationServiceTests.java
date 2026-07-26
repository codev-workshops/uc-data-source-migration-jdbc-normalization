package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliation tests for the legacy to modern data migration.
 */
@SpringBootTest
class DataMigrationServiceTests {

    @Autowired
    private DataMigrationService migrationService;
    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private LoanAccountRepository loanAccountRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void migrationRunsOnStartupAndRowCountsMatchLegacy() {
        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanProductRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }

    @Test
    void migrationIsIdempotent() {
        DataMigrationService.MigrationReport report = migrationService.migrate();

        assertThat(report).isEqualTo(new DataMigrationService.MigrationReport(5, 5, 5, 10));
        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }

    @Test
    void borrowerValuesAreProperlyTyped() {
        Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();

        assertThat(borrower.getId()).isNotNull();
        assertThat(borrower.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(borrower.getCreditScore()).isEqualTo(745);
        assertThat(borrower.getAnnualIncome()).isEqualByComparingTo("92500");
        assertThat(borrower.getStatus()).isEqualTo("ACTIVE");
        assertThat(borrower.getCreatedAt()).isEqualTo(LocalDate.of(2019, 1, 15).atStartOfDay());
    }

    @Test
    void loanAccountForeignKeysAreResolved() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertThat(account.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(account.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(account.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(account.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(account.getTermMonths()).isEqualTo(360);
        assertThat(account.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(account.getMaturityDate()).isEqualTo(LocalDate.of(2049, 2, 15));
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getDelinquencyDays()).isZero();
        assertThat(account.getEscrowBalance()).isEqualByComparingTo("3245.80");
        assertThat(account.getPropertyType()).isEqualTo("Single Family Residence");
    }

    @Test
    void productFlagsAreBooleans() {
        assertThat(loanProductRepository.findByCode("ARM51").orElseThrow().getActive()).isTrue();
        assertThat(loanProductRepository.findByCode("ARM51").orElseThrow().getRateType())
                .isEqualTo("VARIABLE");
        assertThat(loanProductRepository.findByCode("FHA30").orElseThrow().getMaxAmount())
                .isEqualByComparingTo("472030");
    }

    @Test
    void paymentsKeepLegacyIdentifiersAndExpandedCodes() {
        var payments = paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2018-00089");

        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).getExternalId()).isEqualTo("PMT-2025120003");
        assertThat(payments.get(0).getType()).isEqualTo("REGULAR");
        assertThat(payments.get(0).getStatus()).isEqualTo("POSTED");
        assertThat(payments.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(payments.get(1).getLateFee()).isEqualByComparingTo("47.50");
    }

    @Test
    void migratedAmountsReconcileWithLegacyTotals() {
        var totalBalance = loanAccountRepository.findAll().stream()
                .map(LoanAccount::getCurrentBalance)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // 271432.56 + 312876.43 + 178234.12 + 498123.78 + 142567.90
        assertThat(totalBalance).isEqualByComparingTo("1403234.79");
    }
}
