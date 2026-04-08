package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModernLoanService using pre-seeded modern data (data-modern.sql).
 * Migration is no longer needed — modern tables are populated at startup.
 */
@SpringBootTest
@Transactional
class ModernLoanServiceTest {

    @Autowired
    private ModernLoanService modernLoanService;

    @Autowired
    private LoanService loanService;

    // =========================================================================
    // Test 1: getAllLoans count matches
    // =========================================================================

    @Test
    void testGetAllLoans_countMatches() {
        List<LoanSummaryDto> modernLoans = modernLoanService.getAllLoans();
        List<LoanSummaryDto> legacyLoans = loanService.getAllLoans();
        assertEquals(5, modernLoans.size());
        assertEquals(5, legacyLoans.size());
    }

    // =========================================================================
    // Test 2: getAllLoans field values match
    // =========================================================================

    @Test
    void testGetAllLoans_fieldValuesMatch() {
        List<LoanSummaryDto> modernLoans = modernLoanService.getAllLoans();
        List<LoanSummaryDto> legacyLoans = loanService.getAllLoans();

        modernLoans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));
        legacyLoans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));

        for (int i = 0; i < modernLoans.size(); i++) {
            LoanSummaryDto modern = modernLoans.get(i);
            LoanSummaryDto legacy = legacyLoans.get(i);

            assertEquals(legacy.getLoanAccountNumber(), modern.getLoanAccountNumber());
            assertEquals(legacy.getBorrowerName(), modern.getBorrowerName());
            assertEquals(legacy.getProductDescription(), modern.getProductDescription());
            assertEquals(0, legacy.getOriginalAmount().compareTo(modern.getOriginalAmount()));
            assertEquals(0, legacy.getCurrentBalance().compareTo(modern.getCurrentBalance()));
            assertEquals(0, legacy.getInterestRate().compareTo(modern.getInterestRate()));
            assertEquals(0, legacy.getMonthlyPayment().compareTo(modern.getMonthlyPayment()));
            assertEquals(legacy.getStatus(), modern.getStatus());
            assertEquals(legacy.getOriginationDate(), modern.getOriginationDate());
            assertEquals(legacy.getPropertyAddress(), modern.getPropertyAddress());
            assertEquals(legacy.getPropertyType(), modern.getPropertyType());
        }
    }

    // =========================================================================
    // Test 3: getLoanById
    // =========================================================================

    @Test
    void testGetLoanById() {
        LoanSummaryDto modern = modernLoanService.getLoanById("LN-2019-00142");
        LoanSummaryDto legacy = loanService.getLoanById("LN-2019-00142");

        assertEquals(legacy.getLoanAccountNumber(), modern.getLoanAccountNumber());
        assertEquals("James Mitchell", modern.getBorrowerName());
        assertEquals("30-Year Fixed Rate Mortgage", modern.getProductDescription());
        assertEquals(0, legacy.getOriginalAmount().compareTo(modern.getOriginalAmount()));
        assertEquals("Active", modern.getStatus());
        assertEquals("02/15/2019", modern.getOriginationDate());
        assertEquals("Single Family Residence", modern.getPropertyType());
    }

    // =========================================================================
    // Test 4: getLoanById not found
    // =========================================================================

    @Test
    void testGetLoanById_notFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> modernLoanService.getLoanById("NONEXISTENT"));
        assertTrue(ex.getMessage().contains("Loan not found"));
    }

    // =========================================================================
    // Test 5: getAllBorrowers count matches
    // =========================================================================

    @Test
    void testGetAllBorrowers_countMatches() {
        List<BorrowerDto> modernBorrowers = modernLoanService.getAllBorrowers();
        List<BorrowerDto> legacyBorrowers = loanService.getAllBorrowers();
        assertEquals(5, modernBorrowers.size());
        assertEquals(5, legacyBorrowers.size());
    }

    // =========================================================================
    // Test 6: getAllBorrowers field values match
    // =========================================================================

    @Test
    void testGetAllBorrowers_fieldValuesMatch() {
        List<BorrowerDto> modernBorrowers = modernLoanService.getAllBorrowers();
        List<BorrowerDto> legacyBorrowers = loanService.getAllBorrowers();

        modernBorrowers.sort(Comparator.comparing(BorrowerDto::getId));
        legacyBorrowers.sort(Comparator.comparing(BorrowerDto::getId));

        for (int i = 0; i < modernBorrowers.size(); i++) {
            BorrowerDto modern = modernBorrowers.get(i);
            BorrowerDto legacy = legacyBorrowers.get(i);

            assertEquals(legacy.getId(), modern.getId());
            assertEquals(legacy.getFullName(), modern.getFullName());
            assertEquals(legacy.getEmail(), modern.getEmail());
            assertEquals(legacy.getPhone(), modern.getPhone());
            assertEquals(legacy.getCity(), modern.getCity());
            assertEquals(legacy.getState(), modern.getState());
            assertEquals(legacy.getCreditScore(), modern.getCreditScore());
            assertEquals(legacy.getEmploymentStatus(), modern.getEmploymentStatus());
            assertNull(modern.getLoans());
            assertNull(legacy.getLoans());
        }
    }

    // =========================================================================
    // Test 7: getBorrowerById with loans
    // =========================================================================

    @Test
    void testGetBorrowerById_withLoans() {
        BorrowerDto modern = modernLoanService.getBorrowerById("B-10001");
        BorrowerDto legacy = loanService.getBorrowerById("B-10001");

        assertEquals("James R. Mitchell", modern.getFullName());
        assertEquals(745, modern.getCreditScore());
        assertNotNull(modern.getLoans());
        assertEquals(1, modern.getLoans().size());
        assertEquals("LN-2019-00142", modern.getLoans().get(0).getLoanAccountNumber());

        // Compare all loan fields with legacy
        LoanSummaryDto modernLoan = modern.getLoans().get(0);
        LoanSummaryDto legacyLoan = legacy.getLoans().get(0);
        assertEquals(legacyLoan.getLoanAccountNumber(), modernLoan.getLoanAccountNumber());
        assertEquals(legacyLoan.getBorrowerName(), modernLoan.getBorrowerName());
        assertEquals(legacyLoan.getProductDescription(), modernLoan.getProductDescription());
        assertEquals(0, legacyLoan.getOriginalAmount().compareTo(modernLoan.getOriginalAmount()));
        assertEquals(0, legacyLoan.getCurrentBalance().compareTo(modernLoan.getCurrentBalance()));
        assertEquals(legacyLoan.getStatus(), modernLoan.getStatus());
        assertEquals(legacyLoan.getOriginationDate(), modernLoan.getOriginationDate());
        assertEquals(legacyLoan.getPropertyAddress(), modernLoan.getPropertyAddress());
        assertEquals(legacyLoan.getPropertyType(), modernLoan.getPropertyType());
    }

    // =========================================================================
    // Test 8: getBorrowerById null middle initial
    // =========================================================================

    @Test
    void testGetBorrowerById_nullMiddleInitial() {
        BorrowerDto modern = modernLoanService.getBorrowerById("B-10005");
        assertEquals("Robert Williams", modern.getFullName());
        assertEquals(loanService.getBorrowerById("B-10005").getFullName(), modern.getFullName());
    }

    // =========================================================================
    // Test 9: getBorrowerById not found
    // =========================================================================

    @Test
    void testGetBorrowerById_notFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> modernLoanService.getBorrowerById("NONEXISTENT"));
        assertTrue(ex.getMessage().contains("Borrower not found"));
    }

    // =========================================================================
    // Test 10: getPaymentsByLoan count and order
    // =========================================================================

    @Test
    void testGetPaymentsByLoan_countAndOrder() {
        List<PaymentDto> modernPayments = modernLoanService.getPaymentsByLoan("LN-2019-00142");
        List<PaymentDto> legacyPayments = loanService.getPaymentsByLoan("LN-2019-00142");

        assertEquals(2, modernPayments.size());
        assertEquals(2, legacyPayments.size());

        // Assert modern payments are ordered by date descending (Dec before Nov)
        assertTrue(modernPayments.get(0).getPaymentDate().compareTo(modernPayments.get(1).getPaymentDate()) > 0);
    }

    // =========================================================================
    // Test 11: getPaymentsByLoan field values match
    // =========================================================================

    @Test
    void testGetPaymentsByLoan_fieldValuesMatch() {
        List<PaymentDto> modernPayments = modernLoanService.getPaymentsByLoan("LN-2019-00142");
        List<PaymentDto> legacyPayments = loanService.getPaymentsByLoan("LN-2019-00142");

        // Both should already be sorted by date descending
        for (int i = 0; i < modernPayments.size(); i++) {
            PaymentDto modern = modernPayments.get(i);
            PaymentDto legacy = legacyPayments.get(i);

            assertEquals(legacy.getLoanAccountNumber(), modern.getLoanAccountNumber());
            assertEquals(legacy.getPaymentDate(), modern.getPaymentDate());
            assertEquals(0, legacy.getTotalAmount().compareTo(modern.getTotalAmount()));
            assertEquals(0, legacy.getPrincipalAmount().compareTo(modern.getPrincipalAmount()));
            assertEquals(0, legacy.getInterestAmount().compareTo(modern.getInterestAmount()));
            assertEquals(0, legacy.getEscrowAmount().compareTo(modern.getEscrowAmount()));
            assertEquals(0, legacy.getLateFee().compareTo(modern.getLateFee()));
            assertEquals(legacy.getType(), modern.getType());
            assertEquals(legacy.getStatus(), modern.getStatus());
            // Skip paymentId — accepted difference (legacy: "PMT-2025120001", modern: auto-generated Long)
        }
    }

    // =========================================================================
    // Test 12: getPaymentsByLoan with late fee
    // =========================================================================

    @Test
    void testGetPaymentsByLoan_withLateFee() {
        List<PaymentDto> payments = modernLoanService.getPaymentsByLoan("LN-2018-00089");

        // Find the Nov 2025 payment
        PaymentDto novPayment = payments.stream()
                .filter(p -> p.getPaymentDate().equals("11/01/2025"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nov 2025 payment not found"));

        assertEquals(0, new BigDecimal("47.50").compareTo(novPayment.getLateFee()));
        assertEquals("Regular", novPayment.getType());
        assertEquals("Posted", novPayment.getStatus());
    }

    // =========================================================================
    // Test 13: legacy service still works
    // =========================================================================

    @Test
    void testLegacyServiceStillWorks() {
        // getAllLoans
        List<LoanSummaryDto> loans = loanService.getAllLoans();
        assertEquals(5, loans.size());

        // getLoanById
        LoanSummaryDto loan = loanService.getLoanById("LN-2019-00142");
        assertEquals("LN-2019-00142", loan.getLoanAccountNumber());

        // getAllBorrowers
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();
        assertEquals(5, borrowers.size());

        // getBorrowerById
        BorrowerDto borrower = loanService.getBorrowerById("B-10001");
        assertEquals("James R. Mitchell", borrower.getFullName());
        assertNotNull(borrower.getLoans());

        // getPaymentsByLoan
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");
        assertEquals(2, payments.size());
    }
}
