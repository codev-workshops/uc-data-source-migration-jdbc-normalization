package com.workshop.loanservice.migration;

import static com.workshop.loanservice.migration.LegacyDataMigrator.BORROWERS;
import static com.workshop.loanservice.migration.LegacyDataMigrator.LOAN_ACCOUNTS;
import static com.workshop.loanservice.migration.LegacyDataMigrator.LOAN_PRODUCTS;
import static com.workshop.loanservice.migration.LegacyDataMigrator.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.LoanStatus;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.entity.PaymentStatus;
import com.workshop.loanservice.modern.entity.PaymentType;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.LoanProductRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Each test runs in a modern transaction that is rolled back, so it starts from empty tables. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:migrator-test-legacy;DB_CLOSE_DELAY=-1",
            "app.datasource.modern.url=jdbc:h2:mem:migrator-test-modern;DB_CLOSE_DELAY=-1",
            "app.migration.run-on-startup=false"
        })
@Transactional("modernTransactionManager")
class LegacyDataMigratorTest {

    @Autowired private LegacyDataMigrator migrator;
    @Autowired private BorrowerRepository borrowers;
    @Autowired private LoanProductRepository products;
    @Autowired private LoanAccountRepository loans;
    @Autowired private PaymentRepository payments;
    @Autowired private JdbcTemplate legacyJdbc;

    @Test
    void migratesEveryLegacyRowAndPassesValidation() {
        MigrationReport report = migrator.migrate();

        assertThat(report.getProblems()).isEmpty();
        assertThat(report.migrated(BORROWERS)).isEqualTo(5);
        assertThat(report.migrated(LOAN_PRODUCTS)).isEqualTo(5);
        assertThat(report.migrated(LOAN_ACCOUNTS)).isEqualTo(5);
        assertThat(report.migrated(PAYMENTS)).isEqualTo(10);
        assertThat(borrowers.count()).isEqualTo(5);
        assertThat(products.count()).isEqualTo(5);
        assertThat(loans.count()).isEqualTo(5);
        assertThat(payments.count()).isEqualTo(10);
    }

    @Test
    void convertsTypesExpandsCodesAndResolvesForeignKeys() {
        migrator.migrate();

        LoanAccount loan = loans.findByAccountNumber("LN-2019-00142").orElseThrow();
        assertThat(loan.getBorrower().getExternalId()).isEqualTo("B-10001");
        assertThat(loan.getProduct().getCode()).isEqualTo("FXD30");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("285000");
        assertThat(loan.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(loan.getInterestRate()).isEqualByComparingTo("4.750");
        assertThat(loan.getLtvPercent()).isEqualByComparingTo("82.5");
        assertThat(loan.getOriginationDate()).isEqualTo(LocalDate.of(2019, 2, 15));
        assertThat(loan.getCreatedAt()).isEqualTo(LocalDateTime.of(2019, 2, 1, 0, 0));
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(loan.getPropertyType()).isEqualTo("Single Family Residence");
        assertThat(loans.findByAccountNumber("LN-2018-00089").orElseThrow().getDelinquencyDays())
                .isEqualTo(15);

        Payment late = payments.findByLegacyPaymentId("PMT-2025110003").orElseThrow();
        assertThat(late.getLoanAccount().getAccountNumber()).isEqualTo("LN-2018-00089");
        assertThat(late.getPaymentDate()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(late.getLateFee()).isEqualByComparingTo("47.50");
        assertThat(late.getType()).isEqualTo(PaymentType.REGULAR);
        assertThat(late.getStatus()).isEqualTo(PaymentStatus.POSTED);

        Borrower chen = borrowers.findByExternalId("B-10002").orElseThrow();
        assertThat(chen.getAnnualIncome()).isEqualByComparingTo("125000");
        assertThat(chen.getCreditScore()).isEqualTo(780);
        assertThat(chen.getAddressLine2()).isNull();
        assertThat(borrowers.findByExternalId("B-10005").orElseThrow().getMiddleInitial()).isNull();

        LoanProduct va = products.findByCode("VA30").orElseThrow();
        assertThat(va.getMinAmount()).isEqualByComparingTo("0");
        assertThat(va.getActive()).isTrue();
        assertThat(va.getExpirationDate()).isEqualTo(LocalDate.of(2099, 12, 31));
    }

    @Test
    void rerunUpdatesInPlaceWithoutDuplicates() {
        migrator.migrate();
        Map<String, Long> firstRunIds = paymentIdsByLegacyId();

        MigrationReport rerun = migrator.migrate();

        assertThat(rerun.getProblems()).isEmpty();
        assertThat(payments.count()).isEqualTo(10);
        assertThat(loans.count()).isEqualTo(5);
        assertThat(paymentIdsByLegacyId()).isEqualTo(firstRunIds);
    }

    @Test
    void badRowsAreSkippedAndReported() {
        insertLegacyPayment("PMT-BAD-DATE", "LN-2019-00142", "13/45/2025", "100.00");
        insertLegacyPayment("PMT-BAD-AMOUNT", "LN-2019-00142", "10/15/2025", "1,00");
        insertLegacyPayment("PMT-ORPHAN", "LN-9999", "10/15/2025", "100.00");
        try {
            MigrationReport report = migrator.migrate();

            assertThat(report.migrated(PAYMENTS)).isEqualTo(10);
            assertThat(report.getProblems())
                    .containsExactly(
                            "rejected payments PMT-BAD-DATE: invalid date '13/45/2025'",
                            "rejected payments PMT-BAD-AMOUNT: invalid amount '1,00'",
                            "rejected payments PMT-ORPHAN: unknown loan account 'LN-9999'",
                            "payments: 13 legacy rows but 10 modern rows");
            assertThat(payments.findByLegacyPaymentId("PMT-ORPHAN")).isEmpty();
        } finally {
            legacyJdbc.update("DELETE FROM CDW_PMT_HIST WHERE PMT_SEQ_NBR IN (?, ?, ?)",
                    "PMT-BAD-DATE", "PMT-BAD-AMOUNT", "PMT-ORPHAN");
        }
    }

    private Map<String, Long> paymentIdsByLegacyId() {
        return payments.findAll().stream()
                .collect(Collectors.toMap(Payment::getLegacyPaymentId, Payment::getId));
    }

    private void insertLegacyPayment(String id, String loan, String date, String amount) {
        legacyJdbc.update(
                "INSERT INTO CDW_PMT_HIST VALUES (?, ?, ?, ?, '0.00', '0.00', '0.00', '0.00',"
                        + " 'REG', 'PST', NULL, NULL, NULL, NULL)",
                id, loan, date, amount);
    }
}
