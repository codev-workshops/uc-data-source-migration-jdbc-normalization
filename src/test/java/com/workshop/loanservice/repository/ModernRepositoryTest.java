package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:repotest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class ModernRepositoryTest {

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void testBorrowerCount() {
        assertEquals(5, borrowerRepository.count());
    }

    @Test
    void testLoanProductCount() {
        assertEquals(5, loanProductRepository.count());
    }

    @Test
    void testLoanAccountCount() {
        assertEquals(5, loanAccountRepository.count());
    }

    @Test
    void testPaymentCount() {
        assertEquals(10, paymentRepository.count());
    }

    @Test
    void testFindLoanAccountsByBorrowerExternalId() {
        List<LoanAccount> accounts = loanAccountRepository.findByBorrowerExternalId("B-10001");
        assertEquals(1, accounts.size());
        assertEquals("LN-2019-00142", accounts.get(0).getAccountNumber());
    }

    @Test
    void testFindPaymentsByLoanAccountNumber() {
        List<Payment> payments = paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2019-00142");
        assertEquals(2, payments.size());
        assertTrue(payments.get(0).getPaymentDate()
                .isAfter(payments.get(1).getPaymentDate()) ||
                payments.get(0).getPaymentDate()
                .isEqual(payments.get(1).getPaymentDate()));
    }

    @Test
    void testFindBorrowerByExternalId() {
        assertTrue(borrowerRepository.findByExternalId("B-10001").isPresent());
        assertTrue(borrowerRepository.findByExternalId("NONEXISTENT").isEmpty());
    }

    @Test
    void testFindLoanAccountByAccountNumber() {
        assertTrue(loanAccountRepository.findByAccountNumber("LN-2019-00142").isPresent());
        assertTrue(loanAccountRepository.findByAccountNumber("NONEXISTENT").isEmpty());
    }

    @Test
    void testFindLoanProductByCode() {
        assertTrue(loanProductRepository.findByCode("FXD30").isPresent());
        assertTrue(loanProductRepository.findByCode("NONEXISTENT").isEmpty());
    }
}
