package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:modernrepository;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-modern.sql",
        "spring.sql.init.data-locations=classpath:empty.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ModernRepositoryTest {

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsTypedEntitiesAndQueriesRelationshipsByBusinessKey() {
        Borrower borrower = borrowerRepository.saveAndFlush(borrower("B-20001"));
        LoanProduct product = loanProductRepository.saveAndFlush(product("FIX30"));
        LoanAccount account = loanAccountRepository.saveAndFlush(
                account("LN-TEST-001", borrower, product)
        );
        Payment olderPayment = paymentRepository.saveAndFlush(
                payment("PMT-TEST-001", account, LocalDate.of(2025, 11, 1))
        );
        Payment newerPayment = paymentRepository.saveAndFlush(
                payment("PMT-TEST-002", account, LocalDate.of(2025, 12, 1))
        );

        assertThat(borrower.getId()).isNotNull();
        assertThat(product.getId()).isNotNull();
        assertThat(account.getId()).isNotNull();
        assertThat(olderPayment.getId()).isNotNull();
        assertThat(newerPayment.getId()).isNotNull();

        assertThat(borrowerRepository.findByExternalId("B-20001"))
                .get()
                .extracting(Borrower::getDateOfBirth, Borrower::getAnnualIncome)
                .containsExactly(LocalDate.of(1980, 1, 2), new BigDecimal("125000.00"));

        assertThat(loanAccountRepository.findByAccountNumber("LN-TEST-001"))
                .get()
                .satisfies(found -> {
                    assertThat(found.getBorrower().getExternalId()).isEqualTo("B-20001");
                    assertThat(found.getProduct().getCode()).isEqualTo("FIX30");
                    assertThat(found.getInterestRate()).isEqualByComparingTo("4.750");
                });

        assertThat(loanAccountRepository.findByBorrowerExternalIdOrderByIdAsc("B-20001"))
                .extracting(LoanAccount::getAccountNumber)
                .containsExactly("LN-TEST-001");

        List<Payment> payments =
                paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDescIdDesc(
                        "LN-TEST-001"
                );
        assertThat(payments)
                .extracting(Payment::getExternalId)
                .containsExactly("PMT-TEST-002", "PMT-TEST-001");
        assertThat(payments.get(0).getLoanAccount().getAccountNumber())
                .isEqualTo("LN-TEST-001");
    }

    @Test
    void enforcesExternalPaymentIdentifierUniqueness() {
        Borrower borrower = borrowerRepository.saveAndFlush(borrower("B-20002"));
        LoanProduct product = loanProductRepository.saveAndFlush(product("FIX15"));
        LoanAccount account = loanAccountRepository.saveAndFlush(
                account("LN-TEST-002", borrower, product)
        );
        paymentRepository.saveAndFlush(
                payment("PMT-DUPLICATE", account, LocalDate.of(2025, 11, 1))
        );

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(
                payment("PMT-DUPLICATE", account, LocalDate.of(2025, 12, 1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createsRequiredForeignKeysAndIndexes() {
        Integer foreignKeys = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_TYPE = 'FOREIGN KEY'
                  AND TABLE_NAME IN ('LOAN_ACCOUNTS', 'PAYMENTS')
                """,
                Integer.class
        );
        Integer indexes = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.INDEXES
                WHERE TABLE_NAME IN ('BORROWERS', 'LOAN_ACCOUNTS', 'PAYMENTS')
                  AND INDEX_NAME IN (
                      'IDX_BORROWERS_EMAIL',
                      'IDX_BORROWERS_STATUS',
                      'IDX_LOAN_ACCOUNTS_BORROWER',
                      'IDX_LOAN_ACCOUNTS_STATUS',
                      'IDX_PAYMENTS_LOAN',
                      'IDX_PAYMENTS_DATE',
                      'IDX_PAYMENTS_EXTERNAL_ID'
                  )
                """,
                Integer.class
        );

        assertThat(foreignKeys).isEqualTo(3);
        assertThat(indexes).isEqualTo(7);
    }

    private Borrower borrower(String externalId) {
        Borrower borrower = new Borrower();
        borrower.setExternalId(externalId);
        borrower.setFirstName("Test");
        borrower.setLastName("Borrower");
        borrower.setDateOfBirth(LocalDate.of(1980, 1, 2));
        borrower.setCreditScore(750);
        borrower.setAnnualIncome(new BigDecimal("125000.00"));
        borrower.setStatus("ACTIVE");
        borrower.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        borrower.setUpdatedAt(LocalDateTime.of(2025, 1, 2, 0, 0));
        return borrower;
    }

    private LoanProduct product(String code) {
        LoanProduct product = new LoanProduct();
        product.setCode(code);
        product.setName("Fixed Rate Mortgage");
        product.setType("FXD");
        product.setTermMonths(360);
        product.setRateType("FIXED");
        product.setMinAmount(new BigDecimal("50000.00"));
        product.setMaxAmount(new BigDecimal("1000000.00"));
        product.setActive(true);
        product.setEffectiveDate(LocalDate.of(2020, 1, 1));
        return product;
    }

    private LoanAccount account(
            String accountNumber,
            Borrower borrower,
            LoanProduct product
    ) {
        LoanAccount account = new LoanAccount();
        account.setAccountNumber(accountNumber);
        account.setBorrower(borrower);
        account.setProduct(product);
        account.setOriginalAmount(new BigDecimal("285000.00"));
        account.setCurrentBalance(new BigDecimal("271432.56"));
        account.setInterestRate(new BigDecimal("4.750"));
        account.setTermMonths(360);
        account.setMonthlyPayment(new BigDecimal("1487.02"));
        account.setOriginationDate(LocalDate.of(2019, 2, 15));
        account.setMaturityDate(LocalDate.of(2049, 2, 15));
        account.setStatus("ACTIVE");
        account.setDelinquencyDays(0);
        account.setEscrowBalance(new BigDecimal("2500.00"));
        account.setCreatedAt(LocalDateTime.of(2019, 2, 15, 0, 0));
        account.setUpdatedAt(LocalDateTime.of(2025, 12, 1, 0, 0));
        return account;
    }

    private Payment payment(
            String externalId,
            LoanAccount account,
            LocalDate paymentDate
    ) {
        Payment payment = new Payment();
        payment.setExternalId(externalId);
        payment.setLoanAccount(account);
        payment.setPaymentDate(paymentDate);
        payment.setTotalAmount(new BigDecimal("1487.02"));
        payment.setPrincipalAmount(new BigDecimal("456.78"));
        payment.setInterestAmount(new BigDecimal("1074.69"));
        payment.setEscrowAmount(new BigDecimal("355.55"));
        payment.setLateFee(new BigDecimal("0.00"));
        payment.setType("REGULAR");
        payment.setStatus("POSTED");
        payment.setReceivedDate(paymentDate);
        payment.setProcessedDate(paymentDate);
        payment.setCreatedAt(paymentDate.atStartOfDay());
        payment.setUpdatedAt(paymentDate.atStartOfDay());
        return payment;
    }
}
