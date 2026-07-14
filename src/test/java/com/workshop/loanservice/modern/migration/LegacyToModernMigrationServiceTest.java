package com.workshop.loanservice.modern.migration;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;

import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.migration.LegacyToModernMigrationService.MigrationResult;
import com.workshop.loanservice.modern.repository.ModernBorrowerRepository;
import com.workshop.loanservice.modern.repository.ModernLoanAccountRepository;
import com.workshop.loanservice.modern.repository.ModernLoanProductRepository;
import com.workshop.loanservice.modern.repository.ModernPaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the legacy-to-modern data migration: row counts, foreign key
 * resolution, amount reconciliation between legacy (parsed) and modern
 * values, and idempotency (running twice creates no duplicates).
 */
@SpringBootTest(properties = "migration.run-on-startup=false")
@Transactional("modernTransactionManager")
class LegacyToModernMigrationServiceTest {

    @Autowired
    private LegacyToModernMigrationService migrationService;

    @Autowired
    private LegacyBorrowerRepository legacyBorrowerRepository;
    @Autowired
    private LegacyLoanProductRepository legacyLoanProductRepository;
    @Autowired
    private LegacyLoanAccountRepository legacyLoanAccountRepository;
    @Autowired
    private LegacyPaymentRepository legacyPaymentRepository;

    @Autowired
    private ModernBorrowerRepository modernBorrowerRepository;
    @Autowired
    private ModernLoanProductRepository modernLoanProductRepository;
    @Autowired
    private ModernLoanAccountRepository modernLoanAccountRepository;
    @Autowired
    private ModernPaymentRepository modernPaymentRepository;

    @Test
    void migratesAllRecordsWithExpectedCounts() {
        MigrationResult result = migrationService.migrateAll();

        assertEquals(5, result.getBorrowersMigrated());
        assertEquals(5, result.getProductsMigrated());
        assertEquals(5, result.getAccountsMigrated());
        assertEquals(10, result.getPaymentsMigrated());
        assertEquals(0, result.getBorrowersSkippedRecords());
        assertEquals(0, result.getProductsSkippedRecords());
        assertEquals(0, result.getAccountsSkippedRecords());
        assertEquals(0, result.getPaymentsSkippedRecords());

        assertEquals(5, modernBorrowerRepository.count());
        assertEquals(5, modernLoanProductRepository.count());
        assertEquals(5, modernLoanAccountRepository.count());
        assertEquals(10, modernPaymentRepository.count());
    }

    @Test
    void rowCountsReconcileLegacyVsModern() {
        migrationService.migrateAll();

        assertEquals(legacyBorrowerRepository.count(), modernBorrowerRepository.count());
        assertEquals(legacyLoanProductRepository.count(), modernLoanProductRepository.count());
        assertEquals(legacyLoanAccountRepository.count(), modernLoanAccountRepository.count());
        assertEquals(legacyPaymentRepository.count(), modernPaymentRepository.count());
    }

    @Test
    void amountSumsReconcileLegacyVsModern() {
        migrationService.migrateAll();

        BigDecimal legacyIncome = sumLegacy(legacyBorrowerRepository.findAll().stream()
                .map(LegacyBorrower::getAnnualIncome));
        BigDecimal modernIncome = sumModern(modernBorrowerRepository.findAll(), Borrower::getAnnualIncome);
        assertEquals(0, legacyIncome.compareTo(modernIncome), "annual_income sums differ");

        BigDecimal legacyOriginal = sumLegacy(legacyLoanAccountRepository.findAll().stream()
                .map(LegacyLoanAccount::getOriginalAmount));
        BigDecimal modernOriginal = sumModern(modernLoanAccountRepository.findAll(), LoanAccount::getOriginalAmount);
        assertEquals(0, legacyOriginal.compareTo(modernOriginal), "original_amount sums differ");

        BigDecimal legacyBalance = sumLegacy(legacyLoanAccountRepository.findAll().stream()
                .map(LegacyLoanAccount::getCurrentBalance));
        BigDecimal modernBalance = sumModern(modernLoanAccountRepository.findAll(), LoanAccount::getCurrentBalance);
        assertEquals(0, legacyBalance.compareTo(modernBalance), "current_balance sums differ");

        BigDecimal legacyPayments = sumLegacy(legacyPaymentRepository.findAll().stream()
                .map(LegacyPayment::getTotalAmount));
        BigDecimal modernPayments = sumModern(modernPaymentRepository.findAll(), Payment::getTotalAmount);
        assertEquals(0, legacyPayments.compareTo(modernPayments), "payment total_amount sums differ");
    }

