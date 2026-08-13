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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the startup migration produced the expected modern rows.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:migrationdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class DataMigrationServiceTest {

    @Autowired
    private DataMigrationService migrationService;
    @Autowired
    private BorrowerRepository borrowers;
    @Autowired
    private LoanProductRepository products;
    @Autowired
    private LoanAccountRepository loanAccounts;
    @Autowired
    private PaymentRepository payments;

    @Test
    void rowCountsMatchTheLegacySource() {
        assertEquals(5, borrowers.count());
        assertEquals(5, products.count());
        assertEquals(5, loanAccounts.count());
        assertEquals(10, payments.count());
    }

    @Test
    void reRunningTheMigrationIsIdempotent() {
        MigrationReport report = migrationService.migrate();

        assertEquals(0, report.getBorrowersMigrated());
        assertEquals(0, report.getLoanAccountsMigrated());
        assertEquals(25, report.getSkipped().size());
        assertTrue(report.isValid(), () -> "unexpected validation failures: " + report.getValidationFailures());
    }

    @Test
    void borrowerValuesAreProperlyTyped() {
        Borrower borrower = borrowers.findByExternalId("B-10001").orElseThrow();

        assertEquals(LocalDate.of(1978, 3, 15), borrower.getDateOfBirth());
        assertEquals(745, borrower.getCreditScore());
        assertEquals(0, new BigDecimal("92500.00").compareTo(borrower.getAnnualIncome()));
        assertEquals("ACTIVE", borrower.getStatus());
        assertNotNull(borrower.getCreatedAt());
    }

    @Test
    void loanAccountResolvesForeignKeysAndExpandsCodes() {
        LoanAccount account = loanAccounts.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertEquals("B-10001", account.getBorrower().getExternalId());
        assertEquals("FXD30", account.getProduct().getCode());
        assertEquals(0, new BigDecimal("285000.00").compareTo(account.getOriginalAmount()));
        assertEquals(0, new BigDecimal("4.750").compareTo(account.getInterestRate()));
        assertEquals(LocalDate.of(2019, 2, 15), account.getOriginationDate());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals("Single Family Residence", account.getPropertyType());
    }

    @Test
    void productFlagsAreBooleans() {
        LoanProduct product = products.findByCode("FXD30").orElseThrow();

        assertEquals(Boolean.TRUE, product.getActive());
        assertEquals(360, product.getTermMonths());
        assertEquals(0, new BigDecimal("1500000.00").compareTo(product.getMaxAmount()));
    }

    @Test
    void paymentsAreLinkedToTheirLoanAndOrderedByDate() {
        LoanAccount account = loanAccounts.findByAccountNumber("LN-2019-00142").orElseThrow();
        List<Payment> loanPayments = payments.findByLoanAccountOrderByPaymentDateDesc(account);

        assertEquals(List.of("PMT-2025120001", "PMT-2025110001"),
                loanPayments.stream().map(Payment::getExternalId).toList());
        assertEquals("POSTED", loanPayments.get(0).getStatus());
        assertEquals("REGULAR", loanPayments.get(0).getType());
        assertEquals(LocalDate.of(2025, 12, 15), loanPayments.get(0).getPaymentDate());
    }
}
