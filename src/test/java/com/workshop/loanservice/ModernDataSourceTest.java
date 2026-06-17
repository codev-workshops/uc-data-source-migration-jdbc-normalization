package com.workshop.loanservice;

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

/**
 * Smoke test for the modern data source: verifies the second datasource is wired,
 * the modern schema (schema-modern.sql) is applied, the entities map correctly to
 * proper types, and the foreign-key relationships persist and resolve. This does
 * NOT perform the data migration (Task 2) — it only proves Task 1 wiring works.
 */
@SpringBootTest
class ModernDataSourceTest {

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
    void modernEntitiesPersistWithProperTypesAndRelationships() {
        Borrower borrower = new Borrower();
        borrower.setExternalId("B-90001");
        borrower.setFirstName("Test");
        borrower.setLastName("Borrower");
        borrower.setDateOfBirth(LocalDate.of(1980, 1, 1));
        borrower.setCreditScore(720);
        borrower.setAnnualIncome(new BigDecimal("100000.00"));
        borrower.setStatus("ACTIVE");
        borrower = borrowerRepository.save(borrower);

        LoanProduct product = new LoanProduct();
        product.setCode("TST30");
        product.setName("Test 30-Year Fixed");
        product.setType("FXD");
        product.setTermMonths(360);
        product.setRateType("FIXED");
        product.setActive(true);
        product = loanProductRepository.save(product);

        LoanAccount account = new LoanAccount();
        account.setAccountNumber("LN-TEST-0001");
        account.setBorrower(borrower);
        account.setProduct(product);
        account.setOriginalAmount(new BigDecimal("250000.00"));
        account.setCurrentBalance(new BigDecimal("240000.00"));
        account.setInterestRate(new BigDecimal("4.250"));
        account.setTermMonths(360);
        account.setMonthlyPayment(new BigDecimal("1230.00"));
        account.setOriginationDate(LocalDate.of(2020, 1, 1));
        account.setMaturityDate(LocalDate.of(2050, 1, 1));
        account.setStatus("ACTIVE");
        account = loanAccountRepository.save(account);

        Payment payment = new Payment();
        payment.setExternalId("PMT-TEST-0001");
        payment.setLoanAccount(account);
        payment.setPaymentDate(LocalDate.of(2025, 1, 1));
        payment.setTotalAmount(new BigDecimal("1230.00"));
        payment.setType("REGULAR");
        payment.setStatus("POSTED");
        paymentRepository.save(payment);

        // Lookups via the custom finder methods (exercise the FK relationships).
        assertThat(borrowerRepository.findByExternalId("B-90001")).isPresent();
        assertThat(loanProductRepository.findByCode("TST30")).isPresent();

        LoanAccount found = loanAccountRepository.findByAccountNumber("LN-TEST-0001").orElseThrow();
        assertThat(found.getBorrower().getExternalId()).isEqualTo("B-90001");
        assertThat(found.getProduct().getCode()).isEqualTo("TST30");
        assertThat(found.getCurrentBalance()).isEqualByComparingTo("240000.00");

        List<LoanAccount> byBorrower = loanAccountRepository.findByBorrower_ExternalId("B-90001");
        assertThat(byBorrower).hasSize(1);

        List<Payment> payments =
                paymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc("LN-TEST-0001");
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getExternalId()).isEqualTo("PMT-TEST-0001");
        assertThat(payments.get(0).getLoanAccount().getAccountNumber()).isEqualTo("LN-TEST-0001");
    }
}