    @Test
    void resolvesForeignKeys() {
        migrationService.migrateAll();

        for (LoanAccount account : modernLoanAccountRepository.findAll()) {
            assertNotNull(account.getBorrower(), "loan account missing borrower FK");
            assertNotNull(account.getProduct(), "loan account missing product FK");
        }
        for (Payment payment : modernPaymentRepository.findAll()) {
            assertNotNull(payment.getLoanAccount(), "payment missing loan account FK");
        }

        LoanAccount account = modernLoanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertEquals("B-10001", account.getBorrower().getExternalId());
        assertEquals("FXD30", account.getProduct().getCode());
    }

    @Test
    void migrationIsIdempotent() {
        migrationService.migrateAll();
        MigrationResult second = migrationService.migrateAll();

        assertEquals(0, second.getBorrowersMigrated());
        assertEquals(0, second.getProductsMigrated());
        assertEquals(0, second.getAccountsMigrated());
        assertEquals(0, second.getPaymentsMigrated());
        assertEquals(5, second.getBorrowersSkipped());
        assertEquals(5, second.getProductsSkipped());
        assertEquals(5, second.getAccountsSkipped());
        assertEquals(10, second.getPaymentsSkipped());

        assertEquals(5, modernBorrowerRepository.count());
        assertEquals(5, modernLoanProductRepository.count());
        assertEquals(5, modernLoanAccountRepository.count());
        assertEquals(10, modernPaymentRepository.count());
    }

    @Test
    void transformsTypesAndExpandsCodes() {
        migrationService.migrateAll();

        Borrower borrower = modernBorrowerRepository.findByExternalId("B-10001").orElseThrow();
        assertEquals(java.time.LocalDate.of(1978, 3, 15), borrower.getDateOfBirth());
        assertEquals(745, borrower.getCreditScore());
        assertEquals(0, new BigDecimal("92500").compareTo(borrower.getAnnualIncome()));
        assertEquals("ACTIVE", borrower.getStatus());

        LoanAccount account = modernLoanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertEquals("ACTIVE", account.getStatus());
        assertEquals("Single Family", account.getPropertyType());
        assertEquals(0, new BigDecimal("4.750").compareTo(account.getInterestRate()));

        assertTrue(modernPaymentRepository.findAll().stream()
                .allMatch(p -> "REGULAR".equals(p.getType()) && "POSTED".equals(p.getStatus())));
    }

    @Test
    void skipsMalformedPaymentWithWarningWithoutAbortingRun() {
        LegacyPayment malformed = new LegacyPayment();
        malformed.setPaymentSequenceNumber("P-99999");
        malformed.setLoanAccountNumber("LN-2019-00142");
        malformed.setPaymentDate("NOT-A-DATE");
        malformed.setTotalAmount("1,000.00");
        malformed.setTypeCode("REG");
        malformed.setStatusCode("PST");
        legacyPaymentRepository.save(malformed);
        try {
            assertEquals(11, legacyPaymentRepository.count());

            MigrationResult first = migrationService.migrateAll();
            assertEquals(10, first.getPaymentsMigrated());
            assertEquals(1, first.getPaymentsSkippedRecords());
            assertEquals(10, modernPaymentRepository.count());

            BigDecimal legacyValidSum = sumLegacy(legacyPaymentRepository.findAll().stream()
                    .filter(p -> !"P-99999".equals(p.getPaymentSequenceNumber()))
                    .map(LegacyPayment::getTotalAmount));
            BigDecimal modernSum = sumModern(modernPaymentRepository.findAll(), Payment::getTotalAmount);
            assertEquals(0, legacyValidSum.compareTo(modernSum), "payment total_amount sums differ");

            MigrationResult second = migrationService.migrateAll();
            assertEquals(0, second.getPaymentsMigrated());
            assertEquals(10, second.getPaymentsSkipped());
            assertEquals(1, second.getPaymentsSkippedRecords());
            assertEquals(10, modernPaymentRepository.count());
        } finally {
            legacyPaymentRepository.deleteById("P-99999");
        }
    }

    private static BigDecimal sumLegacy(java.util.stream.Stream<String> values) {
        return values.filter(Objects::nonNull)
                .map(v -> new BigDecimal(v.replace(",", "")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> BigDecimal sumModern(Iterable<T> entities, Function<T, BigDecimal> getter) {
        BigDecimal sum = BigDecimal.ZERO;
        for (T entity : entities) {
            BigDecimal value = getter.apply(entity);
            if (value != null) {
                sum = sum.add(value);
            }
        }
        return sum;
    }
}
