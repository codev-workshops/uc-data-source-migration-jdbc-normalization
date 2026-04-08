package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Arrays;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for loan service REST endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class LoanControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testGetAllLoans() {
        ResponseEntity<LoanSummaryDto[]> response = restTemplate.getForEntity("/api/loans", LoanSummaryDto[].class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().length);

        LoanSummaryDto loan = Arrays.stream(response.getBody())
                .filter(l -> "LN-2019-00142".equals(l.getLoanAccountNumber()))
                .findFirst()
                .orElseThrow();
        assertEquals("James Mitchell", loan.getBorrowerName());
        assertEquals("Active", loan.getStatus());
        assertEquals("Single Family Residence", loan.getPropertyType());
        assertEquals("02/15/2019", loan.getOriginationDate());
        assertEquals("30-Year Fixed Rate Mortgage", loan.getProductDescription());
    }

    @Test
    void testGetLoanById() {
        ResponseEntity<LoanSummaryDto> response = restTemplate.getForEntity("/api/loans/LN-2020-00398", LoanSummaryDto.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("LN-2020-00398", response.getBody().getLoanAccountNumber());
        assertEquals("Sarah Chen", response.getBody().getBorrowerName());
        assertEquals("15-Year Fixed Rate Mortgage", response.getBody().getProductDescription());
        assertEquals("Condominium", response.getBody().getPropertyType());
    }

    @Test
    void testGetLoanById_notFound() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/loans/NONEXISTENT", String.class);
        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void testGetPayments() {
        ResponseEntity<PaymentDto[]> response = restTemplate.getForEntity("/api/loans/LN-2019-00142/payments", PaymentDto[].class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);

        for (PaymentDto payment : response.getBody()) {
            assertEquals("LN-2019-00142", payment.getLoanAccountNumber());
            assertEquals("Regular", payment.getType());
            assertEquals("Posted", payment.getStatus());
            assertTrue(payment.getPaymentDate().matches("\\d{2}/\\d{2}/\\d{4}"));
        }
    }

    @Test
    void testGetPayments_withLateFee() {
        ResponseEntity<PaymentDto[]> response = restTemplate.getForEntity("/api/loans/LN-2018-00089/payments", PaymentDto[].class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);

        PaymentDto novPayment = Arrays.stream(response.getBody())
                .filter(p -> "11/01/2025".equals(p.getPaymentDate()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("47.50").compareTo(novPayment.getLateFee()));
    }

    @Test
    void testGetAllBorrowers() {
        ResponseEntity<BorrowerDto[]> response = restTemplate.getForEntity("/api/borrowers", BorrowerDto[].class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().length);

        BorrowerDto borrower = Arrays.stream(response.getBody())
                .filter(b -> "B-10001".equals(b.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("James R. Mitchell", borrower.getFullName());
        assertEquals(745, borrower.getCreditScore());
        assertEquals("EMPLOYED", borrower.getEmploymentStatus());
    }

    @Test
    void testGetBorrowerById() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity("/api/borrowers/B-10001", BorrowerDto.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("James R. Mitchell", response.getBody().getFullName());
        assertEquals(745, response.getBody().getCreditScore());
        assertNotNull(response.getBody().getLoans());
        assertEquals(1, response.getBody().getLoans().size());
        assertEquals("LN-2019-00142", response.getBody().getLoans().get(0).getLoanAccountNumber());
        assertEquals("Active", response.getBody().getLoans().get(0).getStatus());
    }

    @Test
    void testGetBorrowerById_nullMiddleInitial() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity("/api/borrowers/B-10005", BorrowerDto.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Robert Williams", response.getBody().getFullName());
    }

    @Test
    void testGetBorrowerById_notFound() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/borrowers/NONEXISTENT", String.class);
        assertEquals(500, response.getStatusCode().value());
    }
}
