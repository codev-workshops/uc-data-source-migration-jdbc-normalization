package com.workshop.loanservice.integration;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.migration.DataMigrationService;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the ETL's referential-integrity guards with real data. Legacy CDW
 * tables have no foreign keys, so a loan can reference a missing borrower or
 * product and a payment can reference a missing loan; the migration must reject
 * these rather than write orphaned rows. Startup migration is disabled so each
 * test seeds a controlled broken data set and invokes {@link
 * DataMigrationService#migrate()} on demand, asserting it fails loudly. No mocks:
 * the real repositories, transaction manager, and H2 databases are used.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.datasource.legacy.url=jdbc:h2:mem:legacydw_fkerr;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.datasource.modern.url=jdbc:h2:mem:moderndw_fkerr;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.datasource.legacy.data=data-legacy-empty.sql",
        "loanservice.migrate-on-startup=false"
})
class DataMigrationReferentialIntegrityTest {

    @Autowired private DataMigrationService migrationService;
    @Autowired private LegacyBorrowerRepository legacyBorrowers;
    @Autowired private LegacyLoanProductRepository legacyProducts;
    @Autowired private LegacyLoanAccountRepository legacyAccounts;
    @Autowired private LegacyPaymentRepository legacyPayments;

    @AfterEach
    void clearLegacy() {
        legacyPayments.deleteAll();
        legacyAccounts.deleteAll();
        legacyProducts.deleteAll();
        legacyBorrowers.deleteAll();
    }

    @Test
    void rejectsLoanWithUnknownBorrower() {
        legacyProducts.save(product("P-1"));
        legacyAccounts.save(account("A-1", "MISSING", "P-1"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, migrationService::migrate);
        assertTrue(ex.getMessage().contains("unknown borrower"), ex.getMessage());
    }

    @Test
    void rejectsLoanWithUnknownProduct() {
        legacyBorrowers.save(borrower("B-1"));
        legacyAccounts.save(account("A-1", "B-1", "MISSING"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, migrationService::migrate);
        assertTrue(ex.getMessage().contains("unknown product"), ex.getMessage());
    }

    @Test
    void rejectsPaymentWithUnknownLoanAccount() {
        legacyBorrowers.save(borrower("B-1"));
        legacyProducts.save(product("P-1"));
        legacyAccounts.save(account("A-1", "B-1", "P-1"));
        legacyPayments.save(payment("PMT-1", "MISSING"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, migrationService::migrate);
        assertTrue(ex.getMessage().contains("unknown loan account"), ex.getMessage());
    }

    // ---- builders for minimally-valid legacy rows -----------------------------

    private static LegacyBorrower borrower(String id) {
        LegacyBorrower b = new LegacyBorrower();
        b.setBorrowerId(id);
        b.setFirstName("Test");
        b.setLastName("Borrower");
        b.setStatusCode("ACT");
        return b;
    }

    private static LegacyLoanProduct product(String code) {
        LegacyLoanProduct p = new LegacyLoanProduct();
        p.setProductCode(code);
        p.setDescription("Test Product");
        p.setTypeCode("FXD");
        p.setTermMonths("360");
        p.setRateType("FIXED");
        p.setStatusCode("ACT");
        return p;
    }

    private static LegacyLoanAccount account(String number, String borrowerId, String productCode) {
        LegacyLoanAccount a = new LegacyLoanAccount();
        a.setLoanAccountNumber(number);
        a.setBorrowerId(borrowerId);
        a.setProductCode(productCode);
        a.setOriginalAmount("100,000");
        a.setCurrentBalance("90,000");
        a.setInterestRate("4.000");
        a.setTermMonths("360");
        a.setMonthlyPayment("500.00");
        a.setOriginationDate("01/01/2020");
        a.setMaturityDate("01/01/2050");
        a.setStatusCode("ACT");
        a.setPropertyType("SFR");
        return a;
    }

    private static LegacyPayment payment(String seq, String accountNumber) {
        LegacyPayment p = new LegacyPayment();
        p.setPaymentSequenceNumber(seq);
        p.setLoanAccountNumber(accountNumber);
        p.setPaymentDate("01/15/2020");
        p.setTotalAmount("500.00");
        p.setTypeCode("REG");
        p.setStatusCode("PST");
        return p;
    }
}
