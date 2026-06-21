package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MigrationValidationTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Loan Endpoints ---

    @Test
    void getAllLoans_returns5Loans() throws Exception {
        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].loanAccountNumber", everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].borrowerName", everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].status", everyItem(notNullValue())));
    }

    @Test
    void getLoanById_returnsCorrectLoan() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$.borrowerName").value("James Mitchell"))
                .andExpect(jsonPath("$.productDescription").value("30-Year Fixed Rate Mortgage"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.propertyType").value("Single Family Residence"))
                .andExpect(jsonPath("$.originationDate").value("02/15/2019"))
                .andExpect(jsonPath("$.propertyAddress").value("742 Elm Street, Springfield, IL 62701"));
    }

    @Test
    void getLoanById_secondLoan() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2020-00398"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountNumber").value("LN-2020-00398"))
                .andExpect(jsonPath("$.borrowerName").value("Sarah Chen"))
                .andExpect(jsonPath("$.productDescription").value("15-Year Fixed Rate Mortgage"))
                .andExpect(jsonPath("$.propertyType").value("Condominium"))
                .andExpect(jsonPath("$.originationDate").value("04/01/2020"));
    }

    @Test
    void getLoanById_townhouse() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2021-00567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowerName").value("Emily Johnson"))
                .andExpect(jsonPath("$.propertyType").value("Townhouse"));
    }

    @Test
    void getLoanById_notFound_returns500() throws Exception {
        mockMvc.perform(get("/api/loans/NONEXISTENT"))
                .andExpect(status().is5xxServerError());
    }

    // --- Borrower Endpoints ---

    @Test
    void getAllBorrowers_returns5Borrowers() throws Exception {
        mockMvc.perform(get("/api/borrowers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].id", everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].fullName", everyItem(notNullValue())));
    }

    @Test
    void getBorrowerById_returnsWithNestedLoans() throws Exception {
        mockMvc.perform(get("/api/borrowers/B-10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("B-10001"))
                .andExpect(jsonPath("$.fullName").value("James R. Mitchell"))
                .andExpect(jsonPath("$.creditScore").value(745))
                .andExpect(jsonPath("$.email").value("j.mitchell@email.com"))
                .andExpect(jsonPath("$.phone").value("217-555-0142"))
                .andExpect(jsonPath("$.city").value("Springfield"))
                .andExpect(jsonPath("$.state").value("IL"))
                .andExpect(jsonPath("$.employmentStatus").value("EMPLOYED"))
                .andExpect(jsonPath("$.loans", hasSize(1)))
                .andExpect(jsonPath("$.loans[0].loanAccountNumber").value("LN-2019-00142"));
    }

    @Test
    void getBorrowerById_nullMiddleInitial() throws Exception {
        mockMvc.perform(get("/api/borrowers/B-10005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Robert Williams"))
                .andExpect(jsonPath("$.creditScore").value(658))
                .andExpect(jsonPath("$.employmentStatus").value("RETIRED"));
    }

    @Test
    void getBorrowerById_notFound_returns500() throws Exception {
        mockMvc.perform(get("/api/borrowers/NONEXISTENT"))
                .andExpect(status().is5xxServerError());
    }

    // --- Payment Endpoints ---

    @Test
    void getPaymentsByLoan_returns2Payments() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].paymentId").value("PMT-2025120001"))
                .andExpect(jsonPath("$[0].loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$[0].type").value("Regular"))
                .andExpect(jsonPath("$[0].status").value("Posted"))
                .andExpect(jsonPath("$[0].paymentDate").value("12/15/2025"));
    }

    @Test
    void getPaymentsByLoan_lateFeePresent() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2018-00089/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].paymentId").value("PMT-2025110003"))
                .andExpect(jsonPath("$[1].lateFee").value(47.50));
    }

    @Test
    void getPaymentsByLoan_orderedByDateDesc() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].paymentDate").value("12/15/2025"))
                .andExpect(jsonPath("$[1].paymentDate").value("11/15/2025"))
                .andExpect(jsonPath("$[0].paymentId").value("PMT-2025120001"))
                .andExpect(jsonPath("$[1].paymentId").value("PMT-2025110001"));
    }
}
