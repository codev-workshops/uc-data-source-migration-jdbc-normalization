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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DataMigrationServiceTest {

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void migrationProducesCorrectRowCounts() {
        assertEquals(5, borrowerRepository.count(), "Expected 5 borrowers");
        assertEquals(5, loanProductRepository.count(), "Expected 5 loan products");
        assertEquals(5, loanAccountRepository.count(), "Expected 5 loan accounts");
        assertEquals(10, paymentRepository.count(), "Expected 10 payments");
    }

    @Test
    void borrowerDataMigratedCorrectly() {
        Borrower b = borrowerRepository.findByExternalId("B-10001").orElseThrow();
        assertEquals("James", b.getFirstName());
        assertEquals("Mitchell", b.getLastName());
        assertEquals("R", b.getMiddleInitial());
        assertEquals(LocalDate.of(1978, 3, 15), b.getDateOfBirth());
        assertEquals(745, b.getCreditScore());
        assertEquals(0, new BigDecimal("92500").compareTo(b.getAnnualIncome()));
        assertEquals("ACTIVE", b.getStatus());
        assertEquals("j.mitchell@email.com", b.getEmail());
    }

    @Test
    void borrowerWithNullMiddleInitial() {
        Borrower b = borrowerRepository.findByExternalId("B-10005").orElseThrow();
        assertNull(b.getMiddleInitial());
        assertEquals("Robert", b.getFirstName());
        assertEquals("Williams", b.getLastName());
    }

    @Test
    void loanProductMigratedCorrectly() {
        LoanProduct p = loanProductRepository.findByCode("FXD30").orElseThrow();
        assertEquals("30-Year Fixed Rate Mortgage", p.getName());
        assertEquals("FXD", p.getType());
        assertEquals(360, p.getTermMonths());
        assertEquals("FIXED", p.getRateType());
        assertTrue(p.getIsActive());
        assertEquals(0, new BigDecimal("50000").compareTo(p.getMinAmount()));
        assertEquals(0, new BigDecimal("1500000").compareTo(p.getMaxAmount()));
    }

    @Test
    void loanAccountForeignKeysResolved() {
        LoanAccount acct = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertNotNull(acct.getBorrower());
        assertEquals("B-10001", acct.getBorrower().getExternalId());
        assertNotNull(acct.getProduct());
        assertEquals("FXD30", acct.getProduct().getCode());
    }

    @Test
    void loanAccountAmountsAndDates() {
        LoanAccount acct = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertEquals(0, new BigDecimal("285000").compareTo(acct.getOriginalAmount()));
        assertEquals(0, new BigDecimal("271432.56").compareTo(acct.getCurrentBalance()));
        assertEquals(0, new BigDecimal("4.750").compareTo(acct.getInterestRate()));
        assertEquals(360, acct.getTermMonths());
        assertEquals(LocalDate.of(2019, 2, 15), acct.getOriginationDate());
        assertEquals("Active", acct.getStatus());
        assertEquals("Single Family Residence", acct.getPropertyType());
    }

    @Test
    void paymentForeignKeyResolved() {
        LoanAccount acct = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        List<Payment> payments = paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(acct.getId());
        assertEquals(2, payments.size());

        Payment latest = payments.get(0);
        assertEquals("PMT-2025120001", latest.getLegacySequenceNumber());
        assertEquals(0, new BigDecimal("1487.02").compareTo(latest.getTotalAmount()));
        assertEquals("Regular", latest.getType());
        assertEquals("Posted", latest.getStatus());
        assertEquals(LocalDate.of(2025, 12, 15), latest.getPaymentDate());
    }

    @Test
    void paymentWithLateFee() {
        LoanAccount acct = loanAccountRepository.findByAccountNumber("LN-2018-00089").orElseThrow();
        List<Payment> payments = paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(acct.getId());
        Payment novPayment = payments.stream()
                .filter(p -> p.getPaymentDate().equals(LocalDate.of(2025, 11, 1)))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("47.50").compareTo(novPayment.getLateFee()));
    }
}
