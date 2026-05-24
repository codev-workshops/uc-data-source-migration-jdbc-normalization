package com.workshop.loanservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoanServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    // =========================================================================
    // GET /api/borrowers
    // =========================================================================

    @Test
    void getAllBorrowers_returns5Borrowers() throws Exception {
        mockMvc.perform(get("/api/borrowers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id", is("B-10001")))
                .andExpect(jsonPath("$[0].fullName", is("James R. Mitchell")))
                .andExpect(jsonPath("$[0].email", is("j.mitchell@email.com")))
                .andExpect(jsonPath("$[0].phone", is("217-555-0142")))
                .andExpect(jsonPath("$[0].city", is("Springfield")))
                .andExpect(jsonPath("$[0].state", is("IL")))
                .andExpect(jsonPath("$[0].creditScore", is(745)))
                .andExpect(jsonPath("$[0].employmentStatus", is("EMPLOYED")))
                .andExpect(jsonPath("$[0].loans").value(nullValue()));
    }

    @Test
    void getAllBorrowers_borrowerWithNoMiddleInitial() throws Exception {
        mockMvc.perform(get("/api/borrowers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[4].id", is("B-10005")))
                .andExpect(jsonPath("$[4].fullName", is("Robert Williams")))
                .andExpect(jsonPath("$[4].creditScore", is(658)))
                .andExpect(jsonPath("$[4].employmentStatus", is("RETIRED")));
    }

    // =========================================================================
    // GET /api/borrowers/{id}
    // =========================================================================

    @Test
    void getBorrowerById_returnsWithLoans() throws Exception {
        mockMvc.perform(get("/api/borrowers/B-10001").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("B-10001")))
                .andExpect(jsonPath("$.fullName", is("James R. Mitchell")))
                .andExpect(jsonPath("$.email", is("j.mitchell@email.com")))
                .andExpect(jsonPath("$.loans", hasSize(1)))
                .andExpect(jsonPath("$.loans[0].loanAccountNumber", is("LN-2019-00142")))
                .andExpect(jsonPath("$.loans[0].borrowerName", is("James Mitchell")))
                .andExpect(jsonPath("$.loans[0].productDescription", is("30-Year Fixed Rate Mortgage")))
                .andExpect(jsonPath("$.loans[0].interestRate", is(4.750)))
                .andExpect(jsonPath("$.loans[0].status", is("Active")))
                .andExpect(jsonPath("$.loans[0].originationDate", is("02/15/2019")))
                .andExpect(jsonPath("$.loans[0].propertyAddress", is("742 Elm Street, Springfield, IL 62701")))
                .andExpect(jsonPath("$.loans[0].propertyType", is("Single Family Residence")));
    }

    @Test
    void getBorrowerById_notFound() {
        Exception ex = assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/borrowers/INVALID").accept(MediaType.APPLICATION_JSON)));
        assertTrue(ex.getMessage().contains("Borrower not found"));
    }

    // =========================================================================
    // GET /api/loans
    // =========================================================================

    @Test
    void getAllLoans_returns5Loans() throws Exception {
        mockMvc.perform(get("/api/loans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].loanAccountNumber", is("LN-2019-00142")))
                .andExpect(jsonPath("$[0].borrowerName", is("James Mitchell")))
                .andExpect(jsonPath("$[0].productDescription", is("30-Year Fixed Rate Mortgage")))
                .andExpect(jsonPath("$[0].interestRate", is(4.750)))
                .andExpect(jsonPath("$[0].status", is("Active")))
                .andExpect(jsonPath("$[0].originationDate", is("02/15/2019")))
                .andExpect(jsonPath("$[0].propertyAddress", is("742 Elm Street, Springfield, IL 62701")))
                .andExpect(jsonPath("$[0].propertyType", is("Single Family Residence")));
    }

    @Test
    void getAllLoans_verifiesAllProductTypes() throws Exception {
        mockMvc.perform(get("/api/loans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].productDescription", is("15-Year Fixed Rate Mortgage")))
                .andExpect(jsonPath("$[2].productDescription", is("5/1 Adjustable Rate Mortgage")))
                .andExpect(jsonPath("$[3].productDescription", is("30-Year Fixed Rate Mortgage")))
                .andExpect(jsonPath("$[4].productDescription", is("FHA 30-Year Fixed")));
    }

    @Test
    void getAllLoans_verifiesPropertyTypes() throws Exception {
        mockMvc.perform(get("/api/loans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].propertyType", is("Single Family Residence")))
                .andExpect(jsonPath("$[1].propertyType", is("Condominium")))
                .andExpect(jsonPath("$[2].propertyType", is("Single Family Residence")))
                .andExpect(jsonPath("$[3].propertyType", is("Townhouse")))
                .andExpect(jsonPath("$[4].propertyType", is("Single Family Residence")));
    }

    // =========================================================================
    // GET /api/loans/{id}
    // =========================================================================

    @Test
    void getLoanById_returnsLoan() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountNumber", is("LN-2019-00142")))
                .andExpect(jsonPath("$.borrowerName", is("James Mitchell")))
                .andExpect(jsonPath("$.productDescription", is("30-Year Fixed Rate Mortgage")))
                .andExpect(jsonPath("$.interestRate", is(4.750)))
                .andExpect(jsonPath("$.monthlyPayment", is(1487.02)))
                .andExpect(jsonPath("$.status", is("Active")))
                .andExpect(jsonPath("$.originationDate", is("02/15/2019")))
                .andExpect(jsonPath("$.propertyAddress", is("742 Elm Street, Springfield, IL 62701")))
                .andExpect(jsonPath("$.propertyType", is("Single Family Residence")));
    }

    @Test
    void getLoanById_notFound() {
        Exception ex = assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/loans/INVALID").accept(MediaType.APPLICATION_JSON)));
        assertTrue(ex.getMessage().contains("Loan not found"));
    }

    // =========================================================================
    // GET /api/loans/{loanId}/payments
    // =========================================================================

    @Test
    void getPaymentsByLoan_returns2Payments() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].paymentId", notNullValue()))
                .andExpect(jsonPath("$[0].loanAccountNumber", is("LN-2019-00142")))
                .andExpect(jsonPath("$[0].paymentDate", is("12/15/2025")))
                .andExpect(jsonPath("$[0].totalAmount", is(1487.02)))
                .andExpect(jsonPath("$[0].principalAmount", is(456.78)))
                .andExpect(jsonPath("$[0].interestAmount", is(1074.69)))
                .andExpect(jsonPath("$[0].escrowAmount", is(355.55)))
                .andExpect(jsonPath("$[0].lateFee", is(0.00)))
                .andExpect(jsonPath("$[0].type", is("Regular")))
                .andExpect(jsonPath("$[0].status", is("Posted")));
    }

    @Test
    void getPaymentsByLoan_secondPayment() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].paymentDate", is("11/15/2025")))
                .andExpect(jsonPath("$[1].totalAmount", is(1487.02)))
                .andExpect(jsonPath("$[1].principalAmount", is(454.97)))
                .andExpect(jsonPath("$[1].interestAmount", is(1076.50)))
                .andExpect(jsonPath("$[1].type", is("Regular")))
                .andExpect(jsonPath("$[1].status", is("Posted")));
    }

    @Test
    void getPaymentsByLoan_latePayment() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2018-00089/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].lateFee", is(47.50)));
    }

    @Test
    void getPaymentsByLoan_noPayments() throws Exception {
        mockMvc.perform(get("/api/loans/INVALID/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // Cross-endpoint verification
    // =========================================================================

    @Test
    void allBorrowersHaveCorrectData() throws Exception {
        mockMvc.perform(get("/api/borrowers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName", is("James R. Mitchell")))
                .andExpect(jsonPath("$[1].fullName", is("Sarah L. Chen")))
                .andExpect(jsonPath("$[2].fullName", is("Michael A. Torres")))
                .andExpect(jsonPath("$[3].fullName", is("Emily M. Johnson")))
                .andExpect(jsonPath("$[4].fullName", is("Robert Williams")));
    }

    @Test
    void allLoansHaveCorrectBorrowerNames() throws Exception {
        mockMvc.perform(get("/api/loans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].borrowerName", is("James Mitchell")))
                .andExpect(jsonPath("$[1].borrowerName", is("Sarah Chen")))
                .andExpect(jsonPath("$[2].borrowerName", is("Michael Torres")))
                .andExpect(jsonPath("$[3].borrowerName", is("Emily Johnson")))
                .andExpect(jsonPath("$[4].borrowerName", is("Robert Williams")));
    }

    @Test
    void eachLoanHasPayments() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2020-00398/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/loans/LN-2021-00567/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/loans/LN-2017-00034/payments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
