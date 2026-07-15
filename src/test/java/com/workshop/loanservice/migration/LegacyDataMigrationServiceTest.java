package com.workshop.loanservice.migration;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacymigration;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-legacy.sql,classpath:schema-modern.sql",
        "spring.sql.init.data-locations=classpath:data-legacy.sql"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyDataMigrationServiceTest {

    @Autowired
    private LegacyDataMigrationService migrationService;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratesLegacyRowsIntoModernTablesWithTypedValuesAndExternalIds() {
        LegacyDataMigrationResult result = migrationService.migrate();

        assertThat(result).isEqualTo(new LegacyDataMigrationResult(5, 5, 5, 10, false));
        assertModernCounts(5, 5, 5, 10);

        Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();
        assertThat(borrower.getFirstName()).isEqualTo("James");
        assertThat(borrower.getDateOfBirth()).isEqualTo(LocalDate.of(1978, 3, 15));
        assertThat(borrower.getAnnualIncome()).isEqualByComparingTo(new BigDecimal("92500.00"));
        assertThat(borrower.getStatus()).isEqualTo("ACTIVE");

        LoanAccount loanAccount = loanAccountRepository.findByAccountNumber("LN-2019-00142")
                .orElseThrow();
        assertThat(loanAccount.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(loanAccount.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(loanAccount.getOriginalAmount()).isEqualByComparingTo("285000.00");
        assertThat(loanAccount.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(loanAccount.getPropertyType()).isEqualTo("Single Family Residence");

        Payment payment = paymentRepository.findByExternalId("PMT-2025120001").orElseThrow();
        assertThat(payment.getLoanAccount().getAccountNumber()).isEqualTo("LN-2019-00142");
        assertThat(payment.getPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(payment.getType()).isEqualTo("REGULAR");
        assertThat(payment.getStatus()).isEqualTo("POSTED");
    }

    @Test
    void returnsNoOpResultWhenCompleteMigrationIsRerun() {
        migrationService.migrate();

        LegacyDataMigrationResult result = migrationService.migrate();

        assertThat(result).isEqualTo(new LegacyDataMigrationResult(5, 5, 5, 10, true));
        assertModernCounts(5, 5, 5, 10);
    }

    @Test
    void rejectsCompleteTargetWhenMigratedValuesConflictWithLegacySource() {
        migrationService.migrate();
        Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();
        borrower.setCity("Different City");
        borrowerRepository.saveAndFlush(borrower);

        assertThatThrownBy(() -> migrationService.migrate())
                .isInstanceOf(LegacyDataMigrationException.class)
                .hasMessageContaining("conflicts");
        assertModernCounts(5, 5, 5, 10);
    }

    @Test
    void rejectsPartialModernTargetBeforeWritingAdditionalRows() {
        Borrower borrower = new Borrower();
        borrower.setExternalId("B-PARTIAL");
        borrower.setFirstName("Partial");
        borrower.setLastName("Target");
        borrower.setStatus("ACTIVE");
        borrowerRepository.saveAndFlush(borrower);

        assertThatThrownBy(() -> migrationService.migrate())
                .isInstanceOf(LegacyDataMigrationException.class)
                .hasMessageContaining("partially populated");
        assertModernCounts(1, 0, 0, 0);
    }

    @Test
    void rollsBackWhenMalformedRequiredLegacyValueIsEncountered() {
        jdbcTemplate.update(
                """
                INSERT INTO CDW_LN_PROD VALUES (
                    'BAD01', 'Bad Product', 'BAD', 'BAD', 'FIXED',
                    '50,000', '60,000', 'ACT', '01/01/2020', '12/31/2099'
                )
                """
        );

        assertThatThrownBy(() -> migrationService.migrate())
                .isInstanceOf(LegacyDataMigrationException.class)
                .hasMessageContaining("PROD_TERM_MOS");
        assertModernCounts(0, 0, 0, 0);
    }

    private void assertModernCounts(
            int borrowers,
            int products,
            int loanAccounts,
            int payments
    ) {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM borrowers", Integer.class))
                .isEqualTo(borrowers);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM loan_products", Integer.class))
                .isEqualTo(products);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM loan_accounts", Integer.class))
                .isEqualTo(loanAccounts);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class))
                .isEqualTo(payments);
    }
}
