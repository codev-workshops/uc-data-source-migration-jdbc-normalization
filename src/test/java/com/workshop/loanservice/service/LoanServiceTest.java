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
 * Tests for LoanService using pre-seeded data (data-modern.sql).
 */
@SpringBootTest
@Transactional
class LoanServiceTest {

    @Autowired
    private LoanService loanService;

    // =========================================================================
    // Test 1: getAllLoans count
    // =========================================================================

    @Test
    void testGetAllLoans_count() {
        List<LoanSummaryDto> loans = loanService.getAllLoans();
        assertEquals(5, loans.size());
    }

    // =========================================================================
    // Test 2: getAllLoans field values
    // =========================================================================

    @Test
    void testGetAllLoans_fieldValues() {
        List<LoanSummaryDto> loans = loanService.getAllLoans();
        loans.sort(Comparator.comparing(LoanSummaryDto::getLoanAccountNumber));

        LoanSummaryDto loan = loans.stream()
                .filter(l -> "LN-2019-00142".equals(l.getLoanAccountNumber()))
                .findFirst()
                .orElseThrow();

        assertEquals("James Mitchell", loan.getBorrowerName());
        assertEquals("Active", loan.getStatus());
        assertEquals("Single Family Residence", loan.getPropertyType());
        assertEquals("02/15/2019", loan.getOriginationDate());
    }

    // =========================================================================
    // Test 3: getLoanById
    // =========================================================================

    @Test
    void testGetLoanById() {
        LoanSummaryDto loan = loanService.getLoanById("LN-2019-00142");

        assertEquals("LN-2019-00142", loan.getLoanAccountNumber());
        assertEquals("James Mitchell", loan.getBorrowerName());
        assertEquals("30-Year Fixed Rate Mortgage", loan.getProductDescription());
        assertEquals("Active", loan.getStatus());
        assertEquals("02/15/2019", loan.getOriginationDate());
        assertEquals("Single Family Residence", loan.getPropertyType());
    }

    // =========================================================================
    // Test 4: getLoanById not found
    // =========================================================================

    @Test
    void testGetLoanById_notFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loanService.getLoanById("NONEXISTENT"));
        assertTrue(ex.getMessage().contains("Loan not found"));
    }

    // =========================================================================
    // Test 5: getAllBorrowers count
    // =========================================================================

    @Test
    void testGetAllBorrowers_count() {
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();
        assertEquals(5, borrowers.size());
    }

    // =========================================================================
    // Test 6: getAllBorrowers field values
    // =========================================================================

    @Test
    void testGetAllBorrowers_fieldValues() {
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();
        borrowers.sort(Comparator.comparing(BorrowerDto::getId));

        BorrowerDto borrower = borrowers.stream()
                .filter(b -> "B-10001".equals(b.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals("James R. Mitchell", borrower.getFullName());
        assertEquals(745, borrower.getCreditScore());
    }

    // =========================================================================
    // Test 7: getBorrowerById with loans
    // =========================================================================

    @Test
    void testGetBorrowerById_withLoans() {
        BorrowerDto borrower = loanService.getBorrowerById("B-10001");

        assertEquals("James R. Mitchell", borrower.getFullName());
        assertEquals(745, borrower.getCreditScore());
        assertNotNull(borrower.getLoans());
        assertEquals(1, borrower.getLoans().size());
        assertEquals("LN-2019-00142", borrower.getLoans().get(0).getLoanAccountNumber());
    }

    // =========================================================================
    // Test 8: getBorrowerById null middle initial
    // =========================================================================

    @Test
    void testGetBorrowerById_nullMiddleInitial() {
        BorrowerDto borrower = loanService.getBorrowerById("B-10005");
        assertEquals("Robert Williams", borrower.getFullName());
    }

    // =========================================================================
    // Test 9: getBorrowerById not found
    // =========================================================================

    @Test
    void testGetBorrowerById_notFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loanService.getBorrowerById("NONEXISTENT"));
        assertTrue(ex.getMessage().contains("Borrower not found"));
    }

    // =========================================================================
    // Test 10: getPaymentsByLoan count and order
    // =========================================================================

    @Test
    void testGetPaymentsByLoan_countAndOrder() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");

        assertEquals(2, payments.size());

        // Assert payments are ordered by date descending (Dec before Nov)
        assertTrue(payments.get(0).getPaymentDate().compareTo(payments.get(1).getPaymentDate()) > 0);
    }

    // =========================================================================
    // Test 11: getPaymentsByLoan field values
    // =========================================================================

    @Test
    void testGetPaymentsByLoan_fieldValues() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");

        for (PaymentDto payment : payments) {
            assertEquals("LN-2019-00142", payment.getLoanAccountNumber());
            assertEquals("Regular", payment.getType());
            assertEquals("Posted", payment.getStatus());
        }
    }

    // =========================================================================
    // Test 12: getPaymentsByLoan with late fee
    // =========================================================================

    @Test
    void testGetPaymentsByLoan_withLateFee() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2018-00089");

        // Find the Nov 2025 payment
        PaymentDto novPayment = payments.stream()
                .filter(p -> p.getPaymentDate().equals("11/01/2025"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nov 2025 payment not found"));

        assertEquals(0, new BigDecimal("47.50").compareTo(novPayment.getLateFee()));
        assertEquals("Regular", novPayment.getType());
        assertEquals("Posted", novPayment.getStatus());
    }
}
