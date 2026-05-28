package com.workshop.loanservice;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoanServiceApplicationTests {

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void verifyRowCounts() {
        assertEquals(5, borrowerRepository.count(), "Expected 5 borrowers");
        assertEquals(5, loanProductRepository.count(), "Expected 5 loan products");
        assertEquals(5, loanAccountRepository.count(), "Expected 5 loan accounts");
        assertEquals(10, paymentRepository.count(), "Expected 10 payments");
    }

    @Test
    void verifyForeignKeyIntegrity() {
        List<LoanAccount> accounts = loanAccountRepository.findAll();
        for (LoanAccount account : accounts) {
            assertNotNull(account.getBorrower(), "Borrower FK should be valid for " + account.getAccountNumber());
            assertNotNull(account.getProduct(), "Product FK should be valid for " + account.getAccountNumber());
        }

        List<Payment> payments = paymentRepository.findAll();
        for (Payment payment : payments) {
            assertNotNull(payment.getLoanAccount(), "LoanAccount FK should be valid for payment " + payment.getId());
        }
    }

    @Test
    void verifyAmountAccuracy() {
        Optional<LoanAccount> loan = loanAccountRepository.findByAccountNumber("LN-2019-00142");
        assertTrue(loan.isPresent());
        assertEquals(0, new BigDecimal("285000.00").compareTo(loan.get().getOriginalAmount()),
                "LN-2019-00142 original amount should be 285000.00");
        assertEquals(0, new BigDecimal("271432.56").compareTo(loan.get().getCurrentBalance()),
                "LN-2019-00142 current balance should be 271432.56");

        Optional<LoanAccount> loan2 = loanAccountRepository.findByAccountNumber("LN-2020-00398");
        assertTrue(loan2.isPresent());
        assertEquals(0, new BigDecimal("420000.00").compareTo(loan2.get().getOriginalAmount()),
                "LN-2020-00398 original amount should be 420000.00");
    }

    @Test
    void verifyBorrowerExternalIdLookup() {
        Optional<Borrower> borrower = borrowerRepository.findByExternalId("B-10001");
        assertTrue(borrower.isPresent());
        assertEquals("James", borrower.get().getFirstName());
        assertEquals("Mitchell", borrower.get().getLastName());
        assertEquals(745, borrower.get().getCreditScore());
    }

    @Test
    void verifyLoanAccountsByBorrower() {
        Optional<Borrower> borrower = borrowerRepository.findByExternalId("B-10001");
        assertTrue(borrower.isPresent());
        List<LoanAccount> loans = loanAccountRepository.findByBorrowerId(borrower.get().getId());
        assertEquals(1, loans.size());
        assertEquals("LN-2019-00142", loans.get(0).getAccountNumber());
    }

    @Test
    void verifyPaymentsByLoan() {
        Optional<LoanAccount> loan = loanAccountRepository.findByAccountNumber("LN-2019-00142");
        assertTrue(loan.isPresent());
        List<Payment> payments = paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(loan.get().getId());
        assertEquals(2, payments.size());
        assertEquals(0, new BigDecimal("1487.02").compareTo(payments.get(0).getTotalAmount()));
    }

    @Test
    void verifyLoansEndpoint() throws Exception {
        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$[0].borrowerName").value("James Mitchell"))
                .andExpect(jsonPath("$[0].productDescription").value("30-Year Fixed Rate Mortgage"));
    }

    @Test
    void verifyLoanDetailEndpoint() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$.originalAmount").value(285000.0))
                .andExpect(jsonPath("$.propertyAddress").value("742 Elm Street, Springfield, IL 62701"));
    }

    @Test
    void verifyBorrowerEndpoint() throws Exception {
        mockMvc.perform(get("/api/borrowers/B-10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("B-10001"))
                .andExpect(jsonPath("$.fullName").value("James R. Mitchell"))
                .andExpect(jsonPath("$.creditScore").value(745))
                .andExpect(jsonPath("$.loans.length()").value(1));
    }

    @Test
    void verifyPaymentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].loanAccountNumber").value("LN-2019-00142"))
                .andExpect(jsonPath("$[0].totalAmount").value(1487.02));
    }
}
