package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.MigrationResult;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.ModernLoanAccountRepository;
import com.workshop.loanservice.repository.ModernLoanProductRepository;
import com.workshop.loanservice.repository.ModernPaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DataMigrationServiceTest {

    @Autowired
    private DataMigrationService migrationService;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private ModernLoanProductRepository modernLoanProductRepository;

    @Autowired
    private ModernLoanAccountRepository modernLoanAccountRepository;

    @Autowired
    private ModernPaymentRepository modernPaymentRepository;

    @Autowired
    private LoanService loanService;

    @Test
    void testMigrationCounts() {
        MigrationResult result = migrationService.migrate();
        assertEquals(5, result.getBorrowersMigrated());
        assertEquals(5, result.getProductsMigrated());
        assertEquals(5, result.getLoansMigrated());
        assertEquals(10, result.getPaymentsMigrated());
    }

    @Test
    void testBorrowerDataTransformation() {
        migrationService.migrate();
        Optional<Borrower> opt = borrowerRepository.findByExternalId("B-10001");
        assertTrue(opt.isPresent());
        Borrower b = opt.get();
        assertEquals("James", b.getFirstName());
        assertEquals("Mitchell", b.getLastName());
        assertEquals(745, b.getCreditScore());
        assertEquals(0, new BigDecimal("92500").compareTo(b.getAnnualIncome()));
        assertEquals(LocalDate.of(1978, 3, 15), b.getDateOfBirth());
        assertEquals("ACTIVE", b.getStatus());
        assertEquals("IL", b.getState());
        assertEquals("j.mitchell@email.com", b.getEmail());
    }

    @Test
    void testLoanProductDataTransformation() {
        migrationService.migrate();
        var opt = modernLoanProductRepository.findByCode("FXD30");
        assertTrue(opt.isPresent());
        var p = opt.get();
        assertEquals("30-Year Fixed Rate Mortgage", p.getName());
        assertEquals(360, p.getTermMonths());
        assertEquals(0, new BigDecimal("50000").compareTo(p.getMinAmount()));
        assertEquals(0, new BigDecimal("1500000").compareTo(p.getMaxAmount()));
        assertTrue(p.getIsActive());
        assertEquals("FIXED", p.getRateType());
    }

    @Test
    void testLoanAccountForeignKeys() {
        migrationService.migrate();
        var opt = modernLoanAccountRepository.findByAccountNumber("LN-2019-00142");
        assertTrue(opt.isPresent());
        LoanAccount account = opt.get();
        assertNotNull(account.getBorrower());
        assertEquals("B-10001", account.getBorrower().getExternalId());
        assertNotNull(account.getProduct());
        assertEquals("FXD30", account.getProduct().getCode());
    }

    @Test
    void testLoanAccountDataTransformation() {
        migrationService.migrate();
        var opt = modernLoanAccountRepository.findByAccountNumber("LN-2019-00142");
        assertTrue(opt.isPresent());
        LoanAccount a = opt.get();
        assertEquals(0, new BigDecimal("285000").compareTo(a.getOriginalAmount()));
        assertEquals(0, new BigDecimal("271432.56").compareTo(a.getCurrentBalance()));
        assertEquals(0, new BigDecimal("4.750").compareTo(a.getInterestRate()));
        assertEquals(360, a.getTermMonths());
        assertEquals("ACTIVE", a.getStatus());
        assertEquals("Single Family Residence", a.getPropertyType());
        assertEquals(LocalDate.of(2019, 2, 15), a.getOriginationDate());
        assertEquals(0, a.getDelinquencyDays());
    }

    @Test
    void testPaymentDataTransformation() {
        migrationService.migrate();
        var accountOpt = modernLoanAccountRepository.findByAccountNumber("LN-2019-00142");
        assertTrue(accountOpt.isPresent());
        Long loanAccountId = accountOpt.get().getId();

        List<Payment> payments = modernPaymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(loanAccountId);
        assertEquals(2, payments.size());

        Payment first = payments.get(0);
        assertEquals(0, new BigDecimal("1487.02").compareTo(first.getTotalAmount()));
        assertEquals(0, new BigDecimal("456.78").compareTo(first.getPrincipalAmount()));
        assertEquals("REGULAR", first.getType());
        assertEquals("POSTED", first.getStatus());
        assertEquals(LocalDate.of(2025, 12, 15), first.getPaymentDate());
    }

    @Test
    void testDenormalizedFieldsDropped() {
        migrationService.migrate();
        var opt = modernLoanAccountRepository.findByAccountNumber("LN-2019-00142");
        assertTrue(opt.isPresent());
        LoanAccount account = opt.get();
        assertEquals("James", account.getBorrower().getFirstName());
    }

    @Test
    void testNoOrphanedRecords() {
        migrationService.migrate();
        for (Payment payment : modernPaymentRepository.findAll()) {
            assertNotNull(payment.getLoanAccount());
        }
        for (LoanAccount account : modernLoanAccountRepository.findAll()) {
            assertNotNull(account.getBorrower());
            assertNotNull(account.getProduct());
        }
    }

    @Test
    void testLegacyEndpointsStillWork() {
        assertEquals(5, loanService.getAllLoans().size());
        assertEquals(5, loanService.getAllBorrowers().size());
    }
}
