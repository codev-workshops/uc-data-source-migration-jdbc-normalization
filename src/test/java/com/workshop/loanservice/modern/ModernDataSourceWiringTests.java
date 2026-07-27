package com.workshop.loanservice.modern;

import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ModernDataSourceWiringTests {

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @PersistenceContext(unitName = "modern")
    private EntityManager entityManager;

    @Test
    @Transactional("modernTransactionManager")
    void persistsGraphAcrossForeignKeys() {
        Borrower borrower = new Borrower();
        borrower.setExternalId("B0001");
        borrower.setFirstName("Ada");
        borrower.setLastName("Lovelace");
        borrower.setDateOfBirth(LocalDate.of(1980, 3, 15));
        borrower.setAnnualIncome(new BigDecimal("120000.00"));
        borrower.setCreditScore(780);
        borrower.setStatus("ACTIVE");
        borrowerRepository.save(borrower);

        LoanProduct product = new LoanProduct();
        product.setCode("FXD30");
        product.setName("30 Year Fixed");
        product.setType("FXD");
        product.setTermMonths(360);
        product.setRateType("FIXED");
        product.setActive(true);
        loanProductRepository.save(product);

        LoanAccount account = new LoanAccount();
        account.setAccountNumber("LN00000001");
        account.setBorrower(borrower);
        account.setProduct(product);
        account.setOriginalAmount(new BigDecimal("400000.00"));
        account.setCurrentBalance(new BigDecimal("398000.00"));
        account.setInterestRate(new BigDecimal("6.125"));
        account.setTermMonths(360);
        account.setMonthlyPayment(new BigDecimal("2430.45"));
        account.setOriginationDate(LocalDate.of(2023, 1, 10));
        account.setMaturityDate(LocalDate.of(2053, 1, 10));
        account.setStatus("ACTIVE");
        loanAccountRepository.save(account);

        Payment payment = new Payment();
        payment.setLoanAccount(account);
        payment.setPaymentDate(LocalDate.of(2023, 3, 1));
        payment.setTotalAmount(new BigDecimal("2430.45"));
        payment.setPrincipalAmount(new BigDecimal("400.45"));
        payment.setInterestAmount(new BigDecimal("2030.00"));
        payment.setType("REGULAR");
        payment.setStatus("POSTED");
        paymentRepository.save(payment);

        assertThat(borrowerRepository.findByExternalId("B0001")).isPresent();
        assertThat(loanProductRepository.findByActiveTrue()).hasSize(1);
        assertThat(loanAccountRepository.findByBorrowerId(borrower.getId())).hasSize(1);

        List<Payment> payments =
                paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN00000001");
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getLoanAccount().getBorrower().getLastName()).isEqualTo("Lovelace");
    }

    @Test
    @Transactional("modernTransactionManager")
    void appliesDdlDefaultsAndTimestamps() {
        Borrower borrower = new Borrower();
        borrower.setExternalId("B0002");
        borrower.setFirstName("Grace");
        borrower.setLastName("Hopper");
        borrowerRepository.save(borrower);

        LoanProduct product = new LoanProduct();
        product.setCode("ARM51");
        product.setName("5/1 ARM");
        product.setType("ARM");
        product.setTermMonths(360);
        product.setRateType("VARIABLE");
        loanProductRepository.save(product);

        LoanAccount account = new LoanAccount();
        account.setAccountNumber("LN00000002");
        account.setBorrower(borrower);
        account.setProduct(product);
        account.setOriginalAmount(new BigDecimal("250000.00"));
        account.setCurrentBalance(new BigDecimal("250000.00"));
        account.setInterestRate(new BigDecimal("5.500"));
        account.setTermMonths(360);
        account.setMonthlyPayment(new BigDecimal("1419.47"));
        account.setOriginationDate(LocalDate.of(2024, 6, 1));
        account.setMaturityDate(LocalDate.of(2054, 6, 1));
        loanAccountRepository.save(account);

        Payment payment = new Payment();
        payment.setLoanAccount(account);
        payment.setPaymentDate(LocalDate.of(2024, 7, 1));
        payment.setTotalAmount(new BigDecimal("1419.47"));
        payment.setType("REGULAR");
        payment.setStatus("POSTED");
        paymentRepository.save(payment);

        entityManager.flush();
        entityManager.clear();

        Borrower storedBorrower = borrowerRepository.findById(borrower.getId()).orElseThrow();
        assertThat(storedBorrower.getStatus()).isEqualTo("ACTIVE");
        assertThat(storedBorrower.getCreatedAt()).isNotNull();
        assertThat(storedBorrower.getUpdatedAt()).isNotNull();

        LoanProduct storedProduct = loanProductRepository.findById(product.getId()).orElseThrow();
        assertThat(storedProduct.getActive()).isTrue();

        LoanAccount storedAccount = loanAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(storedAccount.getStatus()).isEqualTo("ACTIVE");
        assertThat(storedAccount.getDelinquencyDays()).isZero();
        assertThat(storedAccount.getEscrowBalance()).isEqualByComparingTo("0");
        assertThat(storedAccount.getCreatedAt()).isNotNull();

        Payment storedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(storedPayment.getLateFee()).isEqualByComparingTo("0");
        assertThat(storedPayment.getCreatedAt()).isNotNull();

        LocalDateTime updatedAtBefore = storedAccount.getUpdatedAt();
        storedAccount.setCurrentBalance(new BigDecimal("249000.00"));
        entityManager.flush();
        assertThat(storedAccount.getUpdatedAt()).isAfterOrEqualTo(updatedAtBefore);
    }
}
