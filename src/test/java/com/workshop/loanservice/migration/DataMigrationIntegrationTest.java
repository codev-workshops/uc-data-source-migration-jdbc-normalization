package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the startup migration: row counts, foreign key integrity and a few
 * spot-checked type conversions.
 */
@SpringBootTest
@Transactional
class DataMigrationIntegrationTest {

    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private LoanAccountRepository loanAccountRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private DataMigrationService migrationService;

    @Test
    void migratesEveryLegacyRow() {
        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanProductRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }

    @Test
    void resolvesEveryForeignKey() {
        List<LoanAccount> accounts = loanAccountRepository.findAll();
        assertThat(accounts).allSatisfy(account -> {
            assertThat(account.getBorrower()).isNotNull();
            assertThat(account.getBorrower().getExternalId()).isNotBlank();
            assertThat(account.getProduct()).isNotNull();
            assertThat(account.getProduct().getCode()).isNotBlank();
        });
        assertThat(paymentRepository.findAll()).allSatisfy(payment -> {
            assertThat(payment.getLoanAccount()).isNotNull();
            assertThat(payment.getLegacyId()).startsWith("PMT-");
        });
    }

    @Test
    void convertsTypesOnLoanAccount() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertThat(account.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(account.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(account.getMonthlyPayment()).isEqualByComparingTo("1487.02");
        assertThat(account.getTermMonths()).isEqualTo(360);
        assertThat(account.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(account.getMaturityDate()).isEqualTo(LocalDate.of(2049, 2, 15));
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getPropertyType()).isEqualTo("SINGLE_FAMILY");
        assertThat(account.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(account.getProduct().getCode()).isEqualTo("FXD30");
    }

    @Test
    void convertsTypesOnBorrowerProductAndPayment() {
        Borrower borrower = borrowerRepository.findByExternalId("B-10005").orElseThrow();
        assertThat(borrower.getMiddleInitial()).isNull();
        assertThat(borrower.getCreditScore()).isEqualTo(658);
        assertThat(borrower.getAnnualIncome()).isEqualByComparingTo("65000");
        assertThat(borrower.getDateOfBirth()).isEqualTo(LocalDate.of(1968, 6, 14));
        assertThat(borrower.getStatus()).isEqualTo("ACTIVE");

        LoanProduct product = loanProductRepository.findByCode("FHA30").orElseThrow();
        assertThat(product.getIsActive()).isTrue();
        assertThat(product.getTermMonths()).isEqualTo(360);
        assertThat(product.getMaxAmount()).isEqualByComparingTo("472030");

        List<Payment> payments = paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc("LN-2018-00089");
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(payments.get(1).getLateFee()).isEqualByComparingTo("47.50");
        assertThat(payments.get(0).getType()).isEqualTo("REGULAR");
        assertThat(payments.get(0).getStatus()).isEqualTo("POSTED");
    }

    @Test
    void migrationIsIdempotent() {
        DataMigrationService.MigrationReport report = migrationService.migrate();

        assertThat(report.countsMatch()).isTrue();
        assertThat(report.modernBorrowers()).isEqualTo(5);
        assertThat(report.modernProducts()).isEqualTo(5);
        assertThat(report.modernLoanAccounts()).isEqualTo(5);
        assertThat(report.modernPayments()).isEqualTo(10);
    }
}
