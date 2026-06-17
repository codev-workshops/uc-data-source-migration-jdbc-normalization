package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation + reconciliation tests for {@link MigrationService}.
 *
 * Runs the migration and then proves:
 *   - row counts match the legacy source (5 / 5 / 5 / 10),
 *   - referential integrity holds (every loan account resolves a borrower and a
 *     product; every payment resolves a loan account),
 *   - monetary totals reconcile between legacy (parsed) and modern (stored),
 *   - representative field-level transformations are correct.
 *
 * The test class runs inside the modern transaction so that the migration's
 * writes and lazy FK navigation share one persistence context; the transaction
 * is rolled back at the end, leaving the modern database untouched.
 */
@SpringBootTest
@Transactional("modernTransactionManager")
class MigrationServiceTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private LegacyLoanAccountRepository legacyLoanAccountRepository;
    @Autowired
    private LegacyPaymentRepository legacyPaymentRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private LoanAccountRepository loanAccountRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    private MigrationResult result;

    @BeforeEach
    void runMigration() {
        result = migrationService.migrate();
    }

    @Test
    void rowCountsMatchLegacySource() {
        assertThat(result.borrowers()).isEqualTo(5);
        assertThat(result.loanProducts()).isEqualTo(5);
        assertThat(result.loanAccounts()).isEqualTo(5);
        assertThat(result.payments()).isEqualTo(10);

        assertThat(borrowerRepository.count()).isEqualTo(5);
        assertThat(loanProductRepository.count()).isEqualTo(5);
        assertThat(loanAccountRepository.count()).isEqualTo(5);
        assertThat(paymentRepository.count()).isEqualTo(10);
    }

    @Test
    void referentialIntegrityHolds() {
        for (LoanAccount account : loanAccountRepository.findAll()) {
            assertThat(account.getBorrower())
                    .as("loan account %s must resolve a borrower", account.getAccountNumber())
                    .isNotNull();
            assertThat(account.getProduct())
                    .as("loan account %s must resolve a product", account.getAccountNumber())
                    .isNotNull();
        }
        for (Payment payment : paymentRepository.findAll()) {
            assertThat(payment.getLoanAccount())
                    .as("payment %s must resolve a loan account", payment.getExternalId())
                    .isNotNull();
        }
    }

    @Test
    void monetaryTotalsReconcile() {
        // Loan account balances: legacy (parsed) vs modern (stored).
        BigDecimal legacyOriginal = BigDecimal.ZERO;
        BigDecimal legacyBalance = BigDecimal.ZERO;
        for (LegacyLoanAccount legacy : legacyLoanAccountRepository.findAll()) {
            legacyOriginal = legacyOriginal.add(LegacyValueConverters.parseAmount(legacy.getOriginalAmount()));
            legacyBalance = legacyBalance.add(LegacyValueConverters.parseAmount(legacy.getCurrentBalance()));
        }
        BigDecimal modernOriginal = BigDecimal.ZERO;
        BigDecimal modernBalance = BigDecimal.ZERO;
        for (LoanAccount account : loanAccountRepository.findAll()) {
            modernOriginal = modernOriginal.add(account.getOriginalAmount());
            modernBalance = modernBalance.add(account.getCurrentBalance());
        }
        assertThat(modernOriginal).isEqualByComparingTo(legacyOriginal);
        assertThat(modernBalance).isEqualByComparingTo(legacyBalance);

        // Payment totals: legacy (parsed) vs modern (stored).
        BigDecimal legacyPayments = BigDecimal.ZERO;
        for (LegacyPayment legacy : legacyPaymentRepository.findAll()) {
            legacyPayments = legacyPayments.add(LegacyValueConverters.parseAmount(legacy.getTotalAmount()));
        }
        BigDecimal modernPayments = BigDecimal.ZERO;
        for (Payment payment : paymentRepository.findAll()) {
            modernPayments = modernPayments.add(payment.getTotalAmount());
        }
        assertThat(modernPayments).isEqualByComparingTo(legacyPayments);
    }

    @Test
    void borrowerFieldsAreTransformed() {
        Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();
        assertThat(borrower.getFirstName()).isEqualTo("James");
        assertThat(borrower.getLastName()).isEqualTo("Mitchell");
        assertThat(borrower.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(borrower.getCreditScore()).isEqualTo(745);
        assertThat(borrower.getAnnualIncome()).isEqualByComparingTo("92500");
        assertThat(borrower.getStatus()).isEqualTo("ACTIVE");
        assertThat(borrower.getCreatedAt()).isEqualTo(LocalDate.of(2019, 1, 15).atStartOfDay());
    }

    @Test
    void loanProductFieldsAreTransformed() {
        LoanProduct product = loanProductRepository.findByCode("FXD30").orElseThrow();
        assertThat(product.getName()).isEqualTo("30-Year Fixed Rate Mortgage");
        assertThat(product.getType()).isEqualTo("FXD");
        assertThat(product.getTermMonths()).isEqualTo(360);
        assertThat(product.getRateType()).isEqualTo("FIXED");
        assertThat(product.getMinAmount()).isEqualByComparingTo("50000");
        assertThat(product.getMaxAmount()).isEqualByComparingTo("1500000");
        assertThat(product.getActive()).isTrue();
    }

    @Test
    void loanAccountFieldsAndForeignKeysAreTransformed() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertThat(account.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(account.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(account.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(account.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(account.getTermMonths()).isEqualTo(360);
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getPropertyType()).isEqualTo("Single Family");
        assertThat(account.getLtvPercent()).isEqualByComparingTo("82.5");
        assertThat(account.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));

        List<LoanAccount> byBorrower = loanAccountRepository.findByBorrower_ExternalId("B-10001");
        assertThat(byBorrower).hasSize(1);
    }

    @Test
    void paymentFieldsForeignKeysAndExternalIdAreTransformed() {
        List<Payment> payments =
                paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc("LN-2019-00142");
        assertThat(payments).hasSize(2);

        Payment latest = payments.get(0);
        assertThat(latest.getExternalId()).isEqualTo("PMT-2025120001");
        assertThat(latest.getLoanAccount().getAccountNumber()).isEqualTo("LN-2019-00142");
        assertThat(latest.getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(latest.getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(latest.getType()).isEqualTo("REGULAR");
        assertThat(latest.getStatus()).isEqualTo("POSTED");
    }
}
