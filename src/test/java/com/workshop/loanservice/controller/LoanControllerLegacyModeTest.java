package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.use-modern-datasource=false"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class LoanControllerLegacyModeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testGetAllLoans_legacyMode() {
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
    }

    @Test
    void testGetLoanById_legacyMode() {
        ResponseEntity<LoanSummaryDto> response = restTemplate.getForEntity("/api/loans/LN-2019-00142", LoanSummaryDto.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("LN-2019-00142", response.getBody().getLoanAccountNumber());
        assertEquals("30-Year Fixed Rate Mortgage", response.getBody().getProductDescription());
    }

    @Test
    void testGetLoanById_notFound_legacyMode() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/loans/NONEXISTENT", String.class);
        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void testGetPayments_legacyMode() {
        ResponseEntity<PaymentDto[]> response = restTemplate.getForEntity("/api/loans/LN-2019-00142/payments", PaymentDto[].class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);

        PaymentDto first = response.getBody()[0];
        assertEquals("Regular", first.getType());
        assertEquals("Posted", first.getStatus());
    }

    @Test
    void testGetAllBorrowers_legacyMode() {
        ResponseEntity<BorrowerDto[]> response = restTemplate.getForEntity("/api/borrowers", BorrowerDto[].class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().length);
    }

    @Test
    void testGetBorrowerById_legacyMode() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity("/api/borrowers/B-10001", BorrowerDto.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("James R. Mitchell", response.getBody().getFullName());
        assertEquals(745, response.getBody().getCreditScore());
        assertNotNull(response.getBody().getLoans());
        assertEquals(1, response.getBody().getLoans().size());
    }
}
