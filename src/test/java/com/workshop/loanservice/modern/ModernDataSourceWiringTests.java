package com.workshop.loanservice.modern;

import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
