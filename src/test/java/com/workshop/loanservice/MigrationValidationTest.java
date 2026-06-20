package com.workshop.loanservice;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that validate the modern schema API responses against
 * expected values derived from the legacy golden files.
 *
 * Intentional differences documented:
 * - Payment IDs changed from legacy format ("PMT-2025120001") to auto-generated BIGINT ("1", "2", ...)
 * - BigDecimal amounts from DECIMAL columns may serialize with trailing .0 (e.g. 285000.0 vs 285000)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integrationtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class MigrationValidationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testGetAllLoans() {
        ResponseEntity<List<LoanSummaryDto>> response = restTemplate.exchange(
                "/api/loans", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(200, response.getStatusCode().value());
        List<LoanSummaryDto> loans = response.getBody();
        assertNotNull(loans);
        assertEquals(5, loans.size());

        LoanSummaryDto first = loans.stream()
                .filter(l -> "LN-2019-00142".equals(l.getLoanAccountNumber()))
                .findFirst().orElseThrow();
        assertEquals("James Mitchell", first.getBorrowerName());
        assertEquals("30-Year Fixed Rate Mortgage", first.getProductDescription());
        assertEquals(0, new BigDecimal("285000").compareTo(first.getOriginalAmount()));
        assertEquals(0, new BigDecimal("271432.56").compareTo(first.getCurrentBalance()));
        assertEquals(0, new BigDecimal("4.750").compareTo(first.getInterestRate()));
        assertEquals(0, new BigDecimal("1487.02").compareTo(first.getMonthlyPayment()));
        assertEquals("Active", first.getStatus());
        assertEquals("02/15/2019", first.getOriginationDate());
        assertEquals("742 Elm Street, Springfield, IL 62701", first.getPropertyAddress());
        assertEquals("Single Family Residence", first.getPropertyType());
    }

    @Test
    void testGetLoanById() {
        ResponseEntity<LoanSummaryDto> response = restTemplate.getForEntity(
                "/api/loans/LN-2019-00142", LoanSummaryDto.class);

        assertEquals(200, response.getStatusCode().value());
        LoanSummaryDto loan = response.getBody();
        assertNotNull(loan);
        assertEquals("LN-2019-00142", loan.getLoanAccountNumber());
        assertEquals("James Mitchell", loan.getBorrowerName());
        assertEquals("30-Year Fixed Rate Mortgage", loan.getProductDescription());
        assertEquals("Active", loan.getStatus());
        assertEquals("02/15/2019", loan.getOriginationDate());
        assertEquals("742 Elm Street, Springfield, IL 62701", loan.getPropertyAddress());
        assertEquals("Single Family Residence", loan.getPropertyType());
    }

    @Test
    void testGetAllBorrowers() {
        ResponseEntity<List<BorrowerDto>> response = restTemplate.exchange(
                "/api/borrowers", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(200, response.getStatusCode().value());
        List<BorrowerDto> borrowers = response.getBody();
        assertNotNull(borrowers);
        assertEquals(5, borrowers.size());

        BorrowerDto james = borrowers.stream()
                .filter(b -> "B-10001".equals(b.getId()))
                .findFirst().orElseThrow();
        assertEquals("James R. Mitchell", james.getFullName());
        assertEquals("j.mitchell@email.com", james.getEmail());
        assertEquals("217-555-0142", james.getPhone());
        assertEquals("Springfield", james.getCity());
        assertEquals("IL", james.getState());
        assertEquals(745, james.getCreditScore());
        assertEquals("EMPLOYED", james.getEmploymentStatus());

        BorrowerDto robert = borrowers.stream()
                .filter(b -> "B-10005".equals(b.getId()))
                .findFirst().orElseThrow();
        assertEquals("Robert Williams", robert.getFullName());
    }

    @Test
    void testGetBorrowerById() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity(
                "/api/borrowers/B-10001", BorrowerDto.class);

        assertEquals(200, response.getStatusCode().value());
        BorrowerDto borrower = response.getBody();
        assertNotNull(borrower);
        assertEquals("B-10001", borrower.getId());
        assertEquals("James R. Mitchell", borrower.getFullName());
        assertEquals("j.mitchell@email.com", borrower.getEmail());
        assertEquals(745, borrower.getCreditScore());
        assertNotNull(borrower.getLoans());
        assertEquals(1, borrower.getLoans().size());
        assertEquals("LN-2019-00142", borrower.getLoans().get(0).getLoanAccountNumber());
    }

    @Test
    void testGetPaymentsByLoan() {
        ResponseEntity<List<PaymentDto>> response = restTemplate.exchange(
                "/api/loans/LN-2019-00142/payments", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertEquals(200, response.getStatusCode().value());
        List<PaymentDto> payments = response.getBody();
        assertNotNull(payments);
        assertEquals(2, payments.size());

        PaymentDto first = payments.get(0);
        assertEquals("LN-2019-00142", first.getLoanAccountNumber());
        assertEquals("12/15/2025", first.getPaymentDate());
        assertEquals(0, new BigDecimal("1487.02").compareTo(first.getTotalAmount()));
        assertEquals(0, new BigDecimal("456.78").compareTo(first.getPrincipalAmount()));
        assertEquals(0, new BigDecimal("1074.69").compareTo(first.getInterestAmount()));
        assertEquals(0, new BigDecimal("355.55").compareTo(first.getEscrowAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(first.getLateFee()));
        assertEquals("Regular", first.getType());
        assertEquals("Posted", first.getStatus());

        // Intentional difference: payment ID is now auto-generated BIGINT, not legacy "PMT-xxx" format
        assertNotNull(first.getPaymentId());
        assertDoesNotThrow(() -> Long.parseLong(first.getPaymentId()));
    }
}
