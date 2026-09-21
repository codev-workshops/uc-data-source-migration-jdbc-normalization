package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the modern entities map exactly onto data/modern-schema/modern_tables.sql.
 * The modern DDL is loaded test-scoped only; main config still points at the legacy schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderntest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-legacy.sql,file:data/modern-schema/modern_tables.sql",
        "spring.sql.init.data-locations=",
        "spring.jpa.defer-datasource-initialization=false"
})
class ModernRepositoriesTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private LoanAccountRepository loanAccountRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    private Borrower borrower;
    private LoanAccount loan;

    @BeforeEach
    void seed() {
        borrower = new Borrower();
        borrower.setExternalId("B-10001");
        borrower.setFirstName("Jane");
        borrower.setLastName("Doe");
        borrower.setStatus("ACTIVE");
        borrower.setCreditScore(720);
        borrower.setAnnualIncome(new BigDecimal("85000.00"));
        borrower.setDateOfBirth(LocalDate.of(1980, 5, 12));
        em.persist(borrower);

        LoanProduct product = new LoanProduct();
        product.setCode("FXD30");
        product.setName("30-Year Fixed");
        product.setType("FXD");
        product.setTermMonths(360);
        product.setRateType("FIXED");
        product.setIsActive(true);
        em.persist(product);

        loan = new LoanAccount();
        loan.setAccountNumber("LN-2019-00142");
        loan.setBorrower(borrower);
        loan.setProduct(product);
        loan.setOriginalAmount(new BigDecimal("285000.00"));
        loan.setCurrentBalance(new BigDecimal("270000.00"));
        loan.setInterestRate(new BigDecimal("4.125"));
        loan.setTermMonths(360);
        loan.setMonthlyPayment(new BigDecimal("1487.02"));
        loan.setOriginationDate(LocalDate.of(2019, 3, 1));
        loan.setMaturityDate(LocalDate.of(2049, 3, 1));
        loan.setStatus("ACTIVE");
        em.persist(loan);

        persistPayment(LocalDate.of(2024, 1, 1));
        persistPayment(LocalDate.of(2024, 3, 1));
        persistPayment(LocalDate.of(2024, 2, 1));
        em.flush();
        em.clear();
    }

    private void persistPayment(LocalDate date) {
        Payment p = new Payment();
        p.setLoanAccount(loan);
        p.setPaymentDate(date);
        p.setTotalAmount(new BigDecimal("1487.02"));
        p.setType("REGULAR");
        p.setStatus("POSTED");
        em.persist(p);
    }

    @Test
    void findsBorrowerByExternalId() {
        assertThat(borrowerRepository.findByExternalId("B-10001"))
                .get().extracting(Borrower::getLastName).isEqualTo("Doe");
        assertThat(borrowerRepository.findByExternalId("B-99999")).isEmpty();
    }

    @Test
    void findsProductByCode() {
        assertThat(loanProductRepository.findByCode("FXD30"))
                .get().extracting(LoanProduct::getIsActive).isEqualTo(true);
    }

    @Test
    void findsLoanAccountByAccountNumberAndBorrower() {
        LoanAccount found = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertThat(found.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(found.getProduct().getCode()).isEqualTo("FXD30");

        assertThat(loanAccountRepository.findByBorrowerId(borrower.getId())).hasSize(1);
        assertThat(loanAccountRepository.findByBorrowerExternalId("B-10001")).hasSize(1);
        assertThat(loanAccountRepository.findByProductCode("FXD30")).hasSize(1);
    }

    @Test
    void findsPaymentsOrderedByPaymentDateDesc() {
        List<Payment> byId = paymentRepository.findByLoanAccountIdOrderByPaymentDateDesc(loan.getId());
        List<Payment> byNumber = paymentRepository
                .findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2019-00142");

        List<LocalDate> expected = List.of(
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1));
        assertThat(byId).extracting(Payment::getPaymentDate).containsExactlyElementsOf(expected);
        assertThat(byNumber).extracting(Payment::getPaymentDate).containsExactlyElementsOf(expected);
    }
}
